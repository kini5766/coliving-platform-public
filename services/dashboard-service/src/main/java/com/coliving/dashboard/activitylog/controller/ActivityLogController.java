package com.coliving.dashboard.activitylog.controller;

import com.coliving.dashboard.activitylog.dto.ActivityLogPageResponse;
import com.coliving.dashboard.activitylog.dto.ActivityLogResponse;
import com.coliving.dashboard.activitylog.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 대시보드 화면의 "최근 활동 로그" 영역에 데이터를 내려주는 API. */
@RestController
@RequestMapping("/admin/activity-logs")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    /** 건물별 최근 활동 로그 조회. */
    @GetMapping
    public List<ActivityLogResponse> recent(@RequestParam("buildingId") Long buildingId,
                                            @RequestParam(defaultValue = "10", name = "size") int size) {
        return activityLogService.getRecent(buildingId, size);
    }

    /** "더보기" 팝업용 활동 로그 조회. 건물·유형별로 한 페이지씩 내려준다. */
    @GetMapping("/history")
    public ActivityLogPageResponse history(@RequestParam("buildingId") Long buildingId,
                                           @RequestParam(name = "type", required = false) String type,
                                           @RequestParam(defaultValue = "0", name = "page") int page,
                                           @RequestParam(defaultValue = "20", name = "size") int size) {
        return activityLogService.getHistory(buildingId, type, page, size);
    }
}