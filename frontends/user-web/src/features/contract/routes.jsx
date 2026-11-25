import { Outlet, Route } from 'react-router';
import { ApplyProvider } from './context/ApplyContext';
import ApplyFormPage from './pages/ApplyFormPage';
import ApplyRoomPage from './pages/ApplyRoomPage';
import ApplySubmittedPage from './pages/ApplySubmittedPage';
import ContractCompletePage from './pages/ContractCompletePage';
import ContractPayApprovePage from './pages/ContractPayApprovePage';
import ContractPayPage from './pages/ContractPayPage';
import ContractPayReturnPage from './pages/ContractPayReturnPage';
import ContractSignPage from './pages/ContractSignPage';
import MyApplicationPage from './pages/MyApplicationPage';

// ---------------------------------------------------------------
// 계약 신청 화면 (contract 담당). user-web App.jsx에는 {contractRoutes} 한 줄만 넣는다.
// 헤더·푸터는 user-web UserLayout을 그대로 쓰고, 계약 화면 전용 상태(ApplyProvider)와
// 글꼴은 이 안에서만 적용한다 → 다른 화면에 영향 없음
// ---------------------------------------------------------------
function ContractShell() {
  return (
    <ApplyProvider>
      <div className="text-ink">
        <Outlet />
      </div>
    </ApplyProvider>
  );
}

export const contractRoutes = (
  <Route element={<ContractShell />}>
    {/* 신청 전: roomId만 있음 (?roomId=1). 이전 단계로 돌아오면 &applicationNo= 도 붙음 */}
    <Route path="/apply/room" element={<ApplyRoomPage />} />
    {/* 신청 후: 신청번호를 URL에 둔다. 접근 권한은 서버가 JWT로 본인 확인 */}
    <Route path="/apply/:applicationNo/form" element={<ApplyFormPage />} />
    <Route path="/apply/:applicationNo/submitted" element={<ApplySubmittedPage />} />
    <Route path="/contract/:applicationNo/sign" element={<ContractSignPage />} />
    <Route path="/contract/:applicationNo/pay" element={<ContractPayPage />} />
    <Route path="/contract/:applicationNo/complete" element={<ContractCompletePage />} />
    {/* 카카오페이 결제창에서 돌아오는 주소. config의 approval-url / cancel-url / fail-url과 같아야 한다 */}
    <Route path="/contract/pay/approve" element={<ContractPayApprovePage />} />
    <Route path="/contract/pay" element={<ContractPayReturnPage />} />
    {/* 나의 신청: 신청 목록 + 계약서 보기. ?applicationNo= 로 특정 신청을 바로 연다 */}
    <Route path="/my/application" element={<MyApplicationPage />} />
  </Route>
);
