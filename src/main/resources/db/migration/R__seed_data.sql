-- =========================================================
-- Renova & Delve 시드 데이터 (멱등: 재기동해도 중복 삽입 없음)
-- =========================================================

-- ---------------------------------------------------------
-- 기존 회원 데이터 보정
-- role / status 컬럼은 나중에 추가되었기 때문에, 그 이전에 가입한 계정은 NULL 상태다.
-- NULL이면 로그인·회원목록 조회에서 NPE가 발생하므로 기본값으로 채워준다.
-- ---------------------------------------------------------
UPDATE member SET role = 'USER' WHERE role IS NULL;
UPDATE member SET status = 'ACTIVE' WHERE status IS NULL;
UPDATE member SET nickname = login_id WHERE nickname IS NULL OR nickname = '';

-- 몬스터 목록
INSERT INTO monster (name, description, monster_type, score_reward)
VALUES ('바위 골렘', '고대의 마법으로 움직이는 거대한 석상입니다. 온몸이 단단한 바위로 이루어져 있으며, 육중한 주먹을 휘둘러 지면을 내리쳐 지진을 일으킵니다.', 0, 0),
       ('미친 사용인', '넝마가 된 하인복을 입고 기괴하게 몸을 비틀며 달려드는 좀비입니다. 손에는 날카로운 식기를 쥐고 있거나 날카로운 손톱을 세워 무차별적으로 공격합니다.', 0, 0),
       ('좀비', '썩어가는 살점과 악취를 풍기며 느릿하게 다가오는 시체입니다. 강한 아귀힘으로 대상을 붙잡고 날카로운 손톱으로 할퀴거나 이빨로 물어뜯으려 합니다.', 0, 0),
       ('해골 궁수', '살점 없이 뼈만 남은 궁수가 낡은 활을 들고 경계를 섭니다. 뼈마디가 부딪히는 소리를 내며 원거리에서 저주받은 화살을 정확하게 쏘아 올립니다.', 0, 0),
       ('독거미', '보라색 반점이 특징인 거대한 거미입니다. 천장이나 식물 사이에 숨어 있다가 기습하며, 끈적이는 거미줄로 움직임을 봉쇄한 뒤 날카로운 송곳니로 독을 주입합니다.', 0, 0),
       ('늑대', '굶주림에 눈이 뒤집힌 짐승입니다. 낮은 자세로 으르렁거리며 빠르게 돌진하고, 날카로운 이빨과 발톱을 휘둘러 먹잇감을 찢어발깁니다.', 0, 0),
       ('도적', '어둠 속에 몸을 숨긴 채 기회를 노리는 인간형 적입니다. 복면을 쓰고 소리 없이 접근하여 날카로운 단검으로 치명적인 부위를 찌르고 사라집니다.', 0, 0),
       ('인간 병사', '정신이 오염된 채 과거의 의무를 수행하는 병사입니다. 녹슨 갑옷을 입고 방패로 몸을 보호하며, 훈련된 검술을 사용하여 무차별적인 공격을 가해옵니다.', 0, 0),
       ('뒤틀린 정원 식목', '기괴하게 변이하여 움직이는 나무와 덩굴입니다. 가시 돋친 촉수와 칼날처럼 날카로운 잎을 휘두르며, 바닥에서 솟아오르는 뿌리로 적의 발을 묶습니다.', 0, 0),
       ('탐욕스러운 부랑자', '넝마를 걸치고 보물을 찾아 헤매는 비열한 자들입니다. 지저분한 모습으로 숨어 있다가 깨진 단검이나 독이 든 주머니를 던지며 기습적으로 덤벼듭니다.', 0, 0),
       ('광증의 기사', '광기에 휩싸여 명예를 잃어버린 기사입니다. 부서진 판금 갑옷을 걸친 채 미친 듯이 웃으며 거대한 검을 휘두르고 방패로 적을 밀쳐내며 압박합니다.', 1, 0),
       ('눈먼 저격병', '눈이 가려진 채 소리에만 의존하여 사격하는 궁수입니다. 아주 작은 기척에도 즉각 반응하여 석궁을 발사하며, 보이지 않는 곳에서 화살 세례를 퍼붓습니다.', 1, 0),
       ('파편박힌 살점 괴물', '신체 곳곳에 수많은 무기 파편과 고철이 박힌 끔찍한 살덩어리 괴물입니다. 여러 개의 팔 대신 달린 칼날들을 휘두르며 기괴한 금속 마찰음을 내며 돌진합니다.', 1, 0),
       ('타락한 부관', '부분적인 이성을 유지한 채 정예 기사들을 지휘하는 타락한 지휘관입니다.', 1, 0),
       ('썩어가는 위병', '녹슨 창과 찢어진 군복을 걸친 채 성벽을 배회하는 언데드 위병입니다.', 0, 0),
       ('묘지기 가고일', '석상처럼 위장하고 있다가 날카로운 발톱으로 기습하는 괴수입니다.', 1, 0),
       ('가시 돋친 넝쿨 괴물', '수많은 가시 덩굴을 휘둘러 적을 결박하고 출혈을 일으키는 식물형 괴물입니다.', 1, 0),
       ('광증의 중장갑병', '전신을 중갑으로 두른 채 거대한 철퇴를 휘두르는 광기의 병사입니다.', 1, 0),
       ('굶주린 군견', '뼈가 드러난 채 굶주림에 미쳐버린 왕궁의 사냥개입니다.', 0, 0),
       ('덩굴에 묶인 갑옷', '오염된 식물 덩굴이 녹슨 갑옷을 감싸 스스로 움직이는 존재입니다.', 1, 0),
       ('기사단장', '화려한 갑옷과 장검을 지닌 강력한 기사단의 수장입니다.', 2, 0),
       ('얼음 마법사', '차가운 서리의 기운을 뿜어내는 마법사입니다.', 0, 0),
       ('화염 마법사', '타오르는 불꽃을 다루는 마법사입니다.', 0, 0),
       ('타락한 궁정 마법사', '보라색 마력이 불안정하게 일렁이는 찢어진 로브를 입은 마법사입니다.', 1, 0),
       ('연금술 실패작', '연금술 실험의 부산물로 탄생한 형체 없는 괴물입니다.', 1, 0),
       ('금서의 수호령', '금서를 지키기 위해 결속된 실체 없는 망령입니다.', 1, 0),
       ('별빛의 광신도', '별의 낙인을 몸에 새긴 채 미쳐버린 마법사입니다.', 1, 0),
       ('환영의 마법사', '수정구를 통해 허상을 만들어내는 마법 사제입니다.', 1, 0),
       ('망령', '실체가 없는 투명한 형체로 벽을 자유롭게 통과하며 나타나는 원혼입니다.', 0, 0),
       ('그림자 암살자', '어둠과 하나가 되어 소리 없이 움직이는 암살자입니다.', 1, 0),
       ('슬라임', '연두색 점액질로 이루어진 말랑한 액체 괴물입니다.', 0, 0),
       ('고블린', '작고 교활한 초록색 피부의 괴물입니다.', 0, 0),
       ('스켈레톤', '마계의 기운으로 다시 깨어난 해골 병사입니다.', 0, 0),
       ('거대거미', '어두운 곳에 거미줄을 치고 잠복하는 거대한 괴수입니다.', 0, 0),
       ('쥐인간', '하수구 오물 속에 숨어 사는 쥐 형상의 수인입니다.', 0, 0),
       ('고문실의 집행자', '고문실을 지키는 끔찍한 거구의 괴물입니다.', 1, 0),
       ('하수구 점액질', '하수구의 오물과 폐기물이 섞여 만들어진 악취 나는 점액질 괴물입니다.', 1, 0),
       ('피의 연금술사', '자신의 피를 매개로 금지된 연금술을 부리는 타락한 마법사입니다.', 1, 0),
       ('하수구 쥐괴물', '마계의 기운으로 거대하게 변이된 쥐 괴물입니다.', 1, 0),
       ('변형된 실험체', '강제로 신체가 개조되어 이성을 잃은 실험체입니다.', 1, 0),
       ('식인 식물', '화려한 꽃잎 속에 거대한 입을 숨기고 있는 포식 식물입니다.', 1, 0),
       ('오물 속의 집게발', '하수구 오물 속에 숨어 있는 갑각류 형태의 괴물입니다.', 1, 0),
       ('실패한 융합체', '여러 구의 시체가 기괴하게 뒤섞여 만들어진 살덩어리입니다.', 1, 0),
       ('피의 갈증을 느끼는 사제', '피의 제단에서 금지된 의식을 거행하는 사제입니다.', 1, 0),
       ('가죽을 벗기는 도살자', '고문실에서 인체 실험을 자행하는 거구의 괴물입니다.', 1, 0),
       ('흡혈귀', '창백한 피부와 우아한 귀족의 의복을 걸친 고위 언데드입니다.', 1, 0),
       ('마계 날벌레무리', '인간의 머리만한 크기를 가진 마계의 날벌레 무리입니다.', 0, 0),
       ('어둠의 정령', '실체가 없는 검은 그림자 속에 보라색 불꽃이 일렁이는 정령입니다.', 1, 0),
       ('가시 돋친 내장', '신체의 내장 기관이 뒤틀린 식물처럼 변이된 기괴한 괴물입니다.', 1, 0),
       ('장기 수집가', '여러 사람의 신체 부위를 기괴하게 이어 붙인 인체 실험의 결과물입니다.', 1, 0),
       ('의식의 사제', '검은 로브를 깊게 눌러쓰고 타락한 의식을 진행하는 사제입니다.', 1, 0),
       ('회랑의 망령 기사', '반투명한 갑옷 사이로 푸른 영혼의 불꽃이 타오르는 강력한 파수꾼입니다.', 1, 0),
       ('피의 제단 수호자', '피의 제단을 지키는 거대하고 끔찍한 수호자입니다.', 2, 0),
       ('심연의 그림자', '마계 포탈에서 흘러나온 짙은 심연의 기운이 형상화된 존재입니다.', 2, 0),
       ('흡혈귀 귀족', '왕의 측근이자 강력한 혈액 마법을 사용하는 흡혈귀 백작입니다.', 2, 0),
       ('뼈의 군주', '거대한 해골의 형상을 한 언데드의 지휘관입니다.', 2, 0),
       ('심연의 용', '보라색 화염과 심연의 기운을 내뿜는 거대한 고대 용입니다.', 2, 0),
       ('악마화된 왕', '악마와 계약하여 타락한 군주로 변해버린 최종 보스입니다.', 2, 0),
       ('빛을 잃은 성가대', '정화의 선발대로 내려왔다가 마계의 숨결에 물든 하급 천사들입니다. 깨진 후광을 쓴 채 끊어진 성가를 되풀이하며 다가옵니다.', 0, 0),
       ('속삭이는 임프', '계약을 권하러 다니는 작은 악마입니다. 귓가에 달라붙어 소원을 묻고, 거절당하면 불씨를 던지며 할퀴어 댑니다.', 0, 0),
       ('계시받은 순례자', '천사의 목소리를 들었다고 믿는 인간 광신도입니다. 흰 천으로 눈을 가린 채 정화의 횃불을 휘두릅니다.', 0, 0),
       ('심판의 집행자', '천상의 저울을 든 전투 천사입니다. 죄의 무게를 재고, 기울었다고 판단하면 타오르는 검으로 망설임 없이 베어 냅니다.', 1, 0),
       ('계약의 악마', '붉은 인장이 찍힌 계약서를 두른 악마입니다. 거짓말은 하지 않으며, 상대가 가장 원하는 것을 내밀고 그 대가를 강제로 받아 갑니다.', 1, 0),
       ('추락한 수호천사', '지키던 인간을 잃고 스스로 날개를 꺾은 천사입니다. 검게 그을린 날개로 몸을 감싸고 창을 휘두르며 누구도 지나가지 못하게 막습니다.', 1, 0),
       ('정화의 대천사', '왕국을 불태우라는 명을 받고 내려온 대천사입니다. 여섯 장의 날개와 수많은 눈을 가졌으며, 자비로운 목소리로 정화의 불을 내립니다.', 2, 0),
       ('유혹의 마왕', '왕과 계약을 맺은 장본인인 고위 악마입니다. 우아한 귀족의 모습으로 나타나 달콤한 제안을 건네고, 거절하면 그림자 전체로 덮쳐 옵니다.', 2, 0)
ON CONFLICT (name, monster_type) DO NOTHING;

-- =========================================================
-- 스킬 (cost = 스타팅 포인트 비용)
-- =========================================================
INSERT INTO skill (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
SELECT '기본 공격', 'EnemySingle', 1, 1.00, 0, 0, 0, 0, '적 하나를 공격하는 기본 물리 공격'
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE name = '기본 공격');

INSERT INTO skill (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
SELECT '강타', 'EnemySingle', 1, 1.60, 120, 2, 0, 0, '온 힘을 실은 일격. 공격력의 160% 피해'
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE name = '강타');

INSERT INTO skill (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
SELECT '연속 베기', 'EnemySingle', 3, 0.55, 150, 2, 1, 1, '빠르게 3회 베어 출혈 1스택 부여'
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE name = '연속 베기');

INSERT INTO skill (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
SELECT '출혈 베기', 'EnemySingle', 2, 0.75, 140, 2, 1, 2, '깊게 두 번 베어 출혈 2스택 부여'
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE name = '출혈 베기');

INSERT INTO skill (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
SELECT '화염구', 'EnemyAll', 1, 1.10, 200, 3, 2, 2, '적 전체에 화염 피해 + 화상 2스택'
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE name = '화염구');

INSERT INTO skill (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
SELECT '맹독 단검', 'EnemySingle', 1, 0.90, 180, 2, 3, 3, '독 3스택 부여. 독은 사라지지 않는다'
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE name = '맹독 단검');

INSERT INTO skill (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
SELECT '기절 타격', 'EnemySingle', 1, 0.70, 220, 4, 4, 1, '적 하나를 1턴 기절시킨다'
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE name = '기절 타격');

INSERT INTO skill (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
SELECT '회전 베기', 'EnemyAll', 1, 0.85, 160, 3, 0, 0, '주변 모든 적을 휩쓰는 회전 공격'
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE name = '회전 베기');

INSERT INTO skill (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
SELECT '처형', 'EnemySingle', 1, 2.40, 300, 5, 0, 0, '공격력의 240% 피해를 주는 필살기'
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE name = '처형');

INSERT INTO skill (name, target, attack_count, coefficient, cost, cooldown, status_effect, effect_stack, description)
SELECT '헬파이어', 'EnemyAll', 2, 1.00, 350, 5, 2, 3, '지옥불로 적 전체를 2회 불태우고 화상 3스택'
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE name = '헬파이어');

-- =========================================================
-- 무기 (score_cost 0 = 기본 지급)
-- =========================================================
INSERT INTO weapon (name, type, atk_bonus, special_effect, score_cost, description)
SELECT '낡은 검', 'SWORD', 2, 0, 0, '오래됐지만 믿을 수 있는 기본 검' WHERE NOT EXISTS (SELECT 1 FROM weapon WHERE name = '낡은 검');
INSERT INTO weapon (name, type, atk_bonus, special_effect, score_cost, description)
SELECT '견습 기사의 검', 'SWORD', 6, 0, 100, '왕실 견습 기사에게 지급되던 표준 검' WHERE NOT EXISTS (SELECT 1 FROM weapon WHERE name = '견습 기사의 검');
INSERT INTO weapon (name, type, atk_bonus, special_effect, score_cost, description)
SELECT '암살자의 단검', 'DAGGER', 4, 1, 150, '가볍고 날카롭다. 치명타 확률 +15%' WHERE NOT EXISTS (SELECT 1 FROM weapon WHERE name = '암살자의 단검');
INSERT INTO weapon (name, type, atk_bonus, special_effect, score_cost, description)
SELECT '전쟁 둔기', 'BLUNT', 10, 0, 200, '묵직한 한 방. 느리지만 강력하다' WHERE NOT EXISTS (SELECT 1 FROM weapon WHERE name = '전쟁 둔기');
INSERT INTO weapon (name, type, atk_bonus, special_effect, score_cost, description)
SELECT '마법사의 지팡이', 'STAFF', 8, 0, 180, '마력이 깃든 궁정 마법사의 지팡이' WHERE NOT EXISTS (SELECT 1 FROM weapon WHERE name = '마법사의 지팡이');
INSERT INTO weapon (name, type, atk_bonus, special_effect, score_cost, description)
SELECT '멸망의 지팡이', 'STAFF', 20, 2, 400, '심연의 기운이 요동치는 금단의 무기' WHERE NOT EXISTS (SELECT 1 FROM weapon WHERE name = '멸망의 지팡이');

-- =========================================================
-- 갑옷
-- =========================================================
INSERT INTO armor (name, type, def_bonus, special_effect, score_cost, description)
SELECT '여행자의 천 옷', 'CLOTH', 1, 0, 0, '방어력은 기대하지 말 것' WHERE NOT EXISTS (SELECT 1 FROM armor WHERE name = '여행자의 천 옷');
INSERT INTO armor (name, type, def_bonus, special_effect, score_cost, description)
SELECT '가죽 갑옷', 'LEATHER', 4, 0, 100, '가볍고 실용적인 가죽 방어구' WHERE NOT EXISTS (SELECT 1 FROM armor WHERE name = '가죽 갑옷');
INSERT INTO armor (name, type, def_bonus, special_effect, score_cost, description)
SELECT '사슬 갑옷', 'CHAIN', 8, 0, 200, '촘촘한 사슬이 베기를 막아준다' WHERE NOT EXISTS (SELECT 1 FROM armor WHERE name = '사슬 갑옷');
INSERT INTO armor (name, type, def_bonus, special_effect, score_cost, description)
SELECT '기사단 판금 갑옷', 'METAL', 14, 1, 350, '왕실 기사단의 정식 판금 갑옷' WHERE NOT EXISTS (SELECT 1 FROM armor WHERE name = '기사단 판금 갑옷');

-- =========================================================
-- 투구
-- =========================================================
INSERT INTO helmet (name, type, hp_bonus, special_effect, score_cost, description)
SELECT '가죽 모자', 'LEATHER', 10, 0, 0, '없는 것보단 낫다' WHERE NOT EXISTS (SELECT 1 FROM helmet WHERE name = '가죽 모자');
INSERT INTO helmet (name, type, hp_bonus, special_effect, score_cost, description)
SELECT '마법사의 후드', 'CLOTH', 20, 0, 80, '마력이 스며든 후드' WHERE NOT EXISTS (SELECT 1 FROM helmet WHERE name = '마법사의 후드');
INSERT INTO helmet (name, type, hp_bonus, special_effect, score_cost, description)
SELECT '철 투구', 'METAL', 30, 0, 120, '단단한 강철 투구' WHERE NOT EXISTS (SELECT 1 FROM helmet WHERE name = '철 투구');
INSERT INTO helmet (name, type, hp_bonus, special_effect, score_cost, description)
SELECT '악마의 가면', 'MASK', 60, 2, 300, '강력한 저주가 깃든 가면. 생명력이 넘친다' WHERE NOT EXISTS (SELECT 1 FROM helmet WHERE name = '악마의 가면');

-- =========================================================
-- 소모품
-- =========================================================
INSERT INTO consumable (name, effect_type, effect_value, score_cost, description)
SELECT '소형 회복 물약', 'RECOVERY', 40, 40, 'HP 40 회복' WHERE NOT EXISTS (SELECT 1 FROM consumable WHERE name = '소형 회복 물약');
INSERT INTO consumable (name, effect_type, effect_value, score_cost, description)
SELECT '대형 회복 물약', 'RECOVERY', 100, 120, 'HP 100 회복' WHERE NOT EXISTS (SELECT 1 FROM consumable WHERE name = '대형 회복 물약');
INSERT INTO consumable (name, effect_type, effect_value, score_cost, description)
SELECT '해독제', 'RELEASE', 0, 30, '장 내의 모든 상태이상 기운을 정화한다' WHERE NOT EXISTS (SELECT 1 FROM consumable WHERE name = '해독제');
INSERT INTO consumable (name, effect_type, effect_value, score_cost, description)
SELECT '공격 강화 물약', 'ATTACK', 8, 80, '이번 전투 동안 공격력 +8' WHERE NOT EXISTS (SELECT 1 FROM consumable WHERE name = '공격 강화 물약');
INSERT INTO consumable (name, effect_type, effect_value, score_cost, description)
SELECT '방어 강화 물약', 'DEFENCE', 6, 70, '이번 전투 동안 방어력 +6' WHERE NOT EXISTS (SELECT 1 FROM consumable WHERE name = '방어 강화 물약');
INSERT INTO consumable (name, effect_type, effect_value, score_cost, description)
SELECT '화염 폭탄', 'DAMAGE', 35, 90, '적 전체에게 35의 고정 피해' WHERE NOT EXISTS (SELECT 1 FROM consumable WHERE name = '화염 폭탄');
INSERT INTO consumable (name, effect_type, effect_value, score_cost, description)
SELECT '불사조의 깃털', 'RESURRECTION', 1, 400, '사망 시 1회 부활 (HP 50%)' WHERE NOT EXISTS (SELECT 1 FROM consumable WHERE name = '불사조의 깃털');

-- =========================================================
-- 유물 (효과는 서버 RelicEffects에서 구현)
-- =========================================================
INSERT INTO relic (name, effect_desc, rarity, score_cost)
SELECT '행운의 반지', '골드 획득량 25% 증가', 'COMMON', 150 WHERE NOT EXISTS (SELECT 1 FROM relic WHERE name = '행운의 반지');
INSERT INTO relic (name, effect_desc, rarity, score_cost)
SELECT '사냥꾼의 눈', '치명타 확률 10% 증가', 'COMMON', 180 WHERE NOT EXISTS (SELECT 1 FROM relic WHERE name = '사냥꾼의 눈');
INSERT INTO relic (name, effect_desc, rarity, score_cost)
SELECT '핏빛 반지', '출혈 피해 50% 증가', 'RARE', 250 WHERE NOT EXISTS (SELECT 1 FROM relic WHERE name = '핏빛 반지');
INSERT INTO relic (name, effect_desc, rarity, score_cost)
SELECT '마력 증폭 수정', '스킬 피해 15% 증가', 'RARE', 300 WHERE NOT EXISTS (SELECT 1 FROM relic WHERE name = '마력 증폭 수정');
INSERT INTO relic (name, effect_desc, rarity, score_cost)
SELECT '수호자의 문장', '받는 피해 2 감소', 'RARE', 280 WHERE NOT EXISTS (SELECT 1 FROM relic WHERE name = '수호자의 문장');
INSERT INTO relic (name, effect_desc, rarity, score_cost)
SELECT '고대의 부적', '스킬 쿨타임 1 감소 (최소 1)', 'EPIC', 450 WHERE NOT EXISTS (SELECT 1 FROM relic WHERE name = '고대의 부적');
INSERT INTO relic (name, effect_desc, rarity, score_cost)
SELECT '심연의 핵', '공격력 10% / 방어력 +2 증가', 'EPIC', 500 WHERE NOT EXISTS (SELECT 1 FROM relic WHERE name = '심연의 핵');

-- =========================================================
-- 몬스터 기본 스탯 (필드 강도 시스템: 스테이지 x 난이도)
-- difficulty: 0=EASY, 1=NORMAL, 2=HARD, 3=HELL
--
-- v0.9.0 밸런스 조정: 봇 시뮬레이션(신중형)으로 목표 클리어율 쉬움 60% / 보통 30% / 어려움 12% / 지옥 3%에 맞췄다.
-- 예전 보통 표에 난이도 배율(1.245 / 1.31 / 1.36 / 1.415)과 스테이지 곡선(1.12 / 1.10 / 1.10 / 1.08 / 1.00)을 곱한 값이다.
-- 방어력은 그대로. 측정 방법과 결과는 docs/balance.md.
-- 이 표가 기준값이다: 새 DB에는 넣고, 기존 DB의 값은 아래 UPDATE로 이 표에 맞춘다(파일이 바뀌면 Flyway가 다시 실행한다).
-- =========================================================
CREATE TEMP TABLE seed_monster_base_status (stage smallint, difficulty smallint, base_hp int, base_atk int, base_def int) ON COMMIT DROP;
INSERT INTO seed_monster_base_status VALUES
    (1, 0,  84, 11,  2), (2, 0, 123, 16,  3), (3, 0, 178, 23,  5), (4, 0, 242, 31,  7), (5, 0, 299, 37, 10),
    (1, 1,  88, 12,  2), (2, 1, 130, 17,  4), (3, 1, 187, 24,  6), (4, 1, 255, 33,  9), (5, 1, 314, 39, 12),
    (1, 2,  91, 12,  3), (2, 2, 135, 18,  5), (3, 2, 194, 25,  8), (4, 2, 264, 34, 12), (5, 2, 326, 41, 16),
    (1, 3,  95, 13,  4), (2, 3, 140, 19,  7), (3, 3, 202, 26, 11), (4, 3, 275, 35, 16), (5, 3, 340, 42, 22);

INSERT INTO monster_base_status (stage, difficulty, base_hp, base_atk, base_def)
SELECT s.stage, s.difficulty, s.base_hp, s.base_atk, s.base_def
FROM seed_monster_base_status s
WHERE NOT EXISTS (
    SELECT 1 FROM monster_base_status m
    WHERE m.stage = s.stage AND m.difficulty = s.difficulty
);

UPDATE monster_base_status m
SET base_hp = s.base_hp, base_atk = s.base_atk, base_def = s.base_def
FROM seed_monster_base_status s
WHERE m.stage = s.stage AND m.difficulty = s.difficulty
  AND (m.base_hp, m.base_atk, m.base_def) IS DISTINCT FROM (s.base_hp, s.base_atk, s.base_def);
