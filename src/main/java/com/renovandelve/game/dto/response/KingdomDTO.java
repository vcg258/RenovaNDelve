package com.renovandelve.game.dto.response;

import java.util.List;

/**
 * 지금의 왕국 정세 (화면용).
 *
 * @param state       정세 (HEAVEN_RISING / ABYSS_WIDENING / HUMAN_RESOLVE / BALANCE)
 * @param title       정세 이름
 * @param description 설명
 * @param effects     새 원정에 걸리는 효과
 * @param heaven      천상 비율 (%)
 * @param abyss       마계 비율 (%)
 * @param human       인간 비율 (%)
 * @param players     지난 7일 동안 선택을 남긴 원정자 수
 */
public record KingdomDTO(String state, String title, String description, List<String> effects,
                         int heaven, int abyss, int human, int players) {
}
