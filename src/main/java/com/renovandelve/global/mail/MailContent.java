package com.renovandelve.global.mail;

/**
 * 보낼 메일 한 통. 본문은 일반 텍스트와 HTML 두 가지를 함께 담는다.
 * HTML을 보여주지 않는 메일 프로그램은 텍스트 본문을 보여준다.
 *
 * @param subject 제목
 * @param text    일반 텍스트 본문
 * @param html    HTML 본문
 */
public record MailContent(String subject, String text, String html) {
}
