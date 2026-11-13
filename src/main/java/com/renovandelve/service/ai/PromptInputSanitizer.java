package com.renovandelve.service.ai;

import java.util.regex.Pattern;

/**
 * 외부 LLM으로 보내기 전 사용자 입력 정리.
 *
 * 가이드 챗봇의 입력은 자유 텍스트이고 그대로 국외 AI 사업자에게 전송된다.
 * 사용자가 무심코 적은 연락처·주민등록번호 등이 밖으로 나가지 않도록, 형식이 뚜렷한 개인정보는 전송 전에 가린다.
 * (게임 안내에는 이런 값이 필요 없으므로 가려도 답변 품질에 영향이 없다)
 */
public final class PromptInputSanitizer {
    private static final String MASK = "[개인정보 가림]";

    // 주민등록번호 / 외국인등록번호: 6자리-7자리
    private static final Pattern RESIDENT_NUMBER = Pattern.compile("\\b\\d{6}\\s*-\\s*[1-8]\\d{6}\\b");
    // 카드번호: 4-4-4-4
    private static final Pattern CARD_NUMBER = Pattern.compile("\\b\\d{4}[-\\s]\\d{4}[-\\s]\\d{4}[-\\s]\\d{4}\\b");
    // 휴대전화 / 일반전화
    private static final Pattern PHONE_NUMBER = Pattern.compile("\\b0\\d{1,2}[-\\s.]?\\d{3,4}[-\\s.]?\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

    private PromptInputSanitizer() {
    }

    public static String maskPersonalData(String input) {
        if (input == null) return "";
        String result = RESIDENT_NUMBER.matcher(input).replaceAll(MASK);
        result = CARD_NUMBER.matcher(result).replaceAll(MASK);
        result = PHONE_NUMBER.matcher(result).replaceAll(MASK);
        result = EMAIL.matcher(result).replaceAll(MASK);
        return result;
    }
}
