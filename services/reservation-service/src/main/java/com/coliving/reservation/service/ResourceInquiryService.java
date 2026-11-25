
package com.coliving.reservation.service;

import com.coliving.reservation.entity.ResourceInquiry;
import com.coliving.reservation.entity.ResourceReservation;
import com.coliving.reservation.repository.ResourceInquiryRepository;
import com.coliving.reservation.repository.ResourceReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceInquiryService {

    private final ResourceInquiryRepository resourceInquiryRepository;
    private final ResourceReservationRepository resourceReservationRepository;

    // ==========================================
    // 1. 사용자 문의 등록
    // ==========================================
    @Transactional
    public ResourceInquiry createInquiry(
            Long userId,
            Long reservationId,
            String inquiryType,
            String content) {

        // 문의 유형 검사
        if (inquiryType == null ||
                (!inquiryType.equals("EXTENSION") &&
                        !inquiryType.equals("BREAKDOWN") &&
                        !inquiryType.equals("OTHER"))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 문의 유형을 선택해 주세요."
            );
        }

        // 문의 내용 검사
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "문의 내용을 입력해 주세요."
            );
        }

        // 예약 존재 여부 확인
        ResourceReservation reservation = resourceReservationRepository
                .findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "예약 정보가 없습니다."
                ));

        // 본인 예약인지 확인
        if (userId == null || !userId.equals(reservation.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인 예약에 대해서만 문의할 수 있습니다."
            );
        }

        // 문의 저장
        ResourceInquiry inquiry = new ResourceInquiry(
                userId,
                reservationId,
                inquiryType,
                content.trim()
        );

        return resourceInquiryRepository.save(inquiry);
    }

    // ==========================================
    // 2. 사용자 내 문의 목록 조회
    // ==========================================
    @Transactional(readOnly = true)
    public List<ResourceInquiry> getMyInquiries(Long userId) {
        return resourceInquiryRepository.findByUserId(userId);
    }

    // ==========================================
    // 3. 관리자 전체 문의 목록 조회
    // ==========================================
    @Transactional(readOnly = true)
    public List<ResourceInquiry> getAllInquiries() {
        return resourceInquiryRepository.findAll();
    }

    // ==========================================
    // 4. 사용자 문의 삭제
    // ==========================================
    @Transactional
    public void deleteInquiry(Long inquiryId, Long userId) {

        // 삭제할 문의가 존재하는지 확인
        ResourceInquiry inquiry = resourceInquiryRepository
                .findById(inquiryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "문의 내역이 없습니다."
                ));

        // 본인이 작성한 문의인지 확인
        if (userId == null || !userId.equals(inquiry.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인이 작성한 문의만 삭제할 수 있습니다."
            );
        }

        // 문의 삭제
        resourceInquiryRepository.delete(inquiry);
    }

    // ==========================================
    // 5. 관리자 문의 답변 등록 (신규 추가)
    // ==========================================
    @Transactional
    public ResourceInquiry replyToInquiry(Long inquiryId, String reply) {

        // 1. 답변 내용 검사
        if (reply == null || reply.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "답변 내용을 입력해 주세요."
            );
        }

        // 2. 문의 존재 여부 확인
        ResourceInquiry inquiry = resourceInquiryRepository
                .findById(inquiryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "문의 내역이 없습니다."
                ));

        // 3. 이미 답변한 문의인지 확인
        if ("ANSWERED".equals(inquiry.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 답변이 완료된 문의입니다."
            );
        }

        // 4. 관리자 답변 내용 및 답변 시간 저장
        // 문의 상태를 ANSWERED로 변경
        inquiry.saveReply(reply.trim());

        // 5. 변경된 문의 정보 DB 저장
        return resourceInquiryRepository.save(inquiry);
    }
}
