package com.coliving.platform.contract.mail;

import com.coliving.platform.contract.mail.template.ContractMailTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * MailType → 템플릿을 찾아 메일을 만든다.
 * 템플릿이 빠졌거나 같은 종류가 두 개면 서버 시작 때 실패한다 (보내려는 순간 알게 되는 것보다 낫다).
 *
 * @author 한성현
 */
@Component
public class ContractMailComposer {

    private final Map<MailType, ContractMailTemplate> templates = new EnumMap<>(MailType.class);

    public ContractMailComposer(List<ContractMailTemplate> list) {
        for (ContractMailTemplate t : list) {
            if (templates.put(t.type(), t) != null) {
                throw new IllegalStateException("메일 템플릿이 중복됩니다: " + t.type());
            }
        }
        for (MailType type : MailType.values()) {
            if (!templates.containsKey(type)) {
                throw new IllegalStateException("메일 템플릿이 없습니다: " + type);
            }
        }
    }

    public MailContent compose(ContractMailEvent event) {
        return templates.get(event.type()).render(event.data());
    }
}
