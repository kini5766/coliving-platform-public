package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ContractFileException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * 계약서 완성본 + 서명 이미지 → 서명 완료 PDF (A4).
 *
 * 본문(HTML 양식)은 ContractHtml.toPlainText()로 줄 단위 글자로 바꿔 찍고, 마지막에 서명 확인란을 붙인다.
 * (예전 일반 텍스트 양식은 그대로 찍힌다)
 * 한글 폰트는 나눔고딕(SIL OFL)을 resources/fonts에 포함해서 서버 OS의 폰트에 의존하지 않는다.
 * 폰트에 없는 글자(이모지 등)는 PDF에서만 '?'로 찍힌다. 원문과 해시는 DB의 값이 기준이다.
 *
 * @author 한성현
 */
@Component
public class SignedContractPdfRenderer {

    private static final String FONT_PATH = "/fonts/NanumGothic-Regular.ttf";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter SIGNED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'KST'");

    private static final float MARGIN = 56;
    private static final float BODY_SIZE = 10.5f;
    private static final float BODY_LEADING = 17;
    private static final float INFO_SIZE = 9;
    private static final float INFO_LEADING = 14;
    private static final float TITLE_SIZE = 12;
    private static final float TITLE_LEADING = 20;
    private static final float SIGN_MAX_W = 200;
    private static final float SIGN_MAX_H = 70;

    public record Input(
            String applicationNo,
            String language,        // ko / en
            String content,
            String contentHash,
            String signerName,
            String identityVerificationId,
            Instant signedAt,
            BufferedImage signature
    ) {}

    private record Labels(String title, String applicationNo, String signer, String signedAt,
                          String hash, String identity, String signature) {}

    private static final Labels KO = new Labels("전자서명 확인", "신청 번호", "서명자", "서명 일시",
            "계약서 SHA-256", "본인인증 거래 ID", "서명");
    private static final Labels EN = new Labels("Electronic Signature", "Application No.", "Signer", "Signed at",
            "Contract SHA-256", "Identity verification ID", "Signature");

    public byte[] render(Input in) {
        try (PDDocument doc = new PDDocument(); InputStream fontFile = getClass().getResourceAsStream(FONT_PATH)) {
            if (fontFile == null) throw new IOException("폰트 파일 없음: " + FONT_PATH);
            PDType0Font font = PDType0Font.load(doc, fontFile, true);   // 쓴 글자만 내장

            doc.getDocumentInformation().setTitle("Contract " + in.applicationNo());
            doc.getDocumentInformation().setCreationDate(GregorianCalendar.from(in.signedAt().atZone(SEOUL)));

            Pages pages = new Pages(doc, font);
            for (String line : ContractHtml.toPlainText(in.content()).replace("\r", "").split("\n", -1)) {
                pages.text(line, BODY_SIZE, BODY_LEADING);
            }
            signatureBlock(doc, pages, in);
            pages.close();
            pageNumbers(doc, font, in.applicationNo());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ContractFileException("서명 계약서 PDF 생성 실패: " + in.applicationNo(), e);
        }
    }

    private void signatureBlock(PDDocument doc, Pages pages, Input in) throws IOException {
        Labels l = "en".equals(in.language()) ? EN : KO;
        List<String> info = List.of(
                l.applicationNo() + ": " + in.applicationNo(),
                l.signer() + ": " + in.signerName(),
                l.signedAt() + ": " + SIGNED_AT.format(in.signedAt().atZone(SEOUL)),
                l.hash() + ": " + in.contentHash(),
                l.identity() + ": " + in.identityVerificationId(),
                l.signature() + ":");

        // 확인란이 페이지 사이에서 잘리지 않게 한 페이지에 모은다 (해시·인증 ID가 두 줄이 될 수 있어 여유 2줄)
        pages.ensureRoom(30 + TITLE_LEADING + (info.size() + 2) * INFO_LEADING + SIGN_MAX_H + 6);

        pages.gap(16);
        pages.rule();
        pages.gap(14);
        pages.text(l.title(), TITLE_SIZE, TITLE_LEADING);
        for (String line : info) pages.text(line, INFO_SIZE, INFO_LEADING);

        PDImageXObject image = LosslessFactory.createFromImage(doc, in.signature());   // 투명 배경 유지
        float scale = Math.min(SIGN_MAX_W / image.getWidth(), SIGN_MAX_H / image.getHeight());
        pages.image(image, image.getWidth() * scale, image.getHeight() * scale);
    }

    private void pageNumbers(PDDocument doc, PDType0Font font, String applicationNo) throws IOException {
        int total = doc.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            try (PDPageContentStream s = new PDPageContentStream(
                    doc, doc.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                s.beginText();
                s.setFont(font, 8);
                s.newLineAtOffset(MARGIN, 30);
                s.showText(applicationNo + "  -  " + (i + 1) + " / " + total);
                s.endText();
            }
        }
    }

    /** 현재 페이지와 y 좌표를 들고 다니며, 공간이 모자라면 새 페이지를 만든다 */
    private static final class Pages {
        private static final float WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
        private static final float TOP = PDRectangle.A4.getHeight() - MARGIN;

        private final PDDocument doc;
        private final PDType0Font font;
        private PDPageContentStream stream;
        private float y;

        Pages(PDDocument doc, PDType0Font font) throws IOException {
            this.doc = doc;
            this.font = font;
            newPage();
        }

        void newPage() throws IOException {
            close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = TOP;
        }

        void ensureRoom(float height) throws IOException {
            if (y - height < MARGIN) newPage();
        }

        void gap(float height) {
            y -= height;
        }

        void rule() throws IOException {
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(MARGIN + WIDTH, y);
            stream.stroke();
        }

        void text(String line, float size, float leading) throws IOException {
            for (String part : wrap(printable(line), size)) {
                ensureRoom(leading);
                y -= leading;
                if (part.isEmpty()) continue;
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(part);
                stream.endText();
            }
        }

        void image(PDImageXObject image, float width, float height) throws IOException {
            ensureRoom(height + 6);
            y -= height + 6;
            stream.drawImage(image, MARGIN, y, width, height);
        }

        void close() throws IOException {
            if (stream != null) stream.close();
            stream = null;
        }

        // 탭은 공백으로, 제어 문자는 제거, 폰트에 없는 글자는 '?'
        // ㎡는 계약서에 늘 나오는데 나눔고딕에 없어서 같은 뜻의 글자로 바꾼다
        private String printable(String line) {
            StringBuilder out = new StringBuilder();
            line.replace("\t", "    ").replace("㎡", "m²").codePoints().forEach(cp -> {
                if (Character.isISOControl(cp)) return;
                String ch = Character.toString(cp);
                try {
                    font.encode(ch);
                    out.append(ch);
                } catch (IllegalArgumentException | IOException e) {
                    out.append('?');
                }
            });
            return out.toString();
        }

        // 폭을 넘으면 마지막 공백에서, 공백이 없으면 글자 단위로 줄을 나눈다
        private List<String> wrap(String line, float size) throws IOException {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            int lastSpace = -1;
            for (int cp : line.codePoints().toArray()) {
                cur.appendCodePoint(cp);
                if (cp == ' ') lastSpace = cur.length();
                if (font.getStringWidth(cur.toString()) / 1000 * size <= WIDTH) continue;
                int cut = (lastSpace > 0 && lastSpace < cur.length()) ? lastSpace : cur.length() - Character.charCount(cp);
                out.add(cur.substring(0, cut).stripTrailing());
                cur.delete(0, cut);
                lastSpace = -1;
            }
            out.add(cur.toString());
            return out;
        }
    }
}
