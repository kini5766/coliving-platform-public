package com.coliving.platform.contract.mail;

import com.coliving.platform.contract.mail.template.ApplicationApprovedMailTemplate;
import com.coliving.platform.contract.mail.template.ApplicationRejectedMailTemplate;
import com.coliving.platform.contract.mail.template.ContractMailTemplate;
import com.coliving.platform.contract.mail.template.PaymentCompletedMailTemplate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 템플릿 등록 검사 + 본문 내용 */
class ContractMailTemplateTest {

    private final ContractMailProperties props = new ContractMailProperties(false, "a@gmail.com", "Coliving", "http://front/");
    private final ContractMailComposer composer = new ContractMailComposer(List.of(
            new ApplicationApprovedMailTemplate(props),
            new ApplicationRejectedMailTemplate(props),
            new PaymentCompletedMailTemplate(props)));

    private static ContractMailData data(String rejectReason) {
        return new ContractMailData(12L, "CT-20261106-0001", "hong@example.com", "홍길동",
                "포레스트 153", "302호", LocalDate.of(2026, 12, 1), LocalDate.of(2027, 5, 31),
                500_000L, 1_000_000L, 1_500_000L, rejectReason,
                Instant.parse("2026-11-07T01:20:00Z"), "ko");
    }

    @Test
    void 승인_메일은_결제_기한과_금액을_KST로_보여준다() {
        MailContent m = composer.compose(new ContractMailEvent(MailType.APPLICATION_APPROVED, data(null)));

        assertThat(m.subject()).contains("승인", "CT-20261106-0001");
        assertThat(m.html()).contains("홍길동", "포레스트 153 302호", "1,500,000원",
                "2026.11.07 10:20",                          // 01:20Z → 10:20 KST
                "2026.12.01 ~ 2027.05.31",
                "http://front/my/application");             // 끝의 / 정리
    }

    @Test
    void 반려_사유는_HTML_이스케이프된다() {
        MailContent m = composer.compose(new ContractMailEvent(MailType.APPLICATION_REJECTED, data("<script>x</script> 서류 미비")));

        assertThat(m.html()).contains("&lt;script&gt;x&lt;/script&gt; 서류 미비").doesNotContain("<script>");
    }

    @Test
    void 결제_완료_메일은_보증금과_입주일을_보여준다() {
        MailContent m = composer.compose(new ContractMailEvent(MailType.PAYMENT_COMPLETED, data(null)));

        assertThat(m.subject()).contains("결제가 완료");
        assertThat(m.html()).contains("1,000,000원", "500,000원", "2026.12.01");
    }

    @Test
    void 템플릿이_빠지면_시작할_때_실패한다() {
        List<ContractMailTemplate> missing = List.of(new ApplicationApprovedMailTemplate(props));

        assertThatThrownBy(() -> new ContractMailComposer(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("템플릿이 없습니다");
    }

    @Test
    void 상태_변경별_메일_종류() {
        assertThat(MailType.forTransition(com.coliving.platform.contract.enums.ContractStatus.SUBMITTED,
                com.coliving.platform.contract.enums.ContractStatus.CANCELED)).isEmpty();
        assertThat(MailType.forTransition(null, com.coliving.platform.contract.enums.ContractStatus.DRAFT)).isEmpty();
    }
}
