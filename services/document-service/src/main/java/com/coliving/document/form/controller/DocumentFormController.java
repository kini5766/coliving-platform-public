package com.coliving.document.form.controller;

import com.coliving.document.form.dto.FormCreateRequest;
import com.coliving.document.form.dto.FormResponse;
import com.coliving.document.form.dto.FormUpdateRequest;
import com.coliving.document.form.service.DocumentFormService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자 화면의 "문서 양식" 영역과 계약 서비스에 양식 데이터를 내려주는 API. */
@RestController
@RequestMapping("/admin/forms")
@RequiredArgsConstructor
public class DocumentFormController {

    private final DocumentFormService service;

    /** 전체 양식 목록 조회. */
    @GetMapping
    public List<FormResponse> list() {
        return service.findAll();
    }

    /** 양식 한 건 조회. 계약 서비스가 빈칸을 채울 양식을 가져갈 때도 쓴다. */
    @GetMapping("/{id}")
    public FormResponse get(@PathVariable Long id) {
        return service.find(id);
    }

    /** 양식 등록. 성공하면 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FormResponse create(@Valid @RequestBody FormCreateRequest request) {
        return service.create(request);
    }

    /** 양식 수정. 제목과 본문만 바꿀 수 있다. */
    @PutMapping("/{id}")
    public FormResponse update(@PathVariable Long id, @Valid @RequestBody FormUpdateRequest request) {
        return service.update(id, request);
    }

    /** 양식 삭제. 성공하면 204. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}