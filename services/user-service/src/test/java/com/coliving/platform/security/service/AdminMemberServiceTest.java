package com.coliving.platform.security.service;

import com.coliving.platform.security.dto.AdminMemberResponse;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import com.coliving.platform.user.repository.SocialAccountRepository;
import com.coliving.platform.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminMemberServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final EmailAccountRepository accounts = mock(EmailAccountRepository.class);
    private final SocialAccountRepository social = mock(SocialAccountRepository.class);
    private final AdminMemberService service = new AdminMemberService(users, accounts, social);

    @Test
    void unverifiedSignupsAreNotListedAndWithdrawnUsersStay() {
        UserEntity active = UserEntity.builder().id(1L).enabled(true).nickname("민수").build();
        UserEntity withdrawn = UserEntity.builder().id(2L).enabled(false).build();
        UserEntity unverified = UserEntity.builder().id(3L).enabled(false).build();
        when(users.findAllByOrderByIdDesc()).thenReturn(List.of(unverified, withdrawn, active));
        when(accounts.findAll()).thenReturn(List.of(email(1L, "active@test.com"), email(3L, "wait@test.com")));
        when(social.findUserIdsAndProviders()).thenReturn(List.of());

        List<AdminMemberResponse> members = service.list();

        assertThat(members).extracting(AdminMemberResponse::id).containsExactly(2L, 1L);
        assertThat(members.get(0).enabled()).isFalse();
        assertThat(members.get(1).email()).isEqualTo("active@test.com");
    }

    private static UserEmailAccountEntity email(long userId, String address) {
        UserEntity user = UserEntity.builder().id(userId).enabled(true).build();
        return UserEmailAccountEntity.builder().userId(userId).user(user).email(address).password("{noop}x").build();
    }
}
