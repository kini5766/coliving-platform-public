
package com.coliving.reservation.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableWebSecurity
public class ReservationSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // 업로드된 공용 자원 이미지 조회
                        // 사용자 및 관리자 화면의 img 태그에서 접근 가능
                        .requestMatchers(
                                HttpMethod.GET,
                                "/reservation-service/images/resources/**"
                        ).permitAll()

                        // 관리자 예약 조회 및 강제 취소
                        .requestMatchers(
                                "/reservation-service/reservations/admin",
                                "/reservation-service/reservations/admin/**"
                        ).hasRole("ADMIN")

                        // QR 체크인
                        .requestMatchers(
                                HttpMethod.POST,
                                "/reservation-service/reservations/check-in"
                        ).hasRole("ADMIN")

                        // 관리자 공용 자원 등록, 수정, 삭제 및 이미지 업로드
                        .requestMatchers(
                                "/reservation-service/admin/**"
                        ).hasRole("ADMIN")

                        // 일반 예약 및 문의 API
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");

            if (roles == null) {
                return List.of();
            }

            Collection<GrantedAuthority> authorities = roles.stream()
                    .map(role -> role.startsWith("ROLE_")
                            ? role
                            : "ROLE_" + role)
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                    .toList();

            return authorities;
        });

        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${auth.access-token-public}") String publicKeyPem,
            @Value("${auth.access-token-key-id}") String keyId,
            @Value("${auth.access-token-issuer}") String issuer) throws Exception {

        String publicKeyBody = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(publicKeyBody);

        ECPublicKey publicKey = (ECPublicKey) KeyFactory
                .getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(decoded));

        ECKey ecKey = new ECKey.Builder(
                Curve.forECParameterSpec(publicKey.getParams()),
                publicKey
        )
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.ES256)
                .build();

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSource(
                        new ImmutableJWKSet<>(new JWKSet(ecKey))
                )
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(),
                        new JwtIssuerValidator(issuer)
                )
        );

        return decoder;
    }
}
