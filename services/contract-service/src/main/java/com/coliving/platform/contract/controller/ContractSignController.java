package com.coliving.platform.contract.controller;

import com.coliving.platform.contract.dto.sign.IdentityVerificationRequest;
import com.coliving.platform.contract.dto.sign.IdentityVerificationResponse;
import com.coliving.platform.contract.dto.sign.SignatureRequest;
import com.coliving.platform.contract.dto.sign.SignatureResponse;
import com.coliving.platform.contract.service.ContractSignatureService;
import com.coliving.platform.contract.service.IdentityVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 2단계 계약서 확인·본인인증·서명 API. gateway 경로: /contract-service/apply/applications/**
 * (계약서 본문은 GET /contracts/{applicationNo}/document 를 같이 쓴다)
 *
 * @author 한성현
 */
@RestController
@RequestMapping("/apply/applications")
public class ContractSignController {

    private static final String USER_HEADER = "X-User";

    private final IdentityVerificationService identityVerificationService;
    private final ContractSignatureService contractSignatureService;

    public ContractSignController(IdentityVerificationService identityVerificationService,
                                  ContractSignatureService contractSignatureService) {
        this.identityVerificationService = identityVerificationService;
        this.contractSignatureService = contractSignatureService;
    }

    /**
     * 포트원 본인인증 결과 확인.
     * 서버가 포트원에 직접 조회해서 인증 완료 여부와 신청자 본인인지를 확인한다 (저장은 하지 않는다).
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @PostMapping("/{applicationNo}/identity-verification")
    public IdentityVerificationResponse verifyIdentity(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo,
            @RequestBody IdentityVerificationRequest req) {

        return identityVerificationService.verify(userId, applicationNo, req.identityVerificationId());
    }

    /**
     * 전자 서명 제출 (201).
     * 본인인증 재확인 + 계약서 해시 대조 후 서명 이미지와 서명 PDF를 저장한다.
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @PostMapping("/{applicationNo}/signature")
    public ResponseEntity<SignatureResponse> sign(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo,
            @RequestBody SignatureRequest req) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractSignatureService.sign(userId, applicationNo, req));   // 201
    }
}
