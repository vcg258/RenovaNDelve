-- =========================================================
-- MEMBER
-- =========================================================
INSERT INTO member (member_id, created_at, updated_at, login_id, password_hash)
VALUES (1, now(), now(), 'easy_user', '$2a$10$/MoiaZf3hmyc7jLIM7YTSOmrXD4S2y8NO47C7nHW6sB6t9xWCYHRm'),
       (2, now(), now(), 'normal_user', '$2a$10$4uvNTTilblfzVRXACISqFu7ad9JuNFspi8dOBgxL7ahFIj3y20FH6'),
       (3, now(), now(), 'hell_user', '$2a$10$PEm8kVnQUjKNW2ebKv.8BOcjpbnd10WurjKe0dbClOIQG3sC27ArW');

-- =========================================================
-- SKILL (ID를 명시적으로 지정하여 하위 테이블 참조 오류 방지)
-- =========================================================
INSERT INTO skill (skill_id, attack_count, coefficient, cooldown, target, cost, effect_stack, status_effect, name,
                   description)
VALUES (1, 1, 1.20, 2, 'EnemySingle', 50, 0, 0, '강타', '강한 일격'),
       (2, 3, 0.75, 4, 'EnemySingle', 120, 1, 1, '연속 베기', '빠른 다단 공격'),
       (3, 5, 1.80, 8, 'EnemyAll', 300, 2, 2, '헬파이어', '광역 화염 공격');

-- =========================================================
-- ARMOR
-- =========================================================
INSERT INTO armor (armor_id, def_bonus, score_cost, special_effect, type, name, description)
VALUES (1, 5, 100, 0, 'LEATHER', '견습생 가죽갑옷', '초보자용 방어구'),
       (2, 15, 300, 1, 'CHAIN', '기사단 사슬갑옷', '균형 잡힌 방어구'),
       (3, 35, 900, 2, 'METAL', '지옥 강철갑옷', '최상급 방어구');

-- =========================================================
-- HELMET
-- =========================================================
INSERT INTO helmet (helmet_id, hp_bonus, score_cost, special_effect, type, name, description)
VALUES (1, 20, 80, 0, 'LEATHER', '낡은 투구', '기본 투구'),
       (2, 60, 250, 1, 'METAL', '기사의 철투구', '방어 특화'),
       (3, 150, 1000, 2, 'MASK', '악마의 가면', '강력한 저주가 깃든 가면');

-- =========================================================
-- WEAPON
-- =========================================================
INSERT INTO weapon (weapon_id, atk_bonus, score_cost, special_effect, type, name, description)
VALUES (1, 10, 100, 0, 'SWORD', '초심자의 검', '기본 무기'),
       (2, 30, 400, 1, 'DAGGER', '암살자의 단검', '빠른 공격 가능'),
       (3, 80, 1500, 2, 'STAFF', '멸망의 지팡이', '강력한 마력을 품은 무기');

-- =========================================================
-- CONSUMABLE
-- =========================================================
INSERT INTO consumable (consumable_id, effect_value, score_cost, effect_type, name, description)
VALUES (1, 50, 30, 'RECOVERY', '소형 회복 물약', 'HP 50 회복'),
       (2, 100, 80, 'RECOVERY', '해독제', '상태이상 회복'),
       (3, 30, 120, 'ATTACK', '공격 강화 물약', '공격력 증가'),
       (4, 1, 500, 'RESURRECTION', '불사조의 깃털', '사망 시 부활');

-- =========================================================
-- RELIC
-- =========================================================
INSERT INTO relic (relic_id, score_cost, rarity, name, effect_desc)
VALUES (1, 200, 'COMMON', '행운의 반지', '골드 획득량 증가'),
       (2, 600, 'RARE', '마력 증폭 수정', '스킬 피해 증가'),
       (3, 2000, 'EPIC', '심연의 핵', '모든 능력치 증가');

-- =========================================================
-- GAME STATE
-- =========================================================
INSERT INTO game_state (game_state_id, current_stage, current_node_id, gold, has_phoenix, is_active, score, starting_points, created_at,
                        updated_at, member_id, difficulty)
VALUES (1, 3, 1,150, false, true, 500, 100, now(), now(), 1, 'EASY'),
       (2, 8, 1,600, true, true, 2500, 200, now(), now(), 2, 'NORMAL'),
       (3, 20, 1,2500, true, false, 12000, 500, now(), now(), 3, 'HELL');

-- =========================================================
-- GAME CHARACTER
-- =========================================================
INSERT INTO game_character (game_state_id, attack, current_hp, defense, level, max_hp)
VALUES (1, 15, 90, 8, 3, 100),
       (2, 45, 250, 25, 10, 250),
       (3, 120, 700, 60, 25, 700);

-- =========================================================
-- GAME EQUIPMENT
-- =========================================================
INSERT INTO game_equipment (game_state_id, weapon_id, armor_id, helmet_id)
VALUES (1, 1, 1, 1),
       (2, 2, 2, 2),
       (3, 3, 3, 3);

-- =========================================================
-- GAME CONSUMABLE INVENTORY
-- =========================================================
INSERT INTO game_consumable_inventory (game_state_id, consumable_id)
VALUES (1, 1),
       (2, 2),
       (2, 3),
       (3, 4);

-- =========================================================
-- GAME RELIC INVENTORY
-- =========================================================
INSERT INTO game_relic_inventory (game_state_id, relic_id)
VALUES (1, 1),
       (2, 1),
       (2, 2),
       (3, 1),
       (3, 2),
       (3, 3);

-- =========================================================
-- GAME SKILL SLOT (참조가 안전하게 매핑됨)
-- =========================================================
INSERT INTO game_skill_slot (game_state_id, skill_id, cool_time, slot_index)
VALUES (1, 1, 0, 0),
       (2, 1, 1, 0),
       (2, 2, 0, 1),
       (3, 1, 3, 0),
       (3, 2, 1, 1),
       (3, 3, 0, 2);

-- =========================================================
-- COLLECTION WEAPON
-- =========================================================
INSERT INTO collection_weapon (acquired_at, member_id, weapon_id)
VALUES (now(), 1, 1),
       (now(), 2, 1),
       (now(), 2, 2),
       (now(), 3, 1),
       (now(), 3, 2),
       (now(), 3, 3);

-- =========================================================
-- COLLECTION ARMOR
-- =========================================================
INSERT INTO collection_armor (acquired_at, member_id, armor_id)
VALUES (now(), 1, 1),
       (now(), 2, 2),
       (now(), 3, 3);

-- =========================================================
-- COLLECTION HELMET
-- =========================================================
INSERT INTO collection_helmet (acquired_at, member_id, helmet_id)
VALUES (now(), 1, 1),
       (now(), 2, 2),
       (now(), 3, 3);

-- =========================================================
-- COLLECTION CONSUMABLE
-- =========================================================
INSERT INTO collection_consumable (acquired_at, member_id, consumable_id)
VALUES (now(), 1, 1),
       (now(), 2, 2),
       (now(), 2, 3),
       (now(), 3, 4);

-- =========================================================
-- COLLECTION RELIC
-- =========================================================
INSERT INTO collection_relic (acquired_at, member_id, relic_id)
VALUES (now(), 1, 1),
       (now(), 2, 1),
       (now(), 2, 2),
       (now(), 3, 1),
       (now(), 3, 2),
       (now(), 3, 3);

-- =========================================================
-- COLLECTION SKILL
-- =========================================================
INSERT INTO collection_skill (acquired_at, member_id, skill_id)
VALUES (now(), 1, 1),
       (now(), 2, 1),
       (now(), 2, 2),
       (now(), 3, 1),
       (now(), 3, 2),
       (now(), 3, 3);

-- =========================================================
-- RANKING
-- =========================================================
INSERT INTO ranking (best_score, clear_time, achieved_at, game_state_id, member_id, difficulty)
VALUES (500, 1800, now(), 1, 1, 'EASY'),
       (2500, 4200, now(), 2, 2, 'NORMAL'),
       (12000, 9600, now(), 3, 3, 'HELL');