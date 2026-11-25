package com.coliving.platform.contract.service;

import com.coliving.platform.common.config.ReceiptBusinessProperties;
import com.coliving.platform.common.exception.ContractFileException;
import com.coliving.platform.contract.enums.ReceiptType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

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
 * 결제 영수증 / 취소 영수증 PDF (A4 한 장).
 *
 * 사업자 정보(상호·사업자등록번호·대표자·주소)는 코드에 쓰지 않고 app.receipt.business 설정에서 받는다.
 * 한글 폰트는 SignedContractPdfRenderer처럼 resources/fonts의 나눔고딕을 쓴다.
 *
 * @author 한성현
 */
@Component
public class ReceiptPdfRenderer {

    /** 하단 안내 문구 (결제·취소 영수증 공통) */
    public static final String NOTICE = "본 영수증은 결제 확인용이며 세법상 증빙(현금영수증·세금계산서)이 아닙니다. "
            + "현금영수증 및 카드 매출전표는 카카오페이 결제내역에서 확인하실 수 있습니다.";

    private static final String FONT_PATH = "/fonts/NanumGothic-Regular.ttf";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KST_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'KST'");

    private static final float MARGIN = 56;
    private static final float WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
    private static final float TOP = PDRectangle.A4.getHeight() - MARGIN;
    private static final float LABEL_W = 130;
    private static final float BODY = 10.5f;
    private static final float ROW = 20;

    /**
     * @param approvedAt   카카오 승인 일시
     * @param canceledAt   환불 완료 일시 (CANCEL일 때만 사용)
     * @param totalAmount  총 결제금액 = 첫 결제금액(보증금 + 첫 달 이용료)
     * @param deposit      보증금 (비과세)
     * @param firstMonthFee 첫 달 이용료
     * @param dataHash     영수증 기재 내용의 SHA-256 (PaymentReceiptService.dataHash)
     */
    public record Input(
            ReceiptType type,
            String applicationNo,
            String orderId,
            String tid,
            String paymentMethod,       // 카카오 결제수단: MONEY / CARD
            Instant approvedAt,
            Instant canceledAt,
            long totalAmount,
            long deposit,
            long firstMonthFee,
            Instant issuedAt,
            String dataHash
    ) {}

    private final ReceiptBusinessProperties business;

    public ReceiptPdfRenderer(ReceiptBusinessProperties business) {
        this.business = business;
    }

    /**영수증 pdf 만드는 순서 */
    public byte[] render(Input in) {
        boolean cancel = in.type() == ReceiptType.CANCEL;
        try (PDDocument doc = new PDDocument();
             InputStream fontFile = getClass().getResourceAsStream(FONT_PATH)) {
            if (fontFile == null) throw new IOException("폰트 파일 없음: " + FONT_PATH);
            PDType0Font font = PDType0Font.load(doc, fontFile, true);   // 쓴 글자만 내장

            doc.getDocumentInformation().setTitle((cancel ? "Cancel Receipt " : "Receipt ") + in.applicationNo());
            doc.getDocumentInformation().setCreationDate(GregorianCalendar.from(in.issuedAt().atZone(SEOUL)));

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                Canvas c = new Canvas(stream, font);
                draw(c, in, cancel);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ContractFileException("영수증 PDF 생성 실패: " + in.applicationNo(), e);
        }
    }

    private void draw(Canvas c, Input in, boolean cancel) throws IOException {
        // 제목
        c.down(26);
        c.text(cancel ? "결제 취소 영수증" : "결제 영수증", 22, MARGIN);
        c.down(12);
        c.rule();

        // 사업자 정보 (설정값)
        c.down(8);
        c.row("상호", orDash(business.name()));
        c.row("사업자등록번호", orDash(business.businessNumber()));
        c.row("대표자", orDash(business.representative()));
        c.row("주소", orDash(business.address()));
        c.down(10);
        c.rule();

        // 결제 정보
        c.down(8);
        c.row("신청번호", in.applicationNo());
        c.row("주문번호", in.orderId());
        c.row("카카오 거래번호", in.tid());
        c.row("결제수단", "KAKAOPAY / " + orDash(in.paymentMethod()));
        c.row("승인일시", KST_TIME.format(in.approvedAt().atZone(SEOUL)));
        if (cancel) {
            c.row("취소일시", KST_TIME.format(in.canceledAt().atZone(SEOUL)));
        }
        c.down(10);
        c.rule();

        // 금액 내역: 총 결제금액은 보증금과 첫 달 이용료를 합친 첫 결제금액
        c.down(8);
        c.amount("보증금 (비과세)", won(in.deposit()), BODY);
        c.amount("첫 달 이용료", won(in.firstMonthFee()), BODY);
        c.down(6);
        c.amount("총 결제금액", won(in.totalAmount()), 13);
        if (cancel) {
            c.amount("취소(환불) 금액", won(in.totalAmount()), 13);
        }
        c.down(10);
        c.rule();

        // 안내 문구
        c.down(8);
        c.paragraph(NOTICE, 9, 14);

        // 위변조 확인용 SHA-256
        c.down(14);
        c.paragraph("영수증 SHA-256 (기재 내용 기준)", 8, 12);
        c.mono(in.dataHash(), 8, 12);
        c.paragraph("발급 일시: " + KST_TIME.format(in.issuedAt().atZone(SEOUL)), 8, 12);
        c.paragraph("발급된 PDF 파일의 SHA-256은 서버에 따로 보관되며, 내려받을 때마다 일치하는지 확인합니다.", 8, 12);
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private static String orDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    /** 현재 y 좌표를 들고 다니며 한 줄씩 찍는 작은 도우미 (한 페이지짜리라 쪽 나눔은 없다) */
    private static final class Canvas {
        private final PDPageContentStream s;
        private final PDType0Font font;
        private float y = TOP;

        Canvas(PDPageContentStream s, PDType0Font font) {
            this.s = s;
            this.font = font;
        }

        void down(float h) {
            y -= h;
        }

        void rule() throws IOException {
            s.setLineWidth(0.5f);
            s.moveTo(MARGIN, y);
            s.lineTo(MARGIN + WIDTH, y);
            s.stroke();
        }

        void text(String t, float size, float x) throws IOException {
            String p = printable(t);
            if (p.isEmpty()) return;
            s.beginText();
            s.setFont(font, size);
            s.newLineAtOffset(x, y);
            s.showText(p);
            s.endText();
        }

        // 라벨 | 값. 값이 길면 폭에 맞춰 여러 줄로 나눈다
        void row(String label, String value) throws IOException {
            List<String> lines = wrap(printable(orDash(value)), BODY, WIDTH - LABEL_W);
            y -= ROW;
            text(label, BODY, MARGIN);
            text(lines.get(0), BODY, MARGIN + LABEL_W);
            for (int i = 1; i < lines.size(); i++) {
                y -= 15;
                text(lines.get(i), BODY, MARGIN + LABEL_W);
            }
        }

        // 라벨은 왼쪽, 금액은 오른쪽 끝에 맞춘다
        void amount(String label, String value, float size) throws IOException {
            y -= ROW + (size - BODY);
            text(label, size, MARGIN);
            float w = font.getStringWidth(printable(value)) / 1000 * size;
            text(value, size, MARGIN + WIDTH - w);
        }

        void paragraph(String t, float size, float leading) throws IOException {
            for (String line : wrap(printable(t), size, WIDTH)) {
                y -= leading;
                text(line, size, MARGIN);
            }
        }

        // 해시는 공백이 없으므로 글자 단위로 나뉘지 않게 한 줄로 찍는다 (64자는 폭 안에 들어온다)
        void mono(String t, float size, float leading) throws IOException {
            y -= leading;
            text(t, size, MARGIN);
        }

        // 탭·제어 문자는 제거, 폰트에 없는 글자는 '?'
        private String printable(String line) {
            StringBuilder out = new StringBuilder();
            line.replace("\t", " ").replace("㎡", "m²").codePoints().forEach(cp -> {
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
        private List<String> wrap(String line, float size, float maxWidth) throws IOException {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            int lastSpace = -1;
            for (int cp : line.codePoints().toArray()) {
                cur.appendCodePoint(cp);
                if (cp == ' ') lastSpace = cur.length();
                if (font.getStringWidth(cur.toString()) / 1000 * size <= maxWidth) continue;
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
