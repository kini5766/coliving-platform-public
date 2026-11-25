package com.coliving.platform.user.repository;

import com.coliving.platform.user.entity.UserEmailAccountEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 이메일 로그인 계정.
 *
 * @author 조휘재
 * 작성일: 2026-10-28
 */
@Repository
public interface EmailAccountRepository extends JpaRepository<UserEmailAccountEntity, Long> {
    /** 이메일로 계정을 찾는다. 이메일은 저장 전에 소문자로 맞춘다. */
    @EntityGraph(attributePaths = "user")
    Optional<UserEmailAccountEntity> findByEmail(String email);

    /** 같은 이메일이 있으면 true. */
    boolean existsByEmail(String email);

    /** 확인 전 가입. 탈퇴는 이메일 행이 없으므로 여기 나오지 않는다. */
    @Query("""
            select account from UserEmailAccountEntity account
            join fetch account.user user
            where user.enabled = false
              and (user.createdAt is null or user.createdAt < :cutoff)
            """)
    List<UserEmailAccountEntity> findUnverifiedCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
