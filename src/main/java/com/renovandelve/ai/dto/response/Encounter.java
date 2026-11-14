package com.renovandelve.ai.dto.response;

import java.util.List;

/**
 * AI가 생성한 인카운터 원본. selections = 세력별 선택지 문구, 순서는 [천상, 마계, 인간] 고정
 * 실제 보상·패널티는 서버(GameEngine)가 결정한다.
 */
public record Encounter(
        String text,
        List<String> selections
) {
}
