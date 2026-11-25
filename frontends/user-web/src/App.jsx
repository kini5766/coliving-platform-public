import { Routes, Route } from 'react-router'

import UserLayout from './components/UserLayout'

import HomePage from './pages/user/HomePage'
import LoginPage from './pages/user/LoginPage'
import SignupPage from './pages/user/SignupPage'
import SignupCheckEmailPage from './pages/user/SignupCheckEmailPage'
import SignupConfirmPage from './pages/user/SignupConfirmPage'
import PasswordResetPage from './pages/user/PasswordResetPage'
import AccountSettingsPage from './pages/user/AccountSettingsPage'
import ProfilePage, { ResidentProfileRoute } from './pages/user/ProfilePage'
import ContractAutocompletePage from './pages/user/ContractAutocompletePage'

import BuildingList from './pages/property/BuildingList'
import BuildingDetail from './pages/property/BuildingDetail'
import RoomDetail from './pages/property/RoomDetail'

import PrivacyPolicyPage from './pages/user/PrivacyPolicyPage'

import ResourceListPage from './pages/reservation/ResourceListPage'
import FacilityMenuPage from './pages/reservation/FacilityMenuPage'
import ResourceReservationPage from './pages/reservation/ResourceReservationPage'
import ResourceReservationHistoryPage from './pages/reservation/ResourceReservationHistoryPage'

import BlockListPage from './features/community/pages/BlockListPage'
import CommunityPage from './features/community/pages/CommunityPage'
import PostEditorView from './features/community/pages/PostEditorView'

import { contractRoutes } from './features/contract/routes'

import { CommunityGate } from './features/community/components/CommunityGate'
import { CommunityRealtimeProvider } from './features/community/realtime/CommunityRealtimeProvider'



/** 회원 웹 라우트. 로그인·가입·메일 확인·비밀번호 재설정은 헤더와 푸터 밖에 둔다. */
function App() {
    return (
        // BrowserRouter 는 main.jsx 에 존재합니다.
        // 로그인(/login), 회원가입(/signup), 이메일 확인(/signup/check-email),
        // 비밀번호 재설정(/password-reset)은 이 레이아웃 밖에 두어 헤더와 풋터를 노출하지 않습니다.
        <Routes>
            {/**
             * @author 조휘재
             * 작성일: 2026-10-18
             */}
            <Route path="/login" element={<LoginPage />} />
            {/**
             * @author 조휘재
             * 작성일: 2026-10-30
             */}
            <Route path="/signup" element={<SignupPage />} />
            {/**
             * @author 조휘재
             * 작성일: 2026-10-30
             */}
            <Route path="/signup/check-email" element={<SignupCheckEmailPage />} />
            {/**
             * @author 조휘재
             * 작성일: 2026-11-11
             */}
            <Route path="/signup/confirm" element={<SignupConfirmPage />} />
            {/**
             * @author 조휘재
             * 작성일: 2026-11-11
             */}
            <Route path="/password-reset" element={<PasswordResetPage />} />

            {/**
             * @author 조휘재
             * 작성일: 2026-10-27
             */}
            <Route element={<CommunityRealtimeProvider><UserLayout /></CommunityRealtimeProvider>}>

                {/**
                 * @author 김정호
                 * 작성일: 2026-10-27
                 */}
                <Route path="/property/buildings" element={<BuildingList />} />
                {/**
                 * @author 김정호
                 * 작성일: 2026-10-27
                 */}
                <Route path="/property/buildings/:buildingId" element={<BuildingDetail />} />
                {/**
                 * @author 김정호
                 * 작성일: 2026-10-27
                 */}
                <Route path="/property/rooms/:roomId" element={<RoomDetail />} />

                {/**
                 * @author 조휘재
                 * 작성일: 2026-10-27
                 */}
                <Route path="/" element={<HomePage />} />
                {/**
                 * @author 조휘재
                 * 작성일: 2026-11-05
                 */}
                <Route path="/privacy" element={<PrivacyPolicyPage />} />

                {/**
                 * @author 오시문
                 * 작성일: 2026-10-27
                 */}
                <Route
                    path="/community"
                    element={<CommunityGate><CommunityPage title="커뮤니티" /></CommunityGate>}
                />

                {/**
                 * @author 오시문
                 * 작성일: 2026-10-27
                 */}
                <Route
                    path="/community/posts/:postId"
                    element={<CommunityGate><PostEditorView mode="view" title="게시글" /></CommunityGate>}
                />

                {/**
                 * @author 오시문
                 * 작성일: 2026-10-27
                 */}
                <Route
                    path="/community/write"
                    element={<CommunityGate><PostEditorView mode="create" title="새 글 작성" /></CommunityGate>}
                />

                {/**
                 * @author 오시문
                 * 작성일: 2026-10-27
                 */}
                <Route
                    path="/community/posts/:postId/edit"
                    element={<CommunityGate><PostEditorView mode="edit" title="게시글 수정" /></CommunityGate>}
                />

                {/**
                 * @author 오시문
                 * 작성일: 2026-10-27
                 */}
                <Route
                    path="/community/blocks"
                    element={<BlockListPage title="차단 관리" />}
                />

                {/* 공용시설 예약 선택 화면 */}
                {/**
                 * @author 양희재
                 * 작성일: 2026-11-06
                 */}
                <Route
                    path="/facilities"
                    element={<FacilityMenuPage />}
                />

                {/* 기존 공용 자원 목록 */}
                {/**
                 * @author 양희재
                 * 작성일: 2026-11-06
                 */}
                <Route
                    path="/facilities/resources"
                    element={<ResourceListPage />}
                />

                {/* 공용 자원 예약 신청 */}
                {/**
                 * @author 양희재
                 * 작성일: 2026-11-04
                 */}
                <Route
                    path="/facilities/reservation/:resourceId"
                    element={<ResourceReservationPage />}
                />

                {/**
                 * @author 조휘재
                 * 작성일: 2026-11-02
                 */}
                <Route path="/my/profile" element={<ProfilePage />} />
                {/**
                 * @author 조휘재
                 * 작성일: 2026-11-02
                 */}
                <Route path="/profile/:userId" element={<ResidentProfileRoute />} />

                {/* /my/application(나의 신청)은 contractRoutes 안에 있음 */}

                {/* 공용 자원 예약 내역 - 내가 담당하는 기능 */}
            {/**
             * @author 양희재
             * 작성일: 2026-11-05
             */}
            <Route
                    path="/facilities/reservations"
                    element={<ResourceReservationHistoryPage />}
                />

                {/**
                 * @author 조휘재
                 * 작성일: 2026-11-01
                 */}
                <Route path="/my/settings" element={<AccountSettingsPage />} />
                {/**
                 * @author 조휘재
                 * 작성일: 2026-11-01
                 */}
                <Route path="/my/settings/contract" element={<ContractAutocompletePage />} />

                {/**
                 * @author 한성현
                 * 작성일: 2026-11-03
                 */}
                {contractRoutes}

            </Route>
        </Routes>
    )
}

export default App
