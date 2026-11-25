package com.coliving.platform.user.service;

import com.coliving.platform.user.entity.ContractAutocompleteEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.ContractAutocompleteRepository;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약서 자동완성만 다룬다.
 * 회원 조회 경로에서는 호출하지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-11-01
 */
@Service
public class ContractAutocompleteService {

    private static final int CONTRACT_LANGUAGE_MAX = 10;

    private final ContractAutocompleteRepository repository;
    private final EntityManager entityManager;

    /** 자동완성 저장소와, 아직 영속화되지 않은 회원 참조용 EntityManager. */
    public ContractAutocompleteService(ContractAutocompleteRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    /** 비어 있지 않은 값만 채운다. 전부 비면 행을 만들지 않는다. */
    @Transactional
    public void save(UserEntity user, String email, String name, String phone, String address, String contractLanguage) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = normalizeText(name);
        String normalizedPhone = normalizeText(phone);
        String normalizedAddress = normalizeText(address);
        String normalizedLanguage = normalizeLanguage(contractLanguage);
        if (normalizedEmail == null
                && normalizedName == null
                && normalizedPhone == null
                && normalizedAddress == null
                && normalizedLanguage == null) {
            return;
        }
        if (user.getId() == null) {
            throw new IllegalStateException("회원을 먼저 저장해야 합니다.");
        }
        Long userId = user.getId();
        ContractAutocompleteEntity row = repository.findById(userId)
                .orElseGet(() -> new ContractAutocompleteEntity(
                        entityManager.getReference(UserEntity.class, userId)));
        row.fill(normalizedEmail, normalizedName, normalizedPhone, normalizedAddress, normalizedLanguage);
        repository.save(row);
    }

    /** 값을 통째로 바꾼다. 전부 비면 행을 지운다. 이메일이 형식에 맞지 않으면 거부한다. */
    @Transactional
    public Optional<ContractAutocompleteEntity> replace(
            Long userId,
            String email,
            String name,
            String phone,
            String address,
            String contractLanguage
    ) {
        if (userId == null) {
            throw new IllegalStateException("회원을 먼저 저장해야 합니다.");
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && !isEmail(normalizedEmail)) {
            throw new IllegalArgumentException("이메일 형식이 아닙니다.");
        }
        String normalizedName = normalizeText(name);
        String normalizedPhone = normalizeText(phone);
        String normalizedAddress = normalizeText(address);
        String normalizedLanguage = normalizeLanguage(contractLanguage);
        if (normalizedEmail == null
                && normalizedName == null
                && normalizedPhone == null
                && normalizedAddress == null
                && normalizedLanguage == null) {
            repository.deleteById(userId);
            return Optional.empty();
        }
        ContractAutocompleteEntity row = repository.findById(userId)
                .orElseGet(() -> new ContractAutocompleteEntity(
                        entityManager.getReference(UserEntity.class, userId)));
        row.replace(normalizedEmail, normalizedName, normalizedPhone, normalizedAddress, normalizedLanguage);
        return Optional.of(repository.save(row));
    }

    /**
     * 계약서 자동완성 조회.
     * 감사 로그는 이 메서드에 추가한다. 평문은 기록하지 않고 회원 id 만 남긴다.
     */
    @Transactional(readOnly = true)
    public Optional<ContractAutocompleteEntity> read(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return repository.findById(userId);
    }

    /** 이메일을 소문자로 맞추고, 빈 값은 null. */
    private static String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    /** 앞뒤 공백을 빼고, 빈 값은 null. */
    private static String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    /** @ 와 도메인 점이 있는지만 본다. */
    private static boolean isEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 && email.indexOf('.', at + 1) > at + 1 && !email.contains(" ");
    }

    /** 계약 언어. 10자를 넘기면 거부하고, 빈 값은 null. */
    private static String normalizeLanguage(String raw) {
        String value = normalizeText(raw);
        if (value == null) {
            return null;
        }
        if (value.length() > CONTRACT_LANGUAGE_MAX) {
            throw new IllegalArgumentException("계약 언어는 10자를 넘길 수 없습니다.");
        }
        return value;
    }
}
