package com.renovandelve.game.dto.response;

import java.util.List;

/**
 * 원정 준비에서 아직 고를 수 없는 항목의 ID. 희귀·영웅 등급은 도감에 등록하면 해금된다.
 */
public record SetupLocksDTO(
        List<Long> weapons,
        List<Long> armors,
        List<Long> helmets,
        List<Long> skills,
        List<Long> relics
) {
}
