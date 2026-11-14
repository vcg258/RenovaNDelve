package com.renovandelve.dto.response;

import com.renovandelve.engine.Pact;

/**
 * 원정 준비 화면에 보여줄 서약 한 건. scoreBonusPercent = 점수 보너스(%)
 */
public record PactDTO(
        String code,
        String title,
        String description,
        int scoreBonusPercent
) {
    /**
     * 서약을 화면용으로 옮긴다. 점수 보너스는 퍼센트 정수로 바꾼다.
     *
     * @param pact 서약
     * @return 서약 정보
     */
    public static PactDTO from(Pact pact) {
        return new PactDTO(pact.name(), pact.title(), pact.description(), (int) Math.round(pact.scoreBonus() * 100));
    }
}
