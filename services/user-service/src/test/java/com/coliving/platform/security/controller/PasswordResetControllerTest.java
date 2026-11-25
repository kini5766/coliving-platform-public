package com.coliving.platform.security.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetControllerTest {

    @Test
    void usesTheFirstForwardedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.8");

        assertThat(PasswordResetController.clientIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void fallsBackToTheRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");

        assertThat(PasswordResetController.clientIp(request)).isEqualTo("10.1.2.3");
    }
}
