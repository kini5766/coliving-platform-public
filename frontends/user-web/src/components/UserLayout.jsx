/**
 * @author 조휘재
 * 작성일: 2026-10-27
 */

import { Outlet } from 'react-router'
import ChatButton from './ChatButton'
import Footer from './Footer'
import Header from './Header'
import { SupportWidgetProvider } from '../features/community/components/SupportWidgetContext'

/** 헤더, 본문, 푸터, 채팅 버튼을 한 화면에 두고 고객 문의 위젯 상태를 공유한다. */
function UserLayout() {
  return (
    <SupportWidgetProvider><div className="flex min-h-dvh flex-col bg-background text-text">
      <Header />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
      <ChatButton />
    </div></SupportWidgetProvider>
  )
}

export default UserLayout
