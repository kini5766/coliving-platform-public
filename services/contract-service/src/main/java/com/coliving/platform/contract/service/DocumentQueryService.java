package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.DocumentNotFoundException;
import com.coliving.platform.common.exception.DocumentServiceException;
import com.coliving.platform.common.exception.DocumentServiceUnavailableException;
import com.coliving.platform.contract.dto.internal.DocumentFormDTO;
import com.coliving.platform.grpc.document.DocumentForm;
import com.coliving.platform.grpc.document.DocumentServiceGrpc;
import com.coliving.platform.grpc.document.GetDocumentFormRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 계약서 양식이 필요할 때 거치는 창구 (문서 서비스 gRPC 호출).
 *
 * - gRPC 호출과 에러 변환을 이 클래스 한 곳에 모은다
 * - gRPC 생성 클래스(DocumentForm)는 여기서만 쓰고, 밖으로는 DocumentFormDTO로 넘긴다
 * - 문서 서비스는 양식만 제공한다. 양식을 채운 완성본과 해시 저장은 계약 서비스(contract_document)의 일
 *
 * 언어별 최신 양식 (getLatestFormForContract, 2026-11-05)
 *   관리자가 양식을 새로 등록하면 그 뒤 계약서부터 새 양식을 쓴다.
 *   문서 서비스 gRPC는 번호로만 조회할 수 있어서, 문서 서비스의 양식 목록 API(GET /admin/forms)를
 *   Eureka로 직접 불러(게이트웨이를 거치지 않음) 그 언어에서 번호가 가장 큰(= 가장 마지막에 등록한) 양식을 고른다.
 *   양식 내용을 고친 경우도 매번 새로 가져오므로 바로 반영된다.
 *
 * @author 한성현
 */
@Service
public class DocumentQueryService {

    private static final String DOCUMENT_SERVICE = "document-service";   // 문서 서비스 spring.application.name (Eureka 이름)
    private static final String FORMS_PATH = "/admin/forms";

    private final DocumentServiceGrpc.DocumentServiceBlockingStub documentStub;
    private final DiscoveryClient discoveryClient;
    private final RestClient rest;

    public DocumentQueryService(DocumentServiceGrpc.DocumentServiceBlockingStub documentStub,
                                DiscoveryClient discoveryClient) {
        this.documentStub = documentStub;
        this.discoveryClient = discoveryClient;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(3));   // gRPC deadline(3s)과 같게
        this.rest = RestClient.builder().requestFactory(timeouts).build();
    }

    /**
     * 그 언어("ko"/"en")에서 가장 마지막에 등록된 양식.
     * 등록된 양식이 없으면 404, 문서 서비스가 꺼져 있거나 느리면 503, 그 밖의 실패는 502.
     */
    public DocumentFormDTO getLatestFormForContract(String language) {
        List<ServiceInstance> instances = discoveryClient.getInstances(DOCUMENT_SERVICE);
        if (instances.isEmpty()) {
            throw new DocumentServiceUnavailableException("문서 서비스를 찾을 수 없습니다 (Eureka 미등록)", null);
        }
        FormItem[] forms;
        try {
            forms = rest.get()
                    .uri(instances.get(0).getUri() + FORMS_PATH)
                    .retrieve()
                    .body(FormItem[].class);
        } catch (ResourceAccessException e) {
            throw new DocumentServiceUnavailableException("문서 서비스 응답 없음 (양식 목록)", e);
        } catch (RestClientException e) {
            throw new DocumentServiceException("문서 서비스 양식 목록 조회 실패", e);
        }

        return Arrays.stream(forms == null ? new FormItem[0] : forms)
                .filter(Objects::nonNull)
                .filter(f -> f.id() != null && language.equals(f.language()))
                .filter(f -> f.content() != null && !f.content().isBlank())
                .max(Comparator.comparing(FormItem::id))                       // 번호가 클수록 나중에 등록한 양식
                .map(f -> new DocumentFormDTO(f.id(), f.language(), f.content()))
                .orElseThrow(() -> new DocumentNotFoundException(
                        "등록된 계약서 양식이 없습니다 (" + language + "). 관리자 화면에서 양식을 등록해주세요."));
    }

    /** 문서 서비스 GET /admin/forms 응답 한 줄 (FormResponse와 같은 필드. 시각은 쓰지 않아 글자로 받는다) */
    public record FormItem(Long id, String language, String title, String content, String createdAt, String updatedAt) {}

    public DocumentFormDTO getFormForContract(Long templateId) {
        return toDto(call(templateId));
    }

    // gRPC 호출 + 에러 변환
    private DocumentForm call(Long templateId) {
        GetDocumentFormRequest request = GetDocumentFormRequest.newBuilder()
                .setTemplateId(templateId)
                .build();
        try {
            return documentStub.getDocumentForm(request);   // ★ 여기가 실제 네트워크 호출 ★
        } catch (StatusRuntimeException e) {
            switch (e.getStatus().getCode()) {
                case NOT_FOUND ->
                        throw new DocumentNotFoundException("존재하지 않는 계약서 양식입니다.");
                case UNAVAILABLE, DEADLINE_EXCEEDED ->
                        throw new DocumentServiceUnavailableException("문서 서비스 응답 없음 (" + e.getStatus().getCode() + ")", e);
                default ->
                        throw new DocumentServiceException("문서 서비스 호출 실패 (" + e.getStatus().getCode() + ")", e);
            }
        }
    }

    // gRPC 응답 → DTO
    // proto3는 값이 없으면 null이 아니라 ""를 주므로, 본문이 비어 있으면 비정상 응답으로 본다
    private DocumentFormDTO toDto(DocumentForm f) {
        if (f.getContent().isBlank()) {
            throw new DocumentServiceException("문서 서비스 응답에 양식 본문이 없습니다.", null);
        }
        return new DocumentFormDTO(
                f.getTemplateId(),
                f.getLanguage(),
                f.getContent()
        );
    }
}