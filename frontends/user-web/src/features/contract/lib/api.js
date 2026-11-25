import { MOCK_APPLICATION_NO, ROOM } from '../context/ApplyContext';
import { moveOutDate, today, ymd } from './format';

// 목업 입주일·퇴실일: 날짜를 고정하면 하루만 지나도 "지난 날짜"가 되므로 오늘 기준으로 만든다 (6개월)
const MOCK_MOVE_IN = ymd(today());
const MOCK_MOVE_OUT = ymd(moveOutDate(today(), 6));

// ---------------------------------------------------------------
// 계약 서비스 API 호출은 모두 이 파일을 거친다.
// 화면(pages)은 fetch를 직접 쓰지 않고 여기 함수만 부른다.
//
// 목업 / 실제 전환
//   .env.local 에 VITE_USE_MOCK=1 → 서버 없이 목업 값 반환
//   미리보기 빌드(VITE_PREVIEW=1)도 서버가 없으므로 목업
//   그 외 → /contract-service/** 로 실제 호출 (vite.config.js 프록시가 넘김)
//
// 로그인 사용자
//   실제로는 gateway가 로그인 토큰을 확인하고 X-User 헤더를 넣어준다.
//   gateway 없이 8089로 직접 붙는 개발 중에는 .env.local 의 VITE_DEV_USER_ID 값을 X-User로 직접 보낸다.
//   (gateway를 거치면 gateway가 이 값을 덮어쓰거나 지우므로 운영에서는 의미 없음)
// ---------------------------------------------------------------
const USE_MOCK = import.meta.env.VITE_USE_MOCK === '1' || import.meta.env.VITE_PREVIEW === '1';
const DEV_USER_ID = import.meta.env.VITE_DEV_USER_ID;
// user-web은 프록시 없이 gateway 주소(VITE_BACKEND_API_BASE_URL)로 바로 요청한다 (session.js와 같은 방식)
const GATEWAY = import.meta.env.VITE_BACKEND_API_BASE_URL ?? '';
const BASE = `${GATEWAY}/contract-service`;
// 신청자 정보(계약 자동완성)는 유저 서비스가 가지고 있다. 로그인 세션 쿠키로 본인 것만 조회·저장
const USER_BASE = `${GATEWAY}/user-service`;
// 호실 정보는 매물 서비스가 가지고 있다. GET /property-service/property/** 는 게이트웨이가 비회원에게도 열어 둠
const PROPERTY_BASE = `${GATEWAY}/property-service`;

// 서버 에러를 화면에서 구분하기 위한 에러 (status로 404 / 409 / 503 등을 나눈다)
export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(path, { method = 'GET', body, headers, base = BASE } = {}) {
  let res;
  try {
    res = await fetch(base + path, {
      method,
      credentials: 'include', // 로그인 쿠키 → gateway가 토큰으로 바꿔서 X-User 헤더를 붙임
      headers: {
        'Content-Type': 'application/json',
        ...(DEV_USER_ID && base === BASE && { 'X-User': DEV_USER_ID }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    // 서버(gateway)가 아예 꺼져 있거나 네트워크가 끊긴 경우
    throw new ApiError(0, '서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.');
  }

  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return null;
  return res.json();
}

async function toApiError(res) {
  // CustomControllerAdvice의 buildError는 { message } JSON, RuntimeException 핸들러는 일반 문자열
  let message = `요청에 실패했어요. (${res.status})`;
  try {
    const text = await res.text();
    try {
      message = JSON.parse(text).message ?? message;
    } catch {
      if (text) message = text;
    }
  } catch {
    // 본문을 못 읽으면 기본 메시지 사용
  }
  return new ApiError(res.status, message);
}

// 목업 응답에도 약간의 지연을 줘서 로딩 화면을 확인할 수 있게 함
const mock = (value, ms = 300) => new Promise((resolve) => setTimeout(() => resolve(value), ms));

// 목업 신청 상태: 지금 보는 화면에 맞는 상태를 돌려준다 (서버 없이 2~4단계 화면 확인용)
const mockStatus = () => {
  const path = window.location.hash || window.location.pathname;
  if (path.endsWith('/complete')) return 'PAID';
  if (path.endsWith('/sign') || path.endsWith('/pay')) return 'APPROVED';
  return 'SUBMITTED';
};

// ---------------------------------------------------------------
// [1-1 호실 선택] GET /property-service/property/rooms/{roomId} (매물 서비스, Confluence "매물 사용자 API (계약화면)")
// 없는/삭제된 호실이면 404 "매물을 찾을 수 없습니다."
const ROOM_PATH = (roomId) => `/property/rooms/${encodeURIComponent(roomId)}`;

// 매물 서비스 응답(RoomDetail) → 계약 화면이 쓰는 호실 모양(ApplyContext의 ROOM과 같은 필드)
//   id → roomId, address → region(앞 두 단어만, 예: "서울 성동구"), facilities[{category,name}] → roomFacilities[name],
//   areaM2 → area, floor → floorNumber
//   available은 계약 서비스 연동 전이라 null일 수 있다 (null이면 화면이 배지를 숨김)
// 도로명 주소 → "서울 성동구"처럼 시/도 + 시/군/구만 남긴다 (매물 응답에 region이 없어서 주소에서 뽑음)
//   "서울특별시 성동구 왕십리로 123" → "서울 성동구", "경기도 성남시 분당구 ..." → "경기 성남시"
// 시/도 정식 이름 → 약칭. 표에 없으면(이미 '서울'처럼 짧게 온 경우 등) 그대로 쓴다
const SIDO_SHORT = {
  서울특별시: '서울', 부산광역시: '부산', 대구광역시: '대구', 인천광역시: '인천', 광주광역시: '광주',
  대전광역시: '대전', 울산광역시: '울산', 세종특별자치시: '세종', 경기도: '경기', 강원도: '강원',
  강원특별자치도: '강원', 충청북도: '충북', 충청남도: '충남', 전라북도: '전북', 전북특별자치도: '전북',
  전라남도: '전남', 경상북도: '경북', 경상남도: '경남', 제주특별자치도: '제주',
};
const toRegion = (address) => {
  if (!address) return '';
  const [sido, sigungu] = address.trim().split(/\s+/);
  const shortSido = SIDO_SHORT[sido] ?? sido;
  // 세종시처럼 시/군/구 없이 바로 도로명이 오는 경우는 시/도만 쓴다
  return /[시군구]$/.test(sigungu ?? '') ? `${shortSido} ${sigungu}` : shortSido;
};

const toRoom = (res) => ({
  ...res,
  roomId: res.id,
  region: toRegion(res.address),
  roomFacilities: (res.facilities ?? []).map((f) => f.name),
  area: res.areaM2,
  floorNumber: res.floor,
});

export function getRoom(roomId) {
  if (USE_MOCK) return mock({ ...ROOM, roomId: Number(roomId) });
  return request(ROOM_PATH(roomId), { base: PROPERTY_BASE }).then(toRoom);
}

// ---------------------------------------------------------------
// [1-1 호실 선택] GET /apply/rooms/{roomId}/booked-periods → BookedPeriodResponse[]
// 이 호실에 이미 잡혀 있는 기간 [{ moveInDate: "2026-12-01", moveOutDate: "2027-05-31" }] (오늘 이후 끝나는 것만)
// 본인이 이 호실로 작성 중인 신청은 빠져 있다 ("다음 단계"에서 그 신청을 재사용하므로)
export function getBookedPeriods(roomId) {
  if (USE_MOCK) {
    const t = today();
    const start = new Date(t.getFullYear(), t.getMonth(), t.getDate() + 40);
    return mock([{ moveInDate: ymd(start), moveOutDate: ymd(moveOutDate(start, 3)) }]);
  }
  return request(`/apply/rooms/${encodeURIComponent(roomId)}/booked-periods`);
}

// ---------------------------------------------------------------
// [1-1 → 다음 단계] POST /apply/applications → ApplicationDraftResponse
// body: { roomId, moveInDate: "2026-11-02", months, room: { ...호실 스냅샷 } }
// room은 getRoom()으로 받은 매물 서비스 응답을 계약 서비스 ApplicationDraftRequest.RoomInfo 모양으로 바꿔 보낸다
// (빠지면 계약 서비스가 400 "호실 정보가 없습니다.")
// 같은 호실로 작성 중인 신청서가 있으면 서버가 그걸 수정해서 같은 신청번호를 돌려준다
const toRoomInfo = (room) => room && ({
  buildingId: room.buildingId,
  buildingName: room.buildingName,
  address: room.address,
  roomName: room.roomName,
  roomType: room.roomType,
  roomStructure: room.roomStructure ?? null,
  floorNumber: room.floorNumber,
  area: room.area,
  roomFacilities: room.roomFacilities ?? [],
  monthlyFee: room.monthlyFee,
  deposit: room.deposit,
  managementFee: room.managementFee,
});

export function createApplication({ roomId, moveInDate, months, room }) {
  if (USE_MOCK) return mock({ applicationNo: MOCK_APPLICATION_NO, status: 'DRAFT' });
  return request('/apply/applications', {
    method: 'POST',
    body: { roomId: Number(roomId), moveInDate, months, room: toRoomInfo(room) },
  });
}

// ---------------------------------------------------------------
// [1-2 신청자 정보] GET /apply/applications/{applicationNo}/form → ApplyFormResponse
export function getApplyForm(applicationNo) {
  if (USE_MOCK) {
    return mock({
      applicationNo,
      roomId: ROOM.roomId,
      buildingName: ROOM.buildingName,
      region: ROOM.region,
      roomName: ROOM.roomName,
      roomType: ROOM.roomType,
      moveInDate: MOCK_MOVE_IN,
      moveOutDate: MOCK_MOVE_OUT,
      months: 6,
      monthlyFee: ROOM.monthlyFee,
      managementFee: ROOM.managementFee,
      deposit: ROOM.deposit,
      firstPaymentAmount: ROOM.monthlyFee + ROOM.deposit,
      contractLanguage: null,
      termsVersion: '1',
    });
  }
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}/form`);
}

// ---------------------------------------------------------------
// [1-2 신청자 정보 자동완성] 유저 서비스 ContractAutocompleteController
//   GET /user-service/user/contract-autocomplete → { email, name, phone, address, contractLanguage }
//   - 값이 없으면 "" (행이 없어도 200). 세션이 없으면 401
//   - 예전에는 유저 서비스가 gRPC로 계약 서비스에 보내줬지만, 이제 화면이 직접 불러와 입력칸을 채운다
export const EMPTY_AUTOCOMPLETE = { email: '', name: '', phone: '', address: '', contractLanguage: '' };

const str = (v) => (typeof v === 'string' ? v : '');
const toAutocomplete = (b) => ({
  email: str(b?.email),
  name: str(b?.name),
  phone: str(b?.phone),
  address: str(b?.address),
  contractLanguage: str(b?.contractLanguage),
});

export function getContractAutocomplete() {
  if (USE_MOCK) {
    return mock({ email: 'gildong@email.com', name: '홍길동', phone: '01012345678', address: '', contractLanguage: 'ko' });
  }
  return request('/user/contract-autocomplete', { base: USER_BASE }).then(toAutocomplete);
}

//   PUT /user-service/user/contract-autocomplete — 다섯 필드를 통째로 교체 (빈 값은 해당 칸 삭제)
//   400: 이메일 형식 오류 / contractLanguage 10자 초과
export function saveContractAutocomplete({ email, name, phone, address, contractLanguage }) {
  const body = toAutocomplete({ email, name, phone, address, contractLanguage });
  if (USE_MOCK) return mock(body);
  return request('/user/contract-autocomplete', { method: 'PUT', body, base: USER_BASE }).then(toAutocomplete);
}

// 유저 서비스 자동완성과 같은 이메일 규칙: @가 있고 그 뒤에 .이 있으며 공백 없음
export function isEmail(value) {
  const at = value.indexOf('@');
  return at > 0 && value.indexOf('.', at + 1) > at + 1 && !/\s/.test(value);
}

// ---------------------------------------------------------------
// [1-2 신청서 제출] POST /apply/applications/{applicationNo}/submit → ApplicationSubmitResponse
// body: { name, phone, email, address, contractLang: "ko" | "en", termsVersion }
// 신청자 정보는 화면에서 입력한 값을 그대로 보낸다 (서버가 신청서에 스냅샷으로 저장)
export function submitApplication(applicationNo, { name, phone, email, address, contractLang, termsVersion }) {
  if (USE_MOCK) return mock({ applicationNo, status: 'SUBMITTED' });
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}/submit`, {
    method: 'POST',
    body: { name, phone, email, address, contractLang, termsVersion },
  });
}

// ---------------------------------------------------------------
// [접수 완료 · 나의 신청 상세] GET /apply/applications/{applicationNo} → ApplicationDetailResponse
// 새로고침해도 서버(DB) 값으로 화면을 다시 그리기 위해 사용
export function getApplication(applicationNo) {
  if (USE_MOCK) {
    const status = mockStatus();
    return mock({
      applicationNo,
      status,
      roomId: ROOM.roomId,
      buildingName: ROOM.buildingName,
      roomName: ROOM.roomName,
      roomType: ROOM.roomType,
      moveInDate: MOCK_MOVE_IN,
      moveOutDate: MOCK_MOVE_OUT,
      months: 6,
      monthlyFee: ROOM.monthlyFee,
      deposit: ROOM.deposit,
      firstPaymentAmount: ROOM.monthlyFee + ROOM.deposit,
      submittedAt: '2026-11-02T10:00:00+09:00',
      rejectReason: null,
      paymentDeadline: null,
      payment:
        status === 'PAID'
          ? {
              method: 'KAKAOPAY',
              paidAmount: ROOM.monthlyFee + ROOM.deposit,
              paidAt: '2026-11-02T14:05:00+09:00',
              receiptUrl: null,
            }
          : null,
    });
  }
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}`);
}

// ---------------------------------------------------------------
// [접수 완료 "취소하기"] POST /apply/applications/{applicationNo}/cancel → ApplicationDetailResponse
// 심사 중(SUBMITTED)일 때만 가능. 응답은 바뀐 신청서(status: CANCELED)
export function cancelApplication(applicationNo) {
  if (USE_MOCK) return getApplication(applicationNo).then((a) => ({ ...a, status: 'CANCELED' }));
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}/cancel`, { method: 'POST' });
}

// ---------------------------------------------------------------
// [계약 완료 "입주 전 취소"] POST /apply/applications/{applicationNo}/move-in-cancel → ApplicationDetailResponse
// 결제 완료(PAID·COMPLETED) + 입주일 전날까지만 가능. 전액 환불
// 응답 status: REFUNDED(환불 완료) / MOVE_IN_CANCEL_REQUESTED(카카오 응답 지연 → 서버가 자동으로 다시 환불)
export function cancelBeforeMoveIn(applicationNo) {
  if (USE_MOCK) return getApplication(applicationNo).then((a) => ({ ...a, status: 'REFUNDED', payment: null }));
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}/move-in-cancel`, { method: 'POST' });
}

// ---------------------------------------------------------------
// [나의 신청 목록] GET /apply/applications → ApplicationListItemResponse[]
// 제출한 내 신청 전부 (심사 중·승인·결제·반려·취소 등), 최근 신청 순. 작성 중(DRAFT)은 나오지 않는다
export function getMyApplications() {
  if (USE_MOCK) {
    return mock([
      {
        applicationNo: MOCK_APPLICATION_NO,
        status: 'SUBMITTED',
        buildingName: ROOM.buildingName,
        roomName: ROOM.roomName,
        roomType: ROOM.roomType,
        moveInDate: MOCK_MOVE_IN,
        moveOutDate: MOCK_MOVE_OUT,
        months: 6,
        submittedAt: '2026-11-02T10:00:00+09:00',
      },
    ]);
  }
  return request('/apply/applications');
}

// ---------------------------------------------------------------
// [2 계약서 확인] GET /contracts/{applicationNo}/document → ContractDocumentResponse
// 승인 전이면 404 "아직 발급된 계약서가 없습니다"
export function getContractDocument(applicationNo) {
  if (USE_MOCK) {
    return mock({
      applicationNo,
      documentId: 1,
      language: 'ko',
      content: '주거 공간 이용 계약서\n\n계약번호: ' + applicationNo + '\n임차인: 홍길동\n\n(목업 계약서 본문)',
      contentHash: '0'.repeat(64),
      buildingName: ROOM.buildingName,
      roomName: ROOM.roomName,
      moveInDate: MOCK_MOVE_IN,
      moveOutDate: MOCK_MOVE_OUT,
      firstPaymentAmount: ROOM.monthlyFee + ROOM.deposit,
      identityVerified: false,
      signed: false,
    });
  }
  return request(`/contracts/${encodeURIComponent(applicationNo)}/document`);
}

// ---------------------------------------------------------------
// [2 본인 인증] POST /apply/applications/{applicationNo}/identity-verification → IdentityVerificationResponse
// body: { identityVerificationId } — 이름·휴대폰은 보내지 않는다 (서버가 포트원에 직접 조회)
export function confirmIdentity(applicationNo, identityVerificationId) {
  if (USE_MOCK) return mock({ verified: true, verifiedAt: new Date().toISOString() });
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}/identity-verification`, {
    method: 'POST',
    body: { identityVerificationId },
  });
}

// ---------------------------------------------------------------
// [2 전자 서명] POST /apply/applications/{applicationNo}/signature → SignatureResponse (201)
// signatureImage: PNG Base64 ("data:image/png;base64," 앞부분 제외)
// 서버가 본인인증 재확인 + 해시 대조 후 서명 이미지와 서명 완료 PDF를 저장한다
export function signContract(applicationNo, { identityVerificationId, contentHash, signatureImage }) {
  if (USE_MOCK) return mock({ signatureId: 1, signedAt: new Date().toISOString() });
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}/signature`, {
    method: 'POST',
    body: { identityVerificationId, contentHash, signatureImage },
  });
}

// ---------------------------------------------------------------
// PDF 내려받기 공통: 로그인 정보(쿠키·X-User)가 필요해서 <a href> 대신 fetch로 받아 저장시킨다
async function downloadPdf(path, filename) {
  if (USE_MOCK) throw new ApiError(0, '목업에서는 PDF를 내려받을 수 없어요.');
  let res;
  try {
    res = await fetch(`${BASE}${path}`, {
      credentials: 'include',
      headers: { ...(DEV_USER_ID && { 'X-User': DEV_USER_ID }) },
    });
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.');
  }
  if (!res.ok) throw await toApiError(res);

  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

// [계약서 다운로드] GET /contracts/{applicationNo}/signed-pdf → PDF 파일
export function downloadSignedContract(applicationNo) {
  return downloadPdf(`/contracts/${encodeURIComponent(applicationNo)}/signed-pdf`, `contract-${applicationNo}.pdf`);
}

// [영수증 다운로드] GET /contracts/{applicationNo}/receipt?type=PAYMENT|CANCEL → PDF 파일
// PAYMENT: 결제가 끝난 신청이면 언제든 / CANCEL: 환불이 끝난 신청만 (아니면 409)
export function downloadReceipt(applicationNo, type = 'PAYMENT') {
  const prefix = type === 'CANCEL' ? 'cancel-receipt' : 'receipt';
  return downloadPdf(
    `/contracts/${encodeURIComponent(applicationNo)}/receipt?type=${type}`,
    `${prefix}-${applicationNo}.pdf`,
  );
}

// ---------------------------------------------------------------
// [3 결제 화면] GET /apply/applications/{applicationNo}/payment → PaymentInfoResponse
// 서명 전이거나 결제 대기 상태가 아니면 409
export function getPaymentInfo(applicationNo) {
  if (USE_MOCK) {
    const now = new Date();
    return mock({
      deposit: ROOM.deposit,
      firstMonthFee: ROOM.monthlyFee,
      totalAmount: ROOM.monthlyFee + ROOM.deposit,
      buildingName: ROOM.buildingName,
      roomName: ROOM.roomName,
      managementFee: ROOM.managementFee,
      moveInDate: MOCK_MOVE_IN,
      moveOutDate: MOCK_MOVE_OUT,
      availableMethods: ['KAKAOPAY'],
      paymentDeadline: new Date(now.getTime() + 23 * 3600 * 1000).toISOString(),
      serverTime: now.toISOString(),
    });
  }
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}/payment`);
}

// [3 결제하기] POST .../payment/ready → PaymentReadyResponse (카카오페이 결제창 주소)
export function readyPayment(applicationNo, method) {
  if (USE_MOCK) {
    const hash = import.meta.env.VITE_PREVIEW === '1' ? '#' : '';
    const url = `${hash}/contract/pay/approve?applicationNo=${applicationNo}&orderId=mock-order&pg_token=mock`;
    return mock({ orderId: 'mock-order', method, redirectPcUrl: url, redirectMobileUrl: url });
  }
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}/payment/ready`, {
    method: 'POST',
    body: { method },
  });
}

// [3 결제 승인] POST .../payment/kakaopay/approve → PaymentResultResponse
// 같은 주문을 다시 보내도 서버가 이미 결제된 결과를 돌려준다 (새로고침 안전)
export function approveKakaoPay(applicationNo, { orderId, pgToken }) {
  if (USE_MOCK) return mock({ status: 'PAID', method: 'KAKAOPAY', paidAmount: ROOM.monthlyFee + ROOM.deposit, paidAt: new Date().toISOString() });
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}/payment/kakaopay/approve`, {
    method: 'POST',
    body: { orderId, pgToken },
  });
}

// [3 결제 중단] POST .../payment/abort → 204
// result: "cancel"(결제창에서 취소) | "fail"(결제 실패)
export function abortPayment(applicationNo, { orderId, result }) {
  if (USE_MOCK) return mock(null);
  return request(`/apply/applications/${encodeURIComponent(applicationNo)}/payment/abort`, {
    method: 'POST',
    body: { orderId, result },
  });
}
