package com.renovandelve.ai.service;

import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.ai.dto.response.RunReviewDTO;
import com.renovandelve.game.engine.GameSession;
import com.renovandelve.game.engine.RelicEffects;
import com.renovandelve.game.engine.SessionMonster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 게임 종료 후 AI 런 회고.
 *
 * 기존 "모험 요약"은 대화 기록을 3줄로 줄인 서사였다. 이쪽은 서버가 가진 확정 수치(스탯, 빌드, 마지막 전투)를
 * 근거로 주고, 결과를 자유 텍스트가 아닌 정해진 구조(RunReviewDTO)로 받는다.
 *
 * - Structured Output: .entity(RunReviewDTO.class) -> JSON 파싱/정규식 정리 코드를 직접 짜지 않아도 된다
 * - Grounding: 모델이 수치를 지어내지 않도록 사실 목록과 게임 규칙을 프롬프트에 직접 넣는다
 * - 대화 메모리를 쓰지 않는 1회성 호출이라 토큰 사용량이 일정하다
 * - 실패하면 null을 돌려주고 화면은 회고 없이 그대로 동작한다 (AI는 연출일 뿐 게임 진행의 조건이 아니다)
 */
@Slf4j
@Service
public class RunReviewService {
    private final ChatClient chatClient;

    public RunReviewService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        당신은 어두운 판타지 로그라이크 게임 'Renova & Delve'의 기록관입니다.
                        모험가의 한 판이 끝나면 그 기록을 평가합니다.
                        세계관: 천상(질서)과 마계(욕망) 사이에 낀 인간 왕국 레노바. 왕이 악마와 계약해 지하에 마계의 문이 열렸고, 한 인간이 그 문을 향해 내려갑니다.
                        칭호와 묘비명에는 이 인간이 어느 세력에 기울었는지(성향)를 반영합니다.

                        또한 당신은 '레노바 연대기'의 필경사입니다. 이 플레이어의 원정은 하나씩 연대기의 장(章)이 되고,
                        모든 장은 같은 왕국에서 이어지는 하나의 이야기입니다. 이번 원정의 장을 씁니다.
                        - [앞선 장]이 있으면 첫 문장에서 반드시 앞선 장의 장소·적·선택 중 하나를 이어받습니다.
                          이번 원정자는 앞선 원정자와 다른 사람입니다. 예: '앞선 이가 위병에게서 등을 돌렸던 그 정문을, 그는 다시 지났다.'
                        - [이번 원정의 선택]에 있는 장면과 선택을 장의 뼈대로 삼습니다. 없는 사건을 지어내지 않습니다.
                        - 적의 이름은 [이번 판의 사실]의 '마지막 전투의 적'에 적힌 이름을 그대로 씁니다.
                          '악마화된 왕'은 스테이지 5의 마지막 노드에서만 만납니다. 그 전의 보스를 왕이라 부르지 않습니다.
                        - 수치는 쓰지 않습니다. 3인칭 과거형으로 씁니다.

                        ## 규칙
                        - 반드시 아래 [이번 판의 사실]에 있는 수치와 이름만 근거로 삼습니다. 없는 아이템·스킬·몬스터를 지어내지 않습니다.
                        - 조언은 [게임 규칙] 안에서 실제로 할 수 있는 행동만 제안합니다.
                        - 항상 한국어로, 음산하지만 담백한 문체로 씁니다.

                        ## 게임 규칙
                        - 스테이지 5개 x 10노드, 10번째 노드는 보스. 스테이지 5 보스를 잡으면 클리어.
                        - 피해 = 공격력 x 스킬계수 - 대상 방어력. 받는 피해 = 적 공격력 - 내 방어력 (최소 적 공격력의 15%).
                        - 적은 매 턴 행동을 예고한다: 공격 / 강공(1.6배) / 방어 태세(타격 피해 절반).
                        - 플레이어는 스킬 대신 방어 태세(받는 피해 60% 감소)를 쓸 수 있다.
                        - 상태이상: 출혈(피격마다 추가 피해), 화상(최대 HP 비례), 독(사라지지 않는 고정 피해), 기절(행동 불가).
                        - 보스는 HP 절반 이하에서 격노(공격력 +30%)한다. 보스를 잡으면 모닥불에서 휴식(HP 35%) / 단련(공격 +3, 방어 +1) / 서원(성향 +1) 중 하나를 고른다.
                        - 서약은 원정 전에 스스로 거는 제약이며 점수 보너스를 준다.
                        - 갈림길: 위험한 길(적 +25%, 보상 +50%, 유물 후보 +1) / 은밀한 길(적 첫 턴 행동 불가, 보상 -30%).
                        - 엘리트·보스 전투는 유물 보상 후보가 더 많다. 유물 조합은 시너지를 만든다.
                        - 조우 이벤트의 선택지는 천상/마계/인간의 방식 중 하나다. 한 세력의 선택이 3 이상이고 단독 1위면 가호가 붙는다: 천상(승리 시 HP 6% 회복) / 마계(공격 +12%, 받는 피해 +10%) / 인간(골드 +20%).
                        - 도감에 등록한 항목이 많을수록 다음 판 스타팅 포인트가 늘어난다.
                        """)
                .build();
    }

    /**
     * @return 회고. 생성에 실패하면 null
     */
    public RunReviewDTO review(GameStateEntity gameState, GameSession session, boolean cleared,
                               List<String> previousChapters, List<String> choices) {
        try {
            RunReviewDTO review = chatClient.prompt()
                    .user("[이번 판의 사실]\n" + describe(gameState, session, cleared)
                            + "\n" + story(previousChapters, choices)
                            + "\n\n위 기록을 평가하고, 연대기의 이번 장을 써 주세요.")
                    .call()
                    .entity(RunReviewDTO.class);
            return sanitize(review);
        } catch (Exception e) {
            log.warn("AI 런 회고 생성 실패 gameStateId={} 원인={}", gameState.getGameStateId(), e.getMessage());
            return null;
        }
    }

    /**
     * 연대기를 이어 쓰기 위한 앞 이야기와 이번 원정의 선택.
     *
     * @param previousChapters 앞선 장 (오래된 것부터)
     * @param choices          이번 원정에서 내린 선택
     * @return 프롬프트 조각
     */
    public static String story(List<String> previousChapters, List<String> choices) {
        StringBuilder sb = new StringBuilder("[앞선 장]\n");
        if (previousChapters == null || previousChapters.isEmpty()) sb.append("- 없음 (이번이 연대기의 첫 장)\n");
        else previousChapters.forEach(c -> sb.append("- ").append(c).append('\n'));
        sb.append("[이번 원정의 선택]\n");
        if (choices == null || choices.isEmpty()) sb.append("- 기록 없음\n");
        else choices.forEach(c -> sb.append("- ").append(c).append('\n'));
        return sb.toString();
    }

    /** 모델에게 근거로 넘길 사실 목록. 서버가 가진 확정 값만 담는다. */
    String describe(GameStateEntity gameState, GameSession session, boolean cleared) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 결과: ").append(cleared ? "클리어" : "사망").append('\n');
        sb.append("- 난이도: ").append(gameState.getDifficulty()).append('\n');
        sb.append("- 도달: 스테이지 %d, 노드 %d\n".formatted(gameState.getCurrentStage(),
                gameState.getCurrentNodeId() == null ? 0 : gameState.getCurrentNodeId()));
        sb.append("- 최종 점수 %d, 남은 골드 %d\n".formatted(gameState.getScore(), gameState.getGold()));
        sb.append("- 스스로 건 서약: ").append(gameState.getPacts() == null ? "없음"
                : String.join(", ", com.renovandelve.game.engine.Pact.titles(com.renovandelve.game.engine.Pact.parse(gameState.getPacts())))).append('\n');
        sb.append("- 성향(조우에서 택한 방식의 횟수): ").append(gameState.alignment().describe()).append('\n');

        GameCharacterEntity character = gameState.getGameCharacter() == null ? null
                : gameState.getGameCharacter().stream().findFirst().orElse(null);
        if (character != null) {
            sb.append("- 캐릭터: Lv.%d, 최대 HP %d, 공격력 %d, 방어력 %d\n".formatted(
                    character.getLevel(), character.getMaxHp(), character.getAttack(), character.getDefense()));
        }

        GameEquipmentEntity equipment = gameState.getGameEquipment();
        if (equipment != null) {
            sb.append("- 장비: 무기 %s / 갑옷 %s / 투구 %s\n".formatted(
                    equipment.getWeapon() == null ? "없음" : equipment.getWeapon().getName(),
                    equipment.getArmor() == null ? "없음" : equipment.getArmor().getName(),
                    equipment.getHelmet() == null ? "없음" : equipment.getHelmet().getName()));
        }

        String skills = gameState.getGameSkillSlot() == null ? "" : gameState.getGameSkillSlot().stream()
                .filter(slot -> slot.getSkill() != null)
                .map(slot -> slot.getSkill().getName())
                .collect(Collectors.joining(", "));
        sb.append("- 보유 스킬: ").append(skills.isEmpty() ? "없음" : skills).append('\n');

        List<String> relics = gameState.getGameRelicInventory() == null ? List.of()
                : gameState.getGameRelicInventory().stream()
                    .filter(inventory -> inventory.getRelic() != null)
                    .map(inventory -> inventory.getRelic().getName())
                    .toList();
        sb.append("- 보유 유물: ").append(relics.isEmpty() ? "없음" : String.join(", ", relics)).append('\n');

        List<String> synergies = RelicEffects.of(relics).getActiveSynergies();
        sb.append("- 발동한 유물 시너지: ").append(synergies.isEmpty() ? "없음" : synergies.size() + "개").append('\n');

        if (session != null && session.getMonsters() != null && !session.getMonsters().isEmpty()) {
            String monsters = session.getMonsters().stream()
                    .map(this::describeMonster)
                    .collect(Collectors.joining(" / "));
            sb.append("- 마지막 전투의 적: ").append(monsters).append('\n');
        } else if (!cleared) {
            sb.append("- 마지막 전투의 적: 없음 (조우 이벤트에서 쓰러짐)\n");
        }
        return sb.toString();
    }

    /**
     * 마지막 전투의 몬스터를 회고 프롬프트용 한 줄로 만든다.
     *
     * @param monster 몬스터
     * @return 이름, 등급, 능력치, 남은 HP
     */
    private String describeMonster(SessionMonster monster) {
        String grade = switch (monster.getMonsterType()) { case 2 -> "보스"; case 1 -> "엘리트"; default -> "일반"; };
        return "%s(%s, 공격력 %d, 방어력 %d, 남은 HP %d/%d%s)".formatted(
                monster.getName(), grade, monster.getAtk(), monster.getDef(),
                monster.getCurrentHp(), monster.getMaxHp(), monster.isEnraged() ? ", 격노" : "");
    }

    /** 모델이 형식을 어겼을 때를 대비한 최소한의 정리 (길이 제한, 빈 값 보정) */
    private RunReviewDTO sanitize(RunReviewDTO review) {
        if (review == null || isBlank(review.title()) || isBlank(review.analysis())) {
            return null;
        }
        List<String> tips = review.tips() == null ? List.of()
                : review.tips().stream().filter(tip -> !isBlank(tip)).map(tip -> cut(tip, 80)).limit(3).toList();
        return new RunReviewDTO(cut(review.title(), 20), cut(review.epitaph(), 80), cut(review.analysis(), 240), tips,
                isBlank(review.chapterTitle()) ? null : cut(review.chapterTitle(), 30),
                isBlank(review.chapter()) ? null : cut(review.chapter(), 500));
    }

    /**
     * 비어 있는지 확인한다.
     *
     * @param value 값
     * @return null이거나 공백뿐이면 true
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 앞뒤 공백을 지우고 최대 길이로 자른다.
     *
     * @param value 값
     * @param max   최대 길이
     * @return 자른 값
     */
    private String cut(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }
}
