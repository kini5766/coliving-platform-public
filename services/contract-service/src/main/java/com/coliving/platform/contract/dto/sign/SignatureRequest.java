package com.coliving.platform.contract.dto.sign;

/**
 * [2 전자 서명] POST /contract-service/apply/applications/{applicationNo}/signature
 *
 * identityVerificationId: 본인인증 때 쓴 포트원 ID. 서버가 포트원에 다시 조회하고 contract_signature에 남긴다
 * contentHash   : 사용자가 본 계약서의 해시. 서버의 해시와 다르면 서명 거부 (409)
 *                 → "보는 사이에 계약서가 바뀌지 않았다"를 확인하는 용도
 * signatureImage: 서명 그림. PNG를 Base64로 인코딩한 문자열
 *                 프론트: canvas.toDataURL('image/png') 결과에서 "data:image/png;base64," 앞부분을 떼고 보낸다
 *                 서버: 디코딩 후 PNG 파일 시그니처(첫 8바이트)와 크기 상한을 확인한다
 *                 (PNG를 쓰지 않는다는 결정은 계약서에만 해당, 서명은 PNG)
 *                 저장: 이미지는 ContractFileStorage, DB(contract_signature)에는 경로만
 *
 * @author 한성현
 */
public record SignatureRequest(
        String identityVerificationId,
        String contentHash,
        String signatureImage
) {}
