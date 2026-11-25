package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.DocumentServiceException;
import com.coliving.platform.contract.entity.Contract;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문서 서비스 양식(치환 전 본문) + 신청서 스냅샷 → 계약서 완성본.
 *
 * 양식 안의 {{키}} 자리를 값으로 바꾼다. 예) "임차인: {{applicantName}}" → "임차인: 홍길동"
 *
 * ⚠ 키 이름은 문서 담당자가 만드는 양식과 똑같아야 한다 → 아래 KEYS 목록을 문서 담당자와 공유할 것
 * - 양식에 모르는 키가 있으면 저장하지 않고 실패시킨다.
 *   빈칸이 남은 계약서가 저장되면 고칠 수 없기 때문 (contract_document는 수정·삭제 금지)
 * - 한 번에(single pass) 치환한다. 사용자가 입력한 주소에 "{{...}}" 같은 글자가 있어도 다시 치환되지 않는다
 * - 값이 없으면(주소 미입력 등) 빈 문자열로 넣는다
 * - 양식은 HTML이므로 값은 ContractHtml.escape()로 글자 그대로 넣는다 (신청자가 주소에 태그를 적어도 HTML이 되지 않음)
 *
 * 계약일({{contractDate}}) = 결제 완료일. 계약서는 제출 때 저장되고 결제 전에 서명(해시)까지 끝나므로
 *   저장본에는 {{contractDate}}를 그대로 남겨 두고, 보여줄 때 withContractDate()로 채운다 (ContractContentView).
 *   저장본을 바꾸지 않으므로 서명 해시는 그대로 유지된다.
 *
 * @author 한성현
 */
@Component
public class ContractContentRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    /** 양식에서 쓸 수 있는 키 목록 (문서 담당자 공유용) */
    public static final Set<String> KEYS = Set.of(
            "applicationNo", "contractDate",
            "applicantName", "applicantPhone", "applicantEmail", "applicantAddress",
            "buildingName", "buildingAddress", "roomName", "roomType", "roomStructure", "floorNumber", "area",
            "moveInDate", "moveOutDate", "contractMonths",
            "monthlyFee", "deposit", "managementFee", "firstPayment"
    );

    /** 보여줄 때 채우는 키 (저장본에는 {{contractDate}} 그대로 남는다) */
    public static final String CONTRACT_DATE = "contractDate";
    private static final String CONTRACT_DATE_MARK = "{{" + CONTRACT_DATE + "}}";

    /** 양식 + 신청서(제출 시점 스냅샷) → 저장할 계약서 본문. 계약일만 빈칸({{contractDate}})으로 남긴다 */
    public String render(String template, Contract c) {
        Map<String, String> values = values(c);

        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        Set<String> unknown = new TreeSet<>();
        while (m.find()) {
            String key = m.group(1); //ex)template에 있는 "tenantName" , 1을쓰면 {}는 빼준다.
            String raw = values.get(key); //key에 해당하는 value
            String value = CONTRACT_DATE.equals(key) ? CONTRACT_DATE_MARK : (raw == null ? null : ContractHtml.escape(raw));
            if (value == null) {
                unknown.add(key);          // 일단 그대로 두고, 끝나고 밑에서 한 번에 throw를 던진다(throw하면 반복문 멈춰서)
                value = m.group();          //value가 null인채로 밑에 가면 오류가나서 {key}를 저장
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);

        if (!unknown.isEmpty()) {
            throw new DocumentServiceException("계약서 양식에 채울 수 없는 항목이 있습니다: " + unknown, null);
        }
        return out.toString();
    }

    /**
     * 저장본의 {{contractDate}} → 계약일(결제 완료일). 아직 결제 전이면(null) 빈칸.
     * 저장본은 render()가 키 모양을 {{contractDate}}로 맞춰 두므로 그 글자만 바꾼다.
     */
    public static String withContractDate(String content, LocalDate contractDate) {
        if (content == null) return null;
        return content.replace(CONTRACT_DATE_MARK, date(contractDate));
    }

    // 신청서(제출 시점에 고정된 스냅샷) → 치환 값. 금액·기간은 SUBMITTED 때 고정된 값만 쓴다
    private Map<String, String> values(Contract c) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("applicationNo", c.getApplicationNo());

        v.put("applicantName", nz(c.getApplicantName()));
        v.put("applicantPhone", nz(c.getApplicantPhone()));
        v.put("applicantEmail", nz(c.getApplicantEmail()));
        v.put("applicantAddress", nz(c.getApplicantAddress()));

        v.put("buildingName", nz(c.getBuildingName()));
        v.put("buildingAddress", nz(c.getAddress()));
        v.put("roomName", nz(c.getRoomName()));
        v.put("roomType", nz(c.getRoomType()));
        v.put("roomStructure", nz(c.getRoomStructure()));
        v.put("floorNumber", String.valueOf(c.getFloorNumber()));
        v.put("area", area(c.getArea()));

        v.put("moveInDate", date(c.getMoveInDate()));
        v.put("moveOutDate", date(c.getMoveOutDate()));       // 입주일 + 개월 - 1일
        v.put("contractMonths", c.getContractMonths() == null ? "" : String.valueOf(c.getContractMonths()));

        v.put("monthlyFee", won(c.getMonthlyFee()));
        v.put("deposit", won(c.getDeposit()));
        v.put("managementFee", won(c.getManagementFee()));
        v.put("firstPayment", won(c.getFirstPayment()));
        return v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.toString();                  // 2026-11-02
    }

    private static String won(Long amount) {
        return amount == null ? "" : NumberFormat.getNumberInstance(Locale.KOREA).format(amount);   // 1,780,000
    }

    private static String area(BigDecimal a) {
        return a == null ? "" : a.stripTrailingZeros().toPlainString();                            // 13.5
    }
}
