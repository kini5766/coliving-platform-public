package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ApplicationNotFoundException;
import com.coliving.platform.contract.dto.admin.AdminApplicationDetailResponse;
import com.coliving.platform.contract.dto.admin.AdminApplicationListItemResponse;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractDocument;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractDocumentRepository;
import com.coliving.platform.contract.repository.ContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 관리자 계약서 목록·상세 (읽기 전용).
 *
 * 관리자 화면 → Gateway → 여기(/admin/applications)
 * - 상태 묶음(검토 대기·서명 대기·계약 완료·반려)과 배지 글자는 관리자 화면이 정한다.
 *   여기서는 화면이 넘긴 상태 값 목록으로만 거른다.
 * - 관리자 권한 확인은 gateway 몫 (/*\/admin/** → ROLE_ADMIN)
 *
 * @author 한성현
 */
@Service
public class AdminApplicationQueryService {

    // 상태를 안 넘기면: 작성 중(DRAFT)만 빼고 전부. DRAFT는 신청자가 아직 제출하지 않은 것이라 관리자 대상이 아니다
    private static final Set<ContractStatus> DEFAULT_STATUSES =
            EnumSet.complementOf(EnumSet.of(ContractStatus.DRAFT));
    private static final int KEYWORD_MAX = 100;

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository documentRepository;
    private final ContractContentView contentView;

    public AdminApplicationQueryService(ContractRepository contractRepository,
                                        ContractDocumentRepository documentRepository,
                                        ContractContentView contentView) {
        this.contractRepository = contractRepository;
        this.documentRepository = documentRepository;
        this.contentView = contentView;
    }

    // 목록: 최근 신청 순. keyword가 비어 있으면 상태로만 거른다
    @Transactional(readOnly = true)
    public List<AdminApplicationListItemResponse> findAll(String keyword, Collection<ContractStatus> statuses) {
        Collection<ContractStatus> target = (statuses == null || statuses.isEmpty()) ? DEFAULT_STATUSES : statuses;
        String word = keyword == null ? "" : keyword.trim();
        if (word.length() > KEYWORD_MAX) {
            throw new IllegalArgumentException("검색어는 " + KEYWORD_MAX + "자 이내로 입력해주세요.");
        }

        List<Contract> rows = word.isEmpty()
                ? contractRepository.findByStatusInOrderByCreatedAtDesc(target)
                : contractRepository.searchForAdmin(target, likePattern(word));
        return rows.stream().map(AdminApplicationListItemResponse::from).toList();
    }

    // 상세: 계약서는 제출 때 만들어진다. 본문의 계약일은 결제 완료일(결제 전이면 빈칸)
    @Transactional(readOnly = true)
    public AdminApplicationDetailResponse find(Long applicationId) {
        Contract c = contractRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException("신청서를 찾을 수 없습니다."));
        ContractDocument document = documentRepository.findByApplicationId(c.getId()).orElse(null);
        return AdminApplicationDetailResponse.of(c, document, contentView.content(c, document));
    }

    // "망원_1%" 같은 검색어의 _ % 를 문자 그대로 찾도록 이스케이프 (escape 문자 '!'는 쿼리와 같아야 한다)
    static String likePattern(String word) {
        String escaped = word.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
