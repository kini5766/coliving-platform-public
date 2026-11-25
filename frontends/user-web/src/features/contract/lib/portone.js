// user-web package.json을 건드리지 않도록 npm 패키지 대신 포트원 공식 CDN 스크립트를 필요할 때만 불러온다
// (본인인증 버튼을 누를 때 1번 로드 → window.PortOne)
const PORTONE_SDK_URL = 'https://cdn.portone.io/v2/browser-sdk.js';
let sdkPromise = null;
function loadPortOne() {
  if (window.PortOne) return Promise.resolve(window.PortOne);
  if (!sdkPromise) {
    sdkPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = PORTONE_SDK_URL;
      script.async = true;
      script.onload = () => (window.PortOne ? resolve(window.PortOne) : reject(new Error('포트원 SDK를 불러오지 못했어요.')));
      script.onerror = () => {
        sdkPromise = null; // 다음 클릭 때 다시 시도
        reject(new Error('포트원 SDK를 불러오지 못했어요. 네트워크를 확인해주세요.'));
      };
      document.head.appendChild(script);
    });
  }
  return sdkPromise;
}

// ---------------------------------------------------------------
// 포트원 V2 본인인증 (포트원은 본인인증 전용, 결제는 카카오페이)
//
// .env.local
//   VITE_PORTONE_STORE_ID=store-...                (포트원 콘솔 > 연동 정보)
//   VITE_PORTONE_IDENTITY_CHANNEL_KEY=channel-key-... (본인인증 PG로 연동한 채널)
//   ※ API 시크릿은 절대 프론트에 넣지 않는다 (서버만 가짐)
//
// ⚠ 인증창 결과만 믿으면 안 된다. identityVerificationId를 서버로 보내서
//   서버가 포트원에 직접 조회 + 신청자 이름·휴대폰과 비교해야 한다 (lib/api.js confirmIdentity)
// ---------------------------------------------------------------
const STORE_ID = 'store-3814d2cc-4fb8-45c9-9613-038ba8b4ccbe';
const CHANNEL_KEY = 'channel-key-307f45aa-8a20-43ce-abc9-c2a2551846d3';
// 1 = 포트원 창을 띄우지 않고 가짜 ID 사용 (서버 dummy-portone 프로필과 함께 쓰기)
const MOCK = import.meta.env.VITE_PORTONE_MOCK === '1' || import.meta.env.VITE_USE_MOCK === '1';

export class IdentityCanceledError extends Error {}

/**
 * 본인인증 창을 띄우고 identityVerificationId를 돌려준다.
 * - 실패·사용자 취소 → 에러
 * - 모바일 등에서 리디렉션 방식으로 열리면 이 함수는 끝나지 않고 페이지가 이동한다
 *   → 돌아온 화면에서 readIdentityRedirect()로 결과를 읽는다
 */
export async function openIdentityVerification(applicationNo) {
  // 매번 새 ID (이미 완료된 ID로 다시 요청하면 포트원이 실패시킨다)
  const identityVerificationId = `iv-${applicationNo}-${crypto.randomUUID()}`;
  if (MOCK) return identityVerificationId;

  if (!STORE_ID || !CHANNEL_KEY) {
    throw new Error('본인인증 설정이 없어요. (.env.local VITE_PORTONE_STORE_ID / VITE_PORTONE_IDENTITY_CHANNEL_KEY)');
  }

  const PortOne = await loadPortOne();
  const res = await PortOne.requestIdentityVerification({
    storeId: STORE_ID,
    channelKey: CHANNEL_KEY,
    identityVerificationId,
    // 리디렉션 방식이면 이 주소로 돌아온다: ?identityVerificationId=...&code=...&message=...
    redirectUrl: `${window.location.origin}${window.location.pathname}`,
  });

  if (!res) throw new Error('본인인증 창을 열지 못했어요.');
  if (res.code) {
    // 사용자가 창을 닫은 경우도 code로 온다 → 화면에서는 조용히 넘어가도록 구분
    if (/cancel/i.test(res.code)) throw new IdentityCanceledError(res.message ?? '본인인증을 취소했어요.');
    throw new Error(res.message ?? '본인인증에 실패했어요.');
  }
  return res.identityVerificationId;
}

/**
 * 리디렉션으로 돌아왔을 때 URL에서 결과 읽기. 없으면 null.
 * 읽은 뒤에는 주소창에서 쿼리를 지워서 새로고침해도 다시 처리하지 않게 한다.
 */
export function readIdentityRedirect() {
  const params = new URLSearchParams(window.location.search);
  const id = params.get('identityVerificationId');
  if (!id) return null;
  const result = { identityVerificationId: id, code: params.get('code'), message: params.get('message') };
  window.history.replaceState(null, '', window.location.pathname);
  return result;
}
