package com.renovandelve.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * AI 런 회고 (구조화 출력).
 *
 * Spring AI의 Structured Output(.entity())이 이 레코드의 구조를 JSON 스키마로 만들어 프롬프트에 붙이고,
 * 모델 응답을 이 타입으로 역직렬화한다. 필드 설명은 모델에게 주는 작성 지침이 된다.
 */
public record RunReviewDTO(
        @JsonPropertyDescription("이번 모험가에게 붙이는 칭호. 플레이 방식의 특징을 담아 12자 이내. 예: '출혈의 사냥꾼'")
        String title,

        @JsonPropertyDescription("묘비명 또는 승전 기록 한 문장. 어둡고 간결한 문체, 40자 이내")
        String epitaph,

        @JsonPropertyDescription("이번 판의 승패를 가른 원인 분석. 제공된 수치를 근거로 2문장 이내")
        String analysis,

        @JsonPropertyDescription("다음 판을 위한 구체적인 조언 2~3개. 각 40자 이내이며 제공된 게임 규칙 안에서만 제안")
        List<String> tips,
        @JsonPropertyDescription("레노바 연대기에 실릴 이번 원정 장(章)의 제목. 사건이나 장소를 담아 15자 이내. 예: '금화를 쥔 손'")
        String chapterTitle,
        @JsonPropertyDescription("레노바 연대기에 실릴 이번 원정 이야기. 3~5문장, 300자 이내. 앞선 장이 있으면 그 이야기를 이어받고, 이번 원정의 선택과 결말을 3인칭 과거형('그는 ~했다')으로 쓴다")
        String chapter
) {
}
