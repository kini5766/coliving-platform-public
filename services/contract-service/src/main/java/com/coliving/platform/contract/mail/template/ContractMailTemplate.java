package com.coliving.platform.contract.mail.template;

import com.coliving.platform.contract.mail.ContractMailData;
import com.coliving.platform.contract.mail.MailContent;
import com.coliving.platform.contract.mail.MailType;

/**
 * 메일 종류 하나의 제목·본문. 구현체를 @Component로 만들면 ContractMailComposer가 자동으로 등록한다
 *
 * @author 한성현
 */
public interface ContractMailTemplate {
    MailType type();
    MailContent render(ContractMailData data);
}
