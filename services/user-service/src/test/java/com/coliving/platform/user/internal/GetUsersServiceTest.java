package com.coliving.platform.user.internal;

import com.coliving.platform.grpc.user.GetUsersRequest;
import com.coliving.platform.grpc.user.SearchUsersRequest;
import com.coliving.platform.grpc.user.UserSummary;
import com.coliving.platform.user.entity.Role;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetUsersServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final GetUsersService service = new GetUsersService(userRepository);

    @Test
    void returnsFoundUsersInRequestOrderWithAccountRoles() {
        when(userRepository.findAllById(List.of(5001L, 5003L, 6001L, 404L))).thenReturn(List.of(
                user(6001L, "김예린", Role.CANDIDATE, Role.USER),
                user(5003L, "이도윤", Role.RESIDENT, Role.CANDIDATE, Role.USER),
                user(5001L, "김민서", Role.RESIDENT, Role.USER)
        ));

        List<UserSummary> users = service.getUsers(GetUsersRequest.newBuilder()
                .addAllUserIds(List.of(5001L, 5003L, 6001L, 404L))
                .build()).getUsersList();

        assertThat(users).extracting(UserSummary::getUserId).containsExactly(5001L, 5003L, 6001L);
        assertThat(users.get(0).getName()).isEqualTo("김민서");
        assertThat(users.get(0).getRolesList()).containsExactly("USER", "RESIDENT");
        assertThat(users.get(1).getRolesList()).containsExactly("USER", "CANDIDATE", "RESIDENT");
        assertThat(users.get(2).getRolesList()).containsExactly("USER", "CANDIDATE");
        assertThat(users.get(0).hasProfileImageUrl()).isFalse();
    }

    @Test
    void rejectsMoreThanOneHundredIds() {
        GetUsersRequest.Builder request = GetUsersRequest.newBuilder();
        for (int i = 1; i <= 101; i++) {
            request.addUserIds(i);
        }

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.getUsers(request.build()));
    }

    @Test
    void searchUsersUsesDefaultSizeWhenSizeIsZero() {
        when(userRepository.searchByNickname(eq("김민"), any(Pageable.class))).thenReturn(List.of(
                user(5001L, "김민서", Role.USER, Role.RESIDENT)
        ));

        List<UserSummary> users = service.searchUsers(SearchUsersRequest.newBuilder()
                .setKeyword("김민")
                .build()).getUsersList();

        assertThat(users).extracting(UserSummary::getUserId).containsExactly(5001L);
        org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchByNickname(eq("김민"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(GetUsersService.DEFAULT_SEARCH_SIZE);
    }

    @Test
    void searchUsersFiltersByRole() {
        when(userRepository.searchByNicknameAndRole(eq("이도"), eq(Role.RESIDENT), any(Pageable.class))).thenReturn(List.of(
                user(5003L, "이도윤", Role.RESIDENT, Role.USER)
        ));

        List<UserSummary> users = service.searchUsers(SearchUsersRequest.newBuilder()
                .setKeyword("이도")
                .setRole("RESIDENT")
                .setSize(5)
                .build()).getUsersList();

        assertThat(users).extracting(UserSummary::getName).containsExactly("이도윤");
        assertThat(users.get(0).getRolesList()).containsExactly("USER", "RESIDENT");
    }

    @Test
    void searchUsersRejectsShortKeywordAndUnknownRole() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.searchUsers(SearchUsersRequest.newBuilder().setKeyword("김").build()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.searchUsers(SearchUsersRequest.newBuilder().setKeyword("관리").setRole("ADMIN").build()));
    }

    private static UserEntity user(long id, String nickname, Role... roles) {
        return UserEntity.builder()
                .id(id)
                .nickname(nickname)
                .roles(Set.of(roles))
                .build();
    }
}
