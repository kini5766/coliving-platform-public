package com.coliving.document.form.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/** 계약서 양식 한 건. document_forms 테이블의 한 행과 짝이다. */
@Entity
@Table(name = "document_forms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 양식 언어 (ko, en). */
    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 200)
    private String title;

    /** 양식 본문(HTML). 채울 자리는 {{applicantName}} 모양으로 적는다. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 새 양식을 만든다. id와 시간은 DB와 JPA가 채운다. */
    @Builder
    public DocumentForm(String language, String title, String content) {
        this.language = language;
        this.title = title;
        this.content = content;
    }

    /** 제목과 본문을 고친다. 언어는 양식의 이름표라 바꾸지 않는다. */
    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}