package com.coliving.platform.user.internal;

import com.coliving.platform.grpc.user.GetUsersRequest;
import com.coliving.platform.grpc.user.GetUsersResponse;
import com.coliving.platform.grpc.user.SearchUsersRequest;
import com.coliving.platform.grpc.user.SearchUsersResponse;
import com.coliving.platform.grpc.user.UserSummary;
import com.coliving.platform.user.entity.Role;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * gRPC 회원 조회.
 * 응답 이름은 users.nickname 이고, 계약 자동완성은 싣지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-11-03
 */
@Service
@RequiredArgsConstructor
public class GetUsersService {

    public static final int MAX_IDS = 100;
    public static final int DEFAULT_SEARCH_SIZE = 10;

    private static final Set<Role> SEARCH_ROLES = Set.of(Role.USER, Role.CANDIDATE, Role.RESIDENT);

    private final UserRepository userRepository;

    /** 요청 순서를 유지하고, 없는 번호는 빼서 돌려준다. */
    public GetUsersResponse getUsers(GetUsersRequest request) {
        List<Long> ids = normalize(request.getUserIdsList());
        Map<Long, UserEntity> found = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        GetUsersResponse.Builder response = GetUsersResponse.newBuilder();
        for (Long id : ids) {
            UserEntity user = found.get(id);
            if (user != null) {
                response.addUsers(toUser(user));
            }
        }
        return response.build();
    }

    /** 닉네임 부분 일치. 키워드는 2자 이상이고, size 0 이면 10건이다. */
    public SearchUsersResponse searchUsers(SearchUsersRequest request) {
        String keyword = request.getKeyword() == null ? "" : request.getKeyword().trim();
        if (keyword.length() < 2) {
            throw new IllegalArgumentException("keyword는 2자 이상입니다.");
        }
        int size = request.getSize();
        if (size < 0 || size > MAX_IDS) {
            throw new IllegalArgumentException("size는 0부터 100까지입니다.");
        }
        if (size == 0) {
            size = DEFAULT_SEARCH_SIZE;
        }
        Role role = searchRole(request);
        List<UserEntity> found = role == null
                ? userRepository.searchByNickname(keyword, PageRequest.of(0, size))
                : userRepository.searchByNicknameAndRole(keyword, role, PageRequest.of(0, size));
        SearchUsersResponse.Builder response = SearchUsersResponse.newBuilder();
        found.forEach(user -> response.addUsers(toUser(user)));
        return response.build();
    }

    /** 역할 필터. 없으면 전체이고, USER·CANDIDATE·RESIDENT 만 허용한다. */
    private static Role searchRole(SearchUsersRequest request) {
        if (!request.hasRole()) {
            return null;
        }
        String raw = request.getRole() == null ? "" : request.getRole().trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("role 형식이 아닙니다.");
        }
        Role role;
        try {
            role = Role.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("role 형식이 아닙니다.");
        }
        if (!SEARCH_ROLES.contains(role)) {
            throw new IllegalArgumentException("role 형식이 아닙니다.");
        }
        return role;
    }

    /** 중복을 빼고 양수 id 만 남긴다. 100개를 넘기면 거부한다. */
    private static List<Long> normalize(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("ids가 필요합니다.");
        }
        if (raw.size() > MAX_IDS) {
            throw new IllegalArgumentException("ids는 100개까지입니다.");
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("ids 형식이 아닙니다.");
            }
            seen.add(id);
        }
        return List.copyOf(seen);
    }

    /** 닉네임과 계정 역할을 요약으로 만든다. 닉네임이 없으면 빈 문자열이다. */
    private static UserSummary toUser(UserEntity user) {
        String nickname = user.getNickname();
        UserSummary.Builder builder = UserSummary.newBuilder()
                .setUserId(user.getId())
                .setName(nickname == null ? "" : nickname.trim());
        rolesOf(user).forEach(builder::addRoles);
        return builder.build();
    }

    /** GUEST 를 뺀 역할 이름. ordinal 순이다. */
    static List<String> rolesOf(UserEntity user) {
        Set<Role> roles = user.getRoles() == null ? Set.of() : user.getRoles();
        return roles.stream()
                .filter(role -> role != null && role.isAccountRole())
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .toList();
    }
}
