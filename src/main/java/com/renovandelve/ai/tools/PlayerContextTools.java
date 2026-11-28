package com.renovandelve.ai.tools;

import com.renovandelve.game.engine.StatusEffect;
import com.renovandelve.game.engine.MonsterIntent;
import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.engine.GameSession;
import com.renovandelve.game.engine.RelicEffects;
import com.renovandelve.game.engine.SessionMonster;
import com.renovandelve.game.engine.ShopStockItem;
import com.renovandelve.game.service.GameEngineServiceImpl;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.ranking.repository.RankingRepository;
import com.renovandelve.member.service.CurrentMemberService;
import com.renovandelve.game.service.redis.GameSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * "지금 내 상황"을 답하기 위한 Tools.
 *
 * 기존 GameTools가 정적 사전(아이템 도감)이었다면, 이쪽은 현재 플레이어의
 * 실제 게임 세션(스탯/장비/전투 상황)을 읽어 맥락 있는 조언을 가능하게 한다.
 * 예) "지금 이 보스 어떻게 잡아?", "내 스탯 어때?", "뭘 사야 해?"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerContextTools {

    private final CurrentMemberService currentMemberService;
    private final GameStateRepository gameStateRepository;
    private final GameSessionService gameSessionService;
    private final RankingRepository rankingRepository;

    /**
     * [AI Tool] 로그인한 플레이어의 진행 상황, 능력치, 스킬·유물·소모품, 성향과 가호를 요약한다.
     *
     * @return 상태 요약, 진행 중인 게임이 없으면 안내 문구
     */
    @Tool(description = "플레이어의 현재 상태를 조회합니다. 레벨, HP, 공격력, 방어력, 골드, 진행 중인 스테이지/노드와 전체 스테이지 수, 지금 착용 중인 장비와 보너스, 보유 스킬과 쿨타임, 보유 유물과 발동 중인 조합 시너지, 소모품을 모두 포함합니다. 상점에서 사거나 전투를 치르면 값이 바뀌므로, 이전 대화의 답을 쓰지 말고 질문마다 새로 호출하세요. '내 상태', '지금 내 스탯', '뭘 들고 있지', '내 장비', '어떤 장비가 좋아', '뭘 사야 해', '이 장비가 더 나아', '내가 어디까지 왔지' 같은 질문에 반드시 먼저 호출하세요.")
    @Transactional(readOnly = true)
    public String getMyStatus() {
        Optional<GameStateEntity> found = activeGame();
        if (found.isEmpty()) return "현재 진행 중인 게임이 없습니다. 새 게임을 시작해야 합니다.";
        GameStateEntity game = found.get();

        GameCharacterEntity character = game.getGameCharacter().stream().findFirst().orElse(null);
        StringBuilder sb = new StringBuilder();
        sb.append("[진행 상황] 난이도 %s / 스테이지 %d / 노드 %d (전체 %d스테이지, 스테이지당 %d노드, 각 스테이지의 마지막 노드는 보스. 스테이지 %d의 보스를 쓰러뜨리면 클리어)\n"
                .formatted(game.getDifficulty(), game.getCurrentStage(),
                        game.getCurrentNodeId() == null ? 0 : game.getCurrentNodeId(),
                        GameEngineServiceImpl.FINAL_STAGE, GameEngineServiceImpl.NODES_PER_STAGE, GameEngineServiceImpl.FINAL_STAGE));
        sb.append("[자원] 골드 %d / 누적 점수 %d / 불사조의 깃털(부활) %s\n"
                .formatted(game.getGold(), game.getScore(),
                        Boolean.TRUE.equals(game.getHasPhoenix()) ? "보유" : "없음"));

        List<String> pactTitles = com.renovandelve.game.engine.Pact.titles(com.renovandelve.game.engine.Pact.parse(game.getPacts()));
        sb.append("[서약] ").append(pactTitles.isEmpty() ? "없음" : String.join(", ", pactTitles)).append("\n");
        sb.append("[성향] %s%s\n".formatted(game.alignment().describe(),
                game.alignment().blessing() == null ? "" : " / 발동 중인 가호: " + game.alignment().blessing()));

        if (character != null) {
            sb.append("[스탯] Lv.%d / HP %d-%d / 공격력 %d / 방어력 %d / 경험치 %d(다음 레벨까지 %d)\n"
                    .formatted(character.getLevel(), character.getCurrentHp(), character.getMaxHp(),
                            character.getAttack(), character.getDefense(), character.getExp(),
                            Math.max(0, character.expToNextLevel() - character.getExp())));
        }

        String gear = describeEquipment(game.getGameEquipment());
        sb.append("[지금 착용 중인 장비] ").append(gear).append(" (한 부위에 하나, 사면 바로 교체된다. 위 공격력·방어력·HP에 이미 포함)\n");
        log.debug("[PlayerContextTools] getMyStatus game={} 장비={}", game.getGameStateId(), gear);

        String skills = game.getGameSkillSlot().stream()
                .filter(s -> s.getSkill() != null)
                .sorted((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()))
                .map(s -> "%s(계수 x%s, 쿨 %d턴%s)".formatted(
                        s.getSkill().getName(), s.getSkill().getCoefficient(), s.getSkill().getCooldown(),
                        s.getCoolTime() > 0 ? ", 현재 쿨타임 " + s.getCoolTime() + "턴 남음" : ", 사용 가능"))
                .collect(Collectors.joining(", "));
        sb.append("[스킬] ").append(skills.isEmpty() ? "없음" : skills).append("\n");

        List<String> relicNames = game.getGameRelicInventory().stream()
                .filter(r -> r.getRelic() != null)
                .map(r -> r.getRelic().getName())
                .toList();
        sb.append("[유물] ").append(relicNames.isEmpty() ? "없음" : String.join(", ", relicNames)).append("\n");

        List<String> synergies = RelicEffects.of(relicNames).getActiveSynergies();
        sb.append("[유물 조합 시너지] ").append(synergies.isEmpty() ? "발동 중인 조합 없음" : String.join(" / ", synergies)).append("\n");

        List<String> skillSynergies = com.renovandelve.game.engine.SkillSynergies.of(game.getGameSkillSlot().stream()
                .map(s -> s.getSkill()).filter(java.util.Objects::nonNull).toList()).getActiveSynergies();
        sb.append("[스킬 조합 시너지] ").append(skillSynergies.isEmpty() ? "발동 중인 조합 없음" : String.join(" / ", skillSynergies)).append("\n");

        String consumables = game.getGameConsumableInventory().stream()
                .filter(c -> c.getConsumable() != null)
                .map(c -> c.getConsumable().getName())
                .collect(Collectors.joining(", "));
        sb.append("[소모품] ").append(consumables.isEmpty() ? "없음" : consumables);

        return sb.toString();
    }

    /**
     * [AI Tool] 지금 전투 중인 적의 HP, 행동 예고, 상태이상을 요약한다.
     *
     * @return 전투 요약, 전투 중이 아니면 안내 문구
     */
    @Tool(description = "지금 진행 중인 전투 상황을 조회합니다. 등장한 몬스터의 이름, 등급, 현재/최대 HP, 공격력, 방어력, 걸려 있는 상태이상 스택을 알려줍니다. '이 몬스터 어떻게 잡아', '지금 누구부터 쳐야 해', '이번 전투 이길 수 있어?' 같은 질문에 호출하세요. 전투 중이 아니면 그 사실을 알려줍니다.")
    @Transactional(readOnly = true)
    public String getCurrentBattle() {
        Optional<GameStateEntity> found = activeGame();
        if (found.isEmpty()) return "현재 진행 중인 게임이 없습니다.";

        GameSession session = gameSessionService.get(found.get().getGameStateId());
        if (session == null) return "현재 노드 정보가 없습니다.";

        if (!GameSession.PHASE_BATTLE.equals(session.getPhase())) {
            return "지금은 전투 중이 아닙니다. 현재 단계: %s (%s)".formatted(
                    session.getPhase(), phaseKorean(session.getPhase()));
        }

        StringBuilder sb = new StringBuilder("[전투 유형] %s\n".formatted(
                switch (String.valueOf(session.getNodeType())) {
                    case "BOSS" -> "보스전";
                    case "ELITE" -> "엘리트전";
                    default -> "일반 전투";
                }));
        sb.append("[현재 서사] ").append(shorten(session.getStory())).append("\n[등장 몬스터]\n");

        for (SessionMonster monster : session.getMonsters()) {
            String buffs = monster.getBuffs().isEmpty() ? "없음"
                    : monster.getBuffs().entrySet().stream()
                        .map(e -> koreanBuff(e.getKey()) + " " + e.getValue() + "스택")
                        .collect(Collectors.joining(", "));
            sb.append("- %s (%s) HP %d/%d, 공격력 %d, 방어력 %d, 상태이상: %s, 이번 턴 예고 행동: %s%s%s\n".formatted(
                    monster.getName(),
                    switch (monster.getMonsterType()) { case 2 -> "보스"; case 1 -> "엘리트"; default -> "일반"; },
                    monster.getCurrentHp(), monster.getMaxHp(), monster.getAtk(), monster.getDef(), buffs,
                    switch (String.valueOf(monster.getIntent())) {
                        case MonsterIntent.HEAVY -> "강공(피해 1.6배)";
                        case MonsterIntent.GUARD -> "방어 태세(타격 피해 절반, 공격 안 함)";
                        default -> "일반 공격";
                    },
                    monster.isEnraged() ? " [격노]" : "",
                    monster.isAlive() ? "" : " [처치됨]"));
        }
        return sb.toString();
    }

    /**
     * [AI Tool] 전투 공식과 규칙(피해 계산, 예고, 방어, 격노, 모닥불 등)을 설명한다.
     *
     * @return 규칙 설명
     */
    @Tool(description = "게임의 전투 규칙과 시스템을 설명합니다. 상태이상(출혈/화상/독/기절)의 정확한 동작, 데미지 계산식, 치명타, 쿨타임, 레벨업, 보상/상점 흐름, 난이도별 배율을 알려줍니다. '출혈이 뭐야', '데미지는 어떻게 계산돼', '독이랑 화상 뭐가 달라' 같은 규칙 질문에 호출하세요.")
    public String getGameRules() {
        return """
                [데미지 계산] 최종 피해 = max(1, (공격력 x 스킬계수 x 유물배율) - 대상 방어력). 다단 공격은 이 계산을 타격 횟수만큼 반복합니다.
                [적 행동 예고] 각 몬스터는 이번 턴에 할 행동을 미리 보여줍니다. 공격(ATTACK) / 강공(HEAVY, 피해 1.6배) / 방어 태세(GUARD, 이번 턴 타격 피해를 절반만 받고 공격하지 않음). 같은 특수 행동을 두 턴 연속 하지는 않습니다.
                [방어 태세] 플레이어도 스킬 대신 방어를 선택할 수 있습니다. 공격을 포기하는 대신 이번 턴 받는 피해가 60% 감소하고 모든 스킬 쿨타임이 1턴 줄어듭니다. 적이 강공을 예고했을 때 쓰는 것이 핵심입니다.
                [받는 피해] 적 공격력 - 내 방어력 - 유물 감소분. 단, 방어력이 아무리 높아도 적 공격력의 15%는 최소 피해로 들어옵니다.
                [보스 격노] 보스는 HP가 절반 이하가 되면 격노하여 공격력이 30% 상승합니다. 격노 전에 기절·방어 물약을 아껴두세요.
                [모닥불] 보스를 처치하고 상점을 나서면 모닥불에서 하나를 고릅니다. 휴식(최대 HP의 35% 회복) / 단련(공격력 +3, 방어력 +1 영구) / 서원(천상·마계·인간 중 한 세력의 성향 +1). 체력이 넉넉하면 단련, 위태로우면 휴식이 낫습니다.
                [서약] 원정 전에 스스로 거는 제약입니다. 강철의 적(적 HP +20%, 점수 +15%) / 들끓는 분노(적 공격 +15%, 점수 +15%) / 탐욕의 세금(상점 가격 +30%, 점수 +10%) / 옅은 피(회복 절반, 점수 +15%) / 분노한 군주(보스 선제 격노, 점수 +10%). 점수 보너스는 합산됩니다.
                [세계관] 천상(질서)과 마계(욕망) 사이에 낀 인간 왕국 레노바. 왕이 악마와 계약해 왕궁 지하에 마계의 문이 열렸고, 천상은 왕국을 통째로 정화하려 합니다. 플레이어는 그 문을 향해 내려가는 인간입니다.
                [조우와 성향] 조우 이벤트의 선택지 세 개는 각각 천상/마계/인간의 방식입니다. 천상은 안전(회복, 가끔 헌물로 골드 손실), 마계는 고위험 고수익(유물·큰 골드 또는 큰 피해), 인간은 중간(골드·소모품, 가끔 작은 피해)입니다. 결과는 선택 전에는 알 수 없습니다.
                [가호] 한 세력의 선택이 3회 이상이고 단독 1위가 되면 가호가 붙습니다. 천상의 가호: 전투 승리 시 최대 HP 6% 회복 / 마계의 낙인: 공격력 +12%, 받는 피해 +10% / 인간의 의지: 골드 획득 +20%. 골고루 고르면 가호가 없습니다.
                [서사 반영] 조우에서 내린 선택은 이후 장면과 결말에 반영됩니다. 클리어 시 결말은 성향에 따라 네 가지로 갈립니다.
                [갈림길] 전진할 때 길을 고릅니다. 정면 돌파(기본) / 위험한 길(적 HP·공격력 +10%, 점수·골드·경험치 +50%, 유물 보상 후보 +1) / 은밀한 길(적 전원 첫 턴 행동 불가, 점수·골드·경험치 -20%). 조우 이벤트가 나오면 길 효과는 적용되지 않습니다.
                [유물 보상] 일반 전투는 유물 후보 1개, 엘리트 2개, 보스 3개가 제시됩니다. 여전히 보상은 하나만 고릅니다.
                [상점 재입고] 골드를 내고 진열품을 새로 뽑을 수 있습니다. 같은 상점에서 재입고할수록 비용이 오릅니다.
                [도감 보너스] 기본 스타팅 포인트는 500이고, 도감에 등록한 항목 1종마다 다음 판의 스타팅 포인트가 10씩 늘어납니다(최대 +500). [해금] 원정 준비에서 흔함 등급은 처음부터, 희귀·영웅 등급은 원정 중 한 번 손에 넣어 도감에 등록한 것만 고를 수 있습니다.
                [치명타] 기본 확률 8%. 단검 장착 시 +15%, 사냥꾼의 눈 유물 +10%. 치명타는 피해 1.5배.
                [출혈(BLEED)] 대상이 공격을 맞을 때마다 스택만큼 추가 피해. 방어력 무시. 다단히트 스킬과 궁합이 좋습니다.
                [화상(BURN)] 턴 종료 시 대상 최대 HP의 1% x 스택 피해. 방어력 무시. 매 턴 30% 확률로 1스택씩 자연 소멸.
                [독(POISON)] 턴 종료 시 스택만큼 고정 피해. 방어력 무시. 스스로 사라지지 않아 장기전에 강합니다.
                [기절(STUN)] 대상이 스택만큼의 턴 동안 공격하지 못합니다. 보스의 강한 턴을 넘길 때 유용합니다.
                [쿨타임] 스킬 사용 시 쿨타임이 걸리고, 다른 스킬은 매 턴 1씩 감소합니다. 고대의 부적 유물은 쿨타임을 1 줄여줍니다(최소 1).
                [소모품] 전투 중 사용하면 턴을 소모합니다(적의 반격을 받음). 탐험 중에는 턴 소모 없이 사용 가능합니다.
                [레벨업] 필요 경험치 = 현재 레벨 x 100. 레벨업 시 최대HP +15, 공격 +3, 방어 +2, 최대HP의 30% 회복.
                [진행] 스테이지당 10노드, 10번째 노드는 반드시 보스전. 스테이지 5의 10노드 보스를 처치하면 클리어.
                [전투 후] 승리하면 경험치/골드를 얻고, 스킬·유물·소모품 후보 중 하나만 선택한 뒤 상점을 이용합니다.
                [난이도 배율] EASY 적 0.8배·보상 0.7배(랭킹 미등록) / NORMAL 1.0배 / HARD 적 1.3배·보상 1.3배 / HELL 적 1.8배·보상 2.0배.
                [사망] HP가 0이 되면 게임 오버. 단, 불사조의 깃털을 보유했다면 1회 부활(HP 50%)합니다.
                """;
    }

    /**
     * [AI Tool] 유물 조합 시너지 목록과 발동 조건.
     *
     * @return 시너지 설명
     */
    @Tool(description = "유물 조합 시너지 목록을 조회합니다. 어떤 유물을 함께 모으면 추가 효과가 발동하는지 알려줍니다. '유물 조합', '시너지', '어떤 유물을 같이 써야 해' 같은 질문에 호출하세요.")
    public String getRelicSynergyGuide() {
        return """
                [유물 조합 시너지 목록]
                - 피의 사냥 = 핏빛 반지 + 사냥꾼의 눈 -> 치명타가 터질 때마다 출혈 2스택을 추가로 부여합니다. 다단히트 스킬(연속 베기)과 함께 쓰면 매우 강력합니다.
                - 비전 순환 = 마력 증폭 수정 + 고대의 부적 -> 스킬 피해가 추가로 10% 증가합니다(단일 효과와 합산 시 총 +25%).
                - 탐욕의 심연 = 행운의 반지 + 심연의 핵 -> 골드 획득량이 추가로 25% 증가합니다(총 +50%). 상점 활용도를 크게 올립니다.
                - 심연의 방벽 = 수호자의 문장 + 심연의 핵 -> 받는 피해가 추가로 2 감소합니다(총 -4). 다수의 적에게 맞을 때 효율이 좋습니다.
                - 사신의 공명 = 종류 무관 유물 4개 이상 보유 -> 공격력 +5%, 치명타 확률 +5%.
                시너지는 유물을 보유하기만 하면 자동으로 발동하며, 화면 상단에 금색 칩으로 표시됩니다.
                """;
    }

    /**
     * [AI Tool] 플레이어의 최근 모험 기록과 최고 점수.
     *
     * @return 기록 요약
     */
    @Tool(description = "플레이어의 역대 최고 기록과 난이도별 랭킹 순위를 조회합니다. '내 최고 기록', '내 랭킹', '몇 등이야' 같은 질문에 호출하세요.")
    @Transactional(readOnly = true)
    public String getMyRecords() {
        MemberEntity member;
        try {
            member = currentMemberService.getCurrentMember();
        } catch (Exception e) {
            return "로그인 정보를 확인할 수 없습니다.";
        }

        StringBuilder sb = new StringBuilder("[%s님의 기록]\n".formatted(member.displayName()));
        boolean any = false;
        for (var difficulty : com.renovandelve.game.domain.gamestateentity.Difficulty.values()) {
            var record = rankingRepository.findByMemberAndDifficulty(member, difficulty);
            if (record.isEmpty()) continue;
            any = true;
            var rank = record.get();
            long position = rankingRepository.findTop10ByDifficultyOrderByBestScoreDesc(difficulty).stream()
                    .takeWhile(r -> r.getBestScore() > rank.getBestScore())
                    .count() + 1;
            sb.append("- %s: 최고 점수 %d점 (TOP10 기준 %d위)%s\n".formatted(
                    difficulty, rank.getBestScore(), position,
                    rank.getClearTime() != null ? ", 클리어 " + rank.getClearTime() + "초" : ", 클리어 기록 없음"));
        }
        return any ? sb.toString() : "아직 등록된 기록이 없습니다. (EASY 난이도는 랭킹에 등록되지 않습니다)";
    }

    /* HELPER METHODS */

    /**
     * [AI Tool] 지금 열려 있는 상점의 실제 진열품(가격, 판매 여부, 장비는 착용 중인 것과의 비교)을 알려 준다.
     * 사전 목록(getAll*)은 게임에 존재하는 전체 아이템이고, 상점에는 그중 일부만 나온다.
     *
     * @return 진열품 요약, 상점 단계가 아니면 안내 문구
     */
    @Tool(description = "지금 상점에 실제로 진열된 품목을 조회합니다. 이름, 종류, 가격, 판매 여부, 장비라면 지금 착용 중인 장비와 비교한 보너스를 알려 줍니다. '상점에 뭐 있어', '여기서 뭘 사', '이거 살 만해' 같은 상점 질문에 호출하세요. getAllWeapons 같은 사전 목록은 게임 전체의 아이템이지 상점 진열품이 아닙니다. 상점 단계가 아니면 그 사실을 알려줍니다.")
    @Transactional(readOnly = true)
    public String getShopStock() {
        Optional<GameStateEntity> found = activeGame();
        if (found.isEmpty()) return "현재 진행 중인 게임이 없습니다.";
        GameStateEntity game = found.get();

        GameSession session = gameSessionService.get(game.getGameStateId());
        if (session == null || !GameSession.PHASE_SHOP.equals(session.getPhase())) {
            return "지금은 상점에 있지 않습니다. 현재 단계: %s. 상점은 전투 보상을 고른 뒤 가끔 나타납니다."
                    .formatted(session == null ? "알 수 없음" : phaseKorean(session.getPhase()));
        }
        List<ShopStockItem> stock = session.getShop();
        if (stock == null || stock.isEmpty()) return "상인이 아직 짐을 풀지 않았습니다 (진열품 없음).";

        GameEquipmentEntity equipped = game.getGameEquipment();
        StringBuilder sb = new StringBuilder("[보유 골드] %d\n[지금 착용 중인 장비] %s\n[진열품]\n"
                .formatted(game.getGold(), describeEquipment(equipped)));
        for (ShopStockItem item : stock) {
            sb.append("- %s %s, 가격 %d%s%s: %s\n".formatted(
                    shopTypeKorean(item.getItemType()), item.getName(), item.getPrice(),
                    item.isSold() ? " (판매 완료)" : game.getGold() < item.getPrice() ? " (골드 부족)" : "",
                    compareWithEquipped(item, equipped),
                    shorten(item.getDescription())));
        }
        return sb.toString();
    }

    /**
     * 착용 장비를 한 줄로.
     *
     * @param equipment 장비 슬롯 (없을 수 있음)
     * @return "무기: 이름(공격 +n) / 갑옷: ... / 투구: ..."
     */
    private String describeEquipment(GameEquipmentEntity equipment) {
        if (equipment == null) return "없음";
        return "무기: %s / 갑옷: %s / 투구: %s".formatted(
                equipment.getWeapon() == null ? "없음" : equipment.getWeapon().getName() + "(공격 +" + equipment.getWeapon().getAtkBonus() + ")",
                equipment.getArmor() == null ? "없음" : equipment.getArmor().getName() + "(방어 +" + equipment.getArmor().getDefBonus() + ")",
                equipment.getHelmet() == null ? "없음" : equipment.getHelmet().getName() + "(최대 HP +" + equipment.getHelmet().getHpBonus() + ")");
    }

    /**
     * 진열된 장비를 착용 중인 같은 부위 장비와 비교한 문구. 장비가 아니면 빈 문자열.
     *
     * @param item     진열품
     * @param equipped 장비 슬롯
     * @return 예: ", 공격 +10 (지금 착용 중 +20, 사면 -10)"
     */
    String compareWithEquipped(ShopStockItem item, GameEquipmentEntity equipped) {
        if (item.getBonus() == null) return "";
        String stat;
        int current;
        switch (String.valueOf(item.getItemType())) {
            case "WEAPON" -> { stat = "공격"; current = equipped == null || equipped.getWeapon() == null ? 0 : equipped.getWeapon().getAtkBonus(); }
            case "ARMOR" -> { stat = "방어"; current = equipped == null || equipped.getArmor() == null ? 0 : equipped.getArmor().getDefBonus(); }
            case "HELMET" -> { stat = "최대 HP"; current = equipped == null || equipped.getHelmet() == null ? 0 : equipped.getHelmet().getHpBonus(); }
            default -> { return ""; }
        }
        int diff = item.getBonus() - current;
        return ", %s +%d (지금 착용 중 +%d, 사면 %s%d)".formatted(stat, item.getBonus(), current, diff >= 0 ? "+" : "", diff);
    }

    /**
     * 진열품 종류를 한국어로.
     *
     * @param itemType WEAPON / ARMOR / HELMET / SKILL / RELIC / CONSUMABLE
     * @return 한국어 이름
     */
    private String shopTypeKorean(String itemType) {
        return switch (String.valueOf(itemType)) {
            case "WEAPON" -> "[무기]";
            case "ARMOR" -> "[갑옷]";
            case "HELMET" -> "[투구]";
            case "SKILL" -> "[스킬]";
            case "RELIC" -> "[유물]";
            case "CONSUMABLE" -> "[소모품]";
            default -> "[" + itemType + "]";
        };
    }

    /**
     * 로그인한 플레이어의 진행 중인 게임. 조회에 실패하면 빈 값으로 넘겨 Tool 응답이 깨지지 않게 한다.
     *
     * @return 진행 중인 게임
     */
    private Optional<GameStateEntity> activeGame() {
        try {
            MemberEntity member = currentMemberService.getCurrentMember();
            return gameStateRepository.findByMember_MemberIdAndIsActiveTrue(member.getMemberId());
        } catch (Exception e) {
            log.warn("[PlayerContextTools] 현재 게임 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 게임 단계를 AI가 설명하기 좋은 한국어 문장으로 바꾼다.
     *
     * @param phase 게임 단계
     * @return 설명
     */
    private String phaseKorean(String phase) {
        return switch (String.valueOf(phase)) {
            case GameSession.PHASE_EXPLORE -> "탐험 중 - 다음 노드로 전진할 수 있습니다";
            case GameSession.PHASE_ENCOUNTER -> "조우 이벤트 - 선택지를 고르는 중";
            case GameSession.PHASE_REWARD -> "보상 선택 중";
            case GameSession.PHASE_SHOP -> "상점 이용 중";
            case GameSession.PHASE_REST -> "모닥불 - 휴식 / 단련 / 서원 중 하나를 고르는 중";
            case GameSession.PHASE_GAMEOVER -> "게임 오버";
            case GameSession.PHASE_CLEAR -> "게임 클리어";
            default -> "알 수 없음";
        };
    }

    /**
     * 상태이상 코드를 한국어로 바꾼다.
     *
     * @param buff BLEED / BURN / POISON / STUN
     * @return 한국어 이름
     */
    private String koreanBuff(String buff) {
        return StatusEffect.labelOf(buff);
    }

    /**
     * 줄바꿈을 접고 160자로 자른다 (Tool 응답이 길어지지 않게).
     *
     * @param text 원문
     * @return 줄인 문장
     */
    private String shorten(String text) {
        if (text == null) return "정보 없음";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "...";
    }
}
