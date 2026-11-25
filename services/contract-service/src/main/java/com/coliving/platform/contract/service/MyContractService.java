package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ApplicationAccessDeniedException;
import com.coliving.platform.common.exception.ApplicationNotFoundException;
import com.coliving.platform.common.exception.DocumentNotFoundException;
import com.coliving.platform.contract.dto.contract.MyContractListItemResponse;
import com.coliving.platform.contract.dto.sign.ContractDocumentResponse;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractDocument;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractDocumentRepository;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.ContractSignatureRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 내 계약서 보기 (user-web 마이페이지).
 *
 * userId는 항상 컨트롤러가 X-User 헤더(gateway가 JWT에서 꺼내 넣어준 값)로 넘긴다.
 * 남의 신청번호를 URL에 넣어도 본인 것이 아니면 403.
 * 읽기만 하므로 gRPC 호출 없음 — 계약서는 제출 때 완성본으로 저장되고, 신청자에게는 관리자 승인 뒤부터 보여준다.
 * 본문의 계약일은 결제 완료일로 채워서 내려준다 (ContractContentView).
 *
 * @author 한성현
 */
@Service
public class MyContractService {

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository documentRepository;
    private final ContractSignatureRepository signatureRepository;
    private final ContractContentView contentView;

    // 계약서는 제출 때 만들어지지만, 신청자에게는 관리자 승인 뒤부터 보여준다 (2026-11-05 결정)
    //   승인 전 상태: 심사 중 · 반려 · 심사 중 취소 (작성 중 DRAFT는 계약서 자체가 없다)
    public static final java.util.Set<ContractStatus> BEFORE_APPROVAL = java.util.EnumSet.of(
            ContractStatus.DRAFT, ContractStatus.SUBMITTED, ContractStatus.REJECTED, ContractStatus.CANCELED);

    public MyContractService(ContractRepository contractRepository,
                             ContractDocumentRepository documentRepository,
                             ContractSignatureRepository signatureRepository,
                             ContractContentView contentView) {
        this.contractRepository = contractRepository;
        this.documentRepository = documentRepository;
        this.signatureRepository = signatureRepository;
        this.contentView = contentView;
    }

    // 목록: 관리자 승인된 내 계약서 (최근 생성 순). 승인 전 신청은 빠진다
    public List<MyContractListItemResponse> getMyContracts(Long userId) {
        return documentRepository.findMyContracts(userId, BEFORE_APPROVAL).stream()
                .map(MyContractListItemResponse::from)
                .toList();
    }

    // 상세: 계약서 본문 + 해시 + 서명 여부
    public ContractDocumentResponse getMyContractDocument(Long userId, String applicationNo) {
        Contract c = contractRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new ApplicationNotFoundException("신청서를 찾을 수 없습니다."));
        if (!c.isOwnedBy(userId)) {
            throw new ApplicationAccessDeniedException("본인의 계약서만 볼 수 있습니다.");
        }
        // 404. (DocumentNotFoundException은 원래 "양식 없음"용이지만 응답이 같은 404라 재사용)
        // 계약서는 제출 때 만들어지지만 승인 전에는 없는 것처럼 404로 숨긴다
        String notYet = "아직 발급된 계약서가 없습니다. 관리자 승인 후 확인할 수 있어요.";
        if (BEFORE_APPROVAL.contains(c.getStatus())) {
            throw new DocumentNotFoundException(notYet);
        }
        ContractDocument d = documentRepository.findByApplicationId(c.getId())
                .orElseThrow(() -> new DocumentNotFoundException(notYet));

        boolean signed = signatureRepository.existsByApplicationIdAndDocumentId(c.getId(), d.getId());

        return new ContractDocumentResponse(
                c.getApplicationNo(),
                d.getId(),
                d.getLanguage(),
                contentView.content(c, d),     // 계약일(결제 완료일)까지 채운 본문. 해시는 저장본 기준
                d.getContentHash(),
                c.getBuildingName(),
                c.getRoomName(),
                c.getMoveInDate(),
                c.getMoveOutDate(),
                c.getFirstPayment(),
                // identityVerified: 서명 기록은 본인인증 거래 ID가 필수(NOT NULL)라서, 서명했으면 본인인증도 끝난 것.
                // "본인인증만 끝나고 서명 전" 상태는 아직 저장하는 곳이 없어서 false로 나간다 → 본인인증 API 만들 때 바꿀 것
                signed,
                signed
        );
    }
}
