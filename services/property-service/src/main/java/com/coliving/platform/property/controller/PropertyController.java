package com.coliving.platform.property.controller;

import com.coliving.platform.property.dto.PropertyDtos.*;
import com.coliving.platform.property.service.PropertyQueryService;
import org.springframework.web.bind.annotation.*;

/** 사용자(비회원 포함) 조회 API. 게이트웨이가 /property-service 를 제거해 /property/** 로 전달 */
@RestController
@RequestMapping("/property")
public class PropertyController {

    private final PropertyQueryService service;

    public PropertyController(PropertyQueryService service) {
        this.service = service;
    }

    @GetMapping("/buildings")
    public PageResponse<BuildingSummary> search(@RequestParam(name = "keyword", required = false) String keyword,
                                                @RequestParam(name = "region", required = false) String region,
                                                @RequestParam(name = "minMonthlyFee", required = false) Long minMonthlyFee,
                                                @RequestParam(name = "maxMonthlyFee", required = false) Long maxMonthlyFee,
                                                @RequestParam(name = "maxDeposit", required = false) Long maxDeposit,
                                                @RequestParam(name = "roomType", required = false) String roomType,
                                                @RequestParam(name = "page", defaultValue = "0") int page,
                                                @RequestParam(name = "size", defaultValue = "12") int size) {
        return service.search(new BuildingSearchCondition(keyword, region, minMonthlyFee, maxMonthlyFee, maxDeposit,
                roomType), page, size);
    }

    @GetMapping("/buildings/{id}")
    public BuildingDetail building(@PathVariable("id") Long id) {
        return service.getBuilding(id);
    }

    @GetMapping("/rooms/{id}")
    public RoomDetail room(@PathVariable("id") Long id) {
        return service.getRoom(id);
    }
}
