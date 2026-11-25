
package com.coliving.reservation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class ResourceImageService {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path uploadDirectory;
    private final long maxSize;

    public ResourceImageService(
            @Value("${resource.image.upload-dir}") String uploadDir,
            @Value("${resource.image.max-size}") long maxSize) {

        this.uploadDirectory = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();

        this.maxSize = maxSize;
    }

    // 1. 이미지 파일 저장
    public String upload(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 파일을 선택해 주세요."
            );
        }

        if (file.getSize() > maxSize) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지는 5MB 이하만 업로드할 수 있습니다."
            );
        }

        String extension = ALLOWED_TYPES.get(file.getContentType());

        if (extension == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "JPG, PNG, WebP 이미지만 업로드할 수 있습니다."
            );
        }

        try {
            // 파일 내용으로 이미지 형식 추가 확인
            byte[] header;

            try (InputStream input = file.getInputStream()) {
                header = input.readNBytes(12);
            }

            if (!isValidImageHeader(header, extension)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "올바른 이미지 파일이 아닙니다."
                );
            }

            Files.createDirectories(uploadDirectory);

            // 원본 파일명 대신 UUID 사용
            String fileName = UUID.randomUUID() + extension;

            Path destination = uploadDirectory
                    .resolve(fileName)
                    .normalize();

            try (InputStream input = file.getInputStream()) {
                Files.copy(
                        input,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            // 기존 imageUrl 필드에 저장할 주소
            return "/reservation-service/images/resources/" + fileName;

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 저장 중 오류가 발생했습니다.",
                    e
            );
        }
    }

    // 2. 이미지 파일 조회
    public Path getImagePath(String fileName) {

        if (!fileName.matches(
                "[0-9a-fA-F-]{36}\\.(jpg|png|webp)"
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "잘못된 이미지 파일명입니다."
            );
        }

        Path imagePath = uploadDirectory
                .resolve(fileName)
                .normalize();

        if (!imagePath.startsWith(uploadDirectory)
                || !Files.isRegularFile(imagePath)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "이미지를 찾을 수 없습니다."
            );
        }

        return imagePath;
    }

    // 3. 파일 헤더 검사
    private boolean isValidImageHeader(
            byte[] header,
            String extension) {

        if (".jpg".equals(extension)) {
            return header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
        }

        if (".png".equals(extension)) {
            byte[] signature = {
                    (byte) 0x89, 0x50, 0x4E, 0x47,
                    0x0D, 0x0A, 0x1A, 0x0A
            };

            if (header.length < signature.length) {
                return false;
            }

            for (int i = 0; i < signature.length; i++) {
                if (header[i] != signature[i]) {
                    return false;
                }
            }

            return true;
        }

        if (".webp".equals(extension)) {
            return header.length >= 12
                    && header[0] == 'R'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == 'F'
                    && header[8] == 'W'
                    && header[9] == 'E'
                    && header[10] == 'B'
                    && header[11] == 'P';
        }

        return false;
    }
}
