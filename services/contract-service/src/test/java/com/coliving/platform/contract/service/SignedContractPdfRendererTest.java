package com.coliving.platform.contract.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SignedContractPdfRendererTest {

    private final SignedContractPdfRenderer renderer = new SignedContractPdfRenderer();

    private static BufferedImage signature() {
        BufferedImage image = new BufferedImage(600, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.drawLine(10, 20, 500, 140);
        g.dispose();
        return image;
    }

    private static SignedContractPdfRenderer.Input input(String language, String content) {
        return new SignedContractPdfRenderer.Input("CT-20261102-0001", language, content, "a".repeat(64),
                "홍길동", "iv-CT-20261102-0001-test", Instant.parse("2026-11-02T01:02:03Z"), signature());
    }

    private String text(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void 한글_본문과_서명_정보가_들어간다() throws IOException {
        String text = text(renderer.render(input("ko", "주거 공간 이용 계약서\n\n임차인: 홍길동")));

        assertThat(text).contains("주거 공간 이용 계약서", "임차인: 홍길동", "전자서명 확인",
                "서명자: 홍길동", "2026-11-02 10:02:03 KST", "a".repeat(10));
    }

    @Test
    void HTML_양식은_태그_없이_문단별로_찍힌다() throws IOException {
        String html = "<h1>임대차 계약서</h1><p>임차인: 홍길동 &amp; 김철수</p><ul><li>전용 욕실</li></ul>";
        String text = text(renderer.render(input("ko", html)));

        assertThat(text).contains("임대차 계약서", "임차인: 홍길동 & 김철수", "• 전용 욕실")
                .doesNotContain("<h1>", "<p>", "&amp;");
    }

    @Test
    void 영문_계약서는_영문_라벨을_쓴다() throws IOException {
        assertThat(text(renderer.render(input("en", "Co-living agreement")))).contains("Electronic Signature", "Signer");
    }

    @Test
    void 긴_본문은_여러_페이지로_나뉘고_글자가_빠지지_않는다() throws IOException {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            content.append("제").append(i).append("조 임차인은 공용 공간을 깨끗하게 사용해야 하며 다른 입주자에게 피해를 주어서는 안 됩니다. ".repeat(3)).append('\n');
        }
        byte[] pdf = renderer.render(input("ko", content.toString()));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
        }
        assertThat(text(pdf).replaceAll("\\s", "")).contains("제199조임차인은", "전자서명확인");
    }

    @Test
    void 면적_단위는_물음표로_바뀌지_않는다() throws IOException {
        assertThat(text(renderer.render(input("ko", "면적: 13.5㎡")))).contains("13.5m²").doesNotContain("13.5?");
    }

    @Test
    void 폰트에_없는_글자가_있어도_실패하지_않는다() throws IOException {
        assertThat(text(renderer.render(input("ko", "주소: 서울 😀 성동구\t끝")))).contains("서울 ? 성동구");
    }
}
