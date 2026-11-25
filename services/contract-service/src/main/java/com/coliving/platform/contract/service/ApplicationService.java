package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ApplicationAccessDeniedException;
import com.coliving.platform.common.exception.ApplicationNotFoundException;
import com.coliving.platform.contract.dto.application.ApplicationDetailResponse;
import com.coliving.platform.contract.dto.application.ApplicationDraftRequest;
import com.coliving.platform.contract.dto.application.ApplicationDraftResponse;
import com.coliving.platform.contract.dto.application.ApplicationListItemResponse;
import com.coliving.platform.contract.dto.application.BookedPeriodResponse;
import com.coliving.platform.contract.dto.application.ApplicationSubmitRequest;
import com.coliving.platform.contract.dto.application.ApplicationSubmitResponse;
import com.coliving.platform.contract.dto.application.ApplyFormResponse;
import com.coliving.platform.contract.dto.internal.ApplicantInfo;
import com.coliving.platform.contract.dto.internal.DocumentFormDTO;
import com.coliving.platform.contract.dto.internal.RoomSnapshot;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractDocument;
import com.coliving.platform.contract.entity.ContractStatusHistory;
import com.coliving.platform.contract.enums.PaymentStatus;
import com.coliving.platform.contract.repository.ContractDocumentRepository;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.HistoryRepository;
import com.coliving.platform.contract.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.coliving.platform.contract.enums.ContractStatus.*;

/**
 * 신청 1단계 (1-1 호실 선택 → 1-2 신청자 정보 → 제출).
 *
 * userId는 항상 컨트롤러가 X-User 헤더(gateway가 JWT에서 꺼내 넣어준 값)로 넘긴다.
 * URL이나 body로 받은 회원 번호는 쓰지 않는다.
 *
 * @author 한성현
 */
@Service
public class ApplicationService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.BASIC_ISO_DATE; // 20261028
    static final Set<Integer> ALLOWED_MONTHS = Set.of(3, 6, 12);                // 화면 선택지와 같게 (RoomAvailabilityService도 사용)
    private static final Set<String> ALLOWED_LANGS = Set.of("ko", "en");

    // final + 생성자 주입. (이전 코드는 필드에 주입 표시가 없어서 null이었다)
    private final ContractRepository contractRepository;
    private final HistoryRepository historyRepository;
    private final PaymentRepository paymentRepository;
    private final ContractDocumentRepository documentRepository;
    private final ContractFormLoader formLoader;
    private final ContractContentRenderer renderer;
    private final TransactionTemplate tx;
    private final ContractStatusEvents statusEvents;

    public ApplicationService(ContractRepository contractRepository,
                              HistoryRepository historyRepository,
                              PaymentRepository paymentRepository,
                              ContractDocumentRepository documentRepository,
                              ContractFormLoader formLoader,
                              ContractContentRenderer renderer,
                              TransactionTemplate tx,
                              ContractStatusEvents statusEvents) {
        this.contractRepository = contractRepository;
        this.historyRepository = historyRepository;
        this.paymentRepository = paymentRepository;
        this.documentRepository = documentRepository;
        this.formLoader = formLoader;
        this.renderer = renderer;
        this.tx = tx;
        this.statusEvents = statusEvents;
    }

    private String newApplicationNo() {
        String date = LocalDate.now(SEOUL).format(NO_DATE);
        long seq = contractRepository.nextApplicationSeq();
        return "CT-" + date + "-" + String.format("%04d", seq);   // CT-20261028-0001
    }

    // =====================================================================
    // 1-1 → 다음 단계: DRAFT 생성
    // 같은 회원이 같은 호실로 작성 중인 DRAFT가 있으면 새로 만들지 않고 그걸 수정한다.
    // ("이전"으로 돌아와 기간만 바꾸고 다시 "다음"을 눌러도 신청서가 쌓이지 않게)
    // =====================================================================
    @Transactional
    public ApplicationDraftResponse createDraft(Long userId, ApplicationDraftRequest req) {
        Instant now = Instant.now();
        validateDraftRequest(req);
        RoomSnapshot snap = RoomSnapshot.from(req.roomId(), req.room()); // 화면이 매물 서비스에서 받아 보낸 값 (형식 검사만)

        Optional<Contract> existing = contractRepository.findByUserIdAndRoomIdAndStatus(userId, req.roomId(), DRAFT);
        if (existing.isPresent()) {
            Contract old = existing.get();
            if (!old.isDraftExpired(now)) {                            // 기한 안: 기존처럼 수정해서 재사용
                old.changeDraft(snap, req.moveInDate(), req.months());
                contractRepository.saveAndFlush(old);                  // 기간 겹침 에러를 여기서 바로 받기 위해 flush
                return ApplicationDraftResponse.from(old);
            }
            old.expireDraft(now);                                      // 기한 지남: EXPIRED로 정리하고 아래에서 새로 만든다
            contractRepository.saveAndFlush(old);                      // ★ INSERT보다 먼저 UPDATE가 나가도록 flush
            statusEvents.publish(historyRepository.save(ContractStatusHistory.system(old.getId(), DRAFT, EXPIRED)));
        }

        Contract c = Contract.createDraft(newApplicationNo(), userId, snap,
                req.moveInDate(), req.months(), now);
        contractRepository.saveAndFlush(c);                        // flush해야 기간 겹침 에러가 여기서 터짐
        statusEvents.publish(historyRepository.save(ContractStatusHistory.user(c.getId(), null, DRAFT, userId)));

        return new ApplicationDraftResponse(c.getApplicationNo(), c.getStatus(), c.getExpiresAt().atOffset(ZoneOffset.ofHours(9)));
    }

    // =====================================================================
    // 1-2 신청자 정보 화면
    // 신청자 정보(이름·휴대폰·이메일·주소)는 여기서 내려주지 않는다.
    // 프론트가 user-service 계약 자동완성(GET /user-service/user/contract-autocomplete)에서 불러와 채운다.
    // =====================================================================
    public ApplyFormResponse getForm(Long userId, String applicationNo) {
        Contract c = findOwned(userId, applicationNo);
        if (c.getStatus() != DRAFT) {
            throw new IllegalStateException("이미 제출했거나 작성할 수 없는 신청서입니다.");
        }
        return ApplyFormResponse.of(c);
    }

    // =====================================================================
    // 1-2 신청서 제출: DRAFT → SUBMITTED, 금액 고정 + 계약서(contract_document) 생성
    //   관리자가 심사 화면에서 계약서 본문을 보고 승인·반려할 수 있게 제출 때 만든다 (2026-11-05 변경, 이전: 승인 때)
    //   1) 입력 검사 + 신청서 확인                       (트랜잭션 밖)
    //   2) 문서 서비스에서 양식 조회 (gRPC)               (트랜잭션 밖 → 기다리는 동안 DB 커넥션을 잡지 않는다)
    //   3) 제출 + 계약서 저장 + 상태 이력                 (짧은 트랜잭션 하나 → 셋 다 되거나 셋 다 안 되거나)
    //   2)에서 실패하면(문서 서비스 장애 503 등) 아무것도 저장되지 않아 DRAFT 그대로 → 다시 제출하면 된다
    //   계약일({{contractDate}})은 결제일이라 저장본에서는 빈칸으로 남는다 (ContractContentRenderer)
    // =====================================================================
    public ApplicationSubmitResponse submit(Long userId, String applicationNo, ApplicationSubmitRequest req) {
        Contract read = findOwned(userId, applicationNo);
        if (read.getStatus() != DRAFT) {
            throw new IllegalStateException("이미 제출했거나 작성할 수 없는 신청서입니다.");
        }

        if (req.contractLang() == null || !ALLOWED_LANGS.contains(req.contractLang())) {
            throw new IllegalArgumentException("계약서 언어를 선택해주세요.");
        }
        if (!Contract.CURRENT_TERMS_VERSION.equals(req.termsVersion())) {
            throw new IllegalArgumentException("약관이 변경되었습니다. 새로고침 후 다시 동의해주세요.");
        }

        // 신청자 정보는 화면에서 입력(자동완성 값 + 사용자 수정)한 값을 그대로 스냅샷으로 저장한다
        String email = trimToNull(req.email());
        if (email == null) throw new IllegalArgumentException("이메일을 입력해주세요.");
        if (!isEmail(email)) throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        ApplicantInfo applicant = new ApplicantInfo(      // 이름·휴대폰이 비어 있으면 여기서 400
                trimToNull(req.name()),
                trimToNull(req.phone()),
                email,
                trimToNull(req.address())
        );

        // 2) 양식 조회 — 트랜잭션 밖
        DocumentFormDTO form = formLoader.load(req.contractLang());

        // 3) 제출 + 계약서 + 이력 — 한 트랜잭션
        //    "제출"을 두 번 눌러 겹치면 늦은 쪽은 @Version 충돌(409) 또는 계약서 UNIQUE 위반 → 409로 바꿔 준다
        Instant now = Instant.now();
        try {
            return tx.execute(status -> {
                Contract c = findOwned(userId, applicationNo);
                c.submit(applicant, req.contractLang(), req.termsVersion(), now);   // DRAFT·기한 확인은 엔티티가 한다. 금액은 작성 때 값으로 고정

                // 신청자 정보가 들어간 뒤의 스냅샷으로 본문을 채운다 (메모리 작업)
                String content = renderer.render(form.content(), c);
                documentRepository.save(ContractDocument.create(c.getId(), req.contractLang(), form.templateId(), content));
                statusEvents.publish(historyRepository.save(ContractStatusHistory.user(c.getId(), DRAFT, SUBMITTED, userId)));
                contractRepository.flush();                                       // @Version·UNIQUE 충돌을 여기서 바로 받는다

                return new ApplicationSubmitResponse(c.getApplicationNo(), c.getStatus(), now.atOffset(ZoneOffset.ofHours(9)));
            });
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new IllegalStateException("이미 제출된 신청서입니다. 새로고침 후 확인해주세요.");   // → 409
            }
            throw e;
        }
    }

    // PostgreSQL 23505 = UNIQUE 위반 (예외 안쪽 cause를 따라가며 찾는다)
    private static boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    // =====================================================================
    // 1-1 호실 선택: 이 호실에 이미 잡혀 있는 기간 ("다음 단계" 누르기 전에 화면이 겹치는 날짜를 막는 용도)
    // 예약 상태 목록은 ContractStatus.BOOKING (= DB 제약 no_overlap_booking의 WHERE)
    // =====================================================================

    public List<BookedPeriodResponse> getBookedPeriods(Long userId, Long roomId) {
        if (roomId == null) throw new IllegalArgumentException("호실 정보가 없습니다.");
        return contractRepository.findBookedPeriods(
                roomId, com.coliving.platform.contract.enums.ContractStatus.BOOKING, LocalDate.now(SEOUL), userId == null ? -1L : userId);
    }

    // =====================================================================
    // 나의 신청 목록: 제출한 신청 전부 (심사 중·승인·결제·반려·취소 등). 작성 중(DRAFT)은 뺀다
    // =====================================================================
    public List<ApplicationListItemResponse> getMyApplications(Long userId) {
        return contractRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(userId, DRAFT).stream()
                .map(ApplicationListItemResponse::from)
                .toList();
    }

    // =====================================================================
    // 접수 완료 / 나의 신청 상세: 신청서 1건 조회 (상태 무관, 본인 것만)
    // 화면을 새로고침해도 DB 값으로 다시 그릴 수 있게 한다
    // =====================================================================
    public ApplicationDetailResponse getDetail(Long userId, String applicationNo) {
        Contract c = findOwned(userId, applicationNo);
        ApplicationDetailResponse.PaymentSummary payment = paymentRepository
                .findByApplicationIdAndStatus(c.getId(), PaymentStatus.PAID)
                .map(p -> new ApplicationDetailResponse.PaymentSummary(
                        "KAKAOPAY", p.getAmount(), p.getApprovedAt().atOffset(ZoneOffset.ofHours(9)), null))
                .orElse(null);
        return ApplicationDetailResponse.from(c, payment);
    }

    // =====================================================================
    // 접수 완료 화면 "취소하기": SUBMITTED → CANCELED (심사 중에만)
    // - 승인 이후 취소는 환불이 걸려 있어서 별도 흐름(입주 전 취소 요청)으로 처리한다
    // - CANCELED는 no_overlap_booking 대상이 아니라서, 취소 즉시 같은 기간에 다른 신청이 가능해진다
    // =====================================================================
    @Transactional
    public ApplicationDetailResponse cancel(Long userId, String applicationNo) {
        Contract c = findOwned(userId, applicationNo);
        c.cancelByUser();                                   // SUBMITTED가 아니면 IllegalStateException → 409
        contractRepository.saveAndFlush(c);                 // 관리자가 동시에 승인했다면 @Version 충돌 → 409
        statusEvents.publish(historyRepository.save(ContractStatusHistory.user(c.getId(), SUBMITTED, CANCELED, userId)));
        return ApplicationDetailResponse.from(c);
    }

    // ---------------------------------------------------------------------
    // 신청서 조회 + 본인 확인. URL의 신청번호는 사용자가 바꿀 수 있으므로 항상 주인을 확인한다
    private Contract findOwned(Long userId, String applicationNo) {
        Contract c = contractRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new ApplicationNotFoundException("신청서를 찾을 수 없습니다."));
        if (!c.isOwnedBy(userId)) {
            throw new ApplicationAccessDeniedException("본인의 신청서만 볼 수 있습니다.");
        }
        return c;
    }

    private void validateDraftRequest(ApplicationDraftRequest req) {
        if (req.roomId() == null) throw new IllegalArgumentException("호실을 선택해주세요.");
        if (req.moveInDate() == null) throw new IllegalArgumentException("입주일을 선택해주세요.");
        if (req.moveInDate().isBefore(LocalDate.now(SEOUL))) {
            throw new IllegalArgumentException("입주일은 오늘 이후로 선택해주세요.");
        }
        if (req.months() == null || !ALLOWED_MONTHS.contains(req.months())) {
            throw new IllegalArgumentException("계약 기간은 3, 6, 12개월 중에서 선택해주세요.");
        }
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** user-service 계약 자동완성과 같은 규칙: @가 있고 그 뒤에 .이 있으며 공백 없음 */
    private static boolean isEmail(String s) {
        int at = s.indexOf('@');
        return at > 0 && s.indexOf('.', at + 1) > at + 1 && s.chars().noneMatch(Character::isWhitespace);
    }
}
