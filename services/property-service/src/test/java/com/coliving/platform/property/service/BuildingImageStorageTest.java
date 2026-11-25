package com.coliving.platform.property.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildingImageStorageTest {

    @TempDir
    Path dir;

    @Test
    void storesJpegWhenHeaderMatchesContentType() {
        BuildingImageStorage storage = storage();
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        String url = storage.store(file("image/jpeg", jpeg));

        String fileName = url.substring(url.lastIndexOf('/') + 1);
        assertThat(url).startsWith("/property-service/property/images/");
        assertThat(fileName).endsWith(".jpg");
        assertThat(storage.resolve(fileName)).isPresent();
    }

    @Test
    void storesPngGifAndWebpWhenHeadersMatch() {
        BuildingImageStorage storage = storage();

        assertThat(storage.store(file("image/png", png()))).endsWith(".png");
        assertThat(storage.store(file("image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII)))).endsWith(".gif");
        assertThat(storage.store(file("image/webp", webp()))).endsWith(".webp");
    }

    @Test
    void rejectsFileWhoseHeaderDoesNotMatchContentType() {
        BuildingImageStorage storage = storage();
        MockMultipartFile file = file("image/png", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(dir).isEmptyDirectory();
    }

    private BuildingImageStorage storage() {
        return new BuildingImageStorage(dir.toString(), "/property-service/property/images");
    }

    private static MockMultipartFile file(String contentType, byte[] body) {
        return new MockMultipartFile("file", "photo", contentType, body);
    }

    private static byte[] png() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private static byte[] webp() {
        return new byte[] {
                'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'
        };
    }
}
