package com.coliving.platform.contract.dummy;

import com.coliving.platform.common.exception.DocumentNotFoundException;
import com.coliving.platform.common.exception.DocumentServiceUnavailableException;
import com.coliving.platform.contract.dto.internal.DocumentFormDTO;
import com.coliving.platform.contract.service.DocumentQueryService;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * [개발용 더미] 문서 서비스(gRPC)가 아직 없을 때 쓰는 가짜 양식 조회.
 * - "dummy-document" 프로필을 켰을 때만 등록된다 (안 켜면 진짜 DocumentQueryService가 gRPC 호출)
 * - @Primary라서 DocumentQueryService를 주입받는 곳(ApplicationApprovalService)에 이 클래스가 대신 들어간다
 * 테스트용 templateId
 *   1   : 한국어 양식
 *   2   : 영어 양식
 *   503 : 문서 서비스 꺼짐 흉내 → 503
 *   그 외 : 없는 양식 → 404
 * ⚠ 문서 서비스가 준비되면 프로필만 끄면 된다. 운영 설정에 dummy-document를 넣지 말 것.
 *
 * @author 한성현
 */
@Service
@Primary
@Profile("dummy-document")
public class DummyDocumentQueryService extends DocumentQueryService {

    // 실제 문서 서비스 양식처럼 HTML (관리자 편집기 TipTap 출력 모양). 빈칸은 ContractContentRenderer.KEYS 전부 사용
    private static final String KO = """
            <h1>주거 공간 이용 계약서</h1>
            <p>계약번호: {{applicationNo}}</p>
            <p>계약일: {{contractDate}}</p>
            <h2>임차인</h2>
            <p>성명: {{applicantName}} / 연락처: {{applicantPhone}}</p>
            <p>이메일: {{applicantEmail}} / 주소: {{applicantAddress}}</p>
            <h2>이용 공간</h2>
            <p>{{buildingName}} {{roomName}} ({{roomType}}, {{roomStructure}}, {{floorNumber}}층, {{area}}㎡)</p>
            <p>주소: {{buildingAddress}}</p>
            <h2>계약 기간</h2>
            <p>{{moveInDate}} ~ {{moveOutDate}} ({{contractMonths}}개월, 퇴실일 당일 포함)</p>
            <h2>금액</h2>
            <table><tbody>
            <tr><th>월 이용료</th><td>{{monthlyFee}}원</td></tr>
            <tr><th>보증금</th><td>{{deposit}}원</td></tr>
            <tr><th>관리비(월)</th><td>{{managementFee}}원</td></tr>
            <tr><th>첫 결제 금액</th><td>{{firstPayment}}원</td></tr>
            </tbody></table>
            <p>계약일은 결제가 완료된 날짜로 기재됩니다.</p>
            """;

    private static final String EN = """
            <h1>Residential Space Agreement</h1>
            <p>Contract No.: {{applicationNo}}</p>
            <p>Contract date: {{contractDate}}</p>
            <h2>Tenant</h2>
            <p>{{applicantName}} ({{applicantPhone}}, {{applicantEmail}})</p>
            <p>Address: {{applicantAddress}}</p>
            <h2>Space</h2>
            <p>{{buildingName}} {{roomName}} ({{roomType}}, {{roomStructure}}, floor {{floorNumber}}, {{area}} m2)</p>
            <p>Address: {{buildingAddress}}</p>
            <h2>Term</h2>
            <p>{{moveInDate}} ~ {{moveOutDate}} ({{contractMonths}} months)</p>
            <h2>Payment</h2>
            <ul>
            <li>Monthly fee: KRW {{monthlyFee}}</li>
            <li>Deposit: KRW {{deposit}}</li>
            <li>Management fee (monthly): KRW {{managementFee}}</li>
            <li>First payment: KRW {{firstPayment}}</li>
            </ul>
            <p>The contract date is the date the payment is completed.</p>
            """;

    public DummyDocumentQueryService() {
        super(null, null);   // gRPC stub·Eureka 안 씀
    }

    // 언어별 최신 양식: 더미는 언어마다 하나뿐 (ko → 1, en → 2)
    @Override
    public DocumentFormDTO getLatestFormForContract(String language) {
        return getFormForContract("en".equals(language) ? 2L : 1L);
    }

    @Override
    public DocumentFormDTO getFormForContract(Long templateId) {
        if (templateId == 503L) throw new DocumentServiceUnavailableException("[더미] 문서 서비스 응답 없음", null);
        return switch (templateId.intValue()) {
            case 1 -> new DocumentFormDTO(1L, "ko", KO);
            case 2 -> new DocumentFormDTO(2L, "en", EN);
            default -> throw new DocumentNotFoundException("[더미] 존재하지 않는 계약서 양식입니다: " + templateId);
        };
    }
}
