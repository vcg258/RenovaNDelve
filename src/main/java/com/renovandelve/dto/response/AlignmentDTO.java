package com.renovandelve.dto.response;

import com.renovandelve.engine.Alignment;
import com.renovandelve.engine.Faction;

/**
 * 플레이어 성향 (천상 / 마계 / 인간).
 * dominant = 가호를 주는 세력 (HEAVEN / ABYSS / HUMAN, 없으면 null), blessing = 가호 설명
 */
public record AlignmentDTO(
        int heaven,
        int abyss,
        int human,
        String dominant,
        String blessing
) {
    public static AlignmentDTO from(Alignment alignment) {
        Faction dominant = alignment.dominant();
        return new AlignmentDTO(alignment.heaven(), alignment.abyss(), alignment.human(),
                dominant == null ? null : dominant.name(), alignment.blessing());
    }
}
