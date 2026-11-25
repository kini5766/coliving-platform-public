package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ContractFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 로컬 디스크 저장. 위치: app.storage.local-dir (기본 ./data/contract-files)
 * ⚠ 파드 디스크는 재시작하면 사라진다. 운영에서는 영구 볼륨을 이 경로에 붙이거나 오브젝트 스토리지 구현체로 바꿀 것
 *
 * @author 한성현
 */
@Component
public class LocalContractFileStorage implements ContractFileStorage {

    private final Path root;

    public LocalContractFileStorage(@Value("${app.storage.local-dir:./data/contract-files}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public void save(String path, byte[] content) {
        Path file = resolve(path);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new ContractFileException("파일 저장 실패: " + path, e);
        }
    }

    @Override
    public byte[] read(String path) {
        try {
            return Files.readAllBytes(resolve(path));
        } catch (IOException e) {
            throw new ContractFileException("파일 읽기 실패: " + path, e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException e) {
            throw new ContractFileException("파일 삭제 실패: " + path, e);
        }
    }

    // 저장 폴더 밖을 가리키는 경로는 거부한다
    private Path resolve(String path) {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root)) {
            throw new ContractFileException("잘못된 파일 경로: " + path, null);
        }
        return file;
    }
}
