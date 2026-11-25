
package com.coliving.reservation.controller;

import com.coliving.reservation.service.ResourceImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/reservation-service/images/resources")
@RequiredArgsConstructor
public class ResourceImageController {

    private final ResourceImageService resourceImageService;

    // 업로드된 공용 자원 이미지 조회
    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> getImage(
            @PathVariable String fileName) {

        Path imagePath = resourceImageService.getImagePath(fileName);

        Resource image = new FileSystemResource(imagePath);

        String lowerName = fileName.toLowerCase();

        MediaType mediaType;

        if (lowerName.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (lowerName.endsWith(".webp")) {
            mediaType = MediaType.parseMediaType("image/webp");
        } else {
            mediaType = MediaType.IMAGE_JPEG;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(
                        CacheControl.maxAge(1, TimeUnit.DAYS)
                )
                .body(image);
    }
}
