package com.coliving.platform.user.entity;

import com.coliving.platform.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 이메일 로그인 계정. 회원과 1:1 이고 이메일은 유일하다.
 *
 * @author 조휘재
 * 작성일: 2026-10-28
 */
@ToString
@Entity
@Table(
        name = "user_email_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_email_accounts_email", columnNames = "email")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmailAccountEntity extends BaseTimeEntity {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false)
    private String email;

    @ToString.Exclude
    @Column(nullable = false)
    private String password;

    /** 인코딩된 비밀번호로 바꾼다. */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
