
package com.coliving.reservation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "resource_inquiries")
public class ResourceInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자 ID
    @Column(nullable = false)
    private Long userId;

    // 예약 ID
    @Column(nullable = false)
    private Long reservationId;

    // 문의 유형: 추가 연장, 고장, 기타
    @Column(nullable = false)
    private String inquiryType;

    // 사용자 문의 내용
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 문의 상태: PENDING, ANSWERED
    @Column(nullable = false)
    private String status = "PENDING";

    // 관리자 답변 내용
    @Column(name = "admin_reply", columnDefinition = "TEXT")
    private String adminReply;

    // 관리자 답변 시간
    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    // 문의 등록 시간
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 문의 등록 시 작성 시간 자동 저장
    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    // 사용자 문의 등록 생성자
    public ResourceInquiry(
            Long userId,
            Long reservationId,
            String inquiryType,
            String content) {

        this.userId = userId;
        this.reservationId = reservationId;
        this.inquiryType = inquiryType;
        this.content = content;
    }

    // 관리자 답변 저장
    public void saveReply(String reply) {
        this.adminReply = reply;
        this.repliedAt = LocalDateTime.now();
        this.status = "ANSWERED";
    }
}
