import { createContext, useContext, useMemo, useState } from 'react';
import { useParams } from 'react-router';
import { moveOutDate, today } from '../lib/format';

// 목업 호실. 필드 이름은 RoomApplyResponse와 맞춤
// - 1-1 화면은 lib/api.js의 getRoom()으로 실제 응답을 받아 setRoom으로 교체한다
// - 1-1을 거치지 않고 들어온 나머지 화면은 아직 이 값을 쓴다 (신청 조회 API 연결 전)
export const ROOM = {
  roomId: 1,
  buildingName: '[건물명]',
  region: '서울 성동구',
  roomName: '302호',
  roomType: '1인실',
  thumbnailUrl: null,
  roomFacilities: ['전용 욕실'],
  area: 13.5,
  floorNumber: 3,
  monthlyFee: 780000,
  managementFee: 50000,
  deposit: 1000000,
  available: true,
};

// 10/27 할인 제외 → 개월 수만 선택
export const PERIODS = [3, 6, 12];

// 목업 신청번호 (PreviewBar 등에서 사용). 실제로는 POST /apply/applications 응답의 applicationNo
export const MOCK_APPLICATION_NO = 'CT-20261021-0042';

// 첫 결제 금액 = 첫 달 이용료 + 보증금 (관리비는 별도)
// ⚠ 서버 Contract.applyRoomAndPeriod()의 firstPayment 계산식과 반드시 같게 유지할 것.
//   신청번호가 생긴 뒤의 화면은 이 값 대신 서버 응답의 firstPaymentAmount를 쓴다.
export const firstPaymentOf = (room) => room.monthlyFee + room.deposit;

const ApplyContext = createContext(null);

export function ApplyProvider({ children }) {
  // 입주일 기본값 = 오늘 (서버는 오늘 이전 날짜를 거절한다: "입주일은 오늘 이후로 선택해주세요.")
  // 호실 선택 화면에 ?moveInDate= 가 있으면 그 값으로 바뀐다 (ApplyRoomPage)
  const [moveIn, setMoveIn] = useState(today);
  const [months, setMonths] = useState(6);
  const [room, setRoom] = useState(ROOM);

  const value = useMemo(
    () => ({
      room,
      setRoom,
      moveIn,
      setMoveIn,
      months,
      setMonths,
      moveOut: moveOutDate(moveIn, months),
      firstPayment: firstPaymentOf(room),
    }),
    [moveIn, months, room],
  );

  return <ApplyContext.Provider value={value}>{children}</ApplyContext.Provider>;
}

export const useApply = () => useContext(ApplyContext);

// 신청번호는 Context가 아니라 URL에서 읽는다 → 새로고침해도 유지
export const useApplicationNo = () => useParams().applicationNo;
