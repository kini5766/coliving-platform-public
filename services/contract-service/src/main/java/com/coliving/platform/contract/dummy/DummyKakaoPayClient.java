package com.coliving.platform.contract.dummy;

import com.coliving.platform.common.exception.PaymentGatewayException;
import com.coliving.platform.common.exception.PaymentRejectedException;
import com.coliving.platform.contract.service.KakaoPayClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [개발용 더미] 카카오페이 없이 결제를 흉내낸다. "dummy-kakaopay" 프로필에서만 등록된다.
 * ready의 결제창 주소는 바로 approval_url로 돌아온다 (pg_token=dummy).
 * 결과는 pg_token 값으로 고른다 (주소창의 값을 바꿔 테스트)
 *   "fail" : 카카오가 거절 → 400
 *   "down" : 카카오 장애 → 503
 *   "lost" : 카카오에서는 결제됐는데 승인 응답과 바로 뒤의 주문 조회까지 실패 → 503, 결제는 PENDING으로 남음
 *            → 보정 스케줄러가 다시 주문 조회하면 결제됨으로 보여서 PAID로 맞춰진다
 *   그 외   : 승인. 같은 tid를 다시 승인하면 실제 카카오처럼 거절한다
 * ⚠ 운영 설정에 dummy-kakaopay를 넣지 말 것.
 *
 * @author 한성현
 */
@Service
@Profile("dummy-kakaopay")
public class DummyKakaoPayClient implements KakaoPayClient {

    private final Map<String, Approved> approved = new ConcurrentHashMap<>();
    private final Set<String> orderFailOnce = ConcurrentHashMap.newKeySet();   // "lost": 다음 주문 조회 1번만 실패

    @Override
    public Ready ready(ReadyCommand command) {
        String url = command.approvalUrl() + "&pg_token=dummy";
        return new Ready("T-DUMMY-" + UUID.randomUUID(), url, url);
    }

    @Override
    public Approved approve(String tid, String orderId, String userId, String pgToken) {
        if ("down".equals(pgToken)) throw new PaymentGatewayException("[더미] 카카오페이 장애", null);
        if ("fail".equals(pgToken)) throw new PaymentRejectedException("[더미] 결제가 거절됐습니다.");
        if ("lost".equals(pgToken)) {
            // 카카오에서는 승인됐는데 응답만 유실 → 503. PENDING으로 남고, 보정 스케줄러가 PAID로 맞춰야 한다
            approved.putIfAbsent(tid, new Approved("MONEY", Instant.now()));
            orderFailOnce.add(tid);                 // 승인 직후 서비스가 하는 확인 조회도 실패시켜야 PENDING이 남는다
            throw new PaymentGatewayException("[더미] 승인 응답 유실", null);
        }
        Approved result = new Approved("MONEY", Instant.now());
        if (approved.putIfAbsent(tid, result) != null) {
            throw new PaymentRejectedException("[더미] 이미 승인된 거래입니다.");
        }
        return result;
    }

    @Override
    public Order order(String tid) {
        if (orderFailOnce.remove(tid)) throw new PaymentGatewayException("[더미] 주문 조회 실패", null);
        Approved result = approved.get(tid);
        return result == null ? new Order("READY", null) : new Order("SUCCESS_PAYMENT", result);
    }

    @Override
    public void cancel(String tid, long amount) {
        approved.remove(tid);
    }
}
