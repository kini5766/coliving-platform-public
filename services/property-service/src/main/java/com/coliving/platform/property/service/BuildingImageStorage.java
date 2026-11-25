package com.coliving.platform.property.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 건물 대표 이미지 파일 저장소 (서버 로컬 폴더).
 * 파일 이름은 UUID 로 새로 만들어 겹치거나 경로를 조작할 수 없게 한다.
 * 저장 위치: property.image.dir (application.yaml)
 * 공개 URL: property.image.public-path + "/" + 파일명 (기본 /property-service/property/images)
 */
@Component
public class BuildingImageStorage {

    public static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private static final Map<String, MediaType> TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "webp", MediaType.parseMediaType("image/webp"),
            "gif", MediaType.IMAGE_GIF);

    private static final Pattern FILE_NAME = Pattern.compile("^[0-9a-f\\-]{36}\\.(jpg|png|webp|gif)$");

    private static final byte[] PNG_HEADER = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF87_HEADER = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF89_HEADER = "GIF89a".getBytes(StandardCharsets.US_ASCII);

    private final Path dir;
    private final String publicPath;

    public BuildingImageStorage(@Value("${property.image.dir}") String dir,
                                @Value("${property.image.public-path:/property-service/property/images}") String publicPath) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
        this.publicPath = publicPath.endsWith("/") ? publicPath.substring(0, publicPath.length() - 1) : publicPath;
    }

    /** 파일을 저장하고 공개 URL 을 돌려준다. Content-Type 과 파일 앞바이트가 같은 형식일 때만 저장한다. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "이미지는 5MB 이하만 올릴 수 있습니다.");
        }
        String ext = EXTENSIONS.get(file.getContentType() == null ? "" : file.getContentType().toLowerCase());
        if (ext == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "jpg, png, webp, gif 이미지만 올릴 수 있습니다.");
        }
        byte[] header = readHeader(file);
        if (!hasImageHeader(header, ext)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "올바른 이미지 파일이 아닙니다.");
        }
        String fileName = UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 저장하지 못했습니다.", e);
        }
        return publicPath + "/" + fileName;
    }

    /** 형식 판별에 필요한 앞 12바이트를 읽는다. */
    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 저장하지 못했습니다.", e);
        }
    }

    /** Content-Type 으로 정한 확장자와 파일 앞바이트가 일치하는지 확인한다. */
    private static boolean hasImageHeader(byte[] header, String ext) {
        return switch (ext) {
            case "jpg" -> header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
            case "png" -> startsWith(header, PNG_HEADER);
            case "gif" -> startsWith(header, GIF87_HEADER) || startsWith(header, GIF89_HEADER);
            case "webp" -> header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }

    /** 헤더가 시그니처로 시작하는지 확인한다. */
    private static boolean startsWith(byte[] header, byte[] signature) {
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

    /** 공개 URL 로 저장된 파일을 지운다. 없거나 형식이 다르면 아무것도 하지 않는다. */
    public void deleteByUrl(String url) {
        if (url == null || !url.startsWith(publicPath + "/")) return;
        resolve(url.substring(publicPath.length() + 1)).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // 파일 삭제 실패는 건물 저장을 막지 않는다.
            }
        });
    }

    /** 파일명이 올바르고 실제 파일이 있으면 그 경로. */
    public Optional<Path> resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) return Optional.empty();
        Path path = dir.resolve(fileName).normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) return Optional.empty();
        return Optional.of(path);
    }

    public MediaType mediaType(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
        return TYPES.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
    }
}