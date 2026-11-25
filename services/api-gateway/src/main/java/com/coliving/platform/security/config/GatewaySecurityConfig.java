package com.coliving.platform.security.config;

import com.coliving.platform.common.config.AppProperties;
import com.coliving.platform.security.filter.SessionCookieBearerWebFilter;
import com.coliving.platform.security.filter.UserIdHeaderWebFilter;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
public class GatewaySecurityConfig {

    @Bean
    @LoadBalanced
    WebClient.Builder sessionWebClientBuilder() {
        return WebClient.builder().baseUrl("http://user-service");
    }

    @Bean
    SecurityWebFilterChain gatewaySecurity(
            ServerHttpSecurity http,
            @Qualifier("sessionWebClientBuilder") WebClient.Builder sessionWebClientBuilder,
            AppProperties appProperties) {
        SessionCookieBearerWebFilter sessionCookieBearerWebFilter =
                new SessionCookieBearerWebFilter(sessionWebClientBuilder.build());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsSource(appProperties)))
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(
                                "/user-service/user/login",
                                "/user-service/user/signup",
                                "/user-service/user/signup/email-verification",
                                "/user-service/user/signup/email-verification/resend",
                                "/user-service/user/password-reset",
                                "/user-service/user/password-reset/confirm",
                                "/user-service/user/oauth2/authorization/**",
                                "/user-service/user/oauth2/code/**",
                                "/user-service/user/logout",
                                "/community-service/guest-inquiries",
                                "/community-service/faq-bot/ask"
                        ).permitAll()

                        .pathMatchers(
                                HttpMethod.GET,
                                "/property-service/property/**"
                        ).permitAll()

                        // ==========================================
                        // QR 체크인 관리자 권한 검사
                        // ==========================================
                        .pathMatchers(
                                HttpMethod.POST,
                                "/reservation-service/reservations/check-in"
                        ).hasRole("ADMIN")

                        // ==========================================
                        // 공용 자원 예약 관리자 조회 권한 검사
                        // ==========================================
                        .pathMatchers(
                                HttpMethod.GET,
                                "/reservation-service/reservations/admin",
                                "/reservation-service/reservations/admin/**"
                        ).hasRole("ADMIN")

                        // 기존 관리자 권한 검사 유지
                        .pathMatchers("/*/admin/**").hasRole("ADMIN")

                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .addFilterBefore(
                        sessionCookieBearerWebFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                .addFilterAfter(
                        new UserIdHeaderWebFilter(),
                        SecurityWebFiltersOrder.AUTHORIZATION
                )
                .build();
    }

    /**
     * 관리 포트(18081)만 인증 없이 연다.
     * 공개 API 포트(8081)의 필터와 섞이지 않게 포트로 구분한다.
     * health와 prometheus만 허용하고 나머지 액추에이터는 거절한다.
     */
    @Bean
    @Order(-1)
    SecurityWebFilterChain managementPortSecurity(
            ServerHttpSecurity http,
            @Value("${management.server.port}") int managementPort) {

        http.securityMatcher(exchange -> {
            var address = exchange.getRequest().getLocalAddress();
            int port = address == null ? -1 : address.getPort();

            if (port == managementPort) {
                return ServerWebExchangeMatcher.MatchResult.match();
            }

            return ServerWebExchangeMatcher.MatchResult.notMatch();
        });

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/prometheus"
                        ).permitAll()
                        .anyExchange().denyAll()
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
        return corsSource(appProperties);
    }

    private static UrlBasedCorsConfigurationSource corsSource(AppProperties appProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(appProperties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Set-Cookie"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    ReactiveJwtDecoder accessTokenDecoder(AuthProperties authProperties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSource(
                        new ImmutableJWKSet<>(
                                new JWKSet(ecKey(authProperties))
                        )
                )
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(),
                        new JwtIssuerValidator(
                                authProperties.accessTokenIssuer()
                        )
                )
        );

        return token -> Mono
                .fromCallable(() -> decoder.decode(token))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static ECKey ecKey(AuthProperties authProperties) {
        String body = authProperties.accessTokenPublic()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(
                    Base64.getDecoder().decode(body)
            );

            ECPublicKey publicKey = (ECPublicKey) KeyFactory
                    .getInstance("EC")
                    .generatePublic(spec);

            return new ECKey.Builder(
                    Curve.forECParameterSpec(publicKey.getParams()),
                    publicKey
            )
                    .keyID(authProperties.accessTokenKeyId())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.ES256)
                    .build();

        } catch (
                InvalidKeySpecException |
                NoSuchAlgorithmException |
                IllegalArgumentException ex
        ) {
            throw new IllegalStateException(
                    "invalid EC public key",
                    ex
            );
        }
    }

    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter =
                new ReactiveJwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(this::roles);

        return converter;
    }

    private Flux<GrantedAuthority> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");

        if (roles == null) {
            return Flux.empty();
        }

        return Flux.fromIterable(roles)
                .map(role ->
                        role.startsWith("ROLE_")
                                ? role
                                : "ROLE_" + role
                )
                .map(SimpleGrantedAuthority::new);
    }
}