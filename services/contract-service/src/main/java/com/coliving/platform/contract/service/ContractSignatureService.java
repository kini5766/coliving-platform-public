package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ApplicationAccessDeniedException;
import com.coliving.platform.common.exception.ApplicationNotFoundException;
import com.coliving.platform.common.exception.ContractFileException;
import com.coliving.platform.common.exception.DocumentNotFoundException;
import com.coliving.platform.contract.dto.internal.VerifiedIdentity;
import com.coliving.platform.contract.dto.sign.SignatureRequest;
import com.coliving.platform.contract.dto.sign.SignatureResponse;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractDocument;
import com.coliving.platform.contract.entity.ContractSignature;
import com.coliving.platform.contract.repository.ContractDocumentRepository;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.ContractSignatureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/**
 * 2단계 전자 서명 제출.
 *
 *   1) 서명 이미지 검사 → 소유자·문서 해시·중복 서명 확인
 *   2) 포트원 재조회로 본인인증 재확인, 신청 상태·결제 기한 확인 (트랜잭션 밖)
 *   3) 서명 PDF 생성 + 서명 이미지·PDF 파일 저장 (트랜잭션 밖)
 *   4) contract_signature 저장 (짧은 트랜잭션)
 *
 * 동시에 두 번 제출되면 UNIQUE(application_id, document_id)로 늦은 쪽이 실패(409)하고,
 * 그쪽이 저장한 파일은 지운다. 파일 경로에 UUID가 있어서 먼저 저장된 파일과 겹치지 않는다.
 *
 * @author 한성현
 */
@Service
public class ContractSignatureService {

    private static final Logger log = LoggerFactory.getLogger(ContractSignatureService.class);

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final String UNIQUE_VIOLATION = "23505";
    private static final int MAX_IMAGE_BYTES = 1024 * 1024;     // 서명 PNG 상한 1MB
    private static final int MAX_IMAGE_SIDE = 4000;             // 압축을 풀기 전에 가로·세로 상한 확인
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository documentRepository;
    private final ContractSignatureRepository signatureRepository;
    private final IdentityVerificationService identityVerificationService;
    private final SignedContractPdfRenderer pdfRenderer;
    private final ContractFileStorage storage;
    private final TransactionTemplate tx;
    private final ContractContentView contentView;

    public ContractSignatureService(ContractRepository contractRepository,
                                    ContractDocumentRepository documentRepository,
                                    ContractSignatureRepository signatureRepository,
                                    IdentityVerificationService identityVerificationService,
                                    SignedContractPdfRenderer pdfRenderer,
                                    ContractFileStorage storage,
                                    TransactionTemplate tx,
                                    ContractContentView contentView) {
        this.contractRepository = contractRepository;
        this.documentRepository = documentRepository;
        this.signatureRepository = signatureRepository;
        this.identityVerificationService = identityVerificationService;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
        this.tx = tx;
        this.contentView = contentView;
    }

    public SignatureResponse sign(Long userId, String applicationNo, SignatureRequest req) {
        byte[] png = decodePng(req.signatureImage());
        BufferedImage image = readImage(png);
        if (req.contentHash() == null || req.contentHash().isBlank()) {
            throw new IllegalArgumentException("계약서 해시가 없습니다.");
        }

        Contract c = findOwned(userId, applicationNo, "본인의 계약서에만 서명할 수 있습니다.");
        ContractDocument d = documentRepository.findByApplicationId(c.getId())
                .orElseThrow(() -> new DocumentNotFoundException("아직 발급된 계약서가 없습니다."));
        if (!d.getContentHash().equals(req.contentHash())) {
            throw new IllegalStateException("계약서가 변경됐습니다. 새로고침 후 다시 확인해주세요.");
        }
        if (signatureRepository.existsByApplicationIdAndDocumentId(c.getId(), d.getId())) {
            throw new IllegalStateException("이미 서명한 계약서입니다.");
        }

        // 상태(APPROVED)·기한·신청자 일치는 여기서 함께 확인한다
        VerifiedIdentity identity = identityVerificationService.confirm(c, req.identityVerificationId());
        Instant signedAt = Instant.now();

        // 서명 해시는 저장본 기준 그대로
        byte[] pdf = pdfRenderer.render(new SignedContractPdfRenderer.Input(
                c.getApplicationNo(), d.getLanguage(), contentView.content(c, d), d.getContentHash(),
                identity.name(), identity.identityVerificationId(), signedAt, image));
        String pdfHash = ContractDocument.sha256Hex(pdf);

        String key = c.getApplicationNo() + "/" + UUID.randomUUID();
        String imagePath = "signatures/" + key + ".png";
        String pdfPath = "contracts/" + key + ".pdf";

        try {
            storage.save(imagePath, png);
            storage.save(pdfPath, pdf);
            ContractSignature saved = tx.execute(status -> signatureRepository.saveAndFlush(
                    ContractSignature.create(c.getId(), d.getId(), identity.identityVerificationId(),
                            identity.name(), d.getContentHash(), imagePath, pdfPath, pdfHash, signedAt)));
            return new SignatureResponse(saved.getId(), signedAt.atOffset(KST));
        } catch (DataIntegrityViolationException e) {
            deleteQuietly(imagePath, pdfPath);
            if (UNIQUE_VIOLATION.equals(sqlState(e))) {
                throw new IllegalStateException("이미 서명했거나 사용된 본인인증입니다. 새로고침 후 다시 시도해주세요.");
            }
            throw e;
        } catch (RuntimeException e) {
            deleteQuietly(imagePath, pdfPath);
            throw e;
        }
    }

    /** 서명 완료 PDF. 저장할 때의 해시와 다르면 내려주지 않는다 */
    public byte[] loadSignedPdf(Long userId, String applicationNo) {
        Contract c = findOwned(userId, applicationNo, "본인의 계약서만 볼 수 있습니다.");
        ContractDocument d = documentRepository.findByApplicationId(c.getId())
                .orElseThrow(() -> new DocumentNotFoundException("아직 발급된 계약서가 없습니다."));
        ContractSignature s = signatureRepository.findByApplicationIdAndDocumentId(c.getId(), d.getId())
                .filter(sig -> sig.getSignedPdfPath() != null)
                .orElseThrow(() -> new DocumentNotFoundException("서명한 계약서가 없습니다."));

        byte[] pdf = storage.read(s.getSignedPdfPath());
        if (!ContractDocument.sha256Hex(pdf).equals(s.getSignedPdfHash())) {
            throw new ContractFileException("서명 PDF 해시 불일치: " + s.getSignedPdfPath(), null);
        }
        return pdf;
    }

    private Contract findOwned(Long userId, String applicationNo, String deniedMessage) {
        Contract c = contractRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new ApplicationNotFoundException("신청서를 찾을 수 없습니다."));
        if (!c.isOwnedBy(userId)) {
            throw new ApplicationAccessDeniedException(deniedMessage);
        }
        return c;
    }

    private static byte[] decodePng(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("서명 이미지가 없습니다.");
        }
        if (base64.length() > (MAX_IMAGE_BYTES / 3 + 1) * 4) {
            throw new IllegalArgumentException("서명 이미지가 너무 큽니다.");
        }
        byte[] png;
        try {
            png = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("서명 이미지 형식이 올바르지 않습니다.");
        }
        if (png.length < PNG_MAGIC.length
                || !Arrays.equals(png, 0, PNG_MAGIC.length, PNG_MAGIC, 0, PNG_MAGIC.length)) {
            throw new IllegalArgumentException("서명 이미지는 PNG여야 합니다.");
        }
        return png;
    }

    private static BufferedImage readImage(byte[] png) {
        ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            reader.setInput(in);
            if (reader.getWidth(0) > MAX_IMAGE_SIDE || reader.getHeight(0) > MAX_IMAGE_SIDE) {
                throw new IllegalArgumentException("서명 이미지가 너무 큽니다.");
            }
            return reader.read(0);
        } catch (IOException e) {
            throw new IllegalArgumentException("서명 이미지를 읽을 수 없습니다.");
        } finally {
            reader.dispose();
        }
    }

    private void deleteQuietly(String... paths) {
        for (String path : paths) {
            try {
                storage.delete(path);
            } catch (RuntimeException e) {
                log.warn("서명 파일 정리 실패: {}", path, e);
            }
        }
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
