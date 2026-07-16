package com.renovandelve.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 진행 중인 노드의 휘발성 상태 (Redis 저장).
 * 몬스터 HP, 인카운터 선택지 결과, 보상 후보, 상점 재고 등
 * "클라이언트가 조작하면 안 되는" 전투/노드 상태를 서버가 단일 소유한다.
 *
 * 구성 요소는 각각 별도 클래스로 분리되어 있다:
 * {@link SessionMonster}, {@link EncounterChoice}, {@link RewardOptions}, {@link ShopStockItem}
 */
@Data
@NoArgsConstructor
public class GameSession {
    public static final String PHASE_EXPLORE = "EXPLORE";
    public static final String PHASE_BATTLE = "BATTLE";
    public static final String PHASE_ENCOUNTER = "ENCOUNTER";
    public static final String PHASE_REWARD = "REWARD";
    public static final String PHASE_SHOP = "SHOP";
    public static final String PHASE_GAMEOVER = "GAMEOVER";
    public static final String PHASE_CLEAR = "CLEAR";

    private Long gameStateId;
    private String phase = PHASE_EXPLORE;
    private String nodeType;   // BATTLE / ELITE / BOSS / ENCOUNTER
    private String story;      // 현재 노드 서사
    private List<SessionMonster> monsters = new ArrayList<>();
    private List<EncounterChoice> choices = new ArrayList<>();
    private int tempAtkBonus;  // 소모품으로 얻은 일시적 공격력 (전투 종료 시 초기화)
    private int tempDefBonus;  // 소모품으로 얻은 일시적 방어력
    private RewardOptions reward;
    private List<ShopStockItem> shop = new ArrayList<>();
    private String summaryNarrative; // 종료 후 AI 요약 캐시
}
