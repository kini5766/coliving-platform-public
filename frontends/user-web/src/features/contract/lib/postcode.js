// 카카오(다음) 우편번호 서비스 — 키·가입 없이 쓰는 국내 주소 검색
// portone.js와 같은 방식: npm 패키지 대신 공식 CDN 스크립트를 "주소 찾기"를 누를 때 1번만 불러온다 (→ window.daum.Postcode)
const POSTCODE_SDK_URL = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
let sdkPromise = null;
function loadPostcode() {
  if (window.daum?.Postcode) return Promise.resolve(window.daum.Postcode);
  if (!sdkPromise) {
    sdkPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = POSTCODE_SDK_URL;
      script.async = true;
      script.onload = () =>
        window.daum?.Postcode ? resolve(window.daum.Postcode) : reject(new Error('주소 검색을 불러오지 못했어요.'));
      script.onerror = () => {
        sdkPromise = null; // 다음 클릭 때 다시 시도
        reject(new Error('주소 검색을 불러오지 못했어요. 네트워크를 확인해주세요.'));
      };
      document.head.appendChild(script);
    });
  }
  return sdkPromise;
}

// 주소 검색 창을 띄우고, 주소를 고르면 onPicked({ ko, en })를 부른다
//   ko: 사용자가 고른 주소(도로명/지번) + 건물명    예) 서울 강남구 테헤란로 152 (강남파이낸스센터)
//   en: 같은 주소의 영문 표기                      예) 152 Teheran-ro, Gangnam-gu, Seoul, Republic of Korea
// 창을 그냥 닫으면 아무 일도 일어나지 않는다. 스크립트를 못 불러오면 reject
export async function openAddressSearch(onPicked) {
  const Postcode = await loadPostcode();
  new Postcode({
    oncomplete: (data) => {
      const ko = data.buildingName ? `${data.address} (${data.buildingName})` : data.address;
      onPicked({ ko, en: data.addressEnglish || ko });
    },
  }).open();
}

// 기본 주소 + 상세 주소 → 서버로 보내는 한 문자열 (서버·DB는 주소 한 칸만 가짐)
//   한국어: 기본 주소 뒤에 상세    예) 서울 강남구 테헤란로 152 (강남파이낸스센터) 101동 1001호
//   영문:   상세를 앞에           예) 101-1001, 152 Teheran-ro, Gangnam-gu, Seoul, Republic of Korea
export function joinAddress(base, detail, lang) {
  const b = (base ?? '').trim();
  const d = (detail ?? '').trim();
  if (!b || !d) return b || d;
  return lang === 'en' ? `${d}, ${b}` : `${b} ${d}`;
}
