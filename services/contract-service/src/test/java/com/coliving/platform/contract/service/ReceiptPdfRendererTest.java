package com.coliving.platform.contract.service;

import com.coliving.platform.common.config.ReceiptBusinessProperties;
import com.coliving.platform.contract.enums.ReceiptType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptPdfRendererTest {

    private static final ReceiptBusinessProperties BUSINESS =
            new ReceiptBusinessProperties("코리빙 주식회사", "123-45-67890", "홍길동", "서울특별시 강남구 테헤란로 1");

    private final ReceiptPdfRenderer renderer = new ReceiptPdfRenderer(BUSINESS);

    private static ReceiptPdfRenderer.Input input(ReceiptType type) {
        return new ReceiptPdfRenderer.Input(type, "CT-20261102-0001", "CT-20261102-0001-abcd1234", "T1234567890",
                "CARD", Instant.parse("2026-11-02T05:05:00Z"),
                type == ReceiptType.CANCEL ? Instant.parse("2026-11-03T01:00:00Z") : null,
                1_780_000L, 1_000_000L, 780_000L, Instant.parse("2026-11-04T00:00:00Z"), "b".repeat(64));
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void 결제_영수증에는_요구한_항목이_모두_들어간다() throws IOException {
        byte[] pdf = renderer.render(input(ReceiptType.PAYMENT));
        String text = text(pdf);
        String flat = text.replaceAll("\\s", "");

        assertThat(text).contains("결제 영수증",
                "코리빙 주식회사", "123-45-67890", "홍길동", "서울특별시 강남구 테헤란로 1",
                "CT-20261102-0001", "CT-20261102-0001-abcd1234", "T1234567890",
                "KAKAOPAY / CARD", "2026-11-02 14:05:00 KST",
                "보증금 (비과세)", "1,000,000원", "첫 달 이용료", "780,000원", "총 결제금액", "1,780,000원",
                "b".repeat(20));
        assertThat(flat).contains(ReceiptPdfRenderer.NOTICE.replaceAll("\\s", ""));
        assertThat(text).doesNotContain("취소일시", "취소(환불) 금액");
    }

    @Test
    void 취소_영수증에는_취소일시와_환불금액이_들어간다() throws IOException {
        String text = text(renderer.render(input(ReceiptType.CANCEL)));

        assertThat(text).contains("결제 취소 영수증", "취소일시", "2026-11-03 10:00:00 KST", "취소(환불) 금액", "1,780,000원");
    }

    @Test
    void 사업자_정보가_비어_있으면_대시로_찍힌다() throws IOException {
        ReceiptPdfRenderer empty = new ReceiptPdfRenderer(new ReceiptBusinessProperties(null, "", " ", null));

        String text = text(empty.render(input(ReceiptType.PAYMENT)));

        assertThat(text).contains("사업자등록번호 -").doesNotContain("123-45-67890");
    }

    @Test
    void 한_장으로_나온다() throws IOException {
        try (PDDocument doc = Loader.loadPDF(renderer.render(input(ReceiptType.CANCEL)))) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }
}
