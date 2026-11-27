package com.renovandelve.game.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 인카운터 선택지와 그 숨겨진 결과 (Redis 저장, 클라이언트에는 label만 노출).
 */
@Data
@NoArgsConstructor
public class EncounterChoice {
    private String label;
    private String effect; // HEAL / DAMAGE / GOLD_PLUS / GOLD_MINUS / CONSUMABLE / RELIC / NOTHING
    private int value;
    private Long refId;    // CONSUMABLE / RELIC일 때 대상 ID
    private String contract; // 계약 선택지면 서명할 계약 ("혜택:조항:남은횟수"). 조항은 화면에 보내지 않는다
    private String faction; // 이 선택지가 속한 세력 (HEAVEN / ABYSS / HUMAN). 성향 누적과 화면 표시에 쓰인다
}
