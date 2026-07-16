package com.renovandelve.engine;

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
}
