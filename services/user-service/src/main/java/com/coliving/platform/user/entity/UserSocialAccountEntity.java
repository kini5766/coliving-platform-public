package com.coliving.platform.user.entity;

import com.coliving.platform.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 소셜 로그인 연결. id 는 NAVER_{providerId} 또는 GOOGLE_{sub} 이다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@ToString
@Entity
@Table(name = "user_social_accounts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSocialAccountEntity extends BaseTimeEntity {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialProviderType providerType;

    @Column(nullable = false)
    private String providerId;
}
