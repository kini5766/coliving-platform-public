package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.IdentityServiceUnavailableException;
import com.coliving.platform.common.exception.IdentityVerificationException;
import com.coliving.platform.contract.dto.internal.VerifiedIdentity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

/**
 * 포트원 V2 본인인증 단건 조회.
 *   GET https://api.portone.io/identity-verifications/{identityVerificationId}
 *   Authorization: PortOne {API 시크릿}
 *
 * - 포트원은 본인인증 전용 (결제는 카카오페이)
 * - API 시크릿은 서버에만 둔다: config의 app.portone.api-secret = ${PORTONE_API_SECRET}
 *
 * @author 한성현
 */
@Service
@Profile("!dummy-portone")   // dummy-portone 프로필이면 dummy 패키지의 더미가 대신 등록된다
public class PortOneIdentityVerificationClient implements IdentityVerificationClient {

    private final RestClient restClient;

    public PortOneIdentityVerificationClient(@Value("${app.portone.api-secret}") String apiSecret,
                                             @Value("${app.portone.base-url:https://api.portone.io}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "PortOne " + apiSecret)
                .build();
    }

    @Override
    public VerifiedIdentity fetch(String identityVerificationId) {
        Response res;
        try {
            res = restClient.get()
                    .uri("/identity-verifications/{id}", identityVerificationId)
                    .retrieve()
                    .body(Response.class);
        } catch (RestClientResponseException e) {
            HttpStatusCode code = e.getStatusCode();
            if (code.value() == 404) {
                throw new IdentityVerificationException("본인인증 내역을 찾을 수 없습니다. 다시 인증해주세요.");
            }
            // 401·403 = 시크릿 설정 문제, 5xx = 포트원 장애 → 사용자 잘못이 아니므로 503
            throw new IdentityServiceUnavailableException("포트원 본인인증 조회 실패 (" + code.value() + ")", e);
        } catch (RestClientException e) {
            throw new IdentityServiceUnavailableException("포트원에 연결할 수 없습니다", e);
        }
        if (res == null || res.status() == null) {
            throw new IdentityServiceUnavailableException("포트원 응답이 비어 있습니다", null);
        }

        Customer c = res.verifiedCustomer();
        return new VerifiedIdentity(
                res.id(),
                res.status(),
                c == null ? null : c.name(),
                c == null ? null : c.phoneNumber(),
                res.verifiedAt() == null ? null : Instant.parse(res.verifiedAt())
        );
    }

    // 포트원 응답 중 필요한 필드만 (나머지 ci·di·birthDate 등은 무시)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String status, String id, Customer verifiedCustomer, String verifiedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Customer(String name, String phoneNumber) {}
}
