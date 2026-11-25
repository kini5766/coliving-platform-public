package com.coliving.platform.contract.controller;

import com.coliving.platform.contract.dto.contract.MyContractListItemResponse;
import com.coliving.platform.contract.dto.sign.ContractDocumentResponse;
import com.coliving.platform.contract.enums.ReceiptType;
import com.coliving.platform.contract.service.ContractSignatureService;
import com.coliving.platform.contract.service.MyContractService;
import com.coliving.platform.contract.service.PaymentReceiptService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 내 계약서 보기 API (user-web 마이페이지에서 호출). gateway 경로: /contract-service/contracts/**
 *
 * 회원 번호는 X-User 헤더로만 받는다 (gateway가 로그인 토큰에서 꺼내 넣어줌, 없으면 401).
 * ⚠ contract-service 포트(8089)로 직접 부르면 X-User를 마음대로 넣을 수 있다. 개발 중에만 직접 호출할 것
 *
 * @author 한성현
 */
@RestController
@RequestMapping("/contracts")
public class MyContractController {

    private static final String USER_HEADER = "X-User";   // api-gateway UserIdHeaderWebFilter.SUBJECT_HEADER와 같아야 함

    private final MyContractService myContractService;
    private final ContractSignatureService contractSignatureService;
    private final PaymentReceiptService paymentReceiptService;

    public MyContractController(MyContractService myContractService,
                                ContractSignatureService contractSignatureService,
                                PaymentReceiptService paymentReceiptService) {
        this.myContractService = myContractService;
        this.contractSignatureService = contractSignatureService;
        this.paymentReceiptService = paymentReceiptService;
    }

    /**
     * 내 계약서 목록 (관리자 승인 뒤의 계약서만).
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @GetMapping
    public List<MyContractListItemResponse> getMyContracts(
            @RequestHeader(USER_HEADER) Long userId) {

        return myContractService.getMyContracts(userId);
    }

    /**
     * 계약서 1건 본문. 2단계 계약서 확인·서명 화면도 이 응답을 쓴다.
     * 승인 전이면 404.
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @GetMapping("/{applicationNo}/document")
    public ContractDocumentResponse getMyContractDocument(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo) {

        return myContractService.getMyContractDocument(userId, applicationNo);
    }

    /**
     * 서명 완료 계약서 PDF 내려받기. 서명 전이면 404.
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @GetMapping("/{applicationNo}/signed-pdf")
    public ResponseEntity<byte[]> downloadSignedPdf(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo) {

        byte[] pdf = contractSignatureService.loadSignedPdf(userId, applicationNo);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("contract-" + applicationNo + ".pdf", StandardCharsets.UTF_8)
                        .build().toString())
                .body(pdf);
    }

    /**
     * 결제 영수증 / 취소 영수증 PDF 내려받기.
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     * @param type   PAYMENT(기본) 결제 영수증 / CANCEL 취소 영수증 (환불 완료된 결제만)
     */
    @GetMapping("/{applicationNo}/receipt")
    public ResponseEntity<byte[]> downloadReceipt(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo,
            @RequestParam(name = "type", defaultValue = "PAYMENT") ReceiptType type) {

        byte[] pdf = paymentReceiptService.load(userId, applicationNo, type);
        String prefix = type == ReceiptType.CANCEL ? "cancel-receipt-" : "receipt-";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(prefix + applicationNo + ".pdf", StandardCharsets.UTF_8)
                        .build().toString())
                .body(pdf);
    }
}
