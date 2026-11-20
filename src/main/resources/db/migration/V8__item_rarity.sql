-- 장비·스킬에 유물과 같은 등급(COMMON / RARE / EPIC)을 붙인다. 상점과 전리품은 스테이지별 확률로 등급을 먼저 정한다.
-- 값은 R__seed_data.sql이 채운다 (이 파일 뒤에 실행되고, 이번에 내용이 바뀌어 기존 DB에서도 다시 실행된다).
ALTER TABLE weapon ADD COLUMN rarity VARCHAR(20) NOT NULL DEFAULT 'COMMON';
ALTER TABLE armor  ADD COLUMN rarity VARCHAR(20) NOT NULL DEFAULT 'COMMON';
ALTER TABLE helmet ADD COLUMN rarity VARCHAR(20) NOT NULL DEFAULT 'COMMON';
ALTER TABLE skill  ADD COLUMN rarity VARCHAR(20) NOT NULL DEFAULT 'COMMON';
ALTER TABLE weapon ADD CONSTRAINT weapon_rarity_check CHECK (rarity IN ('COMMON', 'RARE', 'EPIC'));
ALTER TABLE armor  ADD CONSTRAINT armor_rarity_check  CHECK (rarity IN ('COMMON', 'RARE', 'EPIC'));
ALTER TABLE helmet ADD CONSTRAINT helmet_rarity_check CHECK (rarity IN ('COMMON', 'RARE', 'EPIC'));
ALTER TABLE skill  ADD CONSTRAINT skill_rarity_check  CHECK (rarity IN ('COMMON', 'RARE', 'EPIC'));
