package com.renovandelve.game.dto.response;

/**
 * 업적 한 건. achieved = 달성 여부
 */
public record AchievementDTO(
        String code,
        String title,
        String description,
        boolean achieved
) {
}
