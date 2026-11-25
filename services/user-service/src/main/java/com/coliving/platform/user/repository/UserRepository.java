package com.coliving.platform.user.repository;

import com.coliving.platform.user.entity.Role;
import com.coliving.platform.user.entity.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 회원. 닉네임 검색은 대소문자를 가리지 않고 id 오름차순이다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /** id가 큰 회원부터 모두 읽는다. */
    List<UserEntity> findAllByOrderByIdDesc();

    /** 닉네임 부분 일치. */
    @Query("""
            select u from UserEntity u
            where lower(u.nickname) like lower(concat('%', :keyword, '%'))
            order by u.id asc
            """)
    List<UserEntity> searchByNickname(@Param("keyword") String keyword, Pageable pageable);

    /** 닉네임 부분 일치와 역할 조건을 함께 적용한다. */
    @Query("""
            select u from UserEntity u
            where lower(u.nickname) like lower(concat('%', :keyword, '%'))
              and :role member of u.roles
            order by u.id asc
            """)
    List<UserEntity> searchByNicknameAndRole(@Param("keyword") String keyword, @Param("role") Role role, Pageable pageable);
}
