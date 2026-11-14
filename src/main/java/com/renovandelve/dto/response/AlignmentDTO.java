package com.renovandelve.dto.response;

import com.renovandelve.engine.Alignment;
import com.renovandelve.engine.Faction;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 플레이어 성향 (천상 / 마계 / 인간).
 * dominant = 가호를 주는 세력 (HEAVEN / ABYSS / HUMAN, 없으면 null), blessing = 가호 설명
 * tier = 가호 단계 (0 = 없음, 1~3), nextTierAt = 다음 단계에 필요한 점수 (없으면 null)
 * factions = 세력별 설명과 단계표 (화면의 안내 말풍선용. 수치는 서버가 단일 소유한다)
 */
public record AlignmentDTO(
        int heaven,
        int abyss,
        int human,
        String dominant,
        String blessing,
        int tier,
        Integer nextTierAt,
        List<FactionInfo> factions
) {
    /** 세력 한 곳의 안내: 조우에서의 경향과 단계별 가호 */
    public record FactionInfo(String code, String label, String tendency, List<TierInfo> tiers) {
    }

    public record TierInfo(int tier, int requiredScore, String description) {
    }

    private static final List<FactionInfo> FACTIONS = Arrays.stream(Faction.values())
            .map(faction -> new FactionInfo(faction.name(), faction.label(), faction.tendency(),
                    IntStream.rangeClosed(1, Alignment.MAX_TIER)
                            .mapToObj(tier -> new TierInfo(tier, Alignment.TIER_THRESHOLDS[tier - 1],
                                    Alignment.blessingText(faction, tier)))
                            .toList()))
            .toList();

    public static AlignmentDTO from(Alignment alignment) {
        Faction dominant = alignment.dominant();
        return new AlignmentDTO(alignment.heaven(), alignment.abyss(), alignment.human(),
                dominant == null ? null : dominant.name(), alignment.blessing(),
                alignment.tier(), alignment.nextTierAt(), FACTIONS);
    }
}
