package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.PaymentGatewayException;
import com.coliving.platform.common.exception.PaymentRejectedException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 HTTP로 가짜 서버를 불러서 요청 형식과 응답 변환을 확인한다 */
class KakaoPayRestClientTest {

    private HttpServer server;
    private KakaoPayRestClient client;

    private volatile int status = 200;
    private volatile String response = "{}";
    private volatile String path;
    private volatile String authorization;
    private volatile String contentType;
    private volatile String requestBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path = exchange.getRequestURI().getPath();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        client = new KakaoPayRestClient("test-secret", "TC0ONETIME", "http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void ready는_필수값을_보내고_결제창_주소를_읽는다() {
        response = """
                {"tid":"T1","next_redirect_pc_url":"https://pay/pc","next_redirect_mobile_url":"https://pay/m",
                 "next_redirect_app_url":"https://pay/app","created_at":"2026-11-02T12:00:00"}""";

        KakaoPayClient.Ready ready = client.ready(new KakaoPayClient.ReadyCommand(
                "ORDER-1", "7", "건물 302호", 1_780_000L, "http://f/a", "http://f/c", "http://f/x"));

        assertThat(ready).isEqualTo(new KakaoPayClient.Ready("T1", "https://pay/pc", "https://pay/m"));
        assertThat(path).isEqualTo("/online/v1/payment/ready");
        assertThat(authorization).isEqualTo("SECRET_KEY test-secret");
        assertThat(contentType).startsWith("application/json");
        assertThat(requestBody).contains(
                "\"cid\":\"TC0ONETIME\"", "\"partner_order_id\":\"ORDER-1\"", "\"partner_user_id\":\"7\"",
                "\"item_name\":\"건물 302호\"", "\"quantity\":1", "\"total_amount\":1780000", "\"tax_free_amount\":0",
                "\"approval_url\":\"http://f/a\"", "\"cancel_url\":\"http://f/c\"", "\"fail_url\":\"http://f/x\"");
    }

    @Test
    void approve_응답의_시각은_한국_시각으로_해석한다() {
        response = """
                {"aid":"A1","tid":"T1","payment_method_type":"CARD","approved_at":"2026-11-02T12:34:56"}""";

        KakaoPayClient.Approved approved = client.approve("T1", "ORDER-1", "7", "pg");

        assertThat(approved.paymentMethod()).isEqualTo("CARD");
        assertThat(approved.approvedAt()).isEqualTo(Instant.parse("2026-11-02T03:34:56Z"));
        assertThat(path).isEqualTo("/online/v1/payment/approve");
        assertThat(requestBody).contains("\"tid\":\"T1\"", "\"pg_token\":\"pg\"");
    }

    @Test
    void order는_상태를_읽고_미결제면_시각이_없어도_된다() {
        response = """
                {"tid":"T1","status":"READY"}""";

        KakaoPayClient.Order order = client.order("T1");

        assertThat(order.paid()).isFalse();
        assertThat(order.failed()).isFalse();
        assertThat(path).isEqualTo("/online/v1/payment/order");
    }

    @Test
    void cancel은_금액을_보낸다() {
        client.cancel("T1", 1000L);

        assertThat(path).isEqualTo("/online/v1/payment/cancel");
        assertThat(requestBody).contains("\"cancel_amount\":1000", "\"cancel_tax_free_amount\":0");
    }

    @Test
    void 카카오의_400은_거절로_바꾸고_메시지를_유지한다() {
        status = 400;
        response = """
                {"error_code":-780,"error_message":"approval failure!"}""";

        assertThatThrownBy(() -> client.approve("T1", "ORDER-1", "7", "pg"))
                .isInstanceOf(PaymentRejectedException.class).hasMessage("approval failure!");
    }

    @Test
    void 키_오류와_서버_장애는_게이트웨이_오류로_바꾼다() {
        status = 401;
        assertThatThrownBy(() -> client.order("T1")).isInstanceOf(PaymentGatewayException.class);

        status = 500;
        assertThatThrownBy(() -> client.order("T1")).isInstanceOf(PaymentGatewayException.class);
    }
}
