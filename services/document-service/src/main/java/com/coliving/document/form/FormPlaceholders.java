package com.coliving.document.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 양식 본문의 빈칸({{이름}}) 검사. 관리자 화면(formVariables.js)의 검사와 같은 규칙이다.
 *
 * ⚠ KEYS는 계약 서비스 ContractContentRenderer.KEYS, 관리자 화면 formVariables.js와 똑같이 유지할 것 (20개).
 *   계약 서비스는 모르는 빈칸이 있는 양식으로는 계약서를 만들지 못하므로(저장 실패),
 *   잘못된 양식이 저장되지 않도록 여기서 먼저 막는다.
 */
public final class FormPlaceholders {

    public static final Set<String> KEYS = Set.of(
            "applicationNo", "contractDate",
            "applicantName", "applicantPhone", "applicantEmail", "applicantAddress",
            "buildingName", "buildingAddress", "roomName", "roomType", "roomStructure", "floorNumber", "area",
            "moveInDate", "moveOutDate", "contractMonths",
            "monthlyFee", "deposit", "managementFee", "firstPayment"
    );

    // {{ 이름 }} 모양을 찾는 규칙. 괄호 안 공백은 허용하고, 이름은 영문·숫자·밑줄만 가능
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private FormPlaceholders() {
    }

    /** 본문의 문제 문구 목록. 문제가 없으면 빈 목록. */
    public static List<String> problems(String content) {
        Set<String> unknown = new TreeSet<>();   // 모르는 빈칸 이름 (중복 제거, 정렬)
        StringBuilder rest = new StringBuilder(); // 올바른 모양의 빈칸을 모두 지우고 남은 글자
        Matcher m = PLACEHOLDER.matcher(content == null ? "" : content);
        while (m.find()) {
            if (!KEYS.contains(m.group(1))) {
                unknown.add("{{" + m.group(1) + "}}");
            }
            m.appendReplacement(rest, "");        // 찾은 빈칸을 지우고 나머지를 rest에 모은다
        }
        m.appendTail(rest);

        List<String> problems = new ArrayList<>();
        if (!unknown.isEmpty()) {
            problems.add("모르는 빈칸이 있어요: " + String.join(", ", unknown)
                    + ". 빈칸 목록에 있는 이름만 쓸 수 있어요.");
        }
        // 빈칸 일부에만 서식(굵게 등)이 들어가 {{ }}가 태그로 나뉜 경우: 계약서에 중괄호가 그대로 남는다
        if (rest.indexOf("{{") >= 0 || rest.indexOf("}}") >= 0) {
            problems.add("모양이 깨진 빈칸이 있어요. {{ }} 안에는 서식(굵게 등) 없이 이름만 있어야 해요.");
        }
        return problems;
    }
}