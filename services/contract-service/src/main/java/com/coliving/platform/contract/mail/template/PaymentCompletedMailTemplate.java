package com.coliving.platform.contract.mail.template;

import com.coliving.platform.contract.mail.ContractMailData;
import com.coliving.platform.contract.mail.ContractMailProperties;
import com.coliving.platform.contract.mail.MailContent;
import com.coliving.platform.contract.mail.MailType;
import org.springframework.stereotype.Component;

import static com.coliving.platform.contract.mail.template.MailLayout.*;

/**
 * 결제 완료: 계약 확정 + 입주일 안내
 *
 * @author 한성현
 */
@Component
public class PaymentCompletedMailTemplate implements ContractMailTemplate {

    private final ContractMailProperties props;

    public PaymentCompletedMailTemplate(ContractMailProperties props) {
        this.props = props;
    }

    @Override
    public MailType type() {
        return MailType.PAYMENT_COMPLETED;
    }

    @Override
    public MailContent render(ContractMailData d) {
        String subject = "[Coliving] 결제가 완료되었습니다 (" + d.applicationNo() + ")";
        String intro = esc(name(d.applicantName())) + "님, 결제가 완료되어 계약이 확정되었습니다.<br>"
                + "입주일은 <b>" + esc(date(d.moveInDate())) + "</b>입니다.";
        String rows = row("신청번호", d.applicationNo())
                + row("호실", room(d.buildingName(), d.roomName()))
                + row("계약 기간", period(d.moveInDate(), d.moveOutDate()))
                + row("월 이용료", won(d.monthlyFee()))
                + row("보증금", won(d.deposit()))
                + row("결제 금액", won(d.firstPayment()));
        return new MailContent(subject, page("결제 완료", intro, rows, "내 계약 보기", props.myApplicationUrl()));
    }
}
