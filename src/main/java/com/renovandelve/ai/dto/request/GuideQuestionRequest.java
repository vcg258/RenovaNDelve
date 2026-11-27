package com.renovandelve.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 가이드 챗봇 질문.
 * 길이 제한이 없으면 수만 자짜리 입력으로 LLM 토큰 비용을 임의로 키울 수 있다.
 */
public record GuideQuestionRequest(
        @NotBlank(message = "질문을 입력해주세요.")
        @Size(max = 300, message = "질문은 300자 이내로 입력해주세요.")
        String question
) {
}
