import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { findRecommendations } from '../lib/recommendApi';
import { won, ymd } from '../lib/format';
import { USER_PATHS, userWebHref } from '../lib/links';
import Button from './Button';
import Card from './Card';
import Section from './Section';

// 처음 보여줄 개수 / "더보기" 한 번에 추가로 보여줄 개수
const PAGE_SIZE = 5;

// 날짜를 연달아 바꿀 때마다 요청하지 않도록, 마지막 변경 후 잠깐 기다렸다가 부른다
const DEBOUNCE_MS = 300;

/**
 * 1-1 호실 선택 화면 아래쪽 "다른 방 추천" (항상 표시)
 *   같은 건물 → 같은 지역 순으로, 고른 기간(입주일·계약 기간)에 입주할 수 있는 방만 보여준다.
 *   카드를 누르면 고른 입주일·기간을 그대로 들고 그 방의 호실 선택 화면으로 간다.
 *
 * props
 *   room      지금 보고 있는 방 (ApplyContext room)
 *   moveIn    Date
 *   months    3 | 6 | 12
 *   conflict  지금 방이 고른 기간에 막혀 있으면 true → 제목을 강조
 */
export default function RoomRecommendations({ room, moveIn, months, conflict }) {
  const [state, setState] = useState({ status: 'loading', tier: null, region: '', items: [] });
  const moveInYmd = ymd(moveIn);
  const [visible, setVisible] = useState(PAGE_SIZE);

  // 방·입주일·기간이 바뀌면 목록이 새로 만들어지므로 다시 5개부터 보여준다
  useEffect(() => {
    setVisible(PAGE_SIZE);
  }, [room.roomId, moveInYmd, months]);

  useEffect(() => {
    let ignore = false;
    setState((s) => ({ ...s, status: 'loading' }));
    const timer = setTimeout(() => {
      findRecommendations(room, moveInYmd, months)
        .then((res) => !ignore && setState({ status: 'ok', ...res }))
        .catch(() => !ignore && setState({ status: 'error', tier: null, region: '', items: [] }));
    }, DEBOUNCE_MS);
    return () => {
      ignore = true;
      clearTimeout(timer);
    };
    // room 전체가 아니라 추천에 쓰는 값만 의존 (호실 정보 객체가 새로 만들어져도 다시 부르지 않게)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room.roomId, room.buildingId, moveInYmd, months]);

  // 추천을 못 불러오면 조용히 숨긴다 (신청 자체에는 영향 없음)
  if (state.status === 'error') return null;

  const regionName = state.region || '같은 지역';
  const title =
    state.tier === 'region'
      ? `${regionName}에서 이 기간에 입주할 수 있는 방`
      : conflict
        ? '이 기간에 입주할 수 있는 다른 방'
        : '같은 건물의 다른 방';

  return (
    <Section title={title}>
      {state.status === 'loading' && state.items.length === 0 ? (
        <Card className="px-5 py-4 text-sm text-sub">입주할 수 있는 방을 찾고 있어요…</Card>
      ) : state.tier === 'none' ? (
        <Card className="flex items-center justify-between gap-4 px-5 py-4 text-sm">
          <span className="text-sub">같은 건물과 같은 지역에는 이 기간에 입주할 수 있는 방이 없어요.</span>
          <a href={userWebHref(USER_PATHS.rooms)} className="shrink-0 font-medium text-primary-strong hover:underline">
            다른 지역 둘러보기 ›
          </a>
        </Card>
      ) : (
        <>
          <p className="mb-2 text-sm text-sub">
            {state.tier === 'region'
              ? '같은 건물에는 이 기간에 빈 방이 없어서, 같은 지역의 방을 보여드려요.'
              : '선택한 입주일·계약 기간에 입주할 수 있는 방만 보여드려요.'}
          </p>
          {/* 세로 목록: 처음 5개만 보여주고, "더보기"를 누를 때마다 5개씩 추가 */}
          <ul
            className={`flex flex-col gap-3 transition-opacity ${
              state.status === 'loading' ? 'opacity-50' : ''
            }`}
          >
            {state.items.slice(0, visible).map((c) => (
              <li key={c.roomId}>
                <Link
                  to={`/apply/room?roomId=${c.roomId}&moveInDate=${moveInYmd}&months=${months}`}
                  onClick={() => window.scrollTo(0, 0)} // 같은 화면으로 이동하므로 맨 위부터 보이게
                  className="block h-full rounded-lg border border-line bg-surface p-4 transition-colors hover:border-primary hover:bg-primary-soft"
                >
                  {state.tier === 'region' && <p className="truncate text-xs text-sub">{c.buildingName}</p>}
                  <p className="mt-0.5 font-semibold text-ink">
                    {c.roomName} · {c.roomType}
                  </p>
                  <p className="mt-0.5 text-xs text-sub">
                    {c.floor}층 · {c.area}㎡
                  </p>
                  <p className="mt-2 text-sm font-semibold text-primary-strong">월 {won(c.monthlyFee)}원</p>
                </Link>
              </li>
            ))}
          </ul>
          {state.items.length > visible && (
            <Button
              variant="outline"
              className="mt-3 w-full"
              onClick={() => setVisible((v) => v + PAGE_SIZE)}
            >
              더보기 ({visible}/{state.items.length}) ›
            </Button>
          )}
        </>
      )}
    </Section>
  );
}
