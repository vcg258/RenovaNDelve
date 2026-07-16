INSERT INTO SKILL (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
VALUES
    ('기본 공격', 'EnemySingle', 1, 1.00, 0, 0, 0, 0, '기본 물리 공격'),
    ('출혈 베기', 'EnemySingle', 2, 0.80, 3, 2, 1, 2, '출혈 2스택 부여'),
    ('화염구', 'EnemyAll', 1, 1.50, 5, 3, 2, 3, '화상 3스택 부여');

INSERT INTO WEAPON (name, type, atk_bonus, special_effect, score_cost, description)
VALUES
    ('낡은 검', '검', 2, 0, 0, '기본 검'),
    ('암살 단검', '단검', -1, 1, 50, '치명 확률 증가'),
    ('전쟁 둔기', '둔기', 5, 0, 100, '강한 한방');

INSERT INTO ARMOR (name, type, def_bonus, special_effect, score_cost, description)
VALUES
    ('가죽 갑옷', '가죽', 2, 0, 0, '기본 방어구'),
    ('기사 판금', '판금', 5, 1, 120, '방어 특화');

INSERT INTO HELMET (name, type, hp_bonus, special_effect, score_cost, description)
VALUES
    ('가죽 모자', '가죽모자', 5, 0, 0, '기본 투구'),
    ('철 투구', '판금', 15, 0, 80, '체력 증가');

INSERT INTO CONSUMABLE (name, effect_type, effect_value, score_cost, description)
VALUES
    ('체력 포션', '회복', 50, 10, 'HP 50 회복'),
    ('공격력 물약', '공격력', 5, 20, '공격력 일시 증가'),
    ('불사조의 깃털', '부활', 1, 200, '사망 시 1회 부활');

INSERT INTO RELIC (name, effect_desc, rarity, score_cost)
VALUES
    ('핏빛 반지', '출혈 피해 20% 증가', 'RARE', 100),
    ('고대의 부적', '스킬 쿨타임 -1', 'EPIC', 200);

INSERT INTO MONSTER (name, monster_type, score_reward, description)
VALUES
    ('슬라임', 0, 50, '약한 몬스터'),
    ('오크 전사', 1, 150, '엘리트 몬스터'),
    ('고대 드래곤', 2, 500, '보스 몬스터');

INSERT INTO MONSTER_BASE_STATUS (stage, difficulty, base_hp, base_atk, base_def)
VALUES
    (1, 1, 50, 5, 2),
    (1, 5, 120, 12, 6),
    (1, 7, 300, 25, 15);

INSERT INTO MONSTER_BUFF (keyword, effect_desc)
VALUES
    ('광폭화', '체력 50% 이하 시 공격력 증가'),
    ('재생', '매 턴 HP 회복'),
    ('철갑', '방어력 증가');

INSERT INTO MEMBER (login_id, password_hash)
VALUES
    ('testUser1', '$2a$10$examplehashedpassword'),
    ('testUser2', '$2a$10$examplehashedpassword');

INSERT INTO GAME_STATE (member_id, difficulty, current_stage, score, gold)
VALUES
    (1, 'NORMAL', 1, 0, 100),
    (2, 'HARD', 3, 500, 300);

INSERT INTO GAME_CHARACTER (game_state_id, level, current_hp, max_hp, attack, defense)
VALUES
    (1, 1, 100, 100, 10, 5),
    (2, 3, 180, 200, 25, 12);

INSERT INTO GAME_EQUIPMENT (game_state_id, weapon_id, armor_id, helmet_id)
VALUES
    (1, 1, 1, 1),
    (2, 3, 2, 2);

INSERT INTO GAME_SKILL_SLOT (game_state_id, skill_id, slot_index, cool_time)
VALUES
    (1, 1, 0, 0),
    (1, 2, 1, 0),
    (2, 3, 0, 0);

INSERT INTO GAME_RELIC_INVENTORY (game_state_id, relic_id)
VALUES
    (2, 1),
    (2, 2);

INSERT INTO GAME_CONSUMABLE_INVENTORY (game_state_id, consumable_id)
VALUES
    (1, 1),
    (1, 2);

INSERT INTO RANKING (member_id, game_state_id, difficulty, best_score, clear_time)
VALUES
    (2, 2, 'HARD', 500, 360);

INSERT INTO COLLECTION_SKILL (member_id, skill_id) VALUES (1,1);
INSERT INTO COLLECTION_WEAPON (member_id, weapon_id) VALUES (1,1);
INSERT INTO COLLECTION_ARMOR (member_id, armor_id) VALUES (1,1);
INSERT INTO COLLECTION_HELMET (member_id, helmet_id) VALUES (1,1);
INSERT INTO COLLECTION_CONSUMABLE (member_id, consumable_id) VALUES (1,1);
INSERT INTO COLLECTION_RELIC (member_id, relic_id) VALUES (1,1);