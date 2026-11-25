package com.coliving.platform.contract.mail.template;

import com.coliving.platform.contract.mail.ContractMailData;
import com.coliving.platform.contract.mail.ContractMailProperties;
import com.coliving.platform.contract.mail.MailContent;
import com.coliving.platform.contract.mail.MailType;
import org.springframework.stereotype.Component;

import static com.coliving.platform.contract.mail.template.MailLayout.*;

/**
 * 심사 반려: 관리자가 입력한 사유 안내
 *
 * @author 한성현
 */
@Component
public class ApplicationRejectedMailTemplate implements ContractMailTemplate {

    private final ContractMailProperties props;

    public ApplicationRejectedMailTemplate(ContractMailProperties props) {
        this.props = props;
    }

    @Override
    public MailType type() {
        return MailType.APPLICATION_REJECTED;
    }

    @Override
    public MailContent render(ContractMailData d) {
        String subject = "[Coliving] 입주 신청 심사 결과 안내 (" + d.applicationNo() + ")";
        String intro = esc(name(d.applicantName())) + "님, 아쉽게도 입주 신청이 반려되었습니다.<br>"
                + "아래 사유를 확인하신 뒤 새로 신청하실 수 있습니다.";
        String rows = row("신청번호", d.applicationNo())
                + row("호실", room(d.buildingName(), d.roomName()))
                + row("계약 기간", period(d.moveInDate(), d.moveOutDate()))
                + row("반려 사유", d.rejectReason() == null ? "-" : d.rejectReason());
        return new MailContent(subject, page("입주 신청 반려", intro, rows, "내 신청 보기", props.myApplicationUrl()));
    }
}
