-- DB 생성
CREATE DATABASE "renova_n_delve";

-- EXTENSION VECTOR 설치
CREATE EXTENSION IF NOT EXISTS vector;

-----------------------------------------

-- 1. 플레이어
CREATE TABLE SKILL
(
    skill_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 기술 고유 ID
    name          VARCHAR(50)   NOT NULL,                          -- 기술명 (출혈, 화상 등)
    attack_count  INT           NOT NULL,                          -- 공격 횟수
    coefficient   DECIMAL(5, 2) NOT NULL DEFAULT 1.00,             -- 기본 계수 (공격력 배율)
    cost          INT           NOT NULL DEFAULT 0,                -- 기술 사용 비용 (AP 등)
    cooldown      INT           NOT NULL DEFAULT 0,                -- 쿨타임 (턴 수)
    status_effect SMALLINT,                                        -- 부여 상태이상 (0:없음 / 1:출혈 / 2:화상 / 3:독 / 4:기절)
    effect_stack  INT,                                             -- 상태이상 스택 수
    description   TEXT                                             -- 기술 설명
);
COMMENT ON TABLE SKILL IS '캐릭터가 사용하는 기술 목록';
COMMENT ON COLUMN SKILL.skill_id IS '기술 고유 ID';
COMMENT ON COLUMN SKILL.name IS '기술명 (출혈, 화상 등)';
COMMENT ON COLUMN SKILL.attack_count IS '공격 횟수';
COMMENT ON COLUMN SKILL.coefficient IS '기본 계수 (공격력 배율)';
COMMENT ON COLUMN SKILL.cost IS '기술 사용 비용 (AP 등)';
COMMENT ON COLUMN SKILL.cooldown IS '쿨타임 (턴 수)';
COMMENT ON COLUMN SKILL.status_effect IS '부여 상태이상 (0:없음 / 1:출혈 / 2:화상 / 3:독 / 4:기절)';
COMMENT ON COLUMN SKILL.effect_stack IS '상태이상 스택 수';
COMMENT ON COLUMN SKILL.description IS '기술 설명';


CREATE TABLE WEAPON
(
    weapon_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 무기 고유 ID
    name           VARCHAR(50) NOT NULL,                            -- 무기명
    type           VARCHAR(20) NOT NULL,                            -- 검/단검/둔기/스태프
    atk_bonus      INT         NOT NULL DEFAULT 0,                  -- 추가 공격력 (단검: 음수, 둔기: 양수)
    special_effect SMALLINT,                                        -- 무기 특수 효과 코드
    score_cost     INT         NOT NULL DEFAULT 0,                  -- 스타팅 포인트 비용 (기본 세트=0)
    description    TEXT                                             -- 무기 설명
);
COMMENT ON TABLE WEAPON IS '무기 종류별 기본 스탯 및 특성 정의';
COMMENT ON COLUMN WEAPON.weapon_id IS '무기 고유 ID';
COMMENT ON COLUMN WEAPON.name IS '무기명';
COMMENT ON COLUMN WEAPON.type IS '검/단검/둔기/스태프';
COMMENT ON COLUMN WEAPON.atk_bonus IS '추가 공격력 (단검: 음수, 둔기: 양수)';
COMMENT ON COLUMN WEAPON.special_effect IS '무기 특수 효과 코드';
COMMENT ON COLUMN WEAPON.score_cost IS '스타팅 포인트 비용 (기본 세트=0)';
COMMENT ON COLUMN WEAPON.description IS '무기 설명';


CREATE TABLE ARMOR
(
    armor_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 갑옷 고유 ID
    name           VARCHAR(50) NOT NULL,                            -- 갑옷명
    type           VARCHAR(20) NOT NULL,                            -- 가죽/천/체인/판금
    def_bonus      INT         NOT NULL DEFAULT 0,                  -- 추가 방어력
    special_effect SMALLINT,                                        -- 갑옷 특수 효과 코드
    score_cost     INT         NOT NULL DEFAULT 0,                  -- 스타팅 포인트 비용
    description    TEXT                                             -- 갑옷 설명
);
COMMENT ON TABLE ARMOR IS '갑옷 종류별 기본 스탯 및 특성 정의';
COMMENT ON COLUMN ARMOR.armor_id IS '갑옷 고유 ID';
COMMENT ON COLUMN ARMOR.name IS '갑옷명';
COMMENT ON COLUMN ARMOR.type IS '가죽/천/체인/판금';
COMMENT ON COLUMN ARMOR.def_bonus IS '추가 방어력';
COMMENT ON COLUMN ARMOR.special_effect IS '갑옷 특수 효과 코드';
COMMENT ON COLUMN ARMOR.score_cost IS '스타팅 포인트 비용';
COMMENT ON COLUMN ARMOR.description IS '갑옷 설명';


CREATE TABLE HELMET
(
    helmet_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 투구 고유 ID
    name           VARCHAR(50) NOT NULL,                            -- 투구명
    type           VARCHAR(20) NOT NULL,                            -- 가죽모자/천/판금/가면
    hp_bonus       INT         NOT NULL DEFAULT 0,                  -- 추가 체력
    special_effect SMALLINT,                                        -- 투구 특수 효과 코드
    score_cost     INT         NOT NULL DEFAULT 0,                  -- 스타팅 포인트 비용
    description    TEXT                                             -- 투구 설명
);
COMMENT ON TABLE HELMET IS '투구 종류별 기본 스탯 및 특성 정의';
COMMENT ON COLUMN HELMET.helmet_id IS '투구 고유 ID';
COMMENT ON COLUMN HELMET.name IS '투구명';
COMMENT ON COLUMN HELMET.type IS '가죽모자/천/판금/가면';
COMMENT ON COLUMN HELMET.hp_bonus IS '추가 체력';
COMMENT ON COLUMN HELMET.special_effect IS '투구 특수 효과 코드';
COMMENT ON COLUMN HELMET.score_cost IS '스타팅 포인트 비용';
COMMENT ON COLUMN HELMET.description IS '투구 설명';


CREATE TABLE CONSUMABLE
(
    consumable_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 소모품 고유 ID
    name          VARCHAR(50) NOT NULL,                            -- 소모품명
    effect_type   VARCHAR(30) NOT NULL,                            -- 회복/공격력/방어력/해제/부여/데미지/부활
    effect_value  INT         NOT NULL DEFAULT 0,                  -- 효과 수치
    score_cost    INT         NOT NULL DEFAULT 0,                  -- 스타팅 포인트 비용
    description   TEXT                                             -- 소모품 설명
);
COMMENT ON TABLE CONSUMABLE IS '1회용 소모품 마스터 데이터 (불사조의 깃털 등 특수 소모품 포함)';
COMMENT ON COLUMN CONSUMABLE.consumable_id IS '소모품 고유 ID';
COMMENT ON COLUMN CONSUMABLE.name IS '소모품명';
COMMENT ON COLUMN CONSUMABLE.effect_type IS '회복/공격력/방어력/해제/부여/데미지/부활';
COMMENT ON COLUMN CONSUMABLE.effect_value IS '효과 수치';
COMMENT ON COLUMN CONSUMABLE.score_cost IS '스타팅 포인트 비용';
COMMENT ON COLUMN CONSUMABLE.description IS '소모품 설명';


CREATE TABLE RELIC
(
    relic_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 유물 고유 ID
    name        VARCHAR(50) NOT NULL,                            -- 유물명
    effect_desc TEXT        NOT NULL,                            -- 유물 효과 설명
    rarity      VARCHAR(20) NOT NULL DEFAULT 'COMMON',           -- 희귀도 (COMMON/RARE/EPIC)
    score_cost  INT         NOT NULL DEFAULT 0                   -- 스타팅 포인트 비용
);
COMMENT ON TABLE RELIC IS '유물(아티팩트) 마스터 데이터 (게임 시스템에 변화를 주는 필수 핵심 콘텐츠)';
COMMENT ON COLUMN RELIC.relic_id IS '유물 고유 ID';
COMMENT ON COLUMN RELIC.name IS '유물명';
COMMENT ON COLUMN RELIC.effect_desc IS '유물 효과 설명';
COMMENT ON COLUMN RELIC.rarity IS '희귀도 (COMMON/RARE/EPIC)';
COMMENT ON COLUMN RELIC.score_cost IS '스타팅 포인트 비용';


-- 2. 몬스터 테이블
CREATE TABLE MONSTER
(
    monster_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 몬스터 고유 ID
    name         VARCHAR(50) NOT NULL,                            -- 몬스터명
    monster_type SMALLINT    NOT NULL,                            -- 0:NORMAL / 1:ELITE / 2:BOSS (버프 개수와 연계)
    score_reward INT         NOT NULL DEFAULT 0,                  -- 처치 시 기본 점수 (보스=500점 기준)
    description  TEXT                                             -- 몬스터 설명
);
COMMENT ON TABLE MONSTER IS '몬스터 마스터 데이터';
COMMENT ON COLUMN MONSTER.monster_id IS '몬스터 고유 ID';
COMMENT ON COLUMN MONSTER.name IS '몬스터명';
COMMENT ON COLUMN MONSTER.monster_type IS '0:NORMAL / 1:ELITE / 2:BOSS (버프 개수와 연계)';
COMMENT ON COLUMN MONSTER.score_reward IS '처치 시 기본 점수 (보스=500점 기준)';
COMMENT ON COLUMN MONSTER.description IS '몬스터 설명';


CREATE TABLE MONSTER_BASE_STATUS
(
    status_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 스탯 고유 ID
    stage      SMALLINT NOT NULL,                               -- 등장 스테이지 (1~5)
    difficulty SMALLINT NOT NULL,                               -- 난이도 보정 (일반 +1~3, 엘리트 +5, 보스 +7)
    base_hp    INT      NOT NULL,                               -- 기본 체력
    base_atk   INT      NOT NULL,                               -- 기본 공격력
    base_def   INT      NOT NULL                                -- 기본 방어력
);
COMMENT ON TABLE MONSTER_BASE_STATUS IS '몬스터 기본 스탯 (스테이지·난이도별 스탯 분리)';
COMMENT ON COLUMN MONSTER_BASE_STATUS.status_id IS '스탯 고유 ID';
COMMENT ON COLUMN MONSTER_BASE_STATUS.stage IS '등장 스테이지 (1~5)';
COMMENT ON COLUMN MONSTER_BASE_STATUS.difficulty IS '난이도 보정 (일반 +1~3, 엘리트 +5, 보스 +7)';
COMMENT ON COLUMN MONSTER_BASE_STATUS.base_hp IS '기본 체력';
COMMENT ON COLUMN MONSTER_BASE_STATUS.base_atk IS '기본 공격력';
COMMENT ON COLUMN MONSTER_BASE_STATUS.base_def IS '기본 방어력';


CREATE TABLE MONSTER_BUFF
(
    keyword_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 키워드 고유 ID
    keyword     VARCHAR(50) NOT NULL,                            -- 키워드명
    effect_desc TEXT        NOT NULL                             -- 키워드 효과 설명
);
COMMENT ON TABLE MONSTER_BUFF IS '몬스터 버프 키워드 마스터 (ELITE=1개, BOSS=2개 부여용 풀)';
COMMENT ON COLUMN MONSTER_BUFF.keyword_id IS '키워드 고유 ID';
COMMENT ON COLUMN MONSTER_BUFF.keyword IS '키워드명';
COMMENT ON COLUMN MONSTER_BUFF.effect_desc IS '키워드 효과 설명';


-- CREATE TABLE MONSTER_CHARACTERISTIC
-- (
--     keyword_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 키워드 고유 ID
--     monster_id BIGINT      NOT NULL,                            -- 몬스터 ID 참조
--     keyword    VARCHAR(50) NOT NULL,                            -- 키워드명
--
--     CONSTRAINT fk_mc_monster FOREIGN KEY (monster_id)
--         REFERENCES MONSTER (monster_id) ON DELETE CASCADE
-- );
-- COMMENT ON TABLE MONSTER_CHARACTERISTIC IS '몬스터별 특징 키워드 (몬스터-버프 매핑, AI 임베딩 활용)';
-- COMMENT ON COLUMN MONSTER_CHARACTERISTIC.keyword_id IS '키워드 고유 ID';
-- COMMENT ON COLUMN MONSTER_CHARACTERISTIC.monster_id IS '몬스터 ID 참조';
-- COMMENT ON COLUMN MONSTER_CHARACTERISTIC.keyword IS '키워드명';
-- CREATE INDEX idx_monster_characteristic_monster_monster_id ON monster_characteristic (monster_id);


-- 3. 회원 테이블
CREATE TABLE MEMBER
(
    member_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 회원 고유 ID
    login_id      VARCHAR(50) UNIQUE,                              -- 일반 로그인 아이디 (소셜 전용 계정은 NULL)
    password_hash VARCHAR(60),                                     -- 비밀번호 해시값 (BCrypt 권장)
    social_token  VARCHAR(512),                                    -- 소셜 OAuth 토큰 (암호화 저장 권장)
    social_id     VARCHAR(100),                                    -- 소셜 제공자 내 고유 ID
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),                -- 계정 생성일시
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()                 -- 마지막 수정일시
);
COMMENT ON TABLE MEMBER IS '회원 계정 정보 (일반 ID+PW 및 소셜 로그인 모두 지원)';
COMMENT ON COLUMN MEMBER.member_id IS '회원 고유 ID';
COMMENT ON COLUMN MEMBER.login_id IS '일반 로그인 아이디 (소셜 전용 계정은 NULL)';
COMMENT ON COLUMN MEMBER.password_hash IS '비밀번호 해시값 (BCrypt 권장)';
COMMENT ON COLUMN MEMBER.social_token IS '소셜 OAuth 토큰 (암호화 저장 권장)';
COMMENT ON COLUMN MEMBER.social_id IS '소셜 제공자 내 고유 ID';
COMMENT ON COLUMN MEMBER.created_at IS '계정 생성일시';
COMMENT ON COLUMN MEMBER.updated_at IS '마지막 수정일시';


-- 4. 게임 세션 테이블
CREATE TABLE GAME_STATE
(
    game_state_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 게임 세션 고유 ID
    member_id       BIGINT      NOT NULL,                            -- 회원 ID 참조
    difficulty      VARCHAR(20) NOT NULL DEFAULT 'NORMAL',           -- 난이도 (EASY/NORMAL/HARD/HELL)
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,               -- 진행 중 여부 (종료 시 FALSE)
    current_stage   INT         NOT NULL DEFAULT 1,                  -- 현재 스테이지 번호
    current_node_id BIGINT,                                          -- 현재 위치 노드 (map_node FK, 후처리)
    score           INT         NOT NULL DEFAULT 0,                  -- 누적 점수
    gold            INT         NOT NULL DEFAULT 0,                  -- 보유 골드
    starting_points INT         NOT NULL DEFAULT 0,                  -- 사용한 스타팅 포인트 (랭킹 산정 시 참조)
    has_phoenix     BOOLEAN     NOT NULL DEFAULT FALSE,              -- 불사조의 깃털 획득 여부 (게임 내 1회)
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),              -- 세션 시작 일시
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),              -- 마지막 저장 일시

    CONSTRAINT fk_gs_member FOREIGN KEY (member_id)
        REFERENCES MEMBER (member_id) ON DELETE CASCADE
);
COMMENT ON TABLE GAME_STATE IS '현재 진행 중인 게임 세션';
COMMENT ON COLUMN GAME_STATE.game_state_id IS '게임 세션 고유 ID';
COMMENT ON COLUMN GAME_STATE.member_id IS '회원 ID 참조';
COMMENT ON COLUMN GAME_STATE.difficulty IS '난이도 (EASY/NORMAL/HARD/HELL)';
COMMENT ON COLUMN GAME_STATE.is_active IS '진행 중 여부 (종료 시 FALSE)';
COMMENT ON COLUMN GAME_STATE.current_stage IS '현재 스테이지 번호';
COMMENT ON COLUMN GAME_STATE.current_node_id IS '현재 위치 노드 (map_node FK, 후처리)';
COMMENT ON COLUMN GAME_STATE.score IS '누적 점수';
COMMENT ON COLUMN GAME_STATE.gold IS '보유 골드';
COMMENT ON COLUMN GAME_STATE.starting_points IS '사용한 스타팅 포인트 (랭킹 산정 시 참조)';
COMMENT ON COLUMN GAME_STATE.has_phoenix IS '불사조의 깃털 획득 여부 (게임 내 1회)';
COMMENT ON COLUMN GAME_STATE.created_at IS '세션 시작 일시';
COMMENT ON COLUMN GAME_STATE.updated_at IS '마지막 저장 일시';
CREATE INDEX idx_game_state_member_member_id ON game_state (member_id);


CREATE TABLE GAME_CHARACTER
(
    character_id  BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 캐릭터 고유 ID
    game_state_id BIGINT NOT NULL,                                          -- 게임 세션 ID 참조
    level         INT    NOT NULL DEFAULT 1,                                -- 캐릭터 레벨
    current_hp    INT    NOT NULL,                                          -- 현재 HP
    max_hp        INT    NOT NULL,                                          -- 최대 HP (레벨당 10 + 보너스)
    attack        INT    NOT NULL,                                          -- 캐릭터 공격력 (레벨당 1 + 보너스)
    defense       INT    NOT NULL,                                          -- 캐릭터 방어력 (레벨당 1 + 보너스)

    CONSTRAINT fk_gc_game_state FOREIGN KEY (game_state_id)
        REFERENCES GAME_STATE (game_state_id) ON DELETE CASCADE
);
COMMENT ON TABLE GAME_CHARACTER IS '현재 진행 중인 게임 캐릭터 스탯';
COMMENT ON COLUMN GAME_CHARACTER.character_id IS '캐릭터 고유 ID';
COMMENT ON COLUMN GAME_CHARACTER.game_state_id IS '게임 세션 ID 참조';
COMMENT ON COLUMN GAME_CHARACTER.level IS '캐릭터 레벨';
COMMENT ON COLUMN GAME_CHARACTER.current_hp IS '현재 HP';
COMMENT ON COLUMN GAME_CHARACTER.max_hp IS '최대 HP (레벨당 10 + 보너스)';
COMMENT ON COLUMN GAME_CHARACTER.attack IS '캐릭터 공격력 (레벨당 1 + 보너스)';
COMMENT ON COLUMN GAME_CHARACTER.defense IS '캐릭터 방어력 (레벨당 1 + 보너스)';
CREATE INDEX idx_game_character_game_state_game_state_id ON game_character (game_state_id);


CREATE TABLE GAME_EQUIPMENT
(
    equip_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 장비 슬롯 고유 ID
    game_state_id BIGINT NOT NULL,                                 -- 게임 세션 ID 참조
    weapon_id     BIGINT,                                          -- 장착 중인 무기 (NULL=미장착)
    armor_id      BIGINT,                                          -- 장착 중인 갑옷 (NULL=미장착)
    helmet_id     BIGINT,                                          -- 장착 중인 투구 (NULL=미장착)

    CONSTRAINT fk_ge_game_state FOREIGN KEY (game_state_id)
        REFERENCES GAME_STATE (game_state_id) ON DELETE CASCADE,
    CONSTRAINT fk_ge_weapon FOREIGN KEY (weapon_id)
        REFERENCES WEAPON (weapon_id) ON DELETE SET NULL,
    CONSTRAINT fk_ge_armor FOREIGN KEY (armor_id)
        REFERENCES ARMOR (armor_id) ON DELETE SET NULL,
    CONSTRAINT fk_ge_helmet FOREIGN KEY (helmet_id)
        REFERENCES HELMET (helmet_id) ON DELETE SET NULL
);
COMMENT ON TABLE GAME_EQUIPMENT IS '현재 진행 중인 게임에서 착용 중인 장비';
COMMENT ON COLUMN GAME_EQUIPMENT.equip_id IS '장비 슬롯 고유 ID';
COMMENT ON COLUMN GAME_EQUIPMENT.game_state_id IS '게임 세션 ID 참조';
COMMENT ON COLUMN GAME_EQUIPMENT.weapon_id IS '장착 중인 무기 (NULL=미장착)';
COMMENT ON COLUMN GAME_EQUIPMENT.armor_id IS '장착 중인 갑옷 (NULL=미장착)';
COMMENT ON COLUMN GAME_EQUIPMENT.helmet_id IS '장착 중인 투구 (NULL=미장착)';
CREATE INDEX idx_game_equipment_game_state_game_state_id ON game_equipment (game_state_id);
CREATE INDEX idx_game_equipment_weapon_weapon_id ON game_equipment (weapon_id);
CREATE INDEX idx_game_equipment_armor_armor_id ON game_equipment (armor_id);
CREATE INDEX idx_game_equipment_helmet_helmet_id ON game_equipment (helmet_id);


CREATE TABLE GAME_SKILL_SLOT
(
    slot_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,    -- 슬롯 고유 ID
    game_state_id BIGINT NOT NULL,                                    -- 게임 세션 ID 참조
    skill_id      BIGINT NOT NULL,                                    -- 기술 ID 참조
    slot_index    INT    NOT NULL CHECK (slot_index BETWEEN 0 AND 7), -- 슬롯 순번 (0~7)
    cool_time     INT    NOT NULL DEFAULT 0,                          -- 잔여 쿨타임 (0 이상)

    CONSTRAINT uq_gss_slot UNIQUE (game_state_id, slot_index),        -- 동일 세션 내 슬롯 번호 중복 불가
    CONSTRAINT fk_gss_game_state FOREIGN KEY (game_state_id)
        REFERENCES GAME_STATE (game_state_id) ON DELETE CASCADE,
    CONSTRAINT fk_gss_skill FOREIGN KEY (skill_id)
        REFERENCES SKILL (skill_id) ON DELETE RESTRICT
);
COMMENT ON TABLE GAME_SKILL_SLOT IS '게임 세션에서 캐릭터가 배치한 기술 슬롯 (최대 8개)';
COMMENT ON COLUMN GAME_SKILL_SLOT.slot_id IS '슬롯 고유 ID';
COMMENT ON COLUMN GAME_SKILL_SLOT.game_state_id IS '게임 세션 ID 참조';
COMMENT ON COLUMN GAME_SKILL_SLOT.skill_id IS '기술 ID 참조';
COMMENT ON COLUMN GAME_SKILL_SLOT.slot_index IS '슬롯 순번 (0~7)';
COMMENT ON COLUMN GAME_SKILL_SLOT.cool_time IS '잔여 쿨타임 (0 이상)';
CREATE INDEX idx_game_skill_slot_game_state_game_state_id ON game_skill_slot (game_state_id); -- TODO
CREATE INDEX idx_game_skill_slot_skill_skill_id ON game_skill_slot (skill_id);


CREATE TABLE GAME_RELIC_INVENTORY
(
    slot_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 슬롯 고유 ID
    game_state_id BIGINT NOT NULL,                                 -- 게임 세션 ID 참조
    relic_id      BIGINT NOT NULL,                                 -- 유물 ID 참조

    CONSTRAINT fk_gri_game_state FOREIGN KEY (game_state_id)
        REFERENCES GAME_STATE (game_state_id) ON DELETE CASCADE,
    CONSTRAINT fk_gri_relic FOREIGN KEY (relic_id)
        REFERENCES RELIC (relic_id) ON DELETE RESTRICT
);
COMMENT ON TABLE GAME_RELIC_INVENTORY IS '게임 세션에서 캐릭터가 보유한 유물 인벤토리';
COMMENT ON COLUMN GAME_RELIC_INVENTORY.slot_id IS '슬롯 고유 ID';
COMMENT ON COLUMN GAME_RELIC_INVENTORY.game_state_id IS '게임 세션 ID 참조';
COMMENT ON COLUMN GAME_RELIC_INVENTORY.relic_id IS '유물 ID 참조';
CREATE INDEX idx_game_relic_inventory_game_state_game_state_id ON game_relic_inventory (game_state_id);
CREATE INDEX idx_game_relic_inventory_relic_relic_id ON game_relic_inventory (relic_id);


CREATE TABLE GAME_CONSUMABLE_INVENTORY
(
    slot_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 슬롯 고유 ID
    game_state_id BIGINT NOT NULL,                                 -- 게임 세션 ID 참조
    consumable_id BIGINT NOT NULL,                                 -- 소모품 ID 참조

    CONSTRAINT fk_gci_game_state FOREIGN KEY (game_state_id)
        REFERENCES GAME_STATE (game_state_id) ON DELETE CASCADE,
    CONSTRAINT fk_gci_consumable FOREIGN KEY (consumable_id)
        REFERENCES CONSUMABLE (consumable_id) ON DELETE RESTRICT
);
COMMENT ON TABLE GAME_CONSUMABLE_INVENTORY IS '게임 세션에서 캐릭터가 보유한 소모품 인벤토리';
COMMENT ON COLUMN GAME_CONSUMABLE_INVENTORY.slot_id IS '슬롯 고유 ID';
COMMENT ON COLUMN GAME_CONSUMABLE_INVENTORY.game_state_id IS '게임 세션 ID 참조';
COMMENT ON COLUMN GAME_CONSUMABLE_INVENTORY.consumable_id IS '소모품 ID 참조';
CREATE INDEX idx_game_consumable_inventory_game_state_game_state_id ON game_consumable_inventory (game_state_id);
CREATE INDEX idx_game_consumable_inventory_consumable_consumable_id ON game_consumable_inventory (consumable_id);


-- 5. 랭킹 테이블
CREATE TABLE RANKING
(
    ranking_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,    -- 랭킹 레코드 고유 ID
    member_id     BIGINT      NOT NULL,                               -- 회원 ID 참조
    game_state_id BIGINT      NOT NULL,                               -- 게임 세션 ID 참조
    difficulty    VARCHAR(20) NOT NULL,                               -- 난이도 (NORMAL/HARD/HELL, EASY 제외)
    best_score    INT         NOT NULL DEFAULT 0,                     -- 해당 난이도 최고 점수 (배율 적용 후)
    clear_time    INT,                                                -- 클리어 시간 초 단위 (클리어 실패 시 NULL)
    achieved_at   TIMESTAMP   NOT NULL DEFAULT NOW(),                 -- 기록 달성 일시

    CONSTRAINT uq_ranking_member_diff UNIQUE (member_id, difficulty), -- 난이도별 최고 점수 1건 유지
    CONSTRAINT fk_rk_member FOREIGN KEY (member_id)
        REFERENCES MEMBER (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_rk_game_state FOREIGN KEY (game_state_id)
        REFERENCES GAME_STATE (game_state_id) ON DELETE CASCADE
);
COMMENT ON TABLE RANKING IS '회원별 / 난이도별 최고 점수 랭킹 (EASY 난이도는 랭킹 제외)';
COMMENT ON COLUMN RANKING.ranking_id IS '랭킹 레코드 고유 ID';
COMMENT ON COLUMN RANKING.member_id IS '회원 ID 참조';
COMMENT ON COLUMN RANKING.game_state_id IS '게임 세션 ID 참조';
COMMENT ON COLUMN RANKING.difficulty IS '난이도 (NORMAL/HARD/HELL, EASY 제외)';
COMMENT ON COLUMN RANKING.best_score IS '해당 난이도 최고 점수 (배율 적용 후)';
COMMENT ON COLUMN RANKING.clear_time IS '클리어 시간 초 단위 (클리어 실패 시 NULL)';
COMMENT ON COLUMN RANKING.achieved_at IS '기록 달성 일시';
CREATE INDEX idx_ranking_member_member_id ON ranking (member_id); -- TODO
CREATE INDEX idx_ranking_game_state_game_state_id ON ranking (game_state_id);


-- 6. 도감(Collection) 테이블
CREATE TABLE COLLECTION_SKILL
(
    collection_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 도감 항목 고유 ID
    member_id     BIGINT    NOT NULL,                              -- 회원 ID 참조
    skill_id      BIGINT    NOT NULL,                              -- 기술 고유 ID
    acquired_at   TIMESTAMP NOT NULL DEFAULT NOW(),                -- 최초 획득 일시

    CONSTRAINT uq_cs_member_skill UNIQUE (member_id, skill_id),    -- 중복 등록 방지
    CONSTRAINT fk_cs_member FOREIGN KEY (member_id)
        REFERENCES MEMBER (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_cs_skill FOREIGN KEY (skill_id)
        REFERENCES SKILL (skill_id) ON DELETE CASCADE
);
COMMENT ON TABLE COLLECTION_SKILL IS '수집한 기술';
COMMENT ON COLUMN COLLECTION_SKILL.collection_id IS '도감 항목 고유 ID';
COMMENT ON COLUMN COLLECTION_SKILL.member_id IS '회원 ID 참조';
COMMENT ON COLUMN COLLECTION_SKILL.skill_id IS '기술 고유 ID';
COMMENT ON COLUMN COLLECTION_SKILL.acquired_at IS '최초 획득 일시';


CREATE TABLE COLLECTION_WEAPON
(
    collection_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 도감 항목 고유 ID
    member_id     BIGINT    NOT NULL,                              -- 회원 ID 참조
    weapon_id     BIGINT    NOT NULL,                              -- 무기 고유 ID
    acquired_at   TIMESTAMP NOT NULL DEFAULT NOW(),                -- 최초 획득 일시

    CONSTRAINT uq_cw_member_weapon UNIQUE (member_id, weapon_id),  -- 중복 등록 방지
    CONSTRAINT fk_cw_member FOREIGN KEY (member_id)
        REFERENCES MEMBER (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_cw_weapon FOREIGN KEY (weapon_id)
        REFERENCES WEAPON (weapon_id) ON DELETE CASCADE
);
COMMENT ON TABLE COLLECTION_WEAPON IS '수집한 무기';
COMMENT ON COLUMN COLLECTION_WEAPON.collection_id IS '도감 항목 고유 ID';
COMMENT ON COLUMN COLLECTION_WEAPON.member_id IS '회원 ID 참조';
COMMENT ON COLUMN COLLECTION_WEAPON.weapon_id IS '무기 고유 ID';
COMMENT ON COLUMN COLLECTION_WEAPON.acquired_at IS '최초 획득 일시';


CREATE TABLE COLLECTION_ARMOR
(
    collection_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 도감 항목 고유 ID
    member_id     BIGINT    NOT NULL,                              -- 회원 ID 참조
    armor_id      BIGINT    NOT NULL,                              -- 갑옷 고유 ID
    acquired_at   TIMESTAMP NOT NULL DEFAULT NOW(),                -- 최초 획득 일시

    CONSTRAINT uq_ca_member_armor UNIQUE (member_id, armor_id),    -- 중복 등록 방지
    CONSTRAINT fk_ca_member FOREIGN KEY (member_id)
        REFERENCES MEMBER (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_ca_armor FOREIGN KEY (armor_id)
        REFERENCES ARMOR (armor_id) ON DELETE CASCADE
);
COMMENT ON TABLE COLLECTION_ARMOR IS '수집한 갑옷';
COMMENT ON COLUMN COLLECTION_ARMOR.collection_id IS '도감 항목 고유 ID';
COMMENT ON COLUMN COLLECTION_ARMOR.member_id IS '회원 ID 참조';
COMMENT ON COLUMN COLLECTION_ARMOR.armor_id IS '갑옷 고유 ID';
COMMENT ON COLUMN COLLECTION_ARMOR.acquired_at IS '최초 획득 일시';


CREATE TABLE COLLECTION_HELMET
(
    collection_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 도감 항목 고유 ID
    member_id     BIGINT    NOT NULL,                              -- 회원 ID 참조
    helmet_id     BIGINT    NOT NULL,                              -- 투구 고유 ID
    acquired_at   TIMESTAMP NOT NULL DEFAULT NOW(),                -- 최초 획득 일시

    CONSTRAINT uq_ch_member_helmet UNIQUE (member_id, helmet_id),  -- 중복 등록 방지
    CONSTRAINT fk_ch_member FOREIGN KEY (member_id)
        REFERENCES MEMBER (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_ch_helmet FOREIGN KEY (helmet_id)
        REFERENCES HELMET (helmet_id) ON DELETE CASCADE
);
COMMENT ON TABLE COLLECTION_HELMET IS '수집한 투구';
COMMENT ON COLUMN COLLECTION_HELMET.collection_id IS '도감 항목 고유 ID';
COMMENT ON COLUMN COLLECTION_HELMET.member_id IS '회원 ID 참조';
COMMENT ON COLUMN COLLECTION_HELMET.helmet_id IS '투구 고유 ID';
COMMENT ON COLUMN COLLECTION_HELMET.acquired_at IS '최초 획득 일시';


CREATE TABLE COLLECTION_CONSUMABLE
(
    collection_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,        -- 도감 항목 고유 ID
    member_id     BIGINT    NOT NULL,                                     -- 회원 ID 참조
    consumable_id BIGINT    NOT NULL,                                     -- 소모품 고유 ID
    acquired_at   TIMESTAMP NOT NULL DEFAULT NOW(),                       -- 최초 획득 일시

    CONSTRAINT uq_cc_member_consumable UNIQUE (member_id, consumable_id), -- 중복 등록 방지
    CONSTRAINT fk_cc_member FOREIGN KEY (member_id)
        REFERENCES MEMBER (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_cc_consumable FOREIGN KEY (consumable_id)
        REFERENCES CONSUMABLE (consumable_id) ON DELETE CASCADE
);
COMMENT ON TABLE COLLECTION_CONSUMABLE IS '수집한 소모품';
COMMENT ON COLUMN COLLECTION_CONSUMABLE.collection_id IS '도감 항목 고유 ID';
COMMENT ON COLUMN COLLECTION_CONSUMABLE.member_id IS '회원 ID 참조';
COMMENT ON COLUMN COLLECTION_CONSUMABLE.consumable_id IS '소모품 고유 ID';
COMMENT ON COLUMN COLLECTION_CONSUMABLE.acquired_at IS '최초 획득 일시';


CREATE TABLE COLLECTION_RELIC
(
    collection_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 도감 항목 고유 ID
    member_id     BIGINT    NOT NULL,                              -- 회원 ID 참조
    relic_id      BIGINT    NOT NULL,                              -- 유물 고유 ID
    acquired_at   TIMESTAMP NOT NULL DEFAULT NOW(),                -- 최초 획득 일시

    CONSTRAINT uq_cr_member_relic UNIQUE (member_id, relic_id),    -- 중복 등록 방지
    CONSTRAINT fk_cr_member FOREIGN KEY (member_id)
        REFERENCES MEMBER (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_cr_relic FOREIGN KEY (relic_id)
        REFERENCES RELIC (relic_id) ON DELETE CASCADE
);
COMMENT ON TABLE COLLECTION_RELIC IS '수집한 유물';
COMMENT ON COLUMN COLLECTION_RELIC.collection_id IS '도감 항목 고유 ID';
COMMENT ON COLUMN COLLECTION_RELIC.member_id IS '회원 ID 참조';
COMMENT ON COLUMN COLLECTION_RELIC.relic_id IS '유물 고유 ID';
COMMENT ON COLUMN COLLECTION_RELIC.acquired_at IS '최초 획득 일시';