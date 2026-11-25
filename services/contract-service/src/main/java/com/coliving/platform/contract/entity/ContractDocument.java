package com.coliving.platform.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

/**
 * 계약서 완성본. 관리자 승인 시 1건 생성된다 (신청 1 : 계약서 1).
 *
 * - 문서 서비스는 양식만 준다. 양식에 값을 채운 완성본과 해시 저장은 계약 서비스의 일
 * - 저장 후에는 절대 수정·삭제하지 않는다
 *   · @Immutable → Hibernate가 UPDATE를 보내지 않음
 *   · DB 트리거(trg_contract_document_no_update_delete)가 UPDATE/DELETE를 막음
 * - application_id UNIQUE → 같은 신청에 계약서가 두 번 생기면 DB가 막는다
 * - content_hash: content(UTF-8)의 SHA-256 hex 64자. 서명할 때 "이 문서에 서명했다"는 증빙으로 쓴다
 *
 * @author 한성현
 */
@Entity
@Immutable
@Table(name = "contract_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractDocument {

    private static final Set<String> LANGUAGES = Set.of("ko", "en");   // SQL CHECK와 같게

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;                 // contract_application.id

    @Column(name = "language", nullable = false, length = 10)
    private String language;                    // ko / en

    @Column(name = "template_id", nullable = false)
    private Long templateId;                    // 사용한 양식 = 문서 서비스 document_forms.id (FK 없음)

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;                     // 값이 채워진 완성본

    // CHAR(64): String 기본 매핑은 VARCHAR라 validate에서 타입 불일치가 난다 (ContractSignature와 같은 처리)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 해시는 여기서만 계산한다 → content와 hash가 어긋날 수 없다 */
    public static ContractDocument create(Long applicationId, String language, Long templateId, String content) {
        if (applicationId == null) throw new IllegalArgumentException("신청 ID가 없습니다");
        if (!LANGUAGES.contains(language)) throw new IllegalArgumentException("지원하지 않는 계약서 언어입니다: " + language);
        if (templateId == null) throw new IllegalArgumentException("양식 번호가 없습니다");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("계약서 본문이 비어 있습니다");

        ContractDocument d = new ContractDocument();
        d.applicationId = applicationId;
        d.language = language;
        d.templateId = templateId;
        d.content = content;
        d.contentHash = sha256Hex(content);
        return d;
    }

    /** UTF-8 바이트의 SHA-256 → 소문자 hex 64자 (SQL CHECK: ^[0-9a-f]{64}$) */
    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** 파일 등 바이트의 SHA-256 → 소문자 hex 64자 */
    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);   // 표준 JDK에는 항상 있음
        }
    }
}
