package com.coliving.document.form.service;

import com.coliving.document.form.FormPlaceholders;
import com.coliving.document.form.dto.FormCreateRequest;
import com.coliving.document.form.dto.FormResponse;
import com.coliving.document.form.dto.FormUpdateRequest;
import com.coliving.document.form.entity.DocumentForm;
import com.coliving.document.form.repository.DocumentFormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 계약서 양식의 조회·등록·수정·삭제를 맡는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentFormService {

    private final DocumentFormRepository repository;

    /** 전체 양식을 번호순으로 조회한다. */
    public List<FormResponse> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(FormResponse::from).toList();
    }

    /** 번호로 양식 한 건을 조회한다. 계약 서비스도 이 번호로 양식을 가져간다. 없으면 404. */
    public FormResponse find(Long id) {
        return FormResponse.from(getOrThrow(id));
    }

    /** 양식을 새로 등록한다. 빈칸이 잘못됐으면 400. */
    @Transactional
    public FormResponse create(FormCreateRequest request) {
        validatePlaceholders(request.content());
        DocumentForm saved = repository.save(DocumentForm.builder()
                .language(request.language())
                .title(request.title())
                .content(request.content())
                .build());
        return FormResponse.from(saved);
    }

    /** 양식의 제목과 본문을 수정한다. 없으면 404, 빈칸이 잘못됐으면 400. */
    @Transactional
    public FormResponse update(Long id, FormUpdateRequest request) {
        DocumentForm form = getOrThrow(id);
        validatePlaceholders(request.content());
        form.update(request.title(), request.content());
        return FormResponse.from(repository.saveAndFlush(form));
    }

    /** 양식을 삭제한다. 없으면 404, 그 언어의 마지막 양식이면 400. */
    @Transactional
    public void delete(Long id) {
        DocumentForm form = getOrThrow(id);
        if (repository.countByLanguage(form.getLanguage()) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "이 언어의 마지막 양식은 삭제할 수 없어요. 새 양식을 먼저 등록해 주세요.");
        }
        repository.delete(form);
    }

    /** 모르는 빈칸이나 깨진 빈칸이 있으면 400. 이유는 화면에 그대로 보여준다. */
    private void validatePlaceholders(String content) {
        List<String> problems = FormPlaceholders.problems(content);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", problems));
        }
    }

    /** 번호로 양식을 찾고, 없으면 404를 던진다. */
    private DocumentForm getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "양식을 찾을 수 없어요."));
    }
}