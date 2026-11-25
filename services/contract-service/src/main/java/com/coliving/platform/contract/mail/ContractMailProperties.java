package com.coliving.platform.contract.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.mail.*
 *   enabled       : true면 Gmail SMTP로 실제 발송, false면 로그만 (로컬 개발 기본값)
 *   from          : 보내는 주소 (Gmail은 로그인 계정과 같아야 함)
 *   from-name     : 받는 사람에게 보이는 이름
 *   link-base-url : 메일 속 버튼이 여는 사용자 화면 주소
 *
 * @author 한성현
 */
@ConfigurationProperties(prefix = "app.mail")
public record ContractMailProperties(boolean enabled, String from, String fromName, String linkBaseUrl) {

    public ContractMailProperties {
        if (fromName == null || fromName.isBlank()) fromName = "Coliving";
        if (linkBaseUrl == null || linkBaseUrl.isBlank()) linkBaseUrl = "http://localhost:5173";
        if (linkBaseUrl.endsWith("/")) linkBaseUrl = linkBaseUrl.substring(0, linkBaseUrl.length() - 1);
    }

    /** 내 신청 화면 주소 */
    public String myApplicationUrl() {
        return linkBaseUrl + "/my/application";
    }
}
