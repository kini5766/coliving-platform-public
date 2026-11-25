import { Route, Routes } from 'react-router'

import BuildingForm from './pages/property/BuildingForm'
import RoomForm from './pages/property/RoomForm'

import AdminLayout from './components/AdminLayout'
import RequireAuth from './components/RequireAuth'

import LoginPage from './pages/LoginPage'
import ScreenPage from './pages/ScreenPage'

import { notFoundScreen, screens } from './navigation'

import { CommunityRealtimeProvider } from './features/community/realtime/CommunityRealtimeProvider'

/** 관리자 웹 라우트. 로그인은 사이드바 밖에 두고, 나머지는 관리자 세션이 있을 때만 연다. */
function App() {
  return (
    // BrowserRouter는 main.jsx에 존재합니다.
    // 로그인(/login)은 레이아웃 밖에 두어 사이드바를 노출하지 않습니다.
    // 그 밖의 주소는 로그인했고 ADMIN 권한이 있을 때만 엽니다.
    <Routes>

      {/* ==========================================
          1. 로그인 페이지
      ========================================== */}
      {/**
       * @author 조휘재
       * 작성일: 2026-10-26
       */}
      <Route
        path="/login"
        element={<LoginPage />}
      />

      {/* ==========================================
          2. 관리자 인증
      ========================================== */}
      {/**
       * @author 조휘재
       * 작성일: 2026-10-30
       */}
      <Route element={<RequireAuth />}>

        {/* 관리자 공통 레이아웃 */}
        {/**
         * @author 조휘재
         * 작성일: 2026-10-26
         */}
        <Route
          element={
            <CommunityRealtimeProvider>
              <AdminLayout />
            </CommunityRealtimeProvider>
          }
        >

          {/* ==========================================
              3. 관리자 메뉴 화면 자동 연결

              navigation.js에 등록된 화면을
              자동으로 라우팅합니다.

              공용 자원 관리: /resources
              예약 관리: /resources/reservations
              연체 관리: /resources/overdue
          ========================================== */}
          {screens.map((screen) => {
            const Page = screen.page

            /**
             * @author 조휘재
             * 작성일: 2026-10-26
             */
            return (
              <Route
                key={screen.to}
                path={screen.to}
                element={
                  <Page title={screen.label} />
                }
              />
            )
          })}

          {/* ==========================================
              4. 기존 건물 관리 기능
          ========================================== */}

          {/* 건물 신규 등록 */}
          {/**
           * @author 김정호
           * 작성일: 2026-10-30
           */}
          <Route
            path="/property/buildings/new"
            element={<BuildingForm />}
          />

          {/* 건물 정보 수정 */}
          {/**
           * @author 김정호
           * 작성일: 2026-10-30
           */}
          <Route
            path="/property/buildings/:buildingId/edit"
            element={<BuildingForm />}
          />

          {/* ==========================================
              5. 기존 호실 관리 기능
          ========================================== */}

          {/* 호실 신규 등록 */}
          {/**
           * @author 김정호
           * 작성일: 2026-10-30
           */}
          <Route
            path="/property/buildings/:buildingId/rooms/new"
            element={<RoomForm />}
          />

          {/* 호실 정보 수정 */}
          {/**
           * @author 김정호
           * 작성일: 2026-10-30
           */}
          <Route
            path="/property/rooms/:roomId/edit"
            element={<RoomForm />}
          />

          {/* ==========================================
              6. 존재하지 않는 페이지
          ========================================== */}
          {/**
           * @author 조휘재
           * 작성일: 2026-10-26
           */}
          <Route
            path={notFoundScreen.to}
            element={
              <ScreenPage title={notFoundScreen.label} />
            }
          />

        </Route>
      </Route>
    </Routes>
  )
}

export default App
