package com.coliving.platform.user.entity;

import com.coliving.platform.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.*;

/**
 * 회원. 로그인 식별자와 계약 자동완성은 다른 테이블에 있다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@Builder
@ToString
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 커뮤니티와 헤더에 보여주는 이름. 비어 있을 수 있다.
    // 로그인 식별자는 user_email_accounts.email 이다.
    // 계약서 자동완성 값은 contract_autocomplete 에만 있고, 회원 조회에 싣지 않는다.
    @Column(length = 100)
    private String nickname;

    // 활성화 여부 (비활성화 / 회원탈퇴)
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** 탈퇴. 로그인을 막고 닉네임을 지운다. 행과 역할은 남긴다. */
    public void withdraw() {
        this.enabled = false;
        this.nickname = null;
    }

    /** 로그인을 다시 허용한다. */
    public void activate() {
        this.enabled = true;
    }

    /** 커뮤니티에 보이는 이름을 바꾼다. null 이면 비운다. */
    public void rename(String nickname) {
        this.nickname = nickname;
    }

    // 계정 잠금 여부
    @Column(nullable = false)
    @Builder.Default
    private boolean accountNonLocked = true;

    // 계정 역할. GUEST는 비로그인 방문자라 저장하지 않는다.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
