// ---------------------------------------------------------------
// 계약 화면(contract-web) 밖으로 나가는 링크
//
// 홈 · 나의 신청 · 마이페이지는 user-web 화면이다. contract-web 안에서 navigate('/')를 하면
// App.jsx의 "*" 규칙 때문에 /apply/room(호실 선택)으로 돌아가 버린다.
//
//   VITE_USER_WEB_URL 있음 → 그 주소로 이동 (지금처럼 contract-web을 따로 띄울 때)
//   VITE_USER_WEB_URL 없음 → 같은 앱 안 경로로 이동 (나중에 user-web에 화면을 합칠 때)
// ---------------------------------------------------------------
const USER_WEB = (import.meta.env.VITE_USER_WEB_URL ?? '').replace(/\/$/, '');

// user-web 경로 (user-web App.jsx 기준)
export const USER_PATHS = {
  home: '/',
  rooms: '/property/buildings',
  myApplication: '/my/application',
  myPage: '/my/profile',
};

/** 나의 신청 화면 경로. 신청번호를 주면 그 신청(계약서)을 바로 선택해서 연다 */
export const myApplicationPath = (applicationNo) =>
  applicationNo
    ? `${USER_PATHS.myApplication}?applicationNo=${encodeURIComponent(applicationNo)}`
    : USER_PATHS.myApplication;

/** <a href>에 쓸 전체 주소 */
export const userWebHref = (path) => (USER_WEB ? USER_WEB + path : path);

/** 버튼 클릭으로 이동. navigate는 react-router의 useNavigate() 값 */
export function goUserWeb(navigate, path) {
  if (USER_WEB) {
    window.location.assign(USER_WEB + path);
  } else {
    navigate(path);
  }
}
