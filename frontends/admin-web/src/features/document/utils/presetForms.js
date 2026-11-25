// 사전 양식: 서버가 아니라 프런트에 둔 기본 문안. 버튼을 누르면 생성 화면에 채워진다.
// 형식(HTML 태그)과 빈칸 이름은 DB의 시드 양식과 같게 맞춘다.
export const PRESET_FORMS = [
    {
        language: 'ko',
        label: '한글 양식',
        title: '기본 임대차 계약서',
        content: `<h1>임대차 계약서</h1>
<p>임차인: {{applicantName}} ({{applicantEmail}}, {{applicantPhone}})</p>
<p>주소: {{applicantAddress}}</p>
<p>대상 호실: {{buildingName}} {{roomName}}</p>
<p>입주일: {{moveInDate}} / 계약 기간: {{contractMonths}}</p>
<p>월 이용료: {{monthlyFee}} / 보증금: {{deposit}}</p>
<p>첫 결제 금액: {{firstPayment}}</p>`,
    },
    {
        language: 'en',
        label: '영어 양식',
        title: 'Standard Lease Agreement',
        content: `<h1>Lease Agreement</h1>
<p>Lessee: {{applicantName}} ({{applicantEmail}}, {{applicantPhone}})</p>
<p>Address: {{applicantAddress}}</p>
<p>Room: {{buildingName}} {{roomName}}</p>
<p>Move-in date: {{moveInDate}} / Term: {{contractMonths}}</p>
<p>Monthly fee: {{monthlyFee}} / Deposit: {{deposit}}</p>
<p>First payment: {{firstPayment}}</p>`,
    },
]