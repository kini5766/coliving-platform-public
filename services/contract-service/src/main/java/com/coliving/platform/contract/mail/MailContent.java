package com.coliving.platform.contract.mail;

/**
 * 완성된 메일 한 통 (제목 + HTML 본문)
 *
 * @author 한성현
 */
public record MailContent(String subject, String html) {
}
