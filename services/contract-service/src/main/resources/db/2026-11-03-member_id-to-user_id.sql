-- member_id → user_id 이름 변경 (2026-11-03)
-- 이미 만들어진 DB에 한 번만 실행한다. 새로 만드는 DB는 schma-dev-contract-service.sql에 이미 반영됨.
-- ⚠ contract-service를 켜기 전에 실행할 것.
--   dev는 ddl-auto: update라서 이 SQL 없이 켜면 user_id 컬럼을 새로 만들려다 실패하거나(기존 행 NOT NULL),
--   member_id와 user_id가 둘 다 남는다. 운영(validate)은 user_id가 없으면 서버가 뜨지 않는다.

\c contract_service

ALTER TABLE contract_application RENAME COLUMN member_id TO user_id;
ALTER INDEX idx_app_member RENAME TO idx_app_user;

-- 확인: user_id가 보이고 member_id가 없어야 한다
SELECT column_name FROM information_schema.columns
 WHERE table_name = 'contract_application' AND column_name IN ('member_id', 'user_id');
