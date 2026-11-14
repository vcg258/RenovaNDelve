package com.renovandelve.game.dto.response;

import java.util.List;

/**
 * 인카운터 노드 정보. 선택지의 결과(보상/패널티)는 서버 세션에만 보관되어 클라이언트에 노출되지 않는다.
 */
public record EncounterDTO(
        String text,
        List<String> choices,
        List<String> factions   // 각 선택지가 속한 세력 (HEAVEN / ABYSS / HUMAN), choices와 같은 순서
) {
}
