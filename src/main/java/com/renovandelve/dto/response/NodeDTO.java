package com.renovandelve.dto.response;

import java.util.List;

/**
 * 노드 진입 결과.
 * nodeType = BATTLE / ELITE / BOSS / ENCOUNTER
 */
public record NodeDTO(
        String nodeType,
        String story,
        List<BattleMonsterDTO> monsters,
        EncounterDTO encounter
) {
}
