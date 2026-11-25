package com.coliving.platform.user.entity;

import com.coliving.platform.common.entity.BaseTimeEntity;
import com.coliving.platform.user.crypto.PersonalDataConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계약서 자동완성. 회원 1명당 한 행이다.
 * users 를 조회할 때 이 행은 읽지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-11-01
 */
@Getter
@Entity
@Table(name = "contract_autocomplete")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractAutocompleteEntity extends BaseTimeEntity {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Convert(converter = PersonalDataConverter.class)
    @Column(columnDefinition = "text")
    private String email;

    @Convert(converter = PersonalDataConverter.class)
    @Column(columnDefinition = "text")
    private String name;

    @Convert(converter = PersonalDataConverter.class)
    @Column(columnDefinition = "text")
    private String phone;

    @Convert(converter = PersonalDataConverter.class)
    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "contract_language", length = 10)
    private String contractLanguage;

    /** 회원에 연결되는 빈 행. */
    public ContractAutocompleteEntity(UserEntity user) {
        this.user = user;
    }

    /** null 이 아닌 인자만 기존 값 위에 덮어쓴다. */
    public void fill(String email, String name, String phone, String address, String contractLanguage) {
        if (email != null) {
            this.email = email;
        }
        if (name != null) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (address != null) {
            this.address = address;
        }
        if (contractLanguage != null) {
            this.contractLanguage = contractLanguage;
        }
    }

    /** 인자를 그대로 넣는다. null 이면 그 칸을 비운다. */
    public void replace(String email, String name, String phone, String address, String contractLanguage) {
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.contractLanguage = contractLanguage;
    }
}
