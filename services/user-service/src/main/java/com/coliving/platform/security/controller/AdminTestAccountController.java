package com.coliving.platform.security.controller;

import com.coliving.platform.security.dto.AdminTestAccountRequestDTO;
import com.coliving.platform.security.service.AdminTestAccountService;
import com.coliving.platform.security.service.SignupService.EmailAlreadyUsedException;
import com.coliving.platform.security.service.SignupService.InvalidSignupException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 테스트 계정. 게이트웨이에서 ADMIN 만 통과한다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@RestController
@RequiredArgsConstructor
public class AdminTestAccountController {

    private final AdminTestAccountService adminTestAccountService;

    /** 확인 메일 없이 활성 계정을 만든다. 형식 오류는 400, 이미 쓴 이메일은 409. */
    @PostMapping("/admin/test-accounts")
    public ResponseEntity<Map<String, String>> create(@RequestBody AdminTestAccountRequestDTO request) {
        try {
            adminTestAccountService.create(request.email(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (InvalidSignupException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
        } catch (EmailAlreadyUsedException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "EMAIL_TAKEN"));
        }
    }
}
