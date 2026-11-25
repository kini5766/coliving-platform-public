package com.coliving.platform.common.websocket;

import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 연결(HTTP 업그레이드) 요청의 X-User 헤더를 세션에 저장
 * Gateway 가 로그인 쿠키 → JWT 를 확인하고 X-User 를 붙여서 넘겨줌 (REST 와 같은 방식)
 * → STOMP CONNECT 때 StompAuthChannelInterceptor 가 이 값으로 사용자 확인
 */
public class GatewayUserHandshakeInterceptor implements HandshakeInterceptor {

	public static final String GATEWAY_USER = "gatewayUserId";

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		String userId = request.getHeaders().getFirst("X-User");
		if (userId != null && !userId.isBlank()) {
			attributes.put(GATEWAY_USER, userId.trim());
		}
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
	}
}
