package com.yaho.factchecker.domain.result.entity;

import com.yaho.factchecker.domain.result.code.AnalysisStatus;
import com.yaho.factchecker.global.type.InputType;
import com.yaho.factchecker.global.type.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(name = "analysis_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = """
    UPDATE analysis_result 
    SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP 
    WHERE id = ?
""")
public class AnalysisResult {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 모든 사용자의 결과 저장
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * TEXT, URL 등
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", length = 20, nullable = false)
    private InputType inputType;

    /**
     * 사용자가 입력한 원문
     */
    @Column(name = "original_input", columnDefinition = "TEXT", nullable = false)
    private String originalInput;

    /**
     * 정제된 핵심 주장
     */
    @Column(name = "claim_text", columnDefinition = "TEXT", nullable = false)
    private String claimText;

    /**
     * URL 입력인 경우 원본 URL
     */
    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    /**
     * 최종 신뢰도 점수
     */
    @Column(name = "final_score", nullable = false)
    private Double finalScore;

    /**
     * 최종 판정
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", length = 30, nullable = false)
    private Verdict verdict;

    /**
     * 질문 한 줄 요약
     */
    @Column(name = "question_summary", columnDefinition = "TEXT", nullable = false)
    private String questionSummary;

    /**
     * 분석 결과 한 줄 요약
     */
    @Column(name = "answer_summary", columnDefinition = "TEXT", nullable = false)
    private String answerSummary;

    /**
     * 전체 분석에 대한 상세 설명
     */
    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    /**
     * 분석 처리 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 30, nullable = false)
    private AnalysisStatus analysisStatus;

    /**
     * 사용한 AI 모델 버전
     */
    @Column(name = "model_version", length = 50)
    private String modelVersion;

    /**
     * 점수 계산 로직 버전
     */
    @Column(name = "scoring_version", length = 50)
    private String scoringVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Factory Methods ----------------------------------------------------------------------
    // TODO: DTO -> Entity 메서드 추가 필요
    @Builder
    private AnalysisResult(
        UUID userId,
        InputType inputType,
        String originalInput,
        String claimText,
        String sourceUrl,
        Double finalScore,
        Verdict verdict,
        String questionSummary,
        String answerSummary,
        String explanation,
        AnalysisStatus analysisStatus,
        String modelVersion,
        String scoringVersion
    ) {
        this.userId = userId;
        this.inputType = inputType;
        this.originalInput = originalInput;
        this.claimText = claimText;
        this.sourceUrl = sourceUrl;
        this.finalScore = finalScore;
        this.verdict = verdict;
        this.questionSummary = questionSummary;
        this.answerSummary = answerSummary;
        this.explanation = explanation;
        this.analysisStatus = analysisStatus;
        this.modelVersion = modelVersion;
        this.scoringVersion = scoringVersion;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.analysisStatus == null) {
            this.analysisStatus = AnalysisStatus.COMPLETED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateResult(
        Double finalScore,
        Verdict verdict,
        String questionSummary,
        String answerSummary,
        String explanation,
        AnalysisStatus analysisStatus
    ) {
        this.finalScore = finalScore;
        this.verdict = verdict;
        this.questionSummary = questionSummary;
        this.answerSummary = answerSummary;
        this.explanation = explanation;
        this.analysisStatus = analysisStatus;
    }

    public void markFailed(String explanation) {
        this.analysisStatus = AnalysisStatus.FAILED;
        this.explanation = explanation;
    }

    public void restore() {
        this.deletedAt = null;
    }
}
