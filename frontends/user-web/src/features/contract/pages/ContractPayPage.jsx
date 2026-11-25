import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import AmountBox from '../components/AmountBox';
import Button from '../components/Button';
import InfoList from '../components/InfoList';
import PageShell from '../components/PageShell';
import Section from '../components/Section';
import StatusMessage from '../components/StatusMessage';
import StepNav from '../components/StepNav';
import { useApplicationNo } from '../context/ApplyContext';
import { getApplication, getPaymentInfo, readyPayment } from '../lib/api';
import { won, ymdDay } from '../lib/format';

const pad = (n) => String(n).padStart(2, '0');
const hms = (ms) => {
  const s = Math.max(0, Math.floor(ms / 1000));
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
};

const RESULT_MESSAGE = {
  cancel: '결제를 취소했어요. 기한 안에 다시 결제할 수 있어요.',
  fail: '결제에 실패했어요. 다시 시도해주세요.',
};

const isMobile = () => /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);

export default function ContractPayPage() {
  const navigate = useNavigate();
  const applicationNo = useApplicationNo();
  const [params] = useSearchParams();

  const [status, setStatus] = useState('loading'); // loading → ok | error
  const [loadError, setLoadError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [info, setInfo] = useState(null);

  // 카운트다운은 사용자 PC 시계가 아니라 서버 시각 기준
  const [clockOffset, setClockOffset] = useState(0);
  const [now, setNow] = useState(Date.now());

  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState(RESULT_MESSAGE[params.get('result')] ?? params.get('error'));

  useEffect(() => {
    let ignore = false;
    setStatus('loading');
    (async () => {
      try {
        const app = await getApplication(applicationNo);
        if (app.status === 'PAID' || app.status === 'COMPLETED') {
          if (!ignore) navigate(`/contract/${applicationNo}/complete`, { replace: true });
          return;
        }
        const res = await getPaymentInfo(applicationNo);
        if (ignore) return;
        setInfo(res);
        setClockOffset(new Date(res.serverTime).getTime() - Date.now());
        setStatus('ok');
      } catch (e) {
        if (ignore) return;
        setLoadError(e);
        setStatus('error');
      }
    })();
    return () => {
      ignore = true;
    };
  }, [applicationNo, reloadKey, navigate]);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  // 결제하기: 서버가 카카오페이 준비 후 결제창 주소를 준다 → 그 주소로 페이지 이동
  const startPay = async () => {
    if (paying) return;
    setPaying(true);
    setPayError(null);
    try {
      const res = await readyPayment(applicationNo, 'KAKAOPAY');
      window.location.assign(isMobile() ? res.redirectMobileUrl : res.redirectPcUrl);
    } catch (e) {
      setPayError(e.message);
      setPaying(false);
    }
  };

  if (status === 'loading') {
    return (
      <>
        <StepNav current={3} />
        <StatusMessage title="결제 정보를 불러오는 중이에요" />
      </>
    );
  }

  if (status === 'error') {
    const s = loadError?.status;
    const retryable = s === 0 || s >= 500;
    return (
      <>
        <StepNav current={3} />
        <StatusMessage
          title={s === 401 ? '로그인이 필요해요' : s === 403 ? '본인의 신청만 결제할 수 있어요' : '지금은 결제할 수 없어요'}
          description={loadError?.message}
          action={
            retryable ? (
              <Button onClick={() => setReloadKey((k) => k + 1)}>다시 시도</Button>
            ) : (
              <Button variant="outline" onClick={() => navigate(`/contract/${applicationNo}/sign`)}>
                계약서로 돌아가기
              </Button>
            )
          }
        />
      </>
    );
  }

  const deadline = new Date(info.paymentDeadline);
  const remain = deadline.getTime() - (now + clockOffset);
  const expired = remain <= 0;

  return (
    <>
      <StepNav current={3} />
      <PageShell
        title="결제 정보"
        main={
          <>
            <div className="rounded-lg border border-warn-line bg-warn-bg/60 px-5 py-4">
              <p className="font-semibold text-warn-fg">
                {expired ? '결제 기한이 지났어요' : `결제 기한까지 ${hms(remain)} 남았어요`}
              </p>
              <p className="mt-1 text-sm text-warn-fg/90">
                {ymdDay(deadline)} {pad(deadline.getHours())}:{pad(deadline.getMinutes())}까지 결제해야 계약이 완료돼요.
              </p>
            </div>

            <Section title="결제 금액">
              <InfoList
                rows={[
                  ['보증금', `${won(info.deposit)}원`],
                  ['첫 달 이용료', `${won(info.firstMonthFee)}원`],
                  // 관리비는 첫 결제(firstPayment)에 포함되지 않음 → 합계와 맞도록 행을 두지 않는다
                ]}
              />
            </Section>

            <Section title="결제 수단">
              <div className="rounded-lg border border-line bg-surface px-5 py-4">
                <p className="text-sm font-semibold">카카오페이</p>
                <p className="mt-1 text-xs text-sub">결제하기를 누르면 카카오페이 결제창으로 이동해요.</p>
              </div>
            </Section>
          </>
        }
        aside={
          <>
            <InfoList
              rows={[
                ['호실', `${info.buildingName} ${info.roomName}`],
                ['계약 기간', `${info.moveInDate} ~ ${info.moveOutDate}`],
              ]}
            />
            <AmountBox label="총 결제 금액" amount={info.totalAmount} />
            <Button className="w-full" disabled={paying || expired} onClick={startPay}>
              {paying ? '결제창으로 이동 중…' : `${won(info.totalAmount)}원 결제하기`}
            </Button>
            {payError && (
              <p role="alert" className="rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
                {payError}
              </p>
            )}
            <p className="text-xs text-sub">결제 금액은 서버에서 계약 정보로 다시 계산해 확인해요.</p>
          </>
        }
      />
    </>
  );
}
