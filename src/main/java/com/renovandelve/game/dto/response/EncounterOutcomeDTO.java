package com.renovandelve.game.dto.response;

/**
 * 인카운터 선택 처리 결과.
 */
public record EncounterOutcomeDTO(
        String text,
        StatusDTO status,
        String phase
) {
}
