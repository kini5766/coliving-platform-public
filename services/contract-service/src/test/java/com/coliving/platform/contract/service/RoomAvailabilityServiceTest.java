package com.coliving.platform.contract.service;

import com.coliving.platform.contract.dto.application.AvailableRoomsResponse;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomAvailabilityServiceTest {

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final RoomAvailabilityService service = new RoomAvailabilityService(contracts);
    private final LocalDate moveIn = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(10);

    @Test
    void 예약이_겹치는_호실을_빼고_보낸_순서대로_돌려준다() {
        when(contracts.findBookedRoomIds(any(), any(), any(), any(), anyLong())).thenReturn(List.of(42L));

        AvailableRoomsResponse res = service.findAvailable(7L, List.of(57L, 42L, 41L), moveIn, 6);

        assertThat(res.availableRoomIds()).containsExactly(57L, 41L);
        assertThat(res.moveInDate()).isEqualTo(moveIn);
        assertThat(res.moveOutDate()).isEqualTo(moveIn.plusMonths(6).minusDays(1));
        verify(contracts).findBookedRoomIds(any(), eq(ContractStatus.BOOKING), eq(moveIn),
                eq(moveIn.plusMonths(6).minusDays(1)), eq(7L));
    }

    @Test
    void 비로그인은_본인_작성중_제외값으로_마이너스1을_넘긴다() {
        when(contracts.findBookedRoomIds(any(), any(), any(), any(), anyLong())).thenReturn(List.of());

        service.findAvailable(null, List.of(1L), moveIn, 3);

        verify(contracts).findBookedRoomIds(any(), any(), any(), any(), eq(-1L));
    }

    @Test
    void 중복과_null은_한_번만_확인한다() {
        when(contracts.findBookedRoomIds(any(), any(), any(), any(), anyLong())).thenReturn(List.of());

        AvailableRoomsResponse res = service.findAvailable(null, new ArrayList<>(Arrays.asList(3L, null, 3L, 5L)), moveIn, 3);

        assertThat(res.availableRoomIds()).containsExactly(3L, 5L);
    }

    @Test
    void 후보가_없으면_DB를_조회하지_않는다() {
        AvailableRoomsResponse res = service.findAvailable(null, List.of(), moveIn, 6);

        assertThat(res.availableRoomIds()).isEmpty();
        verify(contracts, never()).findBookedRoomIds(any(), any(), any(), any(), anyLong());
    }

    @Test
    void 지난_날짜나_잘못된_기간은_거절한다() {
        assertThatThrownBy(() -> service.findAvailable(null, List.of(1L), moveIn.minusDays(30), 6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAvailable(null, List.of(1L), moveIn, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAvailable(null, List.of(1L), moveIn, 24))   // 신청 화면 선택지(3·6·12) 밖
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAvailable(null, List.of(1L), null, 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 후보가_너무_많으면_거절한다() {
        List<Long> many = LongStream.rangeClosed(1, RoomAvailabilityService.MAX_ROOM_IDS + 1).boxed().toList();

        assertThatThrownBy(() -> service.findAvailable(null, many, moveIn, 6))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
