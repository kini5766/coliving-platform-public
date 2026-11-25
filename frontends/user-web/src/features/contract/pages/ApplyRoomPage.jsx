import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useSupportWidget } from '../../community/components/SupportWidgetContext';
import { useCurrentUser } from '../../community/currentUser';
import AmountBox from '../components/AmountBox';
import Badge from '../components/Badge';
import Button from '../components/Button';
import Calendar from '../components/Calendar';
import Card from '../components/Card';
import InfoList from '../components/InfoList';
import PageShell from '../components/PageShell';
import RoomRecommendations from '../components/RoomRecommendations';
import Section from '../components/Section';
import StatusMessage from '../components/StatusMessage';
import StepNav from '../components/StepNav';
import { PERIODS, ROOM, useApply } from '../context/ApplyContext';
import { createApplication, getBookedPeriods, getRoom } from '../lib/api';
import { amountRows, parseYmd, roomSpec, today, won, ymd, ymdDay } from '../lib/format';

export default function ApplyRoomPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const a = useApply();
  const { openWidget } = useSupportWidget();
  const currentUser = useCurrentUser();
  const r = a.room;
  const { setRoom, setMoveIn, setMonths } = a;

  // ?roomId=1 : 호실 상세에서 넘어온 값 → GET /property-service/property/rooms/{roomId} (lib/api.js getRoom)
  // ?applicationNo=... : 신청자 정보에서 "이전"으로 돌아온 경우.
  //   따로 읽지 않는다. "다음 단계"를 누르면 서버가 같은 호실의 작성 중 신청서를 찾아 수정한다
  // roomId 없이 들어오면 개발 편의상 목업 호실 번호(1)로 조회
  const roomId = params.get('roomId') ?? ROOM.roomId; // POST body의 roomId

  // ?moveInDate=2026-12-01&months=6 : 호실 상세에서 고른 값, 또는 신청자 정보에서 "이전"으로 돌아온 값
  //   → 입주일·계약 기간의 처음 값으로 쓴다 (새로고침해도 유지)
  //   잘못된 값(형식 오류, 지난 날짜, 3/6/12가 아닌 기간)은 무시하고 지금 값(기본: 오늘 · 6개월)을 둔다
  const urlMoveIn = params.get('moveInDate');
  const urlMonths = params.get('months');
  useEffect(() => {
    const d = parseYmd(urlMoveIn);
    if (d && d >= today()) setMoveIn(d);
    const m = Number(urlMonths);
    if (PERIODS.includes(m)) setMonths(m);
  }, [urlMoveIn, urlMonths, setMoveIn, setMonths]);

  // 지난 입주일이 들어와 있으면(어제 저장한 신청서로 "이전" 등) 오늘로 맞춘다. 서버가 지난 날짜를 거절하기 때문
  useEffect(() => {
    if (a.moveIn < today()) setMoveIn(today());
  }, [a.moveIn, setMoveIn]);

  // 호실 조회 상태: loading → ok | error
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0); // "다시 시도" 누르면 1 올려서 재조회

  // 이 호실에 이미 잡혀 있는 기간 (계약 서비스). null = 모름(로딩 중·비로그인·실패) → 표시를 숨기고 서버 확인에 맡긴다
  const [booked, setBooked] = useState(null);
  useEffect(() => {
    let ignore = false;
    setBooked(null);
    getBookedPeriods(roomId)
      .then((list) => !ignore && setBooked(list))
      .catch(() => {}); // 못 받아도 신청은 가능 (겹치면 "다음 단계"에서 서버가 409로 막음)
    return () => {
      ignore = true;
    };
  }, [roomId, reloadKey]);

  // "상담 신청": 커뮤니티 문의 위젯의 "관리자와의 쪽지" 탭을 연다 (로그인 전이면 비회원 문의 탭)
  const openConsult = () => openWidget(currentUser.isLoggedIn ? 'notes' : 'inquiry');

  // "다음 단계" 요청 상태 (중복 클릭 방지 + 에러 문구)
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  useEffect(() => {
    let ignore = false; // 응답 전에 roomId가 바뀌거나 화면을 떠나면 늦게 온 응답은 버림
    setStatus('loading');
    getRoom(roomId)
      .then((room) => {
        if (ignore) return;
        setRoom(room);
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
  }, [roomId, reloadKey, setRoom]);

  if (status === 'loading') {
    return (
      <>
        <StepNav current={1} />
        <StatusMessage title="호실 정보를 불러오는 중이에요" />
      </>
    );
  }

  if (status === 'error') {
    // 404 = 없는/삭제된 호실 → 다시 시도해도 같음. 그 외(503, 502, 연결 실패) → 다시 시도 가능
    const notFound = error?.status === 404;
    return (
      <>
        <StepNav current={1} />
        <StatusMessage
          title={notFound ? '신청할 수 없는 호실이에요' : '호실 정보를 불러오지 못했어요'}
          description={error?.message}
          action={
            notFound ? (
              <Button variant="outline" onClick={() => navigate(-1)}>
                이전 화면으로
              </Button>
            ) : (
              <Button onClick={() => setReloadKey((k) => k + 1)}>다시 시도</Button>
            )
          }
        />
      </>
    );
  }

  // 고른 기간(입주일 ~ 퇴실 예정일)과 겹치는 예약. "YYYY-MM-DD" 문자열은 그대로 크기 비교가 된다
  const moveInYmd = ymd(a.moveIn);
  const moveOutYmd = ymd(a.moveOut);
  const conflict = booked?.find((p) => moveInYmd <= p.moveOutDate && moveOutYmd >= p.moveInDate) ?? null;
  const isBookedDay = (date) => {
    const s = ymd(date);
    return booked?.some((p) => s >= p.moveInDate && s <= p.moveOutDate) ?? false;
  };
  // 입주 가능 여부: 고른 기간(입주일~퇴실 예정일)이 이미 잡힌 예약과 겹치는지로만 판단한다
  //   매물 서비스의 r.available은 "오늘 사용 중인지"라서 여기서는 쓰지 않는다
  //   (지금 누가 살고 있어도 퇴실 다음 날부터는 신청할 수 있어야 함). 예약 기간을 못 받았으면(비로그인 등) 숨김
  const available = booked ? !conflict : null;

  // POST /contract-service/apply/applications { roomId, moveInDate, months, room }
  // "이전"으로 돌아온 경우(?applicationNo=...)에도 같은 API를 부른다.
  // 서버가 같은 회원·같은 호실의 작성 중 신청서를 찾아 수정하고 같은 신청번호를 돌려준다.
  const goNext = async () => {
    if (saving) return;
    setSaving(true);
    setSaveError(null);
    try {
      const res = await createApplication({ roomId, moveInDate: ymd(a.moveIn), months: a.months, room: a.room });
      navigate(`/apply/${res.applicationNo}/form`);
    } catch (e) {
      setSaveError(e.status === 401 ? '로그인 후 신청할 수 있어요.' : e.message);
      setSaving(false);
    }
  };

  return (
    <>
      <StepNav current={1} />
      <PageShell
        main={
          <>
            <Section title="호실 선택">
              <Card className="flex gap-6 p-5">
                {r.thumbnailUrl ? (
                  <img src={r.thumbnailUrl} alt={r.roomName} className="h-40 w-56 shrink-0 rounded-lg object-cover" />
                ) : (
                  <div className="flex h-40 w-56 shrink-0 items-center justify-center rounded-lg bg-soft text-sm text-muted">
                    호실 사진
                  </div>
                )}
                <div className="flex flex-1 flex-col">
                  <p className="text-sm text-sub">
                    {r.buildingName} · {r.region}
                  </p>
                  <h3 className="mt-1 text-2xl font-semibold">
                    {r.roomName} · {r.roomType}
                  </h3>
                  <p className="mt-1 text-sm text-sub">{roomSpec(r)}</p>
                  <div className="mt-auto flex items-end justify-between">
                    {/* 고른 기간 기준 입주 가능 여부. 판단할 정보가 없으면(비로그인 등) 숨긴다 */}
                    {available == null ? (
                      <span />
                    ) : (
                      <Badge tone={available ? 'ok' : 'warn'}>
                        {available ? '선택한 기간 입주 가능' : '선택한 기간 입주 불가'}
                      </Badge>
                    )}
                    <div className="text-right">
                      <p className="text-xs text-sub">월 이용료</p>
                      <p className="text-2xl font-semibold text-primary-strong">{won(r.monthlyFee)}원</p>
                      <p className="text-xs text-sub">관리비 월 {won(r.managementFee)}원 별도</p>
                    </div>
                  </div>
                </div>
              </Card>
            </Section>

            <Section title="계약 기간">
              <div className="grid grid-cols-3 gap-3">
                {PERIODS.map((m) => {
                  const on = m === a.months;
                  return (
                    <button
                      type="button"
                      key={m}
                      onClick={() => a.setMonths(m)}
                      className={`rounded-lg border py-4 text-center transition-colors ${
                        on ? 'border-primary bg-primary-soft' : 'border-line bg-surface hover:bg-soft'
                      }`}
                    >
                      <p className={`font-semibold ${on ? 'text-primary-strong' : 'text-ink'}`}>{m}개월</p>
                    </button>
                  );
                })}
              </div>
              <p className="mt-3 text-sm text-sub">지점별 최소 계약 기간은 3개월이에요.</p>
            </Section>

            {booked && (
              <Section title="예약 현황">
                {booked.length === 0 ? (
                  <Card className="px-5 py-4 text-sm text-sub">지금 이 호실에 잡힌 예약이 없어요. 원하는 날짜에 입주할 수 있어요.</Card>
                ) : (
                  <Card>
                    <p className="border-b border-line px-5 py-3 text-sm text-sub">
                      아래 기간에는 입주할 수 없어요. 퇴실일 다음 날부터 입주할 수 있어요.
                    </p>
                    <ul className="divide-y divide-line">
                      {booked.map((p) => {
                        const hit = p === conflict;
                        return (
                          <li key={`${p.moveInDate}-${p.moveOutDate}`} className="flex items-center justify-between px-5 py-3 text-sm">
                            <span className={hit ? 'font-semibold text-warn-fg' : 'text-ink'}>
                              {p.moveInDate} ~ {p.moveOutDate}
                            </span>
                            <Badge tone={hit ? 'warn' : 'neutral'}>{hit ? '선택한 기간과 겹침' : '예약됨'}</Badge>
                          </li>
                        );
                      })}
                    </ul>
                  </Card>
                )}
              </Section>
            )}

            {/* 다른 방 추천: 같은 건물 → 같은 지역. 고른 기간에 입주할 수 있는 방만 (항상 표시) */}
            <RoomRecommendations room={{ ...r, roomId }} moveIn={a.moveIn} months={a.months} conflict={Boolean(conflict)} />
          </>
        }
        aside={
          <>
            <Card>
              <div className="flex items-center justify-between border-b border-line px-5 py-3.5 text-sm">
                <span className="text-sub">입주일</span>
                <span className="font-medium">{ymdDay(a.moveIn)}</span>
              </div>
              <Calendar value={a.moveIn} onChange={a.setMoveIn} min={today()} isBlocked={booked ? isBookedDay : undefined} />
            </Card>
            <InfoList rows={[['계약 기간', `${a.months}개월`], ['퇴실 예정일', ymdDay(a.moveOut)], ...amountRows(r)]} />
            <AmountBox amount={a.firstPayment} />
            <div className="space-y-2 pt-1">
              {conflict && (
                <p role="alert" className="rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
                  선택한 기간이 이미 예약된 기간({conflict.moveInDate} ~ {conflict.moveOutDate})과 겹쳐요. 입주일이나 계약
                  기간을 바꾸거나, 아래 입주할 수 있는 다른 방을 확인해보세요.
                </p>
              )}
              {saveError && (
                <p role="alert" className="rounded-lg bg-warn-bg/60 px-4 py-3 text-sm text-warn-fg">
                  {saveError}
                </p>
              )}
              <Button className="w-full" disabled={available === false || saving} onClick={goNext}>
                {saving ? '저장 중…' : '다음 단계 ›'}
              </Button>
              <Button variant="outline" className="w-full" onClick={openConsult}>
                상담 신청 ›
              </Button>
            </div>
          </>
        }
      />
    </>
  );
}

