package com.coliving.platform.security.service;

import com.coliving.platform.security.dto.AdminMemberResponse;
import com.coliving.platform.user.entity.SocialProviderType;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import com.coliving.platform.user.repository.SocialAccountRepository;
import com.coliving.platform.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 목록. 확인 전 가입은 빼고 탈퇴는 남긴다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final UserRepository userRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final SocialAccountRepository socialAccountRepository;

    /**
     * 확인 전 가입은 이메일이 남은 비활성 계정이다. 회원 목록에 넣지 않는다.
     * 탈퇴는 이메일을 지우므로 비활성으로 남긴다.
     */
    @Transactional(readOnly = true)
    public List<AdminMemberResponse> list() {
        Map<Long, String> emails = new HashMap<>();
        for (UserEmailAccountEntity account : emailAccountRepository.findAll()) {
            emails.put(account.getUserId(), account.getEmail());
        }
        Map<Long, List<String>> providers = new HashMap<>();
        for (Object[] row : socialAccountRepository.findUserIdsAndProviders()) {
            Long userId = (Long) row[0];
            SocialProviderType provider = (SocialProviderType) row[1];
            providers.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(provider.name());
        }
        return userRepository.findAllByOrderByIdDesc().stream()
                .filter(user -> user.isEnabled() || !emails.containsKey(user.getId()))
                .map(user -> toResponse(user, emails, providers))
                .toList();
    }

    /** 역할과 소셜 제공자를 이름순으로 담는다. 이메일이 없으면 null 이다. */
    private static AdminMemberResponse toResponse(
            UserEntity user, Map<Long, String> emails, Map<Long, List<String>> providers) {
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted(Comparator.naturalOrder()).toList();
        List<String> social = providers.getOrDefault(user.getId(), List.of()).stream().sorted().toList();
        return new AdminMemberResponse(
                user.getId(),
                user.getNickname(),
                emails.get(user.getId()),
                roles,
                social,
                user.isEnabled(),
                user.getCreatedAt());
    }
}
