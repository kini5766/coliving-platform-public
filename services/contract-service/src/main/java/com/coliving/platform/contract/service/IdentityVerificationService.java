package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ApplicationAccessDeniedException;
import com.coliving.platform.common.exception.ApplicationNotFoundException;
import com.coliving.platform.common.exception.IdentityVerificationException;
import com.coliving.platform.contract.dto.internal.VerifiedIdentity;
import com.coliving.platform.contract.dto.sign.IdentityVerificationResponse;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 2단계 본인인증 확인.
 *
 * 프론트는 포트원 인증창이 끝나면 identityVerificationId만 보낸다 (이름·휴대폰은 받지 않는다 — 위조 가능)
 * 서버가 포트원에 직접 조회해서
 *   ① 인증이 완료(VERIFIED)됐는지
 *   ② 인증한 사람이 신청자 본인인지 (신청서에 저장된 이름·휴대폰과 비교)
 * 를 확인한다.
 *
 * 저장은 하지 않는다. 서명 제출 API가 같은 ID로 한 번 더 확인하고 contract_signature에 남긴다
 * (identity_verification_id UNIQUE → 같은 인증으로 두 번 서명 불가).
 *
 * @author 한성현
 */
@Service
public class IdentityVerificationService {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final ContractRepository contractRepository;
    private final IdentityVerificationClient identityClient;

    public IdentityVerificationService(ContractRepository contractRepository,
                                       IdentityVerificationClient identityClient) {
        this.contractRepository = contractRepository;
        this.identityClient = identityClient;
    }

    public IdentityVerificationResponse verify(Long userId, String applicationNo, String identityVerificationId) {
        Contract c = contractRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new ApplicationNotFoundException("신청서를 찾을 수 없습니다."));
        if (!c.isOwnedBy(userId)) {
            throw new ApplicationAccessDeniedException("본인의 신청서만 인증할 수 있습니다.");
        }
        VerifiedIdentity v = confirm(c, identityVerificationId);
        Instant at = v.verifiedAt() == null ? Instant.now() : v.verifiedAt();
        return new IdentityVerificationResponse(true, at.atOffset(KST));
    }

    /**
     * 포트원 재조회 + 신청자 대조. 서명 제출(ContractSignatureService)도 같은 검사를 한 번 더 거친다.
     * 소유자 확인은 부르는 쪽 책임.
     */
    public VerifiedIdentity confirm(Contract c, String identityVerificationId) {
        if (identityVerificationId == null || identityVerificationId.isBlank()) {
            throw new IllegalArgumentException("본인인증 ID가 없습니다.");
        }
        // 서명 단계 = 승인 후 결제 전. 결제 기한(승인 + 1일)이 지났으면 인증해도 의미가 없다
        if (c.getStatus() != ContractStatus.APPROVED) {
            throw new IllegalStateException("승인된 신청서만 본인인증을 할 수 있습니다.");
        }
        if (c.getExpiresAt() != null && Instant.now().isAfter(c.getExpiresAt())) {
            throw new IllegalStateException("결제 기한이 지난 신청서입니다.");
        }

        VerifiedIdentity v = identityClient.fetch(identityVerificationId.trim());
        if (!v.verified()) {
            throw new IdentityVerificationException("본인인증이 완료되지 않았습니다. 다시 인증해주세요.");
        }
        if (!sameName(v.name(), c.getApplicantName())) {
            throw new IdentityVerificationException("인증한 분의 이름이 신청자와 다릅니다. 신청자 본인 명의 휴대폰으로 인증해주세요.");
        }
        // PG에 따라 휴대폰 번호가 안 올 수 있다 → 왔을 때만 비교
        if (v.phone() != null && !digits(v.phone()).equals(digits(c.getApplicantPhone()))) {
            throw new IdentityVerificationException("인증한 휴대폰 번호가 신청서의 번호와 다릅니다.");
        }
        return v;
    }

    private static boolean sameName(String a, String b) {
        return a != null && b != null && a.replace(" ", "").equals(b.replace(" ", ""));
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");     // "010-1234-5678" → "01012345678"
    }
}
