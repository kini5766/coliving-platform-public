package com.coliving.platform.user.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * contract_autocomplete 의 email, name, phone, address 를 Envelope Encryption 으로 저장하고 읽는다.
 * autoApply 가 아니므로 nickname, 계약 언어, user_email_accounts.email 은 평문으로 남는다.
 *
 * @author 조휘재
 * 작성일: 2026-10-30
 */
@Component
@Converter(autoApply = false)
public class PersonalDataConverter implements AttributeConverter<String, String> {

    private final EnvelopeCipher cipher;

    /** 엔벨로프 암호를 컬럼 변환에 쓴다. */
    public PersonalDataConverter(EnvelopeCipher cipher) {
        this.cipher = cipher;
    }

    /** 저장 전에 암호화한다. null 은 null. */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    /** 읽을 때 복호화한다. 접두사 없는 값은 평문 그대로다. */
    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
