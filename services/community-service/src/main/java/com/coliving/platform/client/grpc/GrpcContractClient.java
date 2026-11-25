package com.coliving.platform.client.grpc;

import com.coliving.platform.client.ContractClient;
import com.coliving.platform.grpc.contract.ContractResidentServiceGrpc;
import com.coliving.platform.grpc.contract.GetResidentRoomsRequest;
import com.coliving.platform.common.exception.CommunityException;
import com.coliving.platform.common.exception.ErrorCode;
import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * contract-service gRPC 클라이언트 (연동 요청 API 2-1 GetResidentRooms)
 * community.clients.contract=grpc 일 때 FakeContractClient 대신 사용
 *
 * proto → ResidentRoom 매핑
 *   room_name     → roomNumber      ("302호", 화면 표시용)
 *   move_out_date → contractEndDate (계약 마지막 날)
 *   room_id       → 지금은 쓰는 곳이 없어 받지 않음
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "community.clients.contract", havingValue = "grpc")
public class GrpcContractClient implements ContractClient {

	/** proto 규칙: 한 번에 최대 100명 (넘으면 INVALID_ARGUMENT) */
	private static final int MAX_IDS_PER_CALL = 100;
	private static final long DEADLINE_SECONDS = 3;

	private final ContractResidentServiceGrpc.ContractResidentServiceBlockingStub contractStub;

	public GrpcContractClient(ContractResidentServiceGrpc.ContractResidentServiceBlockingStub contractStub) {
		this.contractStub = contractStub;
		log.info("입주 건물·호실은 contract-service gRPC 로 조회해요 (community.clients.contract=grpc)");
	}

	@Override
	public Map<Long, ResidentRoom> getResidentRooms(Collection<Long> userIds) {
		Map<Long, ResidentRoom> result = new LinkedHashMap<>();
		if (userIds == null || userIds.isEmpty()) {
			return result;
		}
		List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
		for (int from = 0; from < ids.size(); from += MAX_IDS_PER_CALL) {
			List<Long> chunk = ids.subList(from, Math.min(from + MAX_IDS_PER_CALL, ids.size()));
			try {
				contractStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
						.getResidentRooms(GetResidentRoomsRequest.newBuilder().addAllUserIds(chunk).build())
						.getRoomsList()
						.forEach(room -> result.put(room.getUserId(), toResidentRoom(room)));
			} catch (StatusRuntimeException e) {
				log.warn("contract-service GetResidentRooms 실패: {} (ids {}개)", e.getStatus(), chunk.size());
				throw new CommunityException(ErrorCode.SERVICE_UNAVAILABLE, "입주 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
			}
		}
		return result;   // 계약 없는 회원은 빠져 있음
	}

	private static ResidentRoom toResidentRoom(com.coliving.platform.grpc.contract.ResidentRoom room) {
		return new ResidentRoom(
				room.getUserId(),
				room.getContractId() == 0 ? null : room.getContractId(),
				room.getBuildingId(),
				blankToNull(room.getBuildingName()),
				blankToNull(room.getRoomName()),
				blankToNull(room.getContractStatus()),
				room.hasMoveInDate() ? toDate(room.getMoveInDate()) : null,
				room.hasMoveOutDate() ? toDate(room.getMoveOutDate()) : null);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static LocalDate toDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value);   // yyyy-MM-dd
		} catch (DateTimeParseException e) {
			log.warn("contract-service 날짜 형식이 달라요: {}", value);
			return null;
		}
	}
}
