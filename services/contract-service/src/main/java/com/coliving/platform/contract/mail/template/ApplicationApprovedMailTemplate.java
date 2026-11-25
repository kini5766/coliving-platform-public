package com.coliving.platform.contract.mail.template;

import com.coliving.platform.contract.mail.ContractMailData;
import com.coliving.platform.contract.mail.ContractMailProperties;
import com.coliving.platform.contract.mail.MailContent;
import com.coliving.platform.contract.mail.MailType;
import org.springframework.stereotype.Component;

import static com.coliving.platform.contract.mail.template.MailLayout.*;

/**
 * 심사 승인: 결제 기한 안에 결제하라는 안내
 *
 * @author 한성현
 */
@Component
public class ApplicationApprovedMailTemplate implements ContractMailTemplate {

    private final ContractMailProperties props;

    public ApplicationApprovedMailTemplate(ContractMailProperties props) {
        this.props = props;
    }

    @Override
    public MailType type() {
        return MailType.APPLICATION_APPROVED;
    }

    @Override
    public MailContent render(ContractMailData d) {
        String subject = "[Coliving] 입주 신청이 승인되었습니다 (" + d.applicationNo() + ")";
        String intro = esc(name(d.applicantName())) + "님, 입주 신청이 승인되었습니다.<br>"
                + "<b>" + esc(dateTime(d.expiresAt())) + "</b>까지 계약서 서명과 결제를 마쳐 주세요. "
                + "기한이 지나면 신청이 자동으로 만료됩니다.";
        String rows = row("신청번호", d.applicationNo())
                + row("호실", room(d.buildingName(), d.roomName()))
                + row("계약 기간", period(d.moveInDate(), d.moveOutDate()))
                + row("결제 금액", won(d.firstPayment()))
                + row("결제 기한", dateTime(d.expiresAt()));
        return new MailContent(subject, page("입주 신청 승인", intro, rows, "서명·결제하러 가기", props.myApplicationUrl()));
    }
}
