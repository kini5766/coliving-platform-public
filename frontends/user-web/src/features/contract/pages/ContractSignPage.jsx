import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import AmountBox from '../components/AmountBox';
import Badge from '../components/Badge';
import Button from '../components/Button';
import Card from '../components/Card';
import Checkbox from '../components/Checkbox';
import ContractDocumentView from '../components/ContractDocumentView';
import InfoList from '../components/InfoList';
import PageShell from '../components/PageShell';
import Section from '../components/Section';
import SignaturePad from '../components/SignaturePad';
import StatusMessage from '../components/StatusMessage';
import StepNav from '../components/StepNav';
import { useApplicationNo } from '../context/ApplyContext';
import { confirmIdentity, getApplication, getContractDocument, signContract } from '../lib/api';
import { IdentityCanceledError, openIdentityVerification, readIdentityRedirect } from '../lib/portone';

// "2026-11-03T10:00:00+09:00" → "2026-11-03 10:00"
const ymdHm = (s) => (s ? s.slice(0, 16).replace('T', ' ') : '-');

export default function ContractSignPage() {
  const navigate = useNavigate();
  const applicationNo = useApplicationNo();

  // 화면 데이터: 신청 상세(상태·결제 기한) + 계약서 본문
  const [status, setStatus] = useState('loading'); // loading → ok | error
  const [loadError, setLoadError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [app, setApp] = useState(null);
  const [doc, setDoc] = useState(null);

  const [read, setRead] = useState(false);
  const [signed, setSigned] = useState(false);
  const [clearSignal, setClearSignal] = useState(0);

  // 본인인증: idle → verifying → verified | (에러 문구)
  const [verify, setVerify] = useState('idle');
  const [verifyError, setVerifyError] = useState(null);
  const identityIdRef = useRef(null); // 서명 제출 때 같은 ID로 서버가 한 번 더 확인한다

  // 서명 제출
  const padRef = useRef(null);
  const [submitting, setSubmitting] = useState(false);
  const [signError, setSignError] = useState(null);

  // GET /apply/applications/{no} + GET /contracts/{no}/document
  useEffect(() => {
    let ignore = false;
    setStatus('loading');
    Promise.all([getApplication(applicationNo), getContractDocument(applicationNo)])
      .then(([a, d]) => {
        if (ignore) return;
        setApp(a);
        setDoc(d);
        if (d.identityVerified) setVerify('verified');
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
  }, [applicationNo, reloadKey]);

  // 서버에 인증 결과 확인 요청 (포트원 조회 + 신청자 이름·휴대폰 비교는 서버가 한다)
  const confirmWithServer = async (identityVerificationId) => {
    setVerify('verifying');
    setVerifyError(null);
    try {
      await confirmIdentity(applicationNo, identityVerificationId);
      identityIdRef.current = identityVerificationId;
      setVerify('verified');
    } catch (e) {
      setVerify('idle');
      setVerifyError(e.message);
    }
  };

  // 모바일 등에서 리디렉션 방식으로 인증하고 돌아온 경우: URL의 결과를 이어서 처리
  useEffect(() => {
    const r = readIdentityRedirect();
    if (!r) return;
    if (r.code) {
      setVerifyError(r.message ?? '본인인증에 실패했어요.');
      return;
    }
    confirmWithServer(r.identityVerificationId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // "인증하기" 버튼: 포트원 창 열기 → 서버 확인
  const startVerify = async () => {
    if (verify === 'verifying') return;
    setVerifyError(null);
    try {
      const id = await openIdentityVerification(applicationNo);
      await confirmWithServer(id);
    } catch (e) {
      if (e instanceof IdentityCanceledError) return; // 사용자가 창을 닫음 → 문구 없이 그대로
      setVerify('idle');
      setVerifyError(e.message);
    }
  };

  // "서명 완료하고 결제하기": 서버 저장이 성공해야만 결제로 이동한다
  const submitSignature = async () => {
    if (submitting) return;
    setSubmitting(true);
    setSignError(null);
    try {
      await signContract(applicationNo, {
        identityVerificationId: identityIdRef.current,
        contentHash: doc.contentHash,
        signatureImage: padRef.current.toBase64(),
      });
      navigate(`/contract/${applicationNo}/pay`);
    } catch (e) {
      setSignError(e.message);
      setSubmitting(false);
    }
  };

  if (status === 'loading') {
    return (
      <>
        <StepNav current={2} />
        <StatusMessage title="계약서를 불러오는 중이에요" />
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
          ? '본인의 계약서만 볼 수 있어요'
          : s === 404
            ? '아직 계약서가 발급되지 않았어요'
            : '계약서를 불러오지 못했어요';
    return (
      <>
        <StepNav current={2} />
        <StatusMessage
          title={title}
          description={loadError?.message}
          action={
            retryable ? (
              <Button onClick={() => setReloadKey((k) => k + 1)}>다시 시도</Button>
            ) : (
              <Button variant="outline" onClick={() => navigate(`/apply/${applicationNo}/submitted`)}>
                신청 현황 보기
              </Button>
            )
          }
        />
      </>
    );
  }

  // 서명 단계는 APPROVED(승인 · 결제 대기)에서만
  if (app.status !== 'APPROVED') {
    return (
      <>
        <StepNav current={2} />
        <StatusMessage
          title="지금은 계약서에 서명할 수 없어요"
          description={`현재 상태: ${app.status}`}
          action={
            <Button variant="outline" onClick={() => navigate(`/apply/${applicationNo}/submitted`)}>
              신청 현황 보기
            </Button>
          }
        />
      </>
    );
  }

  // 이미 서명했으면 (결제 중단 후 다시 온 경우 등) 바로 결제로 보낸다
  if (doc.signed) {
    return (
      <>
        <StepNav current={2} />
        <StatusMessage
          title="계약서 서명을 마쳤어요"
          description="결제를 진행하면 계약이 완료돼요."
          action={<Button onClick={() => navigate(`/contract/${applicationNo}/pay`)}>결제하기</Button>}
        />
      </>
    );
  }

  const verified = verify === 'verified';
  const ready = read && verified && signed && !submitting;

  return (
    <>
      <StepNav current={2} />
      <PageShell
        main={
          <>
            <Section title="계약서 확인">
              {/* 서버에 저장된 계약서 완성본 (신청서 제출 때 생성, 수정 불가). HTML이라 격리된 iframe으로 보여준다 */}
              <ContractDocumentView content={doc.content} className="h-96" />
              <Card className="mt-3 px-5 py-4">
                <Checkbox checked={read} onChange={setRead}>
                  계약서 내용을 모두 확인했어요
                </Checkbox>
              </Card>
            </Section>

            <Section title="본인 인증">
              <Card className="flex items-center justify-between px-5 py-4">
                <div>
                  <p className="text-sm font-semibold">휴대폰 본인 인증</p>
                  <p className="mt-0.5 text-xs text-sub">신청자 본인 명의 휴대폰으로 인증해주세요.</p>
                </div>
                {verified ? (
                  <Badge tone="ok">인증 완료</Badge>
                ) : (
                  <Button size="sm" disabled={verify === 'verifying'} onClick={startVerify}>
                    {verify === 'verifying' ? '확인 중…' : '인증하기'}
                  </Button>
                )}
              </Card>
              {verifyError && (
                <p role="alert" className="mt-2 rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
                  {verifyError}
                </p>
              )}
            </Section>

            <Section title="전자 서명">
              <SignaturePad ref={padRef} onChange={setSigned} clearSignal={clearSignal} />
              <div className="mt-3">
                <Button variant="outline" size="sm" onClick={() => setClearSignal((n) => n + 1)}>
                  지우기
                </Button>
              </div>
            </Section>
          </>
        }
        aside={
          <>
            <InfoList
              rows={[
                ['신청 번호', applicationNo],
                ['호실', `${doc.buildingName} ${doc.roomName}`],
                ['계약 기간', `${doc.moveInDate} ~ ${doc.moveOutDate}`],
                ['심사 결과', <Badge tone="ok">승인</Badge>],
                ['결제 기한', ymdHm(app.paymentDeadline)],
              ]}
            />
            <AmountBox amount={doc.firstPaymentAmount} />
            <Button className="w-full" disabled={!ready} onClick={submitSignature}>
              {submitting ? '서명 저장 중…' : '서명 완료하고 결제하기 ›'}
            </Button>
            {signError && (
              <p role="alert" className="rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
                {signError}
              </p>
            )}
            <p className="text-xs text-sub">계약서 확인, 본인 인증, 서명을 모두 마쳐야 버튼이 활성화돼요.</p>
          </>
        }
      />
    </>
  );
}
