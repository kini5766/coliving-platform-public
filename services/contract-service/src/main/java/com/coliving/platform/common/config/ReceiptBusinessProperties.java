package com.coliving.platform.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제 영수증 PDF 상단에 찍는 사업자 정보. 코드에 직접 쓰지 않고 설정으로 받는다.
 *
 * application.yaml (또는 config 저장소 contract-service.yaml)
 *   app:
 *     receipt:
 *       business:
 *         name: 상호
 *         business-number: 000-00-00000
 *         representative: 대표자
 *         address: 사업장 주소
 *
 * 값이 비어 있으면 영수증에는 "-"로 찍힌다 (서버는 정상 기동).
 *
 * @author 한성현
 */
@ConfigurationProperties(prefix = "app.receipt.business")
public record ReceiptBusinessProperties(String name, String businessNumber, String representative, String address) {

    public ReceiptBusinessProperties {
        name = clean(name);
        businessNumber = clean(businessNumber);
        representative = clean(representative);
        address = clean(address);
    }

    private static String clean(String v) {
        return v == null ? "" : v.strip();
    }
}
