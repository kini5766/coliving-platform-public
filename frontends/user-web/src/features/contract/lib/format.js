const DAY = ['일', '월', '화', '수', '목', '금', '토'];

export const won = (n) => n.toLocaleString('ko-KR');

export const ymd = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export const ymdDay = (d) => `${ymd(d)} (${DAY[d.getDay()]})`;

// "2026-11-02" → 2026-11-02 0시 (로컬 시간)
// new Date("2026-11-02")는 UTC 자정으로 읽혀서 시간대에 따라 하루 밀릴 수 있어 직접 나눠서 만든다.
// 형식이 틀렸거나 없는 날짜("2026-02-30", "abc", null)면 null
export const parseYmd = (s) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s ?? '');
  if (!match) return null;
  const [y, m, d] = match.slice(1).map(Number);
  const date = new Date(y, m - 1, d);
  // 2026-02-30처럼 넘치는 날짜는 Date가 3월로 바꿔버리므로 그대로인지 확인
  return date.getFullYear() === y && date.getMonth() === m - 1 && date.getDate() === d ? date : null;
};

// 오늘 0시 (로컬 시간). 날짜만 비교할 때 쓴다
export const today = () => {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
};

// 퇴실 예정일 = 입주일 + 계약 개월 - 1일 (SQL: stay_period '[]' 퇴실일 당일 포함)
// 서버 moveInDate.plusMonths(months).minusDays(1)과 같게: 그 달에 없는 날(11/30 + 3개월 → 2/30)은 말일로 맞춘다.
//   예) 2026-11-30 + 3개월 → 2027-02-28 → 퇴실 2027-02-27
export const moveOutDate = (moveIn, months) => {
  const y = moveIn.getFullYear();
  const m = moveIn.getMonth() + months;
  const lastDay = new Date(y, m + 1, 0).getDate(); // 도착한 달의 마지막 날
  const d = new Date(y, m, Math.min(moveIn.getDate(), lastDay));
  d.setDate(d.getDate() - 1);
  return d;
};

export const DAY_NAMES = DAY;

// "전용 욕실 · 13.5㎡ · 3층" (RoomApplyResponse의 roomFacilities + area + floorNumber 조합)
export const roomSpec = (r) => [...r.roomFacilities, `${r.area}㎡`, `${r.floorNumber}층`].join(' · ');

// 금액 표시 행: 화면마다 똑같이 보여주기 위함
// 첫 결제 금액(firstPayment) = 월 이용료 + 보증금. 관리비는 첫 결제에 포함되지 않아 "별도"로 표시
export const amountRows = (r) => [
  ['월 이용료', `${won(r.monthlyFee)}원`],
  ['보증금', `${won(r.deposit)}원`],
  ['관리비 (월, 별도)', `${won(r.managementFee)}원`],
];
