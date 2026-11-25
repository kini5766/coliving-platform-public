package com.coliving.platform.contract.mail;

import com.coliving.platform.contract.mail.sender.MailDelivery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractMailListenerTest {

    private final ContractMailComposer composer = mock(ContractMailComposer.class);
    private final MailDelivery delivery = mock(MailDelivery.class);
    private final ContractMailListener listener = new ContractMailListener(composer, delivery);

    private static ContractMailEvent event(String email) {
        ContractMailData d = new ContractMailData(12L, "CT-1", email, "홍길동", "B", "302호",
                null, null, null, null, null, null, null, "ko");
        return new ContractMailEvent(MailType.APPLICATION_APPROVED, d);
    }

    @Test
    void 메일을_만들어_보낸다() throws Exception {
        MailContent content = new MailContent("s", "<p>b</p>");
        when(composer.compose(any())).thenReturn(content);

        listener.onMail(event("hong@example.com"));

        verify(delivery).send(eq("hong@example.com"), eq(content));
    }

    @Test
    void 주소가_없으면_보내지_않는다() throws Exception {
        listener.onMail(event(" "));

        verify(delivery, never()).send(anyString(), any());
    }

    @Test
    void 발송이_실패해도_예외를_밖으로_던지지_않는다() throws Exception {
        when(composer.compose(any())).thenReturn(new MailContent("s", "b"));
        doThrow(new RuntimeException("SMTP down")).when(delivery).send(anyString(), any());

        assertThatCode(() -> listener.onMail(event("hong@example.com"))).doesNotThrowAnyException();
    }
}
