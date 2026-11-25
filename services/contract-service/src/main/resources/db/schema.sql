-- contract-service 배포용 스키마
-- contract_service 데이터베이스에 연결한 뒤 실행한다.
--   psql -d contract_service -v ON_ERROR_STOP=1 -f schema.sql
-- 데이터베이스와 기존 행은 지우지 않는다. 없는 테이블과 인덱스만 만든다.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 신청번호 생성
CREATE SEQUENCE IF NOT EXISTS application_no_seq;
-- 1. 신청(계약) 본체: 신청 = 계약 1:1, DRAFT부터 COMPLETED까지 한 행
CREATE TABLE IF NOT EXISTS contract_application (
                                      id                 BIGSERIAL    PRIMARY KEY,
                                      application_no     VARCHAR(30)  NOT NULL UNIQUE,      -- 화면 표시용 신청번호
                                      user_id            BIGINT       NOT NULL,             -- user-service 회원 ID (JWT에서 꺼냄)
                                      room_id            BIGINT       NOT NULL,             -- 호실 서비스 ID, 다른 서비스라 FK 없음
                                      building_id        BIGINT       NOT NULL,             -- 건물 ID (매물 서비스, FK 없음)
                                      building_name      VARCHAR(100) NOT NULL,             -- 건물명 스냅샷 (DRAFT 때 호실 서비스에서 복사, 호실 바꾸면 갱신)
                                      address            VARCHAR(500) NOT NULL,             -- 건물 주소 (신청자 주소는 applicant_address)
                                      room_name          VARCHAR(100) NOT NULL,             -- 호실명 스냅샷
                                      room_type          VARCHAR(255) NOT NULL,             -- "1인실"
                                      room_structure     VARCHAR(100),                      -- 매물 쪽에 없을 수 있음
                                      floor_number       INTEGER      NOT NULL,
                                      area               NUMERIC(10,2) NOT NULL,            -- ㎡
                                      room_facilities    TEXT         NOT NULL,             -- "전용 욕실, 발코니"
                                      move_in_date       DATE         NOT NULL,
                                      move_out_date      DATE         NOT NULL,             -- 퇴실 예정일
                                      stay_period        DATERANGE    GENERATED ALWAYS AS
                                          (daterange(move_in_date, move_out_date, '[)')) STORED,
                                      contract_months    SMALLINT     NOT NULL CHECK (contract_months > 0),
                                      status             VARCHAR(30)  NOT NULL DEFAULT 'DRAFT'
                                          CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','PAID','COMPLETED',
                                                            'REJECTED','CANCELED','EXPIRED','REFUNDED',
                                                            'MOVE_IN_CANCEL_REQUESTED','TERMINATION_REQUESTED','TERMINATED')),
    -- 금액 스냅샷: DRAFT 동안 재계산, SUBMITTED에서 고정 (재신청은 새 신청이라 그 시점 가격)
                                      monthly_fee        BIGINT       NOT NULL,
                                      deposit            BIGINT       NOT NULL,
                                      management_fee     BIGINT       NOT NULL,             -- 관리비 (월)
                                      discount_amount    BIGINT       NOT NULL DEFAULT 0,
                                      discount_note      VARCHAR(200),
                                      first_payment      BIGINT       NOT NULL,             -- 첫 달 이용료 + 보증금 - 할인
                                      amount_fixed_at    TIMESTAMPTZ,
    -- 신청자 정보 스냅샷: 이름·휴대폰은 멤버 서비스 값 그대로, 이메일·주소만 수정 가능
                                      applicant_name     VARCHAR(255),                      -- 암호화 저장이라 길이 여유
                                      applicant_email    VARCHAR(255),
                                      applicant_phone    VARCHAR(255),
                                      applicant_address  VARCHAR(500),
                                      contract_language  VARCHAR(10),                       -- ko, en ...
    -- 약관 동의: 약관 1개, 동의해야 다음 단계로 진행 (2단계에서 UPDATE)
                                      terms_version      VARCHAR(20),
                                      terms_agreed_at    TIMESTAMPTZ,
                                      reject_reason      VARCHAR(500),
                                      expires_at         TIMESTAMPTZ,                       -- 작성 2일 / 결제 1일
                                      version            BIGINT       NOT NULL DEFAULT 0,   -- JPA @Version
                                      created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                      updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                      CONSTRAINT chk_period CHECK (move_out_date > move_in_date),
    -- 약관 동의 없이는 제출 불가 (DRAFT, DRAFT에서 만료된 EXPIRED만 비어 있을 수 있음)
                                      CONSTRAINT chk_terms_agreed CHECK (status IN ('DRAFT','EXPIRED') OR terms_agreed_at IS NOT NULL),
    -- 진행 중 상태, 취소·퇴실 요청 중, TERMINATED(확정 퇴실일까지)는 기간 중복 금지 (REJECTED는 즉시 해제)
                                      CONSTRAINT no_overlap_booking EXCLUDE USING gist (
        room_id WITH =, stay_period WITH &&
    ) WHERE (status IN ('DRAFT','SUBMITTED','APPROVED','PAID','COMPLETED',
                        'MOVE_IN_CANCEL_REQUESTED','TERMINATION_REQUESTED','TERMINATED'))
);
CREATE INDEX IF NOT EXISTS idx_app_user    ON contract_application (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_expires ON contract_application (status, expires_at);

-- 2. 상태 이력: 상태가 바뀔 때마다 같은 트랜잭션에서 1행
CREATE TABLE IF NOT EXISTS contract_status_history (
                                         id               BIGSERIAL    PRIMARY KEY,
                                         application_id   BIGINT       NOT NULL REFERENCES contract_application(id),
                                         from_status      VARCHAR(30),
                                         to_status        VARCHAR(30)  NOT NULL,
                                         changed_by_type  VARCHAR(10)  NOT NULL CHECK (changed_by_type IN ('USER','ADMIN','SYSTEM')),
                                         changed_by_id    BIGINT,                              -- SYSTEM(스케줄러)이면 NULL
                                         reason           VARCHAR(500),                        -- 반려 사유 등
                                         created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_hist_app ON contract_status_history (application_id, created_at);

-- 2-1. 계약서: 관리자 승인 시 생성 (신청 1 : 계약서 1). 문서 서비스는 양식만 제공, 저장은 계약 서비스
CREATE TABLE IF NOT EXISTS contract_document (
    id               BIGSERIAL    PRIMARY KEY,
    application_id   BIGINT       NOT NULL UNIQUE REFERENCES contract_application(id),  -- 중복 생성 방지
    language         VARCHAR(10)  NOT NULL CHECK (language IN ('ko', 'en')),
    template_id      BIGINT       NOT NULL,              -- 사용한 양식 = 문서 서비스 document_forms.id (FK 없음)
    content          TEXT         NOT NULL,              -- 완성된 계약서 본문 (저장 후 가공 금지)
    content_hash     CHAR(64)     NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),  -- content(UTF-8)의 SHA-256
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 계약서 수정·삭제 금지 (개발 중 정리: ALTER TABLE contract_document DISABLE/ENABLE TRIGGER USER)
-- 예외: 신청이 REJECTED·CANCELED·EXPIRED면 DELETE 허용 (개인정보 파기 스케줄러용)
CREATE OR REPLACE FUNCTION forbid_contract_document_change() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND EXISTS (
        SELECT 1 FROM contract_application a
         WHERE a.id = OLD.application_id
           AND a.status IN ('REJECTED', 'CANCELED', 'EXPIRED')
    ) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'contract_document는 수정하거나 삭제할 수 없습니다 (%)', TG_OP;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_contract_document_no_update_delete ON contract_document;
CREATE TRIGGER trg_contract_document_no_update_delete
    BEFORE UPDATE OR DELETE ON contract_document
    FOR EACH ROW EXECUTE FUNCTION forbid_contract_document_change();
DROP TRIGGER IF EXISTS trg_contract_document_no_truncate ON contract_document;
CREATE TRIGGER trg_contract_document_no_truncate
    BEFORE TRUNCATE ON contract_document
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_contract_document_change();

-- 3. 본인인증·전자서명: 서명한 계약서(contract_document) ID와 해시를 서명 시점 값으로 보관
CREATE TABLE IF NOT EXISTS contract_signature (
                                    id                        BIGSERIAL    PRIMARY KEY,
                                    application_id            BIGINT       NOT NULL REFERENCES contract_application(id),
                                    document_id               BIGINT       NOT NULL REFERENCES contract_document(id), -- 서명한 계약서
                                    identity_verification_id  VARCHAR(100) NOT NULL UNIQUE,      -- 포트원 인증 거래 ID, 1회만 사용
                                    verified_name             VARCHAR(255),                      -- 암호화 저장
                                    document_hash             CHAR(64)     NOT NULL,             -- 서명 시점의 문서 SHA-256
                                    signature_image_path      VARCHAR(500),                      -- ContractFileStorage 경로
                                    signed_pdf_path           VARCHAR(500),                      -- 서명이 들어간 계약서 PDF 경로
                                    signed_pdf_hash           CHAR(64),                          -- PDF 파일의 SHA-256
                                    signer_ip                 VARCHAR(45),
                                    signed_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                    UNIQUE (application_id, document_id)                         -- 같은 문서에 서명은 1번
);
CREATE INDEX IF NOT EXISTS idx_sign_app ON contract_signature (application_id, signed_at DESC);

-- 4. 결제: 결제 시도마다 1행 (신청 1 : 결제 N)
CREATE TABLE IF NOT EXISTS payment (
                         id              BIGSERIAL    PRIMARY KEY,
                         application_id  BIGINT       NOT NULL REFERENCES contract_application(id),
                         order_id        VARCHAR(64)  NOT NULL UNIQUE,     -- 카카오 partner_order_id
                         tid             VARCHAR(100) UNIQUE,              -- ready 직후 저장
                         amount          BIGINT       NOT NULL,
                         status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING','PAID','FAILED','CANCELED','CANCEL_REQUESTED','REFUNDED')),
                         payment_method  VARCHAR(20),                      -- MONEY / CARD
                         approved_at     TIMESTAMPTZ,
                         fail_reason     VARCHAR(500),
                         canceled_at     TIMESTAMPTZ,
                         created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                         updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 한 신청에 성공한 결제는 1건만
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_one_paid ON payment (application_id) WHERE status = 'PAID';
-- PENDING 보정 작업 대상 조회용
CREATE INDEX IF NOT EXISTS idx_payment_pending ON payment (created_at) WHERE status = 'PENDING';

-- 5. 아웃박스: 결제 확정과 같은 트랜잭션에 저장, 발행 작업이 Kafka로 보냄
CREATE TABLE IF NOT EXISTS outbox_event (
                              id               BIGSERIAL    PRIMARY KEY,
                              event_id         UUID         NOT NULL UNIQUE,        -- 소비자 중복 처리용
                              aggregate_id     BIGINT       NOT NULL,               -- 신청 ID (Kafka 메시지 키)
                              event_type       VARCHAR(50)  NOT NULL,               -- PaymentCompleted ...
                              payload          JSONB        NOT NULL,
                              status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
                              retry_count      INT          NOT NULL DEFAULT 0,
                              last_error       VARCHAR(1000),
                              created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
                              published_at     TIMESTAMPTZ
);
-- 같은 신청에 결제 완료 이벤트는 1건만 (정상 승인과 보정이 겹쳐도 중복 저장 방지)
CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_payment_completed ON outbox_event (aggregate_id)
    WHERE event_type = 'PaymentCompleted';
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_event (id) WHERE status = 'PENDING';

-- 6. ShedLock (스케줄러 방식을 ShedLock으로 정할 때)
CREATE TABLE IF NOT EXISTS shedlock (
                          name        VARCHAR(64)  PRIMARY KEY,
                          lock_until  TIMESTAMP    NOT NULL,
                          locked_at   TIMESTAMP    NOT NULL,
                          locked_by   VARCHAR(255) NOT NULL
);

-- 7. 결제 영수증: 결제 1건에 결제 영수증 1개, 취소 시 취소 영수증 1개
CREATE TABLE IF NOT EXISTS payment_receipt (
                                 id            BIGSERIAL    PRIMARY KEY,
                                 payment_id    BIGINT       NOT NULL REFERENCES payment(id),
                                 receipt_type  VARCHAR(20)  NOT NULL CHECK (receipt_type IN ('PAYMENT','CANCEL')),
                                 file_path     VARCHAR(500) NOT NULL,               -- 오브젝트 스토리지의 PDF
                                 content_hash  CHAR(64)     NOT NULL,               -- SHA-256, 발급 시점 값
                                 emailed_at    TIMESTAMPTZ,                         -- 이메일 발송 완료 시각 (NULL이면 미발송)
                                 issued_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                 UNIQUE (payment_id, receipt_type)
);

-- applicant_profile 은 더 이상 쓰지 않는다. 신청자 정보는 제출 본문으로 받는다.
DROP TABLE IF EXISTS applicant_profile;
