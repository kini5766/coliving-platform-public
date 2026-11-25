package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.PaymentGatewayException;
import com.coliving.platform.common.exception.PaymentRejectedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 카카오페이 온라인 단건 결제 REST API.
 *   POST https://open-api.kakaopay.com/online/v1/payment/{ready|approve|order|cancel}
 *   Authorization: SECRET_KEY {시크릿 키}
 *
 * - 시크릿 키는 서버에만 둔다: config의 app.kakaopay.secret-key = ${KAKAOPAY_SECRET_KEY}
 *
 * @author 한성현
 */
@Service
@Profile("!dummy-kakaopay")   // dummy-kakaopay 프로필이면 dummy 패키지의 더미가 대신 등록된다
public class KakaoPayRestClient implements KakaoPayClient {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RestClient restClient;
    private final String cid;

    public KakaoPayRestClient(@Value("${app.kakaopay.secret-key}") String secretKey,
                              @Value("${app.kakaopay.cid}") String cid,
                              @Value("${app.kakaopay.base-url:https://open-api.kakaopay.com}") String baseUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(30));    // 승인은 카드사까지 다녀오므로 넉넉히

        this.cid = cid;
        this.restClient = RestClient.builder()
                .requestFactory(timeouts)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "SECRET_KEY " + secretKey)
                .build();
    }

    @Override
    public Ready ready(ReadyCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", cid);
        body.put("partner_order_id", command.orderId());
        body.put("partner_user_id", command.userId());
        body.put("item_name", command.itemName());
        body.put("quantity", 1);
        body.put("total_amount", command.amount());
        body.put("tax_free_amount", 0);
        body.put("approval_url", command.approvalUrl());
        body.put("cancel_url", command.cancelUrl());
        body.put("fail_url", command.failUrl());

        ReadyResponse res = post("/online/v1/payment/ready", body, ReadyResponse.class);
        return new Ready(res.tid(), res.redirectPcUrl(), res.redirectMobileUrl());
    }

    @Override
    public Approved approve(String tid, String orderId, String userId, String pgToken) {
        ApproveResponse res = post("/online/v1/payment/approve", Map.of(
                "cid", cid,
                "tid", tid,
                "partner_order_id", orderId,
                "partner_user_id", userId,
                "pg_token", pgToken), ApproveResponse.class);
        return new Approved(res.paymentMethodType(), kst(res.approvedAt()));
    }

    @Override
    public Order order(String tid) {
        OrderResponse res = post("/online/v1/payment/order", Map.of("cid", cid, "tid", tid), OrderResponse.class);
        return new Order(res.status(), new Approved(res.paymentMethodType(), kst(res.approvedAt())));
    }

    @Override
    public void cancel(String tid, long amount) {
        post("/online/v1/payment/cancel", Map.of(
                "cid", cid,
                "tid", tid,
                "cancel_amount", amount,
                "cancel_tax_free_amount", 0), Map.class);
    }

    private <T> T post(String path, Map<String, ?> body, Class<T> type) {
        T res;
        try {
            res = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException e) {
            // 400 = 요청 내용 문제(토큰 오류, 이미 처리된 거래 등). 401·403 = 키 설정 문제, 5xx = 카카오 장애
            if (e.getStatusCode().value() == 400) {
                throw new PaymentRejectedException(rejectMessage(e));
            }
            throw new PaymentGatewayException("카카오페이 호출 실패 (" + e.getStatusCode().value() + ") " + path, e);
        } catch (RestClientException e) {
            throw new PaymentGatewayException("카카오페이에 연결할 수 없습니다: " + path, e);
        }
        if (res == null) {
            throw new PaymentGatewayException("카카오페이 응답이 비어 있습니다: " + path, null);
        }
        return res;
    }

    private static String rejectMessage(RestClientResponseException e) {
        try {
            ErrorResponse error = e.getResponseBodyAs(ErrorResponse.class);
            if (error != null && error.message() != null) return error.message();
        } catch (RuntimeException ignored) {
            // 본문이 JSON이 아니면 기본 문구
        }
        return "결제 요청이 거절됐습니다.";
    }

    // 카카오 응답 시각은 시간대 없는 한국 시각 ("2026-11-02T12:34:56")
    private static Instant kst(String value) {
        return value == null ? null : LocalDateTime.parse(value).atZone(SEOUL).toInstant();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReadyResponse(String tid,
                         @JsonProperty("next_redirect_pc_url") String redirectPcUrl,
                         @JsonProperty("next_redirect_mobile_url") String redirectMobileUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApproveResponse(@JsonProperty("payment_method_type") String paymentMethodType,
                           @JsonProperty("approved_at") String approvedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderResponse(String status,
                         @JsonProperty("payment_method_type") String paymentMethodType,
                         @JsonProperty("approved_at") String approvedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(@JsonProperty("error_message") String message) {}
}
