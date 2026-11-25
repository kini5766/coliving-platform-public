package com.coliving.platform.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * 응답 본문을 버퍼에 모은 뒤 Content-Length 를 붙여 게이트웨이로 보낸다.
 * 청크 본문은 게이트웨이 프록시가 끝나지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-11-01
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContentLengthResponseFilter extends OncePerRequestFilter {

    /** 체인 뒤 버퍼에 모인 본문을 Content-Length 와 함께 복사한다. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);
        // A chunked body without Content-Length never finishes when the gateway proxies it.
        wrapper.copyBodyToResponse();
    }
}
