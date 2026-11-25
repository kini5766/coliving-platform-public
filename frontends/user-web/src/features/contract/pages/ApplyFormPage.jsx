import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import AmountBox from '../components/AmountBox';
import Button from '../components/Button';
import Card from '../components/Card';
import Checkbox from '../components/Checkbox';
import InfoList from '../components/InfoList';
import PageShell from '../components/PageShell';
import Section from '../components/Section';
import StatusMessage from '../components/StatusMessage';
import StepNav from '../components/StepNav';
import TermsModal from '../components/TermsModal';
import { useApplicationNo, useApply } from '../context/ApplyContext';
import {
  EMPTY_AUTOCOMPLETE,
  getApplyForm,
  getContractAutocomplete,
  isEmail,
  saveContractAutocomplete,
  submitApplication,
} from '../lib/api';
import { amountRows, parseYmd, ymdDay } from '../lib/format';
import { joinAddress, openAddressSearch } from '../lib/postcode';
import { TERM_DOCS } from '../lib/terms';
import { USER_PATHS, goUserWeb } from '../lib/links';

const inputCls =
  'h-11 w-full rounded-lg border border-primary/25 bg-surface px-4 text-sm text-ink outline-none placeholder:text-muted focus:border-primary focus:ring-2 focus:ring-accent';

const TERMS = [
  { id: 'privacy', label: '[필수] 개인정보 수집 및 이용 동의', detail: true },
  { id: 'age', label: '[필수] 만 14세 이상 이용 동의', detail: false },
  { id: 'notice', label: '[필수] 입주 신청 유의사항 확인', detail: true },
];

const LANGS = [
  ['ko', '한국어'],
  ['en', 'English'],
];
const LANG_CODES = LANGS.map(([v]) => v);

function Field({ label, children, hint, required = true }) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-ink">
        {label} {required && <span className="text-warn-fg">*</span>}
      </label>
      {children}
      {hint && <p className="mt-1.5 text-xs text-sub">{hint}</p>}
    </div>
  );
}

export default function ApplyFormPage() {
  const navigate = useNavigate();
  const { setRoom, setMoveIn, setMonths } = useApply();
  const applicationNo = useApplicationNo();

  // GET /contract-service/apply/applications/{applicationNo}/form (호실·기간·금액)
  // + GET /user-service/user/contract-autocomplete (신청자 정보 자동완성)
  const [status, setStatus] = useState('loading'); // loading → ok | error
  const [loadError, setLoadError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [form, setForm] = useState(null);

  // 신청자 정보: 자동완성 값으로 채우고 사용자가 고칠 수 있다
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  // 주소: 기본 주소(address) + 상세 주소(addressDetail). 제출할 때 한 문자열로 합쳐 보낸다 (lib/postcode.js joinAddress)
  const [address, setAddress] = useState('');
  const [addressDetail, setAddressDetail] = useState('');
  // "주소 찾기"로 고른 주소의 한글·영문 표기 { ko, en }. 기본 주소 칸을 직접 고치면 null로 되돌린다
  const [pickedAddress, setPickedAddress] = useState(null);
  const [addressError, setAddressError] = useState(null);
  const [lang, setLang] = useState('ko');
  // 제출 후 이 정보를 계약 자동완성에 저장할지 (다음 신청 때 자동 입력)
  const [remember, setRemember] = useState(true);
  // 동의는 사용자가 직접 체크해야 하므로 처음엔 모두 해제
  const [agreed, setAgreed] = useState({ privacy: false, age: false, notice: false });
  const allAgreed = TERMS.every((t) => agreed[t.id]);
  const setAll = (v) => setAgreed(Object.fromEntries(TERMS.map((t) => [t.id, v])));
  // "전문보기"로 연 약관 id (privacy | notice), 닫혀 있으면 null
  const [openTerm, setOpenTerm] = useState(null);

  // POST .../submit 상태
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  useEffect(() => {
    let ignore = false;
    setStatus('loading');
    Promise.all([
      getApplyForm(applicationNo),
      // 자동완성은 못 불러와도 빈 칸으로 신청할 수 있게 한다. 로그인이 풀린 경우(401)만 에러 화면
      getContractAutocomplete().catch((e) => {
        if (e?.status === 401) throw e;
        return EMPTY_AUTOCOMPLETE;
      }),
    ])
      .then(([f, ac]) => {
        if (ignore) return;
        setForm(f);
        setName(ac.name);
        setPhone(ac.phone);
        setEmail(ac.email);
        setAddress(ac.address);
        // 신청서에 이미 고른 값 > 자동완성 언어(ko/en일 때) > ko
        const acLang = ac.contractLanguage.trim().toLowerCase();
        setLang(f.contractLanguage ?? (LANG_CODES.includes(acLang) ? acLang : 'ko'));
        // 다음 화면(접수 완료 등)이 같은 값을 보여주도록 context도 서버 값으로 맞춤
        setRoom((prev) => ({
          ...prev,
          roomId: f.roomId,
          buildingName: f.buildingName,
          region: f.region,
          roomName: f.roomName,
          roomType: f.roomType,
          monthlyFee: f.monthlyFee,
          managementFee: f.managementFee,
          deposit: f.deposit,
        }));
        setMoveIn(parseYmd(f.moveInDate));
        setMonths(f.months);
        setStatus('ok');
      })
      .catch((e) => {
        if (ignore) return;
        setLoadError(e);
        setStatus('error');
      });
    return () => {
      ignore = true;
    };
  }, [applicationNo, reloadKey, setRoom, setMoveIn, setMonths]);

  if (status === 'loading') {
    return (
      <>
        <StepNav current={1} />
        <StatusMessage title="신청서를 불러오는 중이에요" />
      </>
    );
  }

  if (status === 'error') {
    const s = loadError?.status;
    const retryable = s === 0 || s >= 500;
    const title =
      s === 401
        ? '로그인이 필요해요'
        : s === 403
          ? '본인의 신청서만 볼 수 있어요'
          : s === 404
            ? '신청서를 찾을 수 없어요'
            : s === 409
              ? '이미 제출했거나 작성할 수 없는 신청서예요'
              : '신청서를 불러오지 못했어요';
    return (
      <>
        <StepNav current={1} />
        <StatusMessage
          title={title}
          description={loadError?.message}
          action={
            retryable ? (
              <Button onClick={() => setReloadKey((k) => k + 1)}>다시 시도</Button>
            ) : s === 409 ? (
              <Button variant="outline" onClick={() => navigate(`/apply/${applicationNo}/submitted`)}>
                신청 현황 보기
              </Button>
            ) : (
              <Button variant="outline" onClick={() => goUserWeb(navigate, USER_PATHS.home)}>
                홈으로
              </Button>
            )
          }
        />
      </>
    );
  }

  // 주소 찾기로 고른 주소는 계약서 언어를 따라간다 (English면 영문 주소). 직접 입력한 주소는 그대로
  const baseAddress = pickedAddress ? pickedAddress[lang] ?? pickedAddress.ko : address;

  const searchAddress = async () => {
    setAddressError(null);
    try {
      await openAddressSearch(setPickedAddress);
    } catch (e) {
      setAddressError(e.message);
    }
  };

  const submit = async () => {
    if (submitting) return;
    const applicant = {
      name: name.trim(),
      phone: phone.trim(),
      email: email.trim(),
      address: joinAddress(baseAddress, addressDetail, lang),
    };
    if (!applicant.name) return setSubmitError('이름을 입력해주세요.');
    if (!applicant.phone) return setSubmitError('휴대폰 번호를 입력해주세요.');
    if (!applicant.email) return setSubmitError('이메일을 입력해주세요.');
    if (!isEmail(applicant.email)) return setSubmitError('이메일 형식이 올바르지 않아요.');
    if (applicant.address.length > 500) return setSubmitError('주소는 500자까지 입력할 수 있어요.');
    setSubmitting(true);
    setSubmitError(null);
    try {
      await submitApplication(applicationNo, {
        ...applicant,
        address: applicant.address || null,
        contractLang: lang,
        termsVersion: form.termsVersion, // 화면에 보였던 약관 버전을 그대로 돌려보냄
      });
      if (remember) {
        // 다음 신청 때 자동 입력되도록 저장. 실패해도 신청은 이미 끝났으므로 무시
        await saveContractAutocomplete({ ...applicant, contractLanguage: lang }).catch(() => {});
      }
      navigate(`/apply/${applicationNo}/submitted`);
    } catch (e) {
      setSubmitError(e.message);
      setSubmitting(false);
    }
  };

  return (
    <>
      <StepNav current={1} />
      <PageShell
        main={
          <>
            <Section title="신청자 정보">
              <Card className="space-y-5 p-6">
                <Field label="이름" hint="본인인증 때 확인하는 이름이에요. 실명으로 입력해주세요.">
                  <input
                    className={inputCls}
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    autoComplete="name"
                    placeholder="홍길동"
                  />
                </Field>
                <Field label="이메일" hint="영수증이 이 주소로 발송돼요.">
                  <input
                    className={inputCls}
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    autoComplete="email"
                    placeholder="example@email.com"
                  />
                </Field>
                <Field label="휴대폰 번호" hint="본인인증 때 확인하는 번호예요.">
                  <input
                    className={inputCls}
                    type="tel"
                    inputMode="numeric"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    autoComplete="tel"
                    placeholder="01012345678"
                  />
                </Field>
                <Field
                  label="주소"
                  required={false}
                  hint={
                    pickedAddress && lang === 'en'
                      ? '계약서 언어가 English라 영문 주소로 들어가요.'
                      : undefined
                  }
                >
                  <div className="space-y-2">
                    <div className="flex gap-2">
                      <input
                        className={inputCls}
                        value={baseAddress}
                        onChange={(e) => {
                          // 직접 고치면 검색 결과와의 연결을 끊는다 (언어를 바꿔도 덮어쓰지 않게)
                          setPickedAddress(null);
                          setAddress(e.target.value);
                        }}
                        autoComplete="address-line1"
                        placeholder="주소 검색"
                      />
                      <Button variant="outline" className="shrink-0" onClick={searchAddress}>
                        주소 찾기
                      </Button>
                    </div>
                    <input
                      className={inputCls}
                      value={addressDetail}
                      onChange={(e) => setAddressDetail(e.target.value)}
                      autoComplete="address-line2"
                      placeholder="상세 주소 (동·호수 등)"
                    />
                    {addressError && (
                      <p role="alert" className="text-xs text-warn-fg">
                        {addressError}
                      </p>
                    )}
                  </div>
                </Field>
                <div className="border-t border-line pt-4">
                  <Checkbox checked={remember} onChange={setRemember}>
                    다음 신청에도 이 정보 사용 (계약 자동완성에 저장)
                  </Checkbox>
                </div>
              </Card>
            </Section>

            <Section title="계약서 언어">
              <div className="grid grid-cols-2 gap-3">
                {LANGS.map(([v, label]) => (
                  <button
                    type="button"
                    key={v}
                    onClick={() => setLang(v)}
                    className={`h-12 rounded-lg border text-sm font-medium transition-colors ${
                      lang === v ? 'border-primary bg-primary-soft text-primary-strong' : 'border-line bg-surface text-ink hover:bg-soft'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </Section>

            <Section title="약관 동의">
              <Card>
                <div className="border-b border-line px-5 py-4">
                  <Checkbox checked={allAgreed} onChange={setAll} bold>
                    전체 동의
                  </Checkbox>
                </div>
                <ul className="space-y-3.5 px-5 py-4">
                  {TERMS.map((t) => (
                    <li key={t.id} className="flex items-center justify-between">
                      <Checkbox checked={agreed[t.id]} onChange={(v) => setAgreed({ ...agreed, [t.id]: v })}>
                        {t.label}
                      </Checkbox>
                      {t.detail && (
                        <button
                          type="button"
                          className="text-xs text-sub hover:text-ink"
                          onClick={() => setOpenTerm(t.id)}
                        >
                          전문보기 ›
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              </Card>
              {openTerm && TERM_DOCS[openTerm] && (
                <TermsModal
                  doc={TERM_DOCS[openTerm]}
                  onClose={() => setOpenTerm(null)}
                  onAgree={() => {
                    setAgreed((prev) => ({ ...prev, [openTerm]: true }));
                    setOpenTerm(null);
                  }}
                />
              )}
            </Section>
          </>
        }
        aside={
          <>
            <InfoList
              rows={[
                ['호실', `${form.buildingName} ${form.roomName}`],
                ['입주일', ymdDay(parseYmd(form.moveInDate))],
                ['계약 기간', `${form.months}개월`],
                ['퇴실 예정일', ymdDay(parseYmd(form.moveOutDate))],
                ...amountRows(form),
              ]}
            />
            {/* 서버가 계산한 값 (Contract.firstPayment). 1-1 화면 계산식과 다르면 정책 확인 필요 */}
            <AmountBox amount={form.firstPaymentAmount} />
            <p className="rounded-lg border border-warn-line bg-warn-bg/60 px-5 py-3.5 text-sm leading-relaxed text-ink">
              신청서를 제출하면 관리자 심사가 시작돼요. 승인 후 계약서 확인과 결제를 진행해요.
            </p>
            <div className="space-y-2 pt-1">
              {submitError && (
                <p role="alert" className="rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
                  {submitError}
                </p>
              )}
              <Button className="w-full" disabled={!allAgreed || submitting} onClick={submit}>
                {submitting ? '제출 중…' : '신청서 제출'}
              </Button>
              <Button
                variant="outline"
                className="w-full"
                onClick={() =>
                  navigate(
                    `/apply/room?roomId=${form.roomId}&applicationNo=${applicationNo}&moveInDate=${form.moveInDate}&months=${form.months}`,
                  )
                }
              >
                ‹ 이전
              </Button>
            </div>
          </>
        }
      />
    </>
  );
}
