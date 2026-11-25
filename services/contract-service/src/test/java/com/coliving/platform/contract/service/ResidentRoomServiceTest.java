package com.coliving.platform.contract.service;

import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static com.coliving.platform.contract.enums.ContractStatus.APPROVED;
import static com.coliving.platform.contract.enums.ContractStatus.COMPLETED;
import static com.coliving.platform.contract.enums.ContractStatus.PAID;
import static com.coliving.platform.contract.enums.ContractStatus.TERMINATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** community 2-1: 회원당 계약 1개 고르는 규칙 (ResidentRoomService 주석의 순서) */
class ResidentRoomServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    private static Contract contract(long id, long userId, ContractStatus status, String moveIn, String moveOut) {
        Contract c = mock(Contract.class);
        when(c.getId()).thenReturn(id);
        when(c.getUserId()).thenReturn(userId);
        when(c.getStatus()).thenReturn(status);
        when(c.getMoveInDate()).thenReturn(LocalDate.parse(moveIn));
        when(c.getMoveOutDate()).thenReturn(LocalDate.parse(moveOut));
        return c;
    }

    @Test
    void 입주_중인_계약이_심사_중인_새_신청보다_먼저() {
        Contract living = contract(1, 7, COMPLETED, "2026-06-01", "2026-11-30");
        Contract applying = contract(2, 7, APPROVED, "2026-12-01", "2027-05-31");

        assertThat(ResidentRoomService.pickPerUser(List.of(7L), List.of(applying, living), TODAY))
                .containsExactly(living);
    }

    @Test
    void 연장_계약이_있으면_오늘이_기간_안인_계약을_고른다() {
        Contract now = contract(1, 7, COMPLETED, "2026-06-01", "2026-11-30");
        Contract next = contract(2, 7, PAID, "2026-12-01", "2027-05-31");     // 입주일은 더 늦지만 아직 아님

        assertThat(ResidentRoomService.pickPerUser(List.of(7L), List.of(next, now), TODAY))
                .containsExactly(now);
    }

    @Test
    void 기간_밖이면_입주일이_늦은_계약() {
        Contract old = contract(1, 7, TERMINATED, "2025-01-01", "2025-06-30");
        Contract recent = contract(2, 7, TERMINATED, "2025-07-01", "2025-12-31");

        assertThat(ResidentRoomService.pickPerUser(List.of(7L), List.of(old, recent), TODAY))
                .containsExactly(recent);
    }

    @Test
    void 요청한_회원_순서대로_계약_없는_회원과_중복_ID는_빠진다() {
        Contract a = contract(1, 5, PAID, "2026-11-01", "2027-04-30");
        Contract b = contract(2, 9, APPROVED, "2026-12-01", "2027-05-31");

        assertThat(ResidentRoomService.pickPerUser(List.of(9L, 3L, 5L, 9L), List.of(a, b), TODAY))
                .containsExactly(b, a);
    }

    @Test
    void 회원_ID가_100개를_넘으면_거절하고_DB를_조회하지_않는다() {
        ContractRepository repo = mock(ContractRepository.class);
        ResidentRoomService service = new ResidentRoomService(repo);

        assertThatThrownBy(() -> service.findResidentRooms(Collections.nCopies(101, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void 빈_요청은_DB를_조회하지_않고_빈_결과() {
        ContractRepository repo = mock(ContractRepository.class);

        assertThat(new ResidentRoomService(repo).findResidentRooms(List.of())).isEmpty();
        verifyNoInteractions(repo);
    }
}
