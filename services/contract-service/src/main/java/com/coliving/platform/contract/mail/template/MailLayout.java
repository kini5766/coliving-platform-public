package com.coliving.platform.contract.mail.template;

import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 메일 공통 HTML 틀과 값 포맷. 메일 클라이언트는 외부 CSS를 못 읽어서 스타일은 인라인으로 둔다.
 * 사용자 입력(이름, 반려 사유 등)은 반드시 esc()를 거친다.
 *
 * @author 한성현
 */
public final class MailLayout {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private MailLayout() {
    }

    /** title: 큰 제목, intro: 안내 문단(HTML), rows: row()로 만든 표 줄들, buttonText/url: 버튼 (null이면 없음) */
    public static String page(String title, String intro, String rows, String buttonText, String buttonUrl) {
        String button = buttonText == null ? "" : """
                <p style="margin:28px 0 0"><a href="%s" style="display:inline-block;padding:12px 22px;background:#2f5d50;color:#ffffff;text-decoration:none;border-radius:6px;font-weight:bold">%s</a></p>
                """.formatted(esc(buttonUrl), esc(buttonText));
        return """
                <!doctype html>
                <html lang="ko"><body style="margin:0;padding:24px;background:#f4f4f2;font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#222">
                <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px">
                <h1 style="margin:0 0 16px;font-size:20px">%s</h1>
                <div style="font-size:15px;line-height:1.6">%s</div>
                <table style="width:100%%;margin-top:20px;border-collapse:collapse;font-size:14px">%s</table>
                %s
                <p style="margin:32px 0 0;font-size:12px;color:#888">본 메일은 발신 전용입니다.</p>
                </div></body></html>
                """.formatted(esc(title), intro, rows, button);
    }

    public static String row(String label, String value) {
        return """
                <tr><td style="padding:8px 0;border-bottom:1px solid #eee;color:#666;width:120px">%s</td><td style="padding:8px 0;border-bottom:1px solid #eee">%s</td></tr>
                """.formatted(esc(label), esc(value));
    }

    public static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    /** 이름이 없으면 "고객" */
    public static String name(String applicantName) {
        return applicantName == null || applicantName.isBlank() ? "고객" : applicantName;
    }

    public static String won(Long amount) {
        return amount == null ? "-" : NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }

    public static String date(LocalDate d) {
        return d == null ? "-" : DATE.format(d);
    }

    public static String period(LocalDate from, LocalDate to) {
        return date(from) + " ~ " + date(to);
    }

    public static String dateTime(Instant at) {
        return at == null ? "-" : DATE_TIME.format(at.atOffset(KST));
    }

    public static String room(String buildingName, String roomName) {
        return (buildingName == null ? "" : buildingName + " ") + (roomName == null ? "" : roomName);
    }
}
