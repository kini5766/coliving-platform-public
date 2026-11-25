import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import AmountBox from '../components/AmountBox';
import Badge from '../components/Badge';
import Button from '../components/Button';
import Card from '../components/Card';
import ContractDocumentView from '../components/ContractDocumentView';
import InfoList from '../components/InfoList';
import Section from '../components/Section';
import StatusMessage from '../components/StatusMessage';
import {
  downloadReceipt,
  downloadSignedContract,
  getApplication,
  getContractDocument,
  getMyApplications,
} from '../lib/api';

// ---------------------------------------------------------------
// [나의 신청] /my/application?applicationNo=CT-...
//
// 위: 내 신청 목록 (GET /apply/applications = 제출한 신청 전부, 작성 중 제외)
// 아래: 고른 신청의 계약서 본문 + 신청 정보 + 다음 단계 버튼
//
// 계약서는 관리자 승인 때 만들어진다 → 심사 중·반려·취소 신청은 404가 오고 "아직 발급 전" 안내를 띄운다
// applicationNo가 없으면 목록의 첫 번째(최근 신청)를 보여준다
// ---------------------------------------------------------------

// ContractStatus enum 이름과 같게
const STATUS_VIEW = {
  DRAFT: { tone: 'neutral', label: '작성 중' },
  SUBMITTED: { tone: 'warn', label: '심사 중' },
  APPROVED: { tone: 'ok', label: '승인 · 결제 대기' },
  PAID: { tone: 'ok', label: '결제 완료' },
  COMPLETED: { tone: 'ok', label: '계약 완료' },
  REJECTED: { tone: 'warn', label: '반려' },
  CANCELED: { tone: 'neutral', label: '취소' },
  EXPIRED: { tone: 'neutral', label: '기한 만료' },
  MOVE_IN_CANCEL_REQUESTED: { tone: 'warn', label: '환불 처리 중' },
  REFUNDED: { tone: 'neutral', label: '환불 완료' },
  TERMINATION_REQUESTED: { tone: 'warn', label: '퇴실 신청' },
  TERMINATED: { tone: 'neutral', label: '계약 종료' },
};
const statusView = (s) => STATUS_VIEW[s] ?? { tone: 'neutral', label: s };

// 계약서가 아직 없을 때 안내 문구
const NO_DOCUMENT_TEXT = {
  SUBMITTED: '관리자가 신청서를 확인하고 있어요. 승인되면 이곳에서 계약서를 확인할 수 있어요.',
  DRAFT: '신청서 작성을 마치고 제출하면, 관리자 승인 후 계약서가 발급돼요.',
  REJECTED: '반려된 신청이라 계약서가 발급되지 않았어요.',
  CANCELED: '취소한 신청이라 계약서가 발급되지 않았어요.',
  EXPIRED: '기한이 지난 신청이라 계약서가 발급되지 않았어요.',
};

const ymdHm = (s) => (s ? s.slice(0, 16).replace('T', ' ') : '-');

export default function MyApplicationPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const requestedNo = params.get('applicationNo');

  // 목록
  const [list, setList] = useState(null);           // null = 로딩 중
  const [listError, setListError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  // 선택한 신청 상세
  const selectedNo = requestedNo ?? list?.[0]?.applicationNo ?? null;
  const [detail, setDetail] = useState({ state: 'idle' }); // idle | loading | ok | error
  const [downloading, setDownloading] = useState(false);
  const [receiptDownloading, setReceiptDownloading] = useState(null);   // null | 'PAYMENT' | 'CANCEL'
  const [downloadError, setDownloadError] = useState(null);

  useEffect(() => {
    document.title = '나의 신청';
  }, []);

  // GET /apply/applications
  useEffect(() => {
    let ignore = false;
    setList(null);
    setListError(null);
    getMyApplications()
      .then((res) => !ignore && setList(res))
      .catch((e) => {
        if (ignore) return;
        setListError(e);
        setList([]);
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  // GET /apply/applications/{no} + GET /contracts/{no}/document (계약서는 없을 수 있음 → 404면 null)
  useEffect(() => {
    if (!selectedNo) return;
    let ignore = false;
    setDetail({ state: 'loading' });
    setDownloadError(null);
    Promise.all([
      getApplication(selectedNo),
      getContractDocument(selectedNo).catch((e) => {
        if (e.status === 404) return null;
        throw e;
      }),
    ])
      .then(([app, doc]) => !ignore && setDetail({ state: 'ok', app, doc }))
      .catch((error) => !ignore && setDetail({ state: 'error', error }));
    return () => {
      ignore = true;
    };
  }, [selectedNo, reloadKey]);

  const select = (no) => setParams({ applicationNo: no });

  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    setDownloadError(null);
    try {
      await downloadSignedContract(selectedNo);
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
      await downloadReceipt(selectedNo, type);
    } catch (e) {
      setDownloadError(e.message);
    } finally {
      setReceiptDownloading(null);
    }
  };

  // 목록 로딩
  if (list === null) return <StatusMessage title="신청 내역을 불러오는 중이에요" />;

  // 목록 실패 + 볼 신청도 없음
  if (listError && !selectedNo) {
    return (
      <StatusMessage
        title={listError.status === 401 ? '로그인이 필요해요' : '신청 내역을 불러오지 못했어요'}
        description={listError.message}
        action={<Button onClick={() => setReloadKey((k) => k + 1)}>다시 시도</Button>}
      />
    );
  }

  // 아무 신청도 없음
  if (!selectedNo) {
    return (
      <StatusMessage
        title="아직 신청한 내역이 없어요"
        description="마음에 드는 호실을 신청하면 이곳에서 진행 상황과 계약서를 확인할 수 있어요."
        action={<Button onClick={() => navigate('/property/buildings')}>호실 둘러보기</Button>}
      />
    );
  }

  // URL로 넘어온 신청이 목록에 없으면 맨 앞에 붙인다 (방금 만든 신청이 아직 목록 응답에 없을 때 등)
  const items = [...list];
  if (!items.some((i) => i.applicationNo === selectedNo) && detail.state === 'ok') {
    const a = detail.app;
    items.unshift({ applicationNo: a.applicationNo, status: a.status, buildingName: a.buildingName, roomName: a.roomName, moveInDate: a.moveInDate, moveOutDate: a.moveOutDate });
  }

  return (
    <div className="px-4 py-6 sm:px-8 sm:py-8 md:px-12 md:py-10">
      <h1 className="text-2xl font-semibold text-main sm:text-3xl">나의 신청</h1>

      {/* 신청 목록 */}
      {items.length > 0 && (
        <ul className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {items.map((i) => {
            const v = statusView(i.status);
            const active = i.applicationNo === selectedNo;
            return (
              <li key={i.applicationNo}>
                <button
                  type="button"
                  onClick={() => select(i.applicationNo)}
                  aria-current={active ? 'true' : undefined}
                  className={`w-full rounded-lg border px-4 py-3 text-left transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent ${
                    active ? 'border-primary bg-primary-soft' : 'border-line bg-surface hover:bg-soft'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate text-sm font-semibold text-ink">
                      {i.buildingName} {i.roomName}
                    </span>
                    <Badge tone={v.tone}>{v.label}</Badge>
                  </div>
                  <p className="mt-1 text-xs text-sub">
                    {i.applicationNo} · {i.moveInDate} ~ {i.moveOutDate}
                  </p>
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {detail.state === 'loading' || detail.state === 'idle' ? (
        <Card className="mt-8 px-6 py-16 text-center text-sm text-sub">계약서를 불러오는 중이에요</Card>
      ) : detail.state === 'error' ? (
        <Card className="mt-8 flex flex-col items-center gap-3 px-6 py-16 text-center">
          <p className="text-lg font-semibold text-ink">
            {detail.error.status === 403
              ? '본인의 신청만 볼 수 있어요'
              : detail.error.status === 404
                ? '신청서를 찾을 수 없어요'
                : '신청 정보를 불러오지 못했어요'}
          </p>
          <p className="text-sm text-sub">{detail.error.message}</p>
          {(detail.error.status === 0 || detail.error.status >= 500) && (
            <Button onClick={() => setReloadKey((k) => k + 1)}>다시 시도</Button>
          )}
        </Card>
      ) : (
        <Detail
          app={detail.app}
          doc={detail.doc}
          navigate={navigate}
          download={download}
          downloading={downloading}
          downloadReceipt={downloadReceiptPdf}
          receiptDownloading={receiptDownloading}
          downloadError={downloadError}
        />
      )}
    </div>
  );
}

// 결제가 한 번이라도 승인된 상태 = 결제 영수증을 받을 수 있다 (환불돼도 결제 영수증은 그대로)
const RECEIPT_STATUSES = ['PAID', 'COMPLETED', 'MOVE_IN_CANCEL_REQUESTED', 'REFUNDED', 'TERMINATION_REQUESTED', 'TERMINATED'];

function Detail({ app, doc, navigate, download, downloading, downloadReceipt, receiptDownloading, downloadError }) {
  const v = statusView(app.status);
  const no = app.applicationNo;

  // 상태별 다음 단계 버튼
  let next = null;
  if (app.status === 'APPROVED') {
    next = doc?.signed ? (
      <Button className="w-full" onClick={() => navigate(`/contract/${no}/pay`)}>결제하기 ›</Button>
    ) : (
      <Button className="w-full" onClick={() => navigate(`/contract/${no}/sign`)}>계약서 서명하기 ›</Button>
    );
  } else if (app.status === 'PAID' || app.status === 'COMPLETED') {
    next = (
      <Button className="w-full" onClick={() => navigate(`/contract/${no}/complete`)}>계약 내용 보기 ›</Button>
    );
  } else if (app.status === 'SUBMITTED' || app.status === 'REJECTED') {
    next = (
      <Button className="w-full" variant="outline" onClick={() => navigate(`/apply/${no}/submitted`)}>신청 현황 보기</Button>
    );
  }

  return (
    <div className="mt-8 grid gap-8 lg:grid-cols-[1fr_360px]">
      <Section title="계약서">
        {doc ? (
          <>
            <ContractDocumentView content={doc.content} className="h-[70vh] min-h-80" />
            <div className="mt-3 flex flex-wrap items-center gap-3">
              {doc.signed ? (
                <>
                  <Badge tone="ok">서명 완료</Badge>
                  <Button size="sm" variant="outline" disabled={downloading} onClick={download}>
                    {downloading ? '내려받는 중…' : '서명된 계약서 PDF 다운로드'}
                  </Button>
                  {RECEIPT_STATUSES.includes(app.status) && (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={!!receiptDownloading}
                      onClick={() => downloadReceipt('PAYMENT')}
                    >
                      {receiptDownloading === 'PAYMENT' ? '내려받는 중…' : '결제 영수증 다운로드'}
                    </Button>
                  )}
                  {app.status === 'REFUNDED' && (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={!!receiptDownloading}
                      onClick={() => downloadReceipt('CANCEL')}
                    >
                      {receiptDownloading === 'CANCEL' ? '내려받는 중…' : '취소 영수증 다운로드'}
                    </Button>
                  )}
                </>
              ) : (
                <Badge tone="warn">서명 전</Badge>
              )}
            </div>
            {downloadError && (
              <p role="alert" className="mt-2 rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
                {downloadError}
              </p>
            )}
          </>
        ) : (
          <Card className="flex min-h-60 flex-col items-center justify-center gap-2 px-6 py-12 text-center">
            <p className="font-semibold text-ink">아직 발급된 계약서가 없어요</p>
            <p className="max-w-sm text-sm text-sub">
              {NO_DOCUMENT_TEXT[app.status] ?? '관리자 승인 후 계약서가 발급돼요.'}
            </p>
          </Card>
        )}
      </Section>

      <aside className="space-y-3 self-start lg:sticky lg:top-6">
        <h2 className="text-xl font-semibold text-main">신청 정보</h2>
        <InfoList
          rows={[
            ['신청 번호', no],
            ['호실', `${app.buildingName} ${app.roomName}${app.roomType ? ` · ${app.roomType}` : ''}`],
            ['계약 기간', `${app.moveInDate} ~ ${app.moveOutDate}`],
            ['진행 상태', <Badge tone={v.tone}>{v.label}</Badge>],
            ...(app.paymentDeadline ? [['결제 기한', ymdHm(app.paymentDeadline)]] : []),
          ]}
        />
        <AmountBox amount={app.firstPaymentAmount} />
        {next}
        {app.status === 'REJECTED' && app.rejectReason && (
          <p className="rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
            <b>반려 사유</b> {app.rejectReason}
          </p>
        )}
      </aside>
    </div>
  );
}
