package com.yaho.factchecker.domain.retrieval.service;

import com.yaho.factchecker.domain.retrieval.dto.Bm25Result;
import com.yaho.factchecker.domain.retrieval.entity.EvidenceDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class Bm25Scorer {

    private static final String FIELD_ID = "evidence_document_id";
    private static final String FIELD_TEXT = "text";

    // 국가명 부스트 배수 — 핵심 엔티티(국가)가 없는 문서를 밀어내기 위한 가중치
    private static final int COUNTRY_BOOST = 10;

    // 기존 호출 호환용 (국가 부스트 없음)
    public List<Bm25Result> score(String query, List<EvidenceDocument> documents) {
        return score(query, List.of(), documents);
    }

    /**
     * 소주장 + 핵심 국가명으로 BM25 점수/순위 계산
     *
     * <p>BM25는 어휘 빈도·문서 길이 기반이라, 소주장 텍스트만으로는 핵심 엔티티(국가명)가
     * 없는 문서도 공통 어휘("외교관계", "수립" 등)만으로 상위에 오를 수 있음
     * (예: "한국은 루마니아와 외교관계를 유지하고 있다" → "한-몬테네그로 수교" 문서가 1위)
     *
     * <p>이를 막기 위해 claim에서 추출된 국가명을 부스트 항으로 추가해,
     * 해당 국가가 언급된 문서가 유의미하게 앞서도록 함
     *
     * query = 정제된 소주장 텍스트
     * countries = 소주장에서 추출된 국가명 목록 (없으면 빈 리스트)
     * documents = 후보 근거문서
     */
    public List<Bm25Result> score(String query, List<String> countries,
                                  List<EvidenceDocument> documents) {
        if (documents == null || documents.isEmpty() || query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try (Analyzer analyzer = new KoreanAnalyzer();
             Directory directory = new ByteBuffersDirectory()) {

            indexDocuments(directory, analyzer, documents);
            return search(directory, analyzer, query, countries, documents);

        } catch (Exception e) {
            log.error("BM25 점수 계산 실패. query='{}', docCount={}", query, documents.size(), e);
            return Collections.emptyList();
        }
    }

    private void indexDocuments(Directory directory, Analyzer analyzer,
                                List<EvidenceDocument> documents) throws Exception {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (EvidenceDocument doc : documents) {
                String combinedText = buildText(doc);
                Document luceneDoc = new Document();
                luceneDoc.add(new StringField(FIELD_ID, doc.getEvidenceDocumentId().toString(), Field.Store.YES));
                luceneDoc.add(new TextField(FIELD_TEXT, combinedText, Field.Store.NO));
                writer.addDocument(luceneDoc);
            }
        }
    }

    private List<Bm25Result> search(Directory directory, Analyzer analyzer,
                                    String query, List<String> countries,
                                    List<EvidenceDocument> documents) throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());

            QueryParser parser = new QueryParser(FIELD_TEXT, analyzer);
            Query luceneQuery = parser.parse(buildQueryString(query, countries));

            TopDocs topDocs = searcher.search(luceneQuery, documents.size());

            java.util.Map<UUID, Double> scoreMap = new java.util.HashMap<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                UUID id = UUID.fromString(document.get(FIELD_ID));
                scoreMap.put(id, (double) scoreDoc.score);
            }

            List<Bm25Result> results = new ArrayList<>();
            for (EvidenceDocument doc : documents) {
                UUID id = doc.getEvidenceDocumentId();
                double score = scoreMap.getOrDefault(id, 0.0);
                results.add(new Bm25Result(id, score, 0));
            }

            results.sort((a, b) -> Double.compare(b.score(), a.score()));

            double max = results.get(0).score();
            double min = results.get(results.size() - 1).score();

            List<Bm25Result> ranked = new ArrayList<>(results.size());
            for (int i = 0; i < results.size(); ++i) {
                Bm25Result r = results.get(i);
                double normalized = normalizeScore(r.score(), min, max);
                ranked.add(new Bm25Result(r.evidenceDocumentId(), normalized, i + 1));
            }
            return ranked;
        }
    }

    /**
     * 소주장 텍스트 + 국가명 부스트 항으로 Lucene 쿼리 문자열을 만듦
     * 예: "한국은 루마니아와 외교관계를 유지하고 있다" + [루마니아]
     *   → 한국은\ 루마니아와\ ... 루마니아^3
     */
    private String buildQueryString(String query, List<String> countries) {
        StringBuilder sb = new StringBuilder(QueryParser.escape(query));

        if (countries == null || countries.isEmpty()) {
            log.warn("[BM25] 국가명 없음 → 부스트 미적용");
            return sb.toString();
        }

        for (String country : countries) {
            if (country == null || country.isBlank()) continue;
            sb.append(' ')
                    .append(QueryParser.escape(country.trim()))
                    .append('^')
                    .append(COUNTRY_BOOST);
        }

        log.info("[BM25] 부스트 쿼리 = {}", sb);
        return sb.toString();
    }

    private double normalizeScore(double score, double min, double max) {
        if (max == 0.0) {
            return 0.0;
        }
        if (max == min) {
            return 1.0;
        }
        return (score - min) / (max - min);
    }

    private String buildText(EvidenceDocument doc) {
        String title = doc.getTitle() == null ? "" : doc.getTitle();
        String content = doc.getContentCleaned() == null ? "" : doc.getContentCleaned();
        return (title + " " + content).trim();
    }
}
