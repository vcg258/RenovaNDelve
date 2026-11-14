package com.renovandelve.util;

import java.util.Locale;

/**
 * 이메일 주소 정규화. 저장할 때와 찾을 때 같은 규칙을 써야 대소문자만 다른 주소가 서로 다른 계정이 되지 않는다.
 */
public final class EmailNormalizer {
    private EmailNormalizer() {
    }

    /** 앞뒤 공백을 떼고 소문자로 바꾼다. 비어 있으면 null */
    public static String normalize(String email) {
        if (email == null) return null;
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /** 로그에 남길 때 쓰는 가린 주소 (ab***@naver.com) */
    public static String mask(String email) {
        if (email == null) return "(없음)";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + email.substring(at);
    }
}
