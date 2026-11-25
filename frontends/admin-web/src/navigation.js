/**
 * @author 조휘재
 * 작성일: 2026-10-26
 */

import ScreenPage from './pages/ScreenPage'
import CalendarPage from './features/community/pages/CalendarPage'
import FaqBotPage from './features/community/pages/FaqBotPage'
import NotesPage from './features/community/pages/NotesPage'
import ReportsPage from './features/community/pages/ReportsPage'
import ResidentsPage from './features/community/pages/ResidentsPage'
import DashboardPage from './features/dashboard/pages/DashboardPage'
import MonitoringPage from './features/dashboard/pages/MonitoringPage'
import FormsPage from './features/document/pages/FormsPage'
import ContractsPage from './features/document/pages/ContractsPage'

import {
  getAdminNoteBadgeCount,
  getAdminReportBadgeCount,
} from './features/community/api'

import PropertyManage from './pages/property/PropertyManage'
import ResourceManage from './pages/resources/ResourceManage'
import ReservationManage from './pages/resources/ReservationManage'
import OverdueManage from './pages/resources/OverdueManage'
import QrCheckIn from './pages/resources/QrCheckIn'
import InquiryManage from './pages/resources/InquiryManage'
import MembersPage from './pages/MembersPage'

/** 관리자 사이드 메뉴와 그 메뉴가 여는 화면. */
export const navigation = [
  {
    label: '대시보드',
    items: [
      /**
       * @author 문경완
       * 작성일: 2026-10-28
       */
      {
        label: '대시보드',
        to: '/',
        page: DashboardPage,
      },
      /**
       * @author 문경완
       * 작성일: 2026-10-29
       */
      {
        label: '시스템 모니터링',
        to: '/monitoring',
        page: MonitoringPage,
      },
    ],
  },
  {
    label: '문서',
    items: [
      /**
       * @author 문경완
       * 작성일: 2026-11-03
       */
      {
        label: '계약서',
        to: '/documents/contracts',
        page: ContractsPage,
      },
      /**
       * @author 문경완
       * 작성일: 2026-11-02
       */
      {
        label: '문서 양식',
        to: '/documents/forms',
        page: FormsPage,
      },
    ],
  },
  {
    label: '운영',
    items: [
      /**
       * @author 조휘재
       * 작성일: 2026-11-11
       */
      {
        label: '회원 관리',
        to: '/operations/members',
        page: MembersPage,
      },
      /**
       * @author 오시문
       * 작성일: 2026-10-27
       */
      {
        label: '입주자 관리',
        to: '/operations/residents',
        page: ResidentsPage,
      },
      /**
       * @author 오시문
       * 작성일: 2026-10-27
       */
      {
        label: '입주·퇴거 달력',
        to: '/operations/calendar',
        page: CalendarPage,
      },
      /**
       * @author 오시문
       * 작성일: 2026-10-27
       */
      {
        label: '쪽지함',
        to: '/operations/messages',
        page: NotesPage,
        badge: getAdminNoteBadgeCount,
      },
      /**
       * @author 오시문
       * 작성일: 2026-10-27
       */
      {
        label: '신고 관리',
        to: '/operations/reports',
        page: ReportsPage,
        badge: getAdminReportBadgeCount,
      },
      /**
       * @author 오시문
       * 작성일: 2026-10-27
       */
      {
        label: 'FAQ봇',
        to: '/operations/faq',
        page: FaqBotPage,
      },
    ],
  },
  {
    label: '건물 관리',
    items: [
      /**
       * @author 김정호
       * 작성일: 2026-10-30
       */
      {
        label: '매물 관리',
        to: '/property/manage',
        page: PropertyManage,
      },
    ],
  },
  {
    label: '공용 자원',
    items: [
      /**
       * @author 양희재
       * 작성일: 2026-11-06
       */
      {
        label: '공용 자원 관리',
        to: '/resources',
        page: ResourceManage,
      },
      /**
       * @author 양희재
       * 작성일: 2026-11-07
       */
      {
        label: '예약 관리',
        to: '/resources/reservations',
        page: ReservationManage,
      },
      /**
       * @author 양희재
       * 작성일: 2026-11-07
       */
      {
        label: '연체 관리',
        to: '/resources/overdue',
        page: OverdueManage,
      },
      /**
       * @author 양희재
       * 작성일: 2026-11-07
       */
      {
        label: 'QR 체크인',
        to: '/resources/check-in',
        page: QrCheckIn,
      },
      /**
       * @author 양희재
       * 작성일: 2026-11-09
       */
      {
        label: '문의 관리',
        to: '/resources/inquiries',
        page: InquiryManage,
      },
    ],
  },
]

/** 메뉴 항목만 모아 라우트에 넘긴다. */
export const screens = navigation.flatMap(
    (section) => section.items
)

/** 없는 주소에 쓰는 화면. */
export const notFoundScreen = {
  label: '페이지를 찾을 수 없습니다',
  to: '*',
  page: ScreenPage,
}
