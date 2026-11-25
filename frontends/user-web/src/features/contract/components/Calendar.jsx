import { useState } from 'react';
import { DAY_NAMES } from '../lib/format';

// value: 선택된 날짜, min: 이보다 이전 날짜는 고를 수 없음 (없으면 제한 없음)
// isBlocked(date): true면 이미 예약된 날짜 → 고를 수 없고 따로 표시 (없으면 제한 없음)
export default function Calendar({ value, onChange, min, isBlocked }) {
  const [view, setView] = useState(new Date(value.getFullYear(), value.getMonth(), 1));
  // 선택 날짜가 밖에서 다른 달로 바뀌면(URL 값, 서버 값 등) 보이는 달도 따라간다
  const [shown, setShown] = useState(value);
  if (shown !== value) {
    setShown(value);
    if (value.getFullYear() !== view.getFullYear() || value.getMonth() !== view.getMonth()) {
      setView(new Date(value.getFullYear(), value.getMonth(), 1));
    }
  }
  const y = view.getFullYear();
  const m = view.getMonth();
  const isPast = (d) => min && new Date(y, m, d) < min;
  const canPrev = !min || new Date(y, m, 1) > min; // 이전 달로 가도 고를 날이 있는지
  // 년·월 드롭다운 범위: 년은 min(보통 오늘)이 있는 해부터 +5년, 월은 min보다 이전 달은 고를 수 없다
  const startYear = Math.min(min ? min.getFullYear() : y - 5, y);
  const years = Array.from({ length: Math.max(startYear + 5, y) - startYear + 1 }, (_, i) => startYear + i);
  const monthDisabled = (yy, mm) => !!min && new Date(yy, mm + 1, 0) < min; // 그 달 마지막 날도 min보다 이전
  const goTo = (yy, mm) => {
    // 고른 해에서 min보다 이전 달이면 가장 빠른 달로 맞춘다
    while (mm < 11 && monthDisabled(yy, mm)) mm += 1;
    setView(new Date(yy, mm, 1));
  };
  const SELECT =
    'rounded-lg border border-line bg-surface px-2 py-1 text-sm font-semibold text-ink focus-visible:outline-2 focus-visible:outline-accent';
  const firstDay = new Date(y, m, 1).getDay();
  const days = new Date(y, m + 1, 0).getDate();
  const cells = [...Array(firstDay).fill(null), ...Array.from({ length: days }, (_, i) => i + 1)];
  const isSel = (d) => d && value.getFullYear() === y && value.getMonth() === m && value.getDate() === d;
  const isBooked = (d) => !isPast(d) && !!isBlocked?.(new Date(y, m, d));

  return (
    <div className="px-5 pb-4 pt-2">
      <div className="mb-2 flex items-center justify-between">
        <button
          type="button"
          aria-label="이전 달"
          disabled={!canPrev}
          className="size-8 rounded-lg text-sub hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent"
          onClick={() => setView(new Date(y, m - 1, 1))}
        >
          ‹
        </button>
        <div className="flex items-center gap-1.5">
          <select aria-label="년" value={y} className={SELECT} onChange={(e) => goTo(Number(e.target.value), m)}>
            {years.map((yy) => (
              <option key={yy} value={yy}>
                {yy}년
              </option>
            ))}
          </select>
          <select aria-label="월" value={m} className={SELECT} onChange={(e) => goTo(y, Number(e.target.value))}>
            {Array.from({ length: 12 }, (_, mm) => (
              <option key={mm} value={mm} disabled={monthDisabled(y, mm)}>
                {mm + 1}월
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          aria-label="다음 달"
          className="size-8 rounded-lg text-sub hover:bg-primary/10"
          onClick={() => setView(new Date(y, m + 1, 1))}
        >
          ›
        </button>
      </div>
      <div className="grid grid-cols-7 gap-y-1 text-center text-sm">
        {DAY_NAMES.map((d) => (
          <span key={d} className="py-1 text-xs text-muted">
            {d}
          </span>
        ))}
        {cells.map((d, i) =>
          d ? (
            <button
              type="button"
              key={i}
              disabled={isPast(d) || isBooked(d)}
              title={isBooked(d) ? '이미 예약된 날짜예요' : undefined}
              onClick={() => onChange(new Date(y, m, d))}
              className={`mx-auto size-9 rounded-full ${
                isSel(d)
                  ? isBooked(d)
                    ? 'bg-warn-fg font-semibold text-background'
                    : 'bg-primary font-semibold text-background'
                  : isPast(d)
                    ? 'cursor-not-allowed text-muted/60 line-through'
                    : isBooked(d)
                      ? 'cursor-not-allowed bg-warn-bg text-warn-fg/70 line-through'
                      : 'text-ink hover:bg-primary/10'
              }`}
            >
              {d}
            </button>
          ) : (
            <span key={i} />
          ),
        )}
      </div>
      {isBlocked && (
        <p className="mt-3 flex items-center gap-1.5 text-xs text-sub">
          <span className="inline-block size-3 rounded-full bg-warn-bg" aria-hidden="true" />
          이미 예약된 날짜
        </p>
      )}
    </div>
  );
}
