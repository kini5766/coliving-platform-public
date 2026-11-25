-- document-service 배포용 스키마
-- document_service 데이터베이스에 연결한 뒤 실행한다.
--   psql -d document_service -v ON_ERROR_STOP=1 -f schema.sql
-- 데이터베이스와 기존 행은 지우지 않는다. 없는 테이블만 만든다.

CREATE TABLE IF NOT EXISTS document_forms (
    id          BIGSERIAL    PRIMARY KEY,
    language    VARCHAR(10)  NOT NULL DEFAULT 'ko' CHECK (language IN ('ko', 'en')),
    title       VARCHAR(200) NOT NULL,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE document_forms IS '계약서 양식. language 는 ko 또는 en';
COMMENT ON COLUMN document_forms.content IS '빈칸이 있는 본문(HTML)';
