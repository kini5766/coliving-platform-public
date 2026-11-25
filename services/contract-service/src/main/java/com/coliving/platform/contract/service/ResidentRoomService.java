package com.coliving.platform.contract.service;

import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * community-service 연동 2-1: 회원별 입주(예정) 건물·호실 조회.
 * gRPC 서버(ContractResidentGrpcService)가 이 클래스를 부른다.
 *
 * 회원당 계약 1개만 돌려준다. 여러 개면 아래 순서로 고른다 (community는 "지금 사는 건물"이 필요)
 *   1) 상태 묶음: 입주 중·예정(PAID, COMPLETED, 입주 전 취소 요청, 퇴실 신청) → 심사·결제 대기(SUBMITTED, APPROVED) → 퇴실 확정(TERMINATED)
 *   2) 같은 묶음이면 오늘이 계약 기간 안에 있는 계약 먼저
 *   3) 그래도 같으면 입주일이 늦은 것 → id가 큰 것 (가장 최근)
 * 작성 중(DRAFT)·반려·취소·만료·환불은 계약이 아니므로 제외 → 그런 회원은 결과에서 빠진다.
 *
 * @author 한성현
 */
@Service
public class ResidentRoomService {

    public static final int MAX_USER_IDS = 100;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 고르는 순서 = 리스트 순서
    private static final List<Set<ContractStatus>> PRIORITY = List.of(
            EnumSet.of(ContractStatus.PAID, ContractStatus.COMPLETED,
                    ContractStatus.MOVE_IN_CANCEL_REQUESTED, ContractStatus.TERMINATION_REQUESTED),
            EnumSet.of(ContractStatus.SUBMITTED, ContractStatus.APPROVED),
            EnumSet.of(ContractStatus.TERMINATED));

    private static final Set<ContractStatus> TARGET = PRIORITY.stream()
            .flatMap(Set::stream)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(ContractStatus.class)));

    private final ContractRepository contractRepository;

    public ResidentRoomService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    /** 요청한 회원 순서대로, 계약이 있는 회원만 */
    @Transactional(readOnly = true)
    public List<Contract> findResidentRooms(Collection<Long> userIds) {
        if (userIds.size() > MAX_USER_IDS) {
            throw new IllegalArgumentException("한 번에 최대 " + MAX_USER_IDS + "명까지 조회할 수 있습니다.");
        }
        if (userIds.isEmpty()) return List.of();
        List<Contract> contracts = contractRepository.findByUserIdInAndStatusIn(Set.copyOf(userIds), TARGET);
        return pickPerUser(userIds, contracts, LocalDate.now(SEOUL));
    }

    /** 회원별로 1개씩 고른다. 테스트에서 오늘 날짜를 바꿔 검증할 수 있게 today를 받는다 */
    static List<Contract> pickPerUser(Collection<Long> userIds, List<Contract> contracts, LocalDate today) {
        Map<Long, Contract> best = contracts.stream()
                .collect(Collectors.toMap(Contract::getUserId, c -> c,
                        (a, b) -> order(today).compare(a, b) <= 0 ? a : b));
        Map<Long, Contract> ordered = new LinkedHashMap<>();
        for (Long id : userIds) {
            Contract c = best.get(id);
            if (c != null) ordered.putIfAbsent(id, c);   // 같은 id가 두 번 와도 한 번만
        }
        return List.copyOf(ordered.values());
    }

    // 앞에 올수록 우선
    private static Comparator<Contract> order(LocalDate today) {
        return Comparator.<Contract>comparingInt(c -> group(c.getStatus()))
                .thenComparing(c -> !livingOn(c, today))                       // 기간 안(false)이 먼저
                .thenComparing(Contract::getMoveInDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Contract::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static int group(ContractStatus status) {
        for (int i = 0; i < PRIORITY.size(); i++) {
            if (PRIORITY.get(i).contains(status)) return i;
        }
        return PRIORITY.size();
    }

    // 입주일 ≤ 오늘 ≤ 퇴실 예정일 (퇴실일 당일 포함)
    private static boolean livingOn(Contract c, LocalDate today) {
        return c.getMoveInDate() != null && c.getMoveOutDate() != null
                && !today.isBefore(c.getMoveInDate()) && !today.isAfter(c.getMoveOutDate());
    }
}
