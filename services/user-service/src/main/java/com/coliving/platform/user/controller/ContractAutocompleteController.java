package com.coliving.platform.user.controller;

import com.coliving.platform.security.http.CurrentSessionUser;
import com.coliving.platform.user.dto.ContractAutocompletePayload;
import com.coliving.platform.user.service.ContractAutocompleteService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 회원의 계약서 자동완성 조회·교체. 세션이 없으면 401.
 *
 * @author 조휘재
 * 작성일: 2026-11-01
 */
@RestController
@RequiredArgsConstructor
public class ContractAutocompleteController {

    private final CurrentSessionUser currentSessionUser;
    private final ContractAutocompleteService contractAutocompleteService;

    /** 저장된 값을 돌려준다. 없으면 빈 문자열 필드다. */
    @GetMapping("/user/contract-autocomplete")
    public ResponseEntity<ContractAutocompletePayload> read(HttpServletRequest request) {
        return currentSessionUser.id(request)
                .map(userId -> ResponseEntity.ok(
                        contractAutocompleteService.read(userId)
                                .map(ContractAutocompletePayload::from)
                                .orElseGet(ContractAutocompletePayload::empty)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /** 본문으로 통째로 바꾼다. 본문이 없으면 빈 값으로 보고 행을 지운다. */
    @PutMapping("/user/contract-autocomplete")
    public ResponseEntity<ContractAutocompletePayload> replace(
            HttpServletRequest request,
            @RequestBody(required = false) ContractAutocompletePayload body
    ) {
        return currentSessionUser.id(request)
                .map(userId -> replace(userId, body == null ? ContractAutocompletePayload.empty() : body))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /** 형식 오류면 400. */
    private ResponseEntity<ContractAutocompletePayload> replace(Long userId, ContractAutocompletePayload body) {
        try {
            return ResponseEntity.ok(contractAutocompleteService.replace(
                            userId,
                            body.email(),
                            body.name(),
                            body.phone(),
                            body.address(),
                            body.contractLanguage())
                    .map(ContractAutocompletePayload::from)
                    .orElseGet(ContractAutocompletePayload::empty));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
