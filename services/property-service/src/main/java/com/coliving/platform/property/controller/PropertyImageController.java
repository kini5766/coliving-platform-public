package com.coliving.platform.property.controller;

import com.coliving.platform.property.service.BuildingImageStorage;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 건물 대표 이미지 파일 보기 (비회원 포함). 게이트웨이: GET /property-service/property/images/{파일명} */
@RestController
@RequestMapping("/property")
public class PropertyImageController {

    private final BuildingImageStorage storage;

    public PropertyImageController(BuildingImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/images/{fileName}")
    public ResponseEntity<Resource> image(@PathVariable String fileName) {
        return storage.resolve(fileName)
                .<ResponseEntity<Resource>>map(path -> ResponseEntity.ok()
                        .contentType(storage.mediaType(fileName))
                        // 파일명이 매번 새로 만들어지므로 오래 캐시해도 된다.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}