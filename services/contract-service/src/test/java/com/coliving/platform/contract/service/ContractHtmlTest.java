package com.coliving.platform.contract.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContractHtmlTest {

    @Test
    void 입력값의_태그와_중괄호는_글자_그대로_들어가게_바꾼다() {
        assertThat(ContractHtml.escape("<b>홍&\"'{{contractDate}}"))
                .isEqualTo("&lt;b&gt;홍&amp;&quot;&#39;&#123;&#123;contractDate&#125;&#125;");
        assertThat(ContractHtml.escape(null)).isEmpty();
    }

    @Test
    void HTML은_문단마다_줄을_나누고_문자_참조를_되돌린다() {
        String html = "<h1>계약서</h1>\n<p>임차인: A &amp; B</p><p>주소:<br>서울</p><ul><li>욕실</li><li>발코니</li></ul>"
                + "<table><tr><td>월세</td><td>100</td></tr></table><p>&#123;&#123;x&#125;&#125;&nbsp;끝</p>";

        assertThat(ContractHtml.toPlainText(html)).isEqualTo(
                "계약서\n임차인: A & B\n주소:\n서울\n• 욕실\n• 발코니\n월세 | 100 |\n{{x}} 끝");
    }

    @Test
    void 태그가_없는_일반_텍스트는_그대로() {
        assertThat(ContractHtml.toPlainText("계약서\n\n임차인: A < B")).isEqualTo("계약서\n\n임차인: A < B");
    }
}
