package com.renovandelve.dto.response;

import java.util.List;

/**
 * AI가 생성한 인카운터 원본. selections = 선택지 사유 문구 (도망 / 협상)
 * 실제 보상·패널티는 서버(GameEngine)가 결정한다.
 */
public record Encounter(
        String text,
        List<String> selections
) {
}
