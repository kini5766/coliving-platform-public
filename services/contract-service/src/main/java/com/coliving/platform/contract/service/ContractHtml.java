package com.coliving.platform.contract.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 계약서 본문(HTML) 처리 모음.
 *
 * 계약서 양식은 문서 서비스 관리자 편집기(TipTap)로 만든 HTML이다. 예) <h1>임대차 계약서</h1><p>임차인: {{applicantName}}</p>
 *   - 양식에 넣는 신청자 입력값(이름·주소 등)은 escape()로 글자 그대로 들어가게 한다 (HTML 주입 방지)
 *   - 서명 PDF는 HTML을 그릴 수 없어서 toPlainText()로 바꿔 찍는다
 *   - 화면(관리자·신청자)은 HTML 그대로 sandbox iframe에 넣어 보여준다
 *
 * @author 한성현
 */
public final class ContractHtml {

    private ContractHtml() {}

    /**
     * 글자 그대로 보이게 HTML 특수문자를 바꾼다. { } 도 바꿔서 입력값 속 "{{contractDate}}" 같은 글자가
     * 나중에 빈칸으로 오인돼 채워지지 않게 한다.
     */
    public static String escape(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                case '{' -> out.append("&#123;");
                case '}' -> out.append("&#125;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }

    private static final Pattern TAG_LIKE = Pattern.compile("<[a-zA-Z/!][^>]*>");
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern DROP_BLOCK = Pattern.compile("<(script|style|head)\\b[^>]*>.*?</\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BR = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LI_OPEN = Pattern.compile("<li\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL_END = Pattern.compile("</t[dh]\\s*>", Pattern.CASE_INSENSITIVE);
    // 줄을 끝내는 태그: 문단·제목·목록 항목·표 행 (빈 문단 <p></p>은 빈 줄이 된다)
    private static final Pattern BLOCK_END = Pattern.compile(
            "</(p|h[1-6]|li|tr|pre)\\s*>|<hr\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    // 감싸는 태그: 이미 줄이 바뀌어 있으면 줄을 더 넣지 않는다 (</li></ul>, </tr></table>에서 빈 줄이 생기지 않게)
    private static final Pattern SOFT_END = Pattern.compile(
            "</(div|ul|ol|table|tbody|thead|blockquote|section|article|header|footer)\\s*>", Pattern.CASE_INSENSITIVE);
    private static final char SOFT_BREAK = '\u0001';
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");
    private static final Pattern MANY_BLANK_LINES = Pattern.compile("\n{3,}");

    /** 태그가 하나라도 있으면 HTML로 본다 (예전 일반 텍스트 양식과 구분) */
    public static boolean isHtml(String content) {
        return content != null && TAG_LIKE.matcher(content).find();
    }

    /**
     * HTML → 줄 단위 텍스트 (서명 PDF용).
     *   문단·제목·목록 끝 → 줄바꿈, <br> → 줄바꿈, <li> → "• ", 표 칸 끝 → " | ", 나머지 태그는 지운다.
     *   &amp; &lt; &#123; 같은 문자 참조는 원래 글자로 되돌린다.
     * 태그가 없는 일반 텍스트는 그대로 돌려준다.
     */
    public static String toPlainText(String content) {
        if (content == null) return "";
        if (!isHtml(content)) return content;

        String s = content.replace("\r", "");
        s = COMMENT.matcher(s).replaceAll("");
        s = DROP_BLOCK.matcher(s).replaceAll("");
        s = s.replace("\n", " ");                       // HTML 안의 줄바꿈은 공백과 같다
        s = BR.matcher(s).replaceAll("\n");
        s = LI_OPEN.matcher(s).replaceAll("• ");
        s = CELL_END.matcher(s).replaceAll(" | ");
        s = BLOCK_END.matcher(s).replaceAll("\n");
        s = SOFT_END.matcher(s).replaceAll(String.valueOf(SOFT_BREAK));
        s = ANY_TAG.matcher(s).replaceAll("");
        s = softBreaks(s);
        s = unescape(s);

        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            out.append(line.replaceAll("[ \\t\\u00A0]+", " ").strip()).append('\n');
        }
        return MANY_BLANK_LINES.matcher(out.toString()).replaceAll("\n\n").strip();
    }

    // SOFT_BREAK → 앞이 이미 줄바꿈(또는 맨 앞)이면 없애고, 아니면 줄바꿈 (공백만 사이에 있어도 줄바꿈으로 본다)
    private static String softBreaks(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != SOFT_BREAK) {
                out.append(ch);
                continue;
            }
            int j = out.length() - 1;
            while (j >= 0 && (out.charAt(j) == ' ' || out.charAt(j) == '\t')) j--;
            if (j >= 0 && out.charAt(j) != '\n') out.append('\n');
        }
        return out.toString();
    }

    private static String unescape(String s) {
        Matcher m = ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String ch = switch (name) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                default -> numeric(name);
            };
            m.appendReplacement(out, Matcher.quoteReplacement(ch == null ? m.group() : ch));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String numeric(String name) {
        try {
            int cp;
            if (name.startsWith("#x") || name.startsWith("#X")) cp = Integer.parseInt(name.substring(2), 16);
            else if (name.startsWith("#")) cp = Integer.parseInt(name.substring(1));
            else return null;                                 // 모르는 이름(&copy; 등)은 그대로 둔다
            return Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
