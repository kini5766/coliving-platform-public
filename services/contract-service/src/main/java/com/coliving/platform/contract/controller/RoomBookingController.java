package com.coliving.platform.contract.controller;

import com.coliving.platform.contract.dto.application.AvailableRoomsResponse;
import com.coliving.platform.contract.dto.application.BookedPeriodResponse;
import com.coliving.platform.contract.service.ApplicationService;
import com.coliving.platform.contract.service.RoomAvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 1-1 호실 선택 화면용 호실 예약 현황. gateway 경로: /contract-service/apply/rooms/**
 *
 * 예약 기간은 계약 서비스(contract_application)에만 있어서 여기서 내려준다.
 * 회원 정보는 내려주지 않고 기간만 준다.
 * X-User는 없어도 된다 (있으면 본인의 작성 중 신청을 예약 기간에서 뺀다).
 *
 * @author 한성현
 */
@RestController
@RequestMapping("/apply/rooms")
public class RoomBookingController {

    private static final String USER_HEADER = "X-User";   // api-gateway UserIdHeaderWebFilter.SUBJECT_HEADER와 같아야 함

    private final ApplicationService applicationService;
    private final RoomAvailabilityService roomAvailabilityService;

    public RoomBookingController(ApplicationService applicationService,
                                 RoomAvailabilityService roomAvailabilityService) {
        this.applicationService = applicationService;
        this.roomAvailabilityService = roomAvailabilityService;
    }

    /**
     * 이 호실에 이미 잡혀 있는 예약 기간 (오늘 이후 끝나는 것만, 입주일 순).
     *
     * @param userId X-User 헤더 (선택). 있으면 본인의 작성 중 신청은 기간에서 뺀다
     */
    @GetMapping("/{roomId}/booked-periods")
    public List<BookedPeriodResponse> getBookedPeriods(
            @RequestHeader(value = USER_HEADER, required = false) Long userId,
            @PathVariable("roomId") Long roomId) {

        return applicationService.getBookedPeriods(userId, roomId);
    }

    /**
     * 다른 방 추천. 후보 호실 중 고른 기간에 예약이 없는 호실을 돌려준다.
     * 예) GET /contract-service/apply/rooms/available?roomIds=41,42,57&moveInDate=2026-12-01&months=6
     *
     * @param userId     X-User 헤더 (선택). 있으면 본인의 작성 중 신청은 예약에서 뺀다
     * @param roomIds    후보 호실 ID 목록 (화면이 매물 서비스에서 받은 순서)
     * @param moveInDate 입주일 (yyyy-MM-dd)
     * @param months     계약 개월 수
     */
    @GetMapping("/available")
    public AvailableRoomsResponse getAvailableRooms(
            @RequestHeader(value = USER_HEADER, required = false) Long userId,
            @RequestParam(value = "roomIds", required = false) List<Long> roomIds,
            // required=false: 빠지면 서비스의 null 검사가 400으로 안내한다 (필수로 두면 Missing...Exception → 500)
            @RequestParam(value = "moveInDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate moveInDate,
            @RequestParam(value = "months", required = false) Integer months) {

        return roomAvailabilityService.findAvailable(userId, roomIds, moveInDate, months);
    }
}
