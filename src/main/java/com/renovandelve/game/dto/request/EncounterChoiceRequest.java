package com.renovandelve.game.dto.request;

/**
 * 인카운터 선택 요청. choiceIndex = 선택지 순번 (0부터)
 */
public record EncounterChoiceRequest(
        int choiceIndex
) {
}
