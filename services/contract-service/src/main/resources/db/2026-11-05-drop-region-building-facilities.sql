-- 2026-11-05 계약 신청서에서 지역(region)·건물 공용시설(building_facilities) 컬럼 삭제
-- 호실 정보를 화면이 매물 서비스 호실 API에서 받아 보내는 방식으로 바꾸면서, 매물 응답에 없는 지역·건물 공용시설은 저장하지 않기로 함
-- 건물 주소(address)는 계약서 본문(buildingAddress)에 쓰이므로 그대로 둔다
ALTER TABLE contract_application DROP COLUMN IF EXISTS region;
ALTER TABLE contract_application DROP COLUMN IF EXISTS building_facilities;
