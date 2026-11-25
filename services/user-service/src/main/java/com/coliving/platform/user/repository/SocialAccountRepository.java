package com.coliving.platform.user.repository;

import com.coliving.platform.user.entity.SocialProviderType;
import com.coliving.platform.user.entity.UserSocialAccountEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 소셜 로그인 연결.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@Repository
public interface SocialAccountRepository extends JpaRepository<UserSocialAccountEntity, String> {

    /** 그 회원의 소셜 연결을 모두 지운다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from UserSocialAccountEntity account where account.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** 회원 번호와 소셜 제공자. 관리자 목록에서 제공자 이름을 붙인다. */
    @Query("select account.user.id, account.providerType from UserSocialAccountEntity account")
    List<Object[]> findUserIdsAndProviders();
}
