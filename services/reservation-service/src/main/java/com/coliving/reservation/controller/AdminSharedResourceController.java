
package com.coliving.reservation.controller;

import com.coliving.reservation.entity.SharedResource;
import com.coliving.reservation.service.ResourceImageService;
import com.coliving.reservation.service.SharedResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/reservation-service/admin/resources")
@RequiredArgsConstructor
public class AdminSharedResourceController {

    private final SharedResourceService sharedResourceService;
    private final ResourceImageService resourceImageService;

    // 1. 관리자 공용 자원 등록
    @PostMapping
    public SharedResource createResource(
            @RequestBody SharedResource sharedResource) {

        return sharedResourceService.createResource(sharedResource);
    }

    // 2. 관리자 공용 자원 수정
    @PatchMapping("/{id}")
    public SharedResource updateResource(
            @PathVariable Long id,
            @RequestBody SharedResource updatedResource) {

        return sharedResourceService.updateResource(id, updatedResource);
    }

    // 3. 관리자 공용 자원 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(
            @PathVariable Long id) {

        try {
            sharedResourceService.deleteResource(id);

            return ResponseEntity.noContent().build();

        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );
        }
    }

    // 4. 관리자 공용 자원 이미지 파일 업로드
    @PostMapping(
            value = "/upload-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Map<String, String> uploadImage(
            @RequestParam("file") MultipartFile file) {

        String imageUrl = resourceImageService.upload(file);

        return Map.of("imageUrl", imageUrl);
    }
}
