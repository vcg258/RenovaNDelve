package com.renovandelve.game.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 모닥불(보스 처치 후 휴식 지점) 선택.
 * choice = HEAL(휴식) / TRAIN(단련) / HEAVEN, ABYSS, HUMAN(해당 세력에 서원)
 */
public record RestChoiceRequest(
        @NotBlank(message = "무엇을 할지 선택해주세요.")
        @Size(max = 20)
        String choice
) {
}
