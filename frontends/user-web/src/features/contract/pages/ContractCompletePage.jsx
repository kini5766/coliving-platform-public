import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import AmountBox from '../components/AmountBox';
import Button from '../components/Button';
import InfoList from '../components/InfoList';
import StatusIcon from '../components/StatusIcon';
import StatusMessage from '../components/StatusMessage';
import StepNav from '../components/StepNav';
import { useApplicationNo } from '../context/ApplyContext';
import { cancelBeforeMoveIn, downloadReceipt, downloadSignedContract, getApplication } from '../lib/api';
import { won, ymd } from '../lib/format';
import { goUserWeb, myApplicationPath } from '../lib/links';

// "2026-11-02T14:05:00+09:00" → "2026-11-02 14:05"
const ymdHm = (s) => (s ? s.slice(0, 16).replace('T', ' ') : '-');

// 서버가 내려주는 결제수단 코드 → 화면 표시명
const PAY_METHOD_LABEL = { KAKAOPAY: '카카오페이' };

export default function ContractCompletePage() {
  const navigate = useNavigate();
  const applicationNo = useApplicationNo();

  const [status, setStatus] = useState('loading'); // loading → ok | error
  const [loadError, setLoadError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [app, setApp] = useState(null);

  const [downloading, setDownloading] = useState(false);
  const [receiptDownloading, setReceiptDownloading] = useState(null);   // null | 'PAYMENT' | 'CANCEL'
  const [downloadError, setDownloadError] = useState(null);

  // 입주 전 취소: 버튼 → 확인 상자 → 취소 요청
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState(null);

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
        setLoadError(e);
        setStatus('error');
      });
    return () => {
      ignore = true;
    };
  }, [applicationNo, reloadKey]);

  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    setDownloadError(null);
    try {
      await downloadSignedContract(applicationNo);
    } catch (e) {
      setDownloadError(e.message);
    } finally {
      setDownloading(false);
    }
  };

  const downloadReceiptPdf = async (type) => {
    if (receiptDownloading) return;
    setReceiptDownloading(type);
    setDownloadError(null);
    try {
      await downloadReceipt(applicationNo, type);
    } catch (e) {
      setDownloadError(e.message);
    } finally {
      setReceiptDownloading(null);
    }
  };

  const cancel = async () => {
    if (canceling) return;
    setCanceling(true);
    setCancelError(null);
    try {
      setApp(await cancelBeforeMoveIn(applicationNo));   // 응답이 바뀐 신청서 → 아래 REFUNDED 화면으로 바뀐다
      setConfirmingCancel(false);
    } catch (e) {
      setCancelError(e.message);
    } finally {
      setCanceling(false);
    }
  };

  if (status === 'loading') {
    return (
      <>
        <StepNav current={4} />
        <StatusMessage title="계약 정보를 불러오는 중이에요" />
      </>
    );
  }

  if (status === 'error') {
    return (
      <>
        <StepNav current={4} />
        <StatusMessage
          title="계약 정보를 불러오지 못했어요"
          description={loadError?.message}
          action={<Button onClick={() => setReloadKey((k) => k + 1)}>다시 시도</Button>}
        />
      </>
    );
  }

  if (app.status === 'REFUNDED') {
    return (
      <>
        <StepNav current={4} />
        <StatusMessage
          title="입주 전 취소가 완료됐어요"
          description={`결제 금액 ${won(app.firstPaymentAmount)}원이 카카오페이로 환불됐어요. 카드 결제는 카드사에 따라 며칠 걸릴 수 있어요.`}
          action={
            <div className="flex flex-col items-center gap-3">
              <div className="flex flex-wrap justify-center gap-3">
                <Button variant="outline" disabled={!!receiptDownloading} onClick={() => downloadReceiptPdf('CANCEL')}>
                  {receiptDownloading === 'CANCEL' ? '내려받는 중…' : '취소 영수증 다운로드'}
                </Button>
                <Button onClick={() => goUserWeb(navigate, myApplicationPath(applicationNo))}>나의 신청으로</Button>
              </div>
              {downloadError && (
                <p role="alert" className="rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
                  {downloadError}
                </p>
              )}
            </div>
          }
        />
      </>
    );
  }

  if (app.status === 'MOVE_IN_CANCEL_REQUESTED') {
    return (
      <>
        <StepNav current={4} />
        <StatusMessage
          title="환불을 처리하고 있어요"
          description="취소 요청은 접수됐어요. 카카오페이 응답이 늦어 환불을 자동으로 다시 시도하고 있으니 잠시 후 다시 확인해주세요."
          action={<Button onClick={() => setReloadKey((k) => k + 1)}>다시 확인</Button>}
        />
      </>
    );
  }

  if (app.status !== 'PAID' && app.status !== 'COMPLETED') {
    return (
      <>
        <StepNav current={4} />
        <StatusMessage
          title="아직 결제가 완료되지 않았어요"
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

  return (
    <>
      <StepNav current={4} />
      <div className="mx-auto max-w-[680px] px-6 py-14">
        <StatusIcon type="check" />
        <h1 className="mt-5 text-center text-2xl font-semibold">계약이 완료됐어요</h1>
        <p className="mt-2 text-center text-sm leading-relaxed text-sub">
          서명한 계약서는 아래에서 내려받을 수 있어요.
        </p>

        <InfoList
          className="mt-8"
          rows={[
            ['계약 번호', app.applicationNo],
            ['호실', `${app.buildingName} ${app.roomName} · ${app.roomType}`],
            ['계약 기간', `${app.moveInDate} ~ ${app.moveOutDate} (${app.months}개월)`],
            ['결제 수단', PAY_METHOD_LABEL[app.payment?.method] ?? app.payment?.method ?? '-'],
            ['결제 일시', ymdHm(app.payment?.paidAt)],
          ]}
        />
        <div className="mt-3">
          <AmountBox label="결제 금액" amount={app.payment?.paidAmount ?? app.firstPaymentAmount} />
        </div>

        <div className="mt-6 grid grid-cols-2 gap-3">
          <Button variant="outline" disabled={downloading} onClick={download}>
            {downloading ? '내려받는 중…' : '계약서 다운로드'}
          </Button>
          <Button variant="outline" disabled={!!receiptDownloading} onClick={() => downloadReceiptPdf('PAYMENT')}>
            {receiptDownloading === 'PAYMENT' ? '내려받는 중…' : '영수증 다운로드'}
          </Button>
        </div>
        <div className="mt-3">
          <Button className="w-full" onClick={() => goUserWeb(navigate, myApplicationPath(applicationNo))}>
            나의 신청으로
          </Button>
        </div>
        {downloadError && (
          <p role="alert" className="mt-4 rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
            {downloadError}
          </p>
        )}

        {/* 입주 전 취소: 입주일 전날까지만 보인다 (최종 판단은 서버가 한 번 더 한다) */}
        {ymd(new Date()) < app.moveInDate && (
          <div className="mt-10 border-t border-line pt-6">
            {!confirmingCancel ? (
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm text-sub">입주일 전날까지 취소하면 전액 환불돼요.</p>
                <Button variant="outline" size="sm" onClick={() => setConfirmingCancel(true)}>
                  입주 전 취소
                </Button>
              </div>
            ) : (
              <div className="rounded-xl border border-warn-line bg-warn-bg/60 px-5 py-4">
                <p className="font-semibold text-warn-fg">계약을 취소할까요?</p>
                <p className="mt-1 text-sm text-warn-fg/90">
                  결제 금액 {won(app.payment?.paidAmount ?? app.firstPaymentAmount)}원이 전액 환불되고, 취소한 계약은 되돌릴 수 없어요.
                </p>
                <div className="mt-4 flex justify-end gap-2">
                  <Button variant="outline" size="sm" disabled={canceling} onClick={() => setConfirmingCancel(false)}>
                    닫기
                  </Button>
                  <Button size="sm" disabled={canceling} onClick={cancel}>
                    {canceling ? '취소하는 중…' : '취소하고 환불받기'}
                  </Button>
                </div>
              </div>
            )}
            {cancelError && (
              <p role="alert" className="mt-3 rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
                {cancelError}
              </p>
            )}
          </div>
        )}
      </div>
    </>
  );
}
