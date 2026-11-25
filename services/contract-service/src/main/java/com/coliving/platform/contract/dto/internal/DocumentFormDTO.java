package com.coliving.platform.contract.dto.internal;

/**
 * 문서 서비스에서 조회한 계약서 양식 (치환 전 본문).
 * gRPC 생성 클래스(DocumentForm)는 DocumentQueryService 안에서만 쓰고, 밖으로는 이 DTO로 넘긴다.
 *
 * @param templateId 문서 서비스 양식 번호 (document_forms.id). 계약서(contract_document)에 함께 저장된다
 * @param content    양식 본문 (HTML). {{키}} 자리는 ContractContentRenderer가 신청서 값으로 채운다
 * @author 한성현
 */
public record DocumentFormDTO(
        Long templateId,
        String language,
        String content
) {
}