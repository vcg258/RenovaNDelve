package com.renovandelve.global.mail;

import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 계정 메일의 문구와 HTML.
 *
 * 틀은 resources/mail/layout.html 하나다. 메일 프로그램은 외부 CSS·웹 글꼴·스크립트를 거의 지원하지 않아서
 * 표(table) 배치와 인라인 스타일만 쓴다. 맨 위의 도트 그림도 이미지가 아니라 표의 칸이다(이미지 차단과 무관하게 보인다).
 * 사용자가 입력한 값(이름, 아이디)은 HTML로 넣기 전에 반드시 이스케이프한다.
 */
public final class MailTemplates {
    private static final String SUBJECT_PREFIX = "[Renova & Delve] ";
    private static final String LAYOUT = load("mail/layout.html");

    private static final String PARAGRAPH = "<p style=\"margin:0 0 16px;font-size:15px;line-height:1.75;color:#ece3cd;\">%s</p>";

    private MailTemplates() {
    }

    /**
     * 아이디 찾기 결과.
     *
     * @param name     회원 이름
     * @param loginId  아이디
     * @param loginUrl 로그인 화면 주소
     * @return 메일
     */
    public static MailContent loginId(String name, String loginId, String loginUrl) {
        String note = "직접 요청하지 않았다면 이 메일은 무시하셔도 됩니다. 계정에는 아무 변화가 없습니다.";
        String text = """
                %s 님, 요청하신 아이디를 알려드립니다.

                아이디: %s

                로그인: %s

                %s
                """.formatted(name, loginId, loginUrl, note);
        String body = paragraph(esc(name) + " 님이 요청하신 아이디입니다.")
                + value(esc(loginId))
                + button("로그인하러 가기", loginUrl);
        return new MailContent(SUBJECT_PREFIX + "아이디 안내", text,
                page("아이디 안내", "요청하신 아이디를 알려드립니다.", "아이디를 알려드립니다", body, esc(note)));
    }

    /**
     * 비밀번호 재설정 링크.
     *
     * @param link    재설정 주소 (토큰 포함)
     * @param minutes 링크 유효 시간 (분)
     * @return 메일
     */
    public static MailContent passwordReset(String link, long minutes) {
        String note = "이 링크는 %d분 동안 한 번만 쓸 수 있습니다. 직접 요청하지 않았다면 무시하셔도 됩니다. 비밀번호는 바뀌지 않습니다."
                .formatted(minutes);
        String text = """
                비밀번호 재설정을 요청하셨습니다. 아래 주소를 열어 새 비밀번호를 정해주세요.

                %s

                %s
                """.formatted(link, note);
        String body = paragraph("비밀번호 재설정을 요청하셨습니다. 아래 버튼을 눌러 새 비밀번호를 정해주세요.")
                + button("새 비밀번호 정하기", link)
                + fallbackLink(link);
        return new MailContent(SUBJECT_PREFIX + "비밀번호 재설정 안내", text,
                page("비밀번호 재설정 안내", "%d분 안에 새 비밀번호를 정해주세요.".formatted(minutes),
                        "새 비밀번호를 정해주세요", body, esc(note)));
    }

    /**
     * 비밀번호가 바뀌었다는 알림.
     *
     * @param loginId    아이디
     * @param recoverUrl 비밀번호 찾기 화면 주소
     * @return 메일
     */
    public static MailContent passwordChanged(String loginId, String recoverUrl) {
        String warning = "직접 한 일이 아니라면 곧바로 비밀번호 찾기로 다시 재설정하고, 이메일 계정의 비밀번호도 바꿔주세요.";
        String text = """
                계정(%s)의 비밀번호가 방금 재설정되었습니다. 모든 기기에서 로그아웃되었습니다.

                %s
                비밀번호 찾기: %s
                """.formatted(loginId, warning, recoverUrl);
        String body = paragraph("계정 <b style=\"color:#e8c860;\">" + esc(loginId)
                        + "</b>의 비밀번호가 방금 재설정되었습니다. 모든 기기에서 로그아웃되었습니다.")
                + warning(esc(warning))
                + button("비밀번호 찾기", recoverUrl);
        return new MailContent(SUBJECT_PREFIX + "비밀번호가 변경되었습니다", text,
                page("비밀번호 변경 알림", "계정의 비밀번호가 재설정되었습니다.", "비밀번호가 바뀌었습니다", body,
                        "이 메일은 비밀번호가 바뀔 때마다 계정에 등록된 주소로 보냅니다."));
    }

    /* ==================== 조각 ==================== */

    private static String page(String title, String preheader, String heading, String body, String note) {
        return LAYOUT.replace("{{subject}}", esc(title))
                .replace("{{preheader}}", esc(preheader))
                .replace("{{heading}}", esc(heading))
                .replace("{{note}}", note)
                .replace("{{body}}", body);
    }

    private static String paragraph(String html) {
        return PARAGRAPH.formatted(html);
    }

    /** 옮겨 적을 값 (아이디). 금색 테두리 칸 */
    private static String value(String html) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:0 0 22px;\"><tr>"
                + "<td style=\"padding:12px 20px;background:#0f0c18;border:2px solid #e8c860;font-size:20px;line-height:1.3;"
                + "font-weight:700;letter-spacing:1px;color:#e8c860;\">" + html + "</td></tr></table>";
    }

    /** 게임 화면의 버튼처럼 모서리 없이, 아래에 짙은 금색 그림자 줄 */
    private static String button(String label, String url) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:0 0 20px;\"><tr>"
                + "<td style=\"background:#e8c860;border-bottom:4px solid #8a6a1c;\">"
                + "<a href=\"" + esc(url) + "\" style=\"display:inline-block;padding:12px 24px;font-size:15px;line-height:1.2;"
                + "font-weight:700;color:#05040a;text-decoration:none;\">" + esc(label) + "</a></td></tr></table>";
    }

    /** 버튼이 열리지 않는 메일 프로그램을 위한 주소 원문 */
    private static String fallbackLink(String url) {
        return "<p style=\"margin:0 0 16px;font-size:13px;line-height:1.7;color:#9d95b3;\">버튼이 열리지 않으면 이 주소를 브라우저에 붙여 넣으세요.<br>"
                + "<a href=\"" + esc(url) + "\" style=\"color:#e8c860;word-break:break-all;\">" + esc(url) + "</a></p>";
    }

    /** 마계의 붉은 줄을 왼쪽에 둔 경고 */
    private static String warning(String html) {
        return "<p style=\"margin:0 0 20px;padding:12px 16px;background:#1e1936;border-left:4px solid #e0483c;"
                + "font-size:14px;line-height:1.7;color:#ece3cd;\">" + html + "</p>";
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }

    private static String load(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("메일 틀을 읽지 못했습니다: " + path, e);
        }
    }
}
