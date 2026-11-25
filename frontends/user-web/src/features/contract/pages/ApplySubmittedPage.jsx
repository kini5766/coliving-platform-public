import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import AmountBox from '../components/AmountBox';
import Badge from '../components/Badge';
import Button from '../components/Button';
import InfoList from '../components/InfoList';
import StatusIcon from '../components/StatusIcon';
import StatusMessage from '../components/StatusMessage';
import StepNav from '../components/StepNav';
import { useApplicationNo } from '../context/ApplyContext';
import { cancelApplication, getApplication } from '../lib/api';
import { USER_PATHS, goUserWeb, myApplicationPath } from '../lib/links';

// 상태별 배지 · 안내 문구 (ContractStatus enum 이름과 같게)
const STATUS_VIEW = {
  SUBMITTED: { tone: 'warn', label: '심사 중', title: '신청이 접수됐어요', desc: '관리자가 신청서를 확인하고 있어요. 승인되면 알림과 이메일로 알려드릴게요.' },
  APPROVED: { tone: 'ok', label: '승인 · 결제 대기', title: '신청이 승인됐어요', desc: '계약서를 확인하고 서명한 뒤 결제를 진행해주세요.' },
  PAID: { tone: 'ok', label: '결제 완료', title: '결제가 완료됐어요', desc: '계약 내용과 서명한 계약서를 확인할 수 있어요.' },
  COMPLETED: { tone: 'ok', label: '계약 완료', title: '계약이 완료됐어요', desc: '계약 내용과 서명한 계약서를 확인할 수 있어요.' },
  REJECTED: { tone: 'warn', label: '반려', title: '신청이 반려됐어요', desc: '아래 사유를 확인하고 다른 날짜로 다시 신청할 수 있어요.' },
  CANCELED: { tone: 'neutral', label: '취소', title: '신청을 취소했어요', desc: '' },
  EXPIRED: { tone: 'neutral', label: '기한 만료', title: '기한이 지나 신청이 종료됐어요', desc: '' },
};

// "2026-11-02T10:00:00+09:00" → "2026-11-02 10:00"
const ymdHm = (s) => (s ? s.slice(0, 16).replace('T', ' ') : '-');

export default function ApplySubmittedPage() {
  const navigate = useNavigate();
  const applicationNo = useApplicationNo();

  // GET /contract-service/apply/applications/{applicationNo}
  // 이전에는 context(메모리) 값을 써서 새로고침하면 목업 값으로 돌아갔다 → 서버 값으로 그린다
  const [status, setStatus] = useState('loading'); // loading → ok | error
  const [error, setError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [app, setApp] = useState(null);

  // "취소하기": 한 번 누르면 확인 문구, 한 번 더 눌러야 실제 취소 (실수 방지)
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState(null);

  const goHome = () => goUserWeb(navigate, USER_PATHS.home);
  // 나의 신청 화면이 이 신청을 바로 선택해서 계약서(또는 발급 전 안내)를 보여주도록 신청번호를 넘긴다
  const goMyApplication = () => goUserWeb(navigate, myApplicationPath(applicationNo));

  const doCancel = async () => {
    if (canceling) return;
    setCanceling(true);
    setCancelError(null);
    try {
      const updated = await cancelApplication(applicationNo);   // SUBMITTED → CANCELED
      setApp(updated);                                          // 화면을 서버 응답으로 바로 갱신
      setConfirmCancel(false);
    } catch (e) {
      // 409: 그 사이 관리자가 승인·반려함 → 최신 상태를 다시 불러온다
      setCancelError(e.message);
      if (e.status === 409) setReloadKey((k) => k + 1);
    } finally {
      setCanceling(false);
    }
  };

  useEffect(() => {
    let ignore = false;
    setStatus('loading');
    getApplication(applicationNo)
      .then((res) => {
        if (ignore) return;
        setApp(res);
        setStatus('ok');
      })
      .catch((e) => {
        if (ignore) return;
        setError(e);
        setStatus('error');
      });
    return () => {
      ignore = true;
    };
  }, [applicationNo, reloadKey]);

  if (status === 'loading') {
    return (
      <>
        <StepNav current={0} done={1} />
        <StatusMessage title="신청 정보를 불러오는 중이에요" />
      </>
    );
  }

  if (status === 'error') {
    const s = error?.status;
    const retryable = s === 0 || s >= 500;
    const title =
      s === 401 ? '로그인이 필요해요' : s === 403 ? '본인의 신청서만 볼 수 있어요' : s === 404 ? '신청서를 찾을 수 없어요' : '신청 정보를 불러오지 못했어요';
    return (
      <>
        <StepNav current={0} done={1} />
        <StatusMessage
          title={title}
          description={error?.message}
          action={
            retryable ? (
              <Button onClick={() => setReloadKey((k) => k + 1)}>다시 시도</Button>
            ) : (
              <Button variant="outline" onClick={goHome}>
                홈으로
              </Button>
            )
          }
        />
      </>
    );
  }

  const view = STATUS_VIEW[app.status] ?? { tone: 'neutral', label: app.status, title: '신청 현황', desc: '' };

  return (
    <>
      <StepNav current={0} done={1} />
      <div className="mx-auto max-w-[680px] px-6 py-14">
        <StatusIcon type={['APPROVED', 'PAID', 'COMPLETED'].includes(app.status) ? 'check' : 'clock'} />
        <h1 className="mt-5 text-center text-2xl font-semibold">{view.title}</h1>
        {view.desc && <p className="mt-2 text-center text-sm leading-relaxed text-sub">{view.desc}</p>}

        <InfoList
          className="mt-8"
          rows={[
            ['신청 번호', app.applicationNo],
            ['호실', `${app.buildingName} ${app.roomName} · ${app.roomType}`],
            ['입주일 · 기간', `${app.moveInDate} · ${app.months}개월`],
            ['퇴실 예정일', app.moveOutDate],
            ['신청 일시', ymdHm(app.submittedAt)],
            ['진행 상태', <Badge tone={view.tone}>{view.label}</Badge>],
            ...(app.paymentDeadline ? [['결제 기한', ymdHm(app.paymentDeadline)]] : []),
          ]}
        />
        <div className="mt-4">
          <AmountBox amount={app.firstPaymentAmount} />
        </div>

        {app.status === 'REJECTED' && (
          <div className="mt-4 rounded-lg border border-warn-line bg-warn-bg/60 px-5 py-4 text-sm">
            <p>
              <b>반려 사유</b> <span className="text-sub">{app.rejectReason ?? '-'}</span>
            </p>
            <button
              type="button"
              onClick={() => navigate(`/apply/room?roomId=${app.roomId}`)}
              className="mt-2 font-semibold text-primary-strong hover:underline"
            >
              다른 날짜로 다시 신청하기 ›
            </button>
          </div>
        )}

        {confirmCancel && (
          <div className="mt-6 rounded-lg border border-warn-line bg-warn-bg/60 px-5 py-4 text-sm">
            <p className="font-semibold">신청을 취소할까요?</p>
            <p className="mt-1 text-sub">취소하면 되돌릴 수 없고, 다시 신청하려면 처음부터 작성해야 해요.</p>
            <div className="mt-3 flex gap-2">
              <Button size="sm" disabled={canceling} onClick={doCancel}>
                {canceling ? '취소 중…' : '신청 취소'}
              </Button>
              <Button size="sm" variant="outline" disabled={canceling} onClick={() => setConfirmCancel(false)}>
                돌아가기
              </Button>
            </div>
          </div>
        )}
        {cancelError && (
          <p role="alert" className="mt-4 rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
            {cancelError}
          </p>
        )}

        <div className="mt-6 grid grid-cols-3 gap-3">
          <Button variant="outline" onClick={goHome}>
            홈으로
          </Button>
          {app.status === 'APPROVED' ? (
            <Button onClick={() => navigate(`/contract/${app.applicationNo}/sign`)}>계약서 확인하기</Button>
          ) : app.status === 'PAID' || app.status === 'COMPLETED' ? (
            <Button onClick={() => navigate(`/contract/${app.applicationNo}/complete`)}>계약 내용 보기</Button>
          ) : (
            <Button onClick={goMyApplication}>나의 신청 보기</Button>
          )}
          {/* 심사 중(SUBMITTED)에만 취소 가능. 승인 이후 취소는 환불이 걸려 있어 별도 흐름 */}
          <Button
            variant="outline"
            disabled={app.status !== 'SUBMITTED' || confirmCancel}
            onClick={() => setConfirmCancel(true)}
          >
            취소하기
          </Button>
        </div>
      </div>
    </>
  );
}
