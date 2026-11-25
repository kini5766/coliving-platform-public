package com.coliving.platform.security.controller;

import com.coliving.platform.security.dto.AdminMemberResponse;
import com.coliving.platform.security.service.AdminMemberService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 회원 목록. 게이트웨이에서 ADMIN 만 통과한다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@RestController
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    /** 활성 회원과 탈퇴 회원. 확인 전 가입은 넣지 않는다. */
    @GetMapping("/admin/members")
    public List<AdminMemberResponse> list() {
        return adminMemberService.list();
    }
}
