package com.coliving.platform.property.controller;

import com.coliving.platform.property.dto.PropertyDtos.*;
import com.coliving.platform.property.service.PropertyAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// 관리자 API. 게이트웨이가 admin 경로에 ADMIN 권한을 요구하고, /property-service 를 제거해 /admin/** 로 전달한다.
@RestController
@RequestMapping("/admin")
public class PropertyAdminController {

    private final PropertyAdminService service;

    public PropertyAdminController(PropertyAdminService service) {
        this.service = service;
    }

    @GetMapping("/buildings")
    public PageResponse<AdminBuildingSummary> buildings(@RequestParam(name = "page", defaultValue = "0") int page,
                                                        @RequestParam(name = "size", defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/buildings/{id}")
    public AdminBuildingDetail building(@PathVariable("id") Long id) {
        return service.getBuilding(id);
    }

    @PostMapping("/buildings")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse createBuilding(@Valid @RequestBody BuildingSaveRequest req) {
        return service.createBuilding(req);
    }

    @PutMapping("/buildings/{id}")
    public void updateBuilding(@PathVariable("id") Long id, @Valid @RequestBody BuildingSaveRequest req) {
        service.updateBuilding(id, req);
    }

    @DeleteMapping("/buildings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBuilding(@PathVariable("id") Long id) {
        service.deleteBuilding(id);
    }

    @PutMapping("/buildings/{id}/contract-forms")
    public void saveContractForms(@PathVariable("id") Long id, @Valid @RequestBody ContractFormsRequest req) {
        service.saveContractForms(id, req);
    }

    /** 대표 이미지 업로드(교체): multipart/form-data, 필드 이름 file */
    @PostMapping(value = "/buildings/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageResponse uploadBuildingImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.uploadBuildingImage(id, file);
    }

    @DeleteMapping("/buildings/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBuildingImage(@PathVariable Long id) {
        service.deleteBuildingImage(id);
    }

    @GetMapping("/rooms/{id}")
    public AdminRoomDetail room(@PathVariable("id") Long id) {
        return service.getRoom(id);
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse createRoom(@Valid @RequestBody RoomSaveRequest req) {
        return service.createRoom(req);
    }

    @PutMapping("/rooms/{id}")
    public void updateRoom(@PathVariable("id") Long id, @Valid @RequestBody RoomSaveRequest req) {
        service.updateRoom(id, req);
    }

    @DeleteMapping("/rooms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(@PathVariable("id") Long id) {
        service.deleteRoom(id);
    }
}