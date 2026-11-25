package com.coliving.platform.contract.service;

import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractDocumentRepository;
import com.coliving.platform.contract.repository.ContractRepository;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminApplicationQueryServiceTest {

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final AdminApplicationQueryService service =
            new AdminApplicationQueryService(contracts, mock(ContractDocumentRepository.class), mock(ContractContentView.class));

    @Test
    void 검색어의_퍼센트_밑줄_느낌표는_문자_그대로_찾는다() {
        assertThat(AdminApplicationQueryService.likePattern("Room_1%!")).isEqualTo("%room!_1!%!!%");
    }

    @Test
    void 검색어가_없으면_상태로만_거른다() {
        when(contracts.findByStatusInOrderByCreatedAtDesc(any())).thenReturn(List.of());

        service.findAll("  ", List.of(ContractStatus.SUBMITTED));

        verify(contracts).findByStatusInOrderByCreatedAtDesc(List.of(ContractStatus.SUBMITTED));
        verify(contracts, never()).searchForAdmin(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 상태를_안_넘기면_DRAFT만_빼고_조회한다() {
        when(contracts.searchForAdmin(any(), eq("%망원%"))).thenAnswer(i -> {
            Collection<ContractStatus> statuses = i.getArgument(0);
            assertThat(statuses).doesNotContain(ContractStatus.DRAFT)
                    .contains(ContractStatus.SUBMITTED, ContractStatus.APPROVED, ContractStatus.REJECTED);
            return List.of();
        });

        service.findAll("망원", null);

        verify(contracts).searchForAdmin(any(), eq("%망원%"));
    }

    @Test
    void 검색어가_너무_길면_400() {
        assertThatThrownBy(() -> service.findAll("가".repeat(101), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
