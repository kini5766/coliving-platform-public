-- document-service DB 초기화 스크립트
-- postgres DB에 접속한 상태에서 psql로 실행한다.
SELECT current_database();

DROP DATABASE IF EXISTS document_service WITH (FORCE);

CREATE DATABASE document_service ENCODING 'UTF8';

-- psql 전용. 이 줄이 없으면 아래 테이블이 postgres DB에 만들어진다.
\c document_service

CREATE TABLE document_forms (
                                id          BIGSERIAL    PRIMARY KEY,
                                language    VARCHAR(10)  NOT NULL DEFAULT 'ko' CHECK (language IN ('ko', 'en')),  -- 양식 언어
    title       VARCHAR(200) NOT NULL,                                                -- 양식 이름
    content     TEXT         NOT NULL,                                                -- 빈칸이 있는 본문(HTML)
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
