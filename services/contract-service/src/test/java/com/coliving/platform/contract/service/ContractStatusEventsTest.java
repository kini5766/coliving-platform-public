package com.coliving.platform.contract.service;

import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractStatusHistory;
import com.coliving.platform.contract.entity.OutboxEvent;
import com.coliving.platform.contract.mail.ContractMailEvent;
import com.coliving.platform.contract.mail.MailType;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.coliving.platform.contract.enums.ContractStatus.APPROVED;
import static com.coliving.platform.contract.enums.ContractStatus.COMPLETED;
import static com.coliving.platform.contract.enums.ContractStatus.DRAFT;
import static com.coliving.platform.contract.enums.ContractStatus.EXPIRED;
import static com.coliving.platform.contract.enums.ContractStatus.PAID;
import static com.coliving.platform.contract.enums.ContractStatus.REJECTED;
import static com.coliving.platform.contract.enums.ContractStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** contract.status-changed 발행 범위(DRAFT 생성 포함 모든 상태 변경 + deposit) + 메일 이벤트 */
class ContractStatusEventsTest {

    private static final long APP_ID = 12L;
    private static final long DEPOSIT = 1_000_000L;

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private ContractStatusEvents events;

    @BeforeEach
    void setUp() {
        events = new ContractStatusEvents(contracts, outbox, json, publisher);

        Contract c = mock(Contract.class);
        when(c.getId()).thenReturn(APP_ID);
        when(c.getApplicantName()).thenReturn(null);            // 제출 전(DRAFT)이라 아직 없음
        when(c.getBuildingId()).thenReturn(1L);
        when(c.getBuildingName()).thenReturn("포레스트 153");
        when(c.getRoomId()).thenReturn(31L);
        when(c.getRoomName()).thenReturn("302호");
        when(c.getMoveInDate()).thenReturn(LocalDate.of(2026, 12, 1));
        when(c.getMoveOutDate()).thenReturn(LocalDate.of(2027, 5, 31));
        when(c.getDeposit()).thenReturn(DEPOSIT);
        when(c.getUserId()).thenReturn(7L);
        when(c.getApplicationNo()).thenReturn("CT-20261106-0001");
        when(c.getApplicantEmail()).thenReturn("hong@example.com");
        when(contracts.findById(APP_ID)).thenReturn(Optional.of(c));
    }

    private List<OutboxEvent> saved(int expected) {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void DRAFT_생성도_status_changed_1건을_쌓고_deposit을_포함한다() {
        events.publish(ContractStatusHistory.user(APP_ID, null, DRAFT, 7L));

        OutboxEvent e = saved(1).get(0);
        assertThat(e.getEventType()).isEqualTo(OutboxEvent.CONTRACT_STATUS_CHANGED);
        assertThat(e.getAggregateId()).isEqualTo(APP_ID);

        JsonNode p = json.readTree(e.getPayload());
        assertThat(p.get("eventId").asString()).isEqualTo(e.getEventId().toString());
        assertThat(p.get("applicationId").asLong()).isEqualTo(APP_ID);
        assertThat(p.get("previousStatus").isNull()).isTrue();
        assertThat(p.get("currentStatus").asString()).isEqualTo("DRAFT");
        assertThat(p.get("deposit").asLong()).isEqualTo(DEPOSIT);
        assertThat(p.get("moveInDate").asString()).isEqualTo("2026-12-01");
        assertThat(p.get("moveOutDate").asString()).isEqualTo("2027-05-31");
        assertThat(p.get("occurredAt").asString()).endsWith("+09:00");
        // 명세 3-1의 13개 필드가 모두 있다
        assertThat(p.propertyNames()).containsExactly(
                "eventId", "occurredAt", "applicationId", "applicantName", "buildingId", "buildingName",
                "roomId", "roomName", "previousStatus", "currentStatus", "moveInDate", "moveOutDate", "deposit");
    }

    @Test
    void DRAFT_만료도_status_changed_1건을_쌓는다() {
        events.publish(ContractStatusHistory.system(APP_ID, DRAFT, EXPIRED));

        JsonNode p = json.readTree(saved(1).get(0).getPayload());
        assertThat(p.get("previousStatus").asString()).isEqualTo("DRAFT");
        assertThat(p.get("currentStatus").asString()).isEqualTo("EXPIRED");
        assertThat(p.get("deposit").asLong()).isEqualTo(DEPOSIT);
    }

    @Test
    void 제출은_status_changed만_쌓고_completed는_쌓지_않는다() {
        events.publish(ContractStatusHistory.user(APP_ID, DRAFT, SUBMITTED, 7L));

        assertThat(saved(1).get(0).getEventType()).isEqualTo(OutboxEvent.CONTRACT_STATUS_CHANGED);
    }

    @Test
    void PAID에서_COMPLETED는_status_changed와_completed_2건() {
        events.publish(ContractStatusHistory.system(APP_ID, PAID, COMPLETED));

        List<OutboxEvent> all = saved(2);
        assertThat(all).extracting(OutboxEvent::getEventType)
                .containsExactly(OutboxEvent.CONTRACT_STATUS_CHANGED, OutboxEvent.CONTRACT_COMPLETED);
        assertThat(json.readTree(all.get(0).getPayload()).get("deposit").asLong()).isEqualTo(DEPOSIT);
    }

    @Test
    void 이력이_null이면_아무것도_쌓지_않는다() {
        events.publish(null);

        verifyNoInteractions(contracts);
        verify(outbox, never()).save(any());
        verifyNoInteractions(publisher);
    }

    // ---------------------------------------------------------------- 메일 이벤트

    private ContractMailEvent mailEvent() {
        ArgumentCaptor<ContractMailEvent> captor = ArgumentCaptor.forClass(ContractMailEvent.class);
        verify(publisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void 승인이면_승인_메일_이벤트() {
        events.publish(ContractStatusHistory.admin(APP_ID, SUBMITTED, APPROVED, 1L, null));

        ContractMailEvent e = mailEvent();
        assertThat(e.type()).isEqualTo(MailType.APPLICATION_APPROVED);
        assertThat(e.data().applicationId()).isEqualTo(APP_ID);
        assertThat(e.data().toEmail()).isEqualTo("hong@example.com");
        assertThat(e.data().applicationNo()).isEqualTo("CT-20261106-0001");
    }

    @Test
    void 반려면_반려_메일_이벤트() {
        events.publish(ContractStatusHistory.admin(APP_ID, SUBMITTED, REJECTED, 1L, "서류 미비"));

        assertThat(mailEvent().type()).isEqualTo(MailType.APPLICATION_REJECTED);
    }

    @Test
    void 결제_완료면_결제_완료_메일_이벤트() {
        events.publish(ContractStatusHistory.system(APP_ID, APPROVED, PAID));

        assertThat(mailEvent().type()).isEqualTo(MailType.PAYMENT_COMPLETED);
        assertThat(mailEvent().data().deposit()).isEqualTo(DEPOSIT);
    }

    @Test
    void 메일_대상이_아닌_변경은_메일_이벤트가_없다() {
        events.publish(ContractStatusHistory.user(APP_ID, null, DRAFT, 7L));
        events.publish(ContractStatusHistory.user(APP_ID, DRAFT, SUBMITTED, 7L));
        events.publish(ContractStatusHistory.system(APP_ID, PAID, COMPLETED));

        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
