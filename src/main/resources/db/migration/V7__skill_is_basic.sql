-- 기본 공격 판정을 "비용 0"에서 컬럼으로 바꾼다.
-- 값은 R__seed_data.sql이 채운다 (이 파일 뒤에 실행되고, 이번에 내용이 바뀌어 기존 DB에서도 다시 실행된다).
ALTER TABLE skill ADD COLUMN is_basic BOOLEAN NOT NULL DEFAULT FALSE;
