/**
 * @author 조휘재
 * 작성일: 2026-11-05
 */

import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

const ko = {
  title: '개인정보처리방침',
  version: '문서 버전 privacy-2026-11-05 · 시행일 2026년 11월 5일',
  lead: '룸링크(이하 "회사")는 공유 주거 서비스 이용자의 개인정보를 보호하고, 관련 고충을 빠르게 처리하기 위해 이 방침을 공개합니다. 이 방침은 룸링크 웹사이트와 그 안에서 제공하는 입주 신청, 계약, 결제, 커뮤니티, 공용시설 예약, 고객 문의에 적용됩니다.',
  sections: [
    {
      heading: '1. 수집하는 개인정보',
      body: '회사는 서비스에 필요한 최소한의 개인정보만 수집합니다. 선택 항목을 제공하지 않아도 그 기능 외의 서비스는 이용할 수 있습니다.',
      table: {
        head: ['구분', '항목', '목적', '보유 기간'],
        rows: [
          ['회원가입', '이메일, 비밀번호', '회원 식별, 로그인, 공지', '회원 탈퇴 시까지'],
          ['소셜 로그인', '이메일, 이름, 제공자 식별자', '네이버 또는 Google 계정으로 가입·로그인', '회원 탈퇴 시까지'],
          ['프로필', '닉네임', '커뮤니티에서 회원을 표시', '회원 탈퇴 또는 삭제 시까지'],
          ['입주 신청·계약', '이름, 이메일, 휴대폰 번호, 주소, 계약서 언어, 호실, 입주일, 계약 기간', '신청 접수, 심사, 계약서 작성, 입주 안내', '신청 반려·취소 후 30일, 계약이 성립하면 계약 종료 후 법령상 기간'],
          ['본인인증', '인증 결과와 일치 여부. 연계정보(CI)·중복가입확인정보(DI)는 저장하지 않음', '계약 서명 전 이름·휴대폰 번호 확인', '인증 처리에 필요한 기간'],
          ['결제', '주문번호, 결제 상태, 결제 금액. 카드번호는 저장하지 않음', '보증금·이용료 결제와 환불', '법령상 보존 기간'],
          ['커뮤니티', '게시글, 댓글, 채팅, 신고·차단 기록', '입주민 소통, 안전, 분쟁 확인', '삭제 또는 회원 탈퇴 시까지. 분쟁 기록은 처리 종료 후 3년'],
          ['공용시설 예약', '회원 식별자, 시설, 예약 시간', '예약 접수와 이용 관리', '예약 종료 후 1년'],
          ['비회원 문의', '이름, 전화번호, 이메일, 문의 내용', '문의 답변', '답변 완료 후 30일'],
          ['FAQ 질문', '질문 내용, 회원 식별자', '답변 제공, 답하지 못한 질문 확인. 화면에는 회원 식별자를 표시하지 않음', '90일'],
        ],
      },
    },
    {
      heading: '2. 수집 방법',
      items: [
        '회원가입, 프로필, 입주 신청, 문의 화면에서 이용자가 직접 입력합니다.',
        '네이버 또는 Google 로그인을 선택한 경우 해당 사업자로부터 이메일 등 인증에 필요한 정보를 받습니다.',
        '서비스 이용 과정에서 접속 일시, IP 주소, 쿠키가 자동으로 만들어질 수 있습니다.',
      ],
    },
    {
      heading: '3. 14세 미만 아동',
      body: '룸링크는 14세 미만 아동을 대상으로 하지 않으며, 법정대리인 동의 없이 아동의 개인정보를 수집하지 않습니다. 입주 신청에서는 성년 여부를 확인합니다.',
    },
    {
      heading: '4. 제3자 제공',
      body: '회사는 이용자의 별도 동의 또는 법령에 따른 요청이 없으면 개인정보를 제3자에게 판매하거나 제공하지 않습니다. 같은 건물의 커뮤니티에 이용자가 직접 올린 글과 닉네임은 그 공간의 다른 이용자에게 보입니다.',
    },
    {
      heading: '5. 처리 위탁과 국외 이전',
      body: '서비스 운영에 필요한 업무를 아래 업체에 맡깁니다. 위탁한 정보가 위탁 목적 밖에서 쓰이지 않도록 계약을 두고 관리합니다. Google 서비스는 이용자 정보가 대한민국 밖으로 전송될 수 있습니다. Google 로그인을 쓰지 않으면 이메일 회원가입을 이용할 수 있고, FAQ 봇은 이용하지 않을 수 있습니다.',
      table: {
        head: ['수탁자', '맡기는 일', '이전 항목'],
        rows: [
          ['카카오페이', '결제 승인, 취소, 환불', '주문번호, 결제 금액, 결제 상태'],
          ['포트원', '휴대폰 본인인증', '인증 요청 정보. CI·DI는 룸링크에 저장하지 않음'],
          ['네이버', '소셜 로그인', '이메일, 이름, 제공자 식별자'],
          ['Google LLC (미국)', '소셜 로그인, FAQ 답변 생성(Gemini API)', '로그인 계정 정보. FAQ 질문과 답변에 필요한 문서 조각'],
        ],
      },
    },
    {
      heading: '6. 보유와 파기',
      body: '보유 기간이 끝나거나 처리 목적이 달성되면 지체 없이 파기합니다. 전자 파일은 복구할 수 없는 방법으로 삭제합니다. 관계 법령이 보관을 요구하면 그 기간만 따로 보관하고 다른 용도로 쓰지 않습니다.',
      table: {
        head: ['보존 항목', '기간', '근거'],
        rows: [
          ['계약 또는 청약철회 기록', '5년', '전자상거래법'],
          ['대금결제와 재화 공급 기록', '5년', '전자상거래법'],
          ['소비자 불만·분쟁 처리 기록', '3년', '전자상거래법'],
          ['표시·광고 기록', '6개월', '전자상거래법'],
          ['전자금융 거래 기록', '5년', '전자금융거래법'],
          ['접속 기록', '3개월', '통신비밀보호법'],
        ],
      },
    },
    {
      heading: '7. 이용자의 권리',
      items: [
        '자신의 개인정보를 열람·정정·삭제·처리정지 요청할 수 있습니다.',
        '계정 설정에서 회원 탈퇴를 요청할 수 있고, 동의는 고객 문의를 통해 철회할 수 있습니다.',
        '요청은 본인 확인 후 처리합니다. 법정대리인이나 위임을 받은 사람도 요청할 수 있습니다.',
        '법령상 보관 중인 정보는 그 기간이 끝날 때까지 삭제가 제한될 수 있습니다.',
      ],
    },
    {
      heading: '8. 쿠키',
      body: '로그인 유지에 필요한 쿠키를 사용합니다. 쿠키에는 이름이나 전화번호를 넣지 않습니다. 브라우저 설정에서 쿠키를 거부할 수 있으며, 거부하면 로그인이 필요한 기능을 쓸 수 없습니다.',
    },
    {
      heading: '9. 안전성 확보조치',
      items: [
        '비밀번호는 저장 시 암호화하고, 계약에 필요한 개인정보는 암호화해 보관합니다.',
        '개인정보를 다루는 권한은 업무에 필요한 범위로 나눕니다.',
        '접속 기록과 보안 프로그램을 점검합니다.',
      ],
    },
    {
      heading: '10. 개인정보 보호책임자',
      table: {
        head: ['구분', '내용'],
        rows: [
          ['서비스', '룸링크'],
          ['책임자', '조휘재'],
          ['주소', '서울 마포구 백범로 3 3층 하이미디어'],
          ['연락처', 'kini5766@naver.com'],
        ],
      },
    },
    {
      heading: '11. 권익침해 구제',
      items: [
        '개인정보침해 신고센터: 118, privacy.kisa.or.kr',
        '개인정보 분쟁조정위원회: 1833-6972, www.kopico.go.kr',
        '대검찰청 사이버수사과: 1301, www.spo.go.kr',
        '경찰청 사이버수사국: 182, ecrm.police.go.kr',
      ],
    },
    {
      heading: '12. 방침의 변경',
      body: '법령이나 서비스가 바뀌면 이 방침을 고칩니다. 수집 항목, 목적, 제3자 제공처럼 중요한 변경은 시행 7일 전에 서비스 안에 공지합니다. 이 방침은 2026년 11월 5일부터 적용됩니다.',
    },
  ],
}

const en = {
  title: 'Privacy policy',
  version: 'Version privacy-2026-11-05 · Effective 5 November 2026',
  lead: 'Roomlink ("we") publishes this policy to protect personal information of people who use the shared-housing service and to handle related requests. It covers the Roomlink website and move-in applications, contracts, payments, community, shared-facility bookings, and support requests offered there.',
  sections: [
    {
      heading: '1. Information we collect',
      body: 'We collect only what each feature needs. You can skip optional features and still use the rest of the service.',
      table: {
        head: ['Feature', 'Items', 'Purpose', 'Retention'],
        rows: [
          ['Sign-up', 'Email, password', 'Account and notices', 'Until the account is deleted'],
          ['Social login', 'Email, name, provider identifier', 'Sign-in with Naver or Google', 'Until the account is deleted'],
          ['Profile', 'Nickname', 'Show you in the community', 'Until deleted or the account is closed'],
          ['Move-in and contract', 'Name, email, mobile number, address, contract language, room, move-in date, term', 'Application, review, contract, move-in notices', '30 days after a rejection or cancellation. After a contract is formed, for the statutory period after it ends'],
          ['Identity check', 'Match result only. We do not store CI or DI', 'Confirm name and mobile number before signing', 'For the time needed to complete the check'],
          ['Payment', 'Order id, status, amount. We do not store card numbers', 'Deposit and rent payment, refunds', 'Statutory retention period'],
          ['Community', 'Posts, comments, chat, reports, blocks', 'Resident communication and safety', 'Until deleted or the account is closed. Dispute records for 3 years after the case closes'],
          ['Facility booking', 'User id, facility, time', 'Booking and use', '1 year after the booking ends'],
          ['Guest inquiry', 'Name, phone, email, message', 'Reply to the inquiry', '30 days after the reply'],
          ['FAQ question', 'Question text, user id', 'Answer and review unanswered questions. The user id is not shown', '90 days'],
        ],
      },
    },
    {
      heading: '2. How we collect it',
      items: [
        'You enter it in sign-up, profile, application, and inquiry screens.',
        'If you choose Naver or Google login, we receive the identity details needed to sign you in.',
        'Use of the service may create access time, IP address, and cookies.',
      ],
    },
    {
      heading: '3. Children under 14',
      body: 'Roomlink is not directed at children under 14, and we do not knowingly collect their personal information without a guardian’s consent. Move-in applications ask you to confirm that you are an adult.',
    },
    {
      heading: '4. Sharing',
      body: 'We do not sell personal information. We do not share it with third parties unless you consent or the law requires it. Posts and nicknames you publish in a building community are visible to other people in that space.',
    },
    {
      heading: '5. Processors and overseas transfer',
      body: 'We use the processors below to run the service. Google services may transfer information outside Korea. You can create an email account instead of using Google login, and you can choose not to use the FAQ bot.',
      table: {
        head: ['Processor', 'Task', 'Items'],
        rows: [
          ['Kakao Pay', 'Payment approval, cancel, refund', 'Order id, amount, payment status'],
          ['PortOne', 'Mobile identity check', 'Verification request. CI and DI are not stored by Roomlink'],
          ['Naver', 'Social login', 'Email, name, provider identifier'],
          ['Google LLC (United States)', 'Social login and FAQ answers (Gemini API)', 'Login account details. FAQ question text and the document excerpts needed to answer'],
        ],
      },
    },
    {
      heading: '6. Retention and deletion',
      body: 'We delete information when the retention period ends or the purpose is achieved. Electronic files are deleted so they cannot be restored. Where a statute requires longer storage, we keep the information only for that period and do not use it for anything else.',
      table: {
        head: ['Record', 'Period', 'Law'],
        rows: [
          ['Contract or withdrawal records', '5 years', 'E-Commerce Act'],
          ['Payment and supply records', '5 years', 'E-Commerce Act'],
          ['Complaint and dispute records', '3 years', 'E-Commerce Act'],
          ['Advertising records', '6 months', 'E-Commerce Act'],
          ['Electronic financial transaction records', '5 years', 'Electronic Financial Transactions Act'],
          ['Access logs', '3 months', 'Protection of Communications Secrets Act'],
        ],
      },
    },
    {
      heading: '7. Your rights',
      items: [
        'You may ask to access, correct, delete, or stop processing of your personal information.',
        'You may delete your account in settings, and you may withdraw consent by contacting us.',
        'We verify the requester. A guardian or an authorized agent may also make the request.',
        'Information we must keep by law cannot be deleted until that period ends.',
      ],
    },
    {
      heading: '8. Cookies',
      body: 'We use a cookie to keep you signed in. It does not store your name or phone number. You can refuse cookies in your browser. Sign-in features will not work without that cookie.',
    },
    {
      heading: '9. Safeguards',
      items: [
        'Passwords are stored in hashed form, and personal information needed for a contract is encrypted.',
        'Access is limited to people who need it for their work.',
        'We review access records and security software.',
      ],
    },
    {
      heading: '10. Privacy contact',
      table: {
        head: ['Item', 'Detail'],
        rows: [
          ['Service', 'Roomlink'],
          ['Contact', 'Hwija Cho'],
          ['Address', '3F, High Media, 3 Baekbeom-ro, Mapo-gu, Seoul'],
          ['Email', 'kini5766@naver.com'],
        ],
      },
    },
    {
      heading: '11. Complaints',
      items: [
        'KISA privacy report center: 118, privacy.kisa.or.kr',
        'Personal Information Dispute Mediation Committee: 1833-6972, www.kopico.go.kr',
        'Supreme Prosecutors’ Office cyber investigation: 1301, www.spo.go.kr',
        'Korean National Police cyber bureau: 182, ecrm.police.go.kr',
      ],
    },
    {
      heading: '12. Changes',
      body: 'We update this policy when the law or the service changes. Material changes to items, purposes, or sharing are posted in the service at least 7 days before they take effect. This policy applies from 5 November 2026.',
    },
  ],
}

/** 방침 표. 가로로 넘치면 스크롤한다. */
function Table({ table }) {
  return (
    <div className="mt-4 overflow-x-auto rounded-lg border border-main/15">
      <table className="w-full min-w-[40rem] border-collapse text-left text-sm">
        <thead className="bg-main/5 text-main">
          <tr>
            {table.head.map((cell) => (
              <th key={cell} className="px-3 py-2 font-semibold">{cell}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {table.rows.map((row) => (
            <tr key={row.join('|')} className="border-t border-main/10 align-top">
              {row.map((cell) => (
                <td key={cell} className="px-3 py-2 leading-6 text-text/80">{cell}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** 현재 언어의 개인정보 처리 방침을 보여 준다. */
function PrivacyPolicyPage() {
  const { i18n } = useTranslation('common')
  const copy = i18n.language?.startsWith('en') ? en : ko

  useEffect(() => {
    document.title = copy.title
  }, [copy.title])

  return (
    <article className="px-4 py-6 sm:px-8 sm:py-8 md:px-12 md:py-10">
      <div className="mx-auto max-w-3xl">
        <h1 className="text-2xl font-semibold text-main sm:text-3xl">{copy.title}</h1>
        <p className="mt-2 text-xs text-text/60">{copy.version}</p>
        <p className="mt-4 text-sm leading-6 text-text/80">{copy.lead}</p>
        {copy.sections.map((section) => (
          <section key={section.heading}>
            <h2 className="mt-8 text-lg font-semibold text-main">{section.heading}</h2>
            {section.body && <p className="mt-3 text-sm leading-6 text-text/80">{section.body}</p>}
            {section.items && (
              <ul className="mt-3 list-disc space-y-1 pl-5 text-sm leading-6 text-text/80">
                {section.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            )}
            {section.table && <Table table={section.table} />}
          </section>
        ))}
      </div>
    </article>
  )
}

export default PrivacyPolicyPage
