package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.DocumentServiceException;
import com.coliving.platform.contract.dto.internal.DocumentFormDTO;
import org.springframework.stereotype.Component;

/**
 * 계약서 언어 → 문서 서비스 양식 (치환 전 본문).
 *
 * 그 언어에서 가장 마지막에 등록된 양식을 쓴다 (DocumentQueryService.getLatestFormForContract).
 *   → 관리자가 양식을 새로 등록하거나 고치면, 그 뒤에 제출되는 신청서부터 바뀐 양식으로 계약서가 만들어진다.
 *   → 이미 만들어진 계약서(contract_document)는 바뀌지 않는다 (서명 해시가 걸려 있어서 바뀌면 안 됨).
 *   (이전: 설정 app.document.templates의 고정 번호 ko=1, en=2만 썼다 → 새로 등록한 양식이 반영되지 않았음)
 *
 * 문서 서비스 호출이라 트랜잭션 밖에서 부를 것 (기다리는 동안 DB 커넥션을 잡지 않게).
 *
 * @author 한성현
 */
@Component
public class ContractFormLoader {

    private final DocumentQueryService documentQueryService;

    public ContractFormLoader(DocumentQueryService documentQueryService) {
        this.documentQueryService = documentQueryService;
    }

    /** 언어("ko"/"en")의 최신 양식. 응답 언어가 다르면 실패 (DocumentServiceException) */
    public DocumentFormDTO load(String language) {
        DocumentFormDTO form = documentQueryService.getLatestFormForContract(language);
        if (!language.equals(form.language())) {
            throw new DocumentServiceException(
                    "양식 언어가 맞지 않습니다. 요청: " + language + ", 응답: " + form.language(), null);
        }
        return form;
    }
}
