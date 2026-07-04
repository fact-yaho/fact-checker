-- ============================================================
-- 코퍼스 문서 스키마 변경
--
-- ddl-auto=update 가 처리하지 못하는 제약 변경(기존 컬럼 NOT NULL 제거/추가)을 수동 실행
-- 앱을 한 번 실행해 Hibernate 가 컬럼(source_type)을 생성한 "이후"에 1회 실행
--
-- ▶ 실행 방법 (도커)
--   docker exec -it factchecker-postgres \
--     psql -U user -d factchecker -f /tmp/corpus-schema.sql
--   (또는 -c 로 개별 실행. -U/-d 는 .env 값과 일치)
--
-- 여러 번 실행해도 안전하도록 작성됨
-- ============================================================

-- 1) claim_id: 코퍼스 문서는 소주장과 무관 → NOT NULL 제거
ALTER TABLE evidence_document
    ALTER COLUMN claim_id DROP NOT NULL;

-- 2) source_type: 기존 데이터가 있어도 안전하게 NOT NULL 전환
--    (DEFAULT 지정 → 기존 NULL 행 백필 → NOT NULL 순서)

-- 2-1) DB 레벨 기본값 지정 (신규 INSERT 시 값 누락돼도 PER_CLAIM)
ALTER TABLE evidence_document
    ALTER COLUMN source_type SET DEFAULT 'PER_CLAIM';

-- 2-2) 기존 행 백필 (컬럼 추가 시 NULL 로 남은 기존 데이터를 PER_CLAIM 으로)
UPDATE evidence_document
SET source_type = 'PER_CLAIM'
WHERE source_type IS NULL;

-- 2-3) 백필 완료 후 NOT NULL 제약 적용
ALTER TABLE evidence_document
    ALTER COLUMN source_type SET NOT NULL;
