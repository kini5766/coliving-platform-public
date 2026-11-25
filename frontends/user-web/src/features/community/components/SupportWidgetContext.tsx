// 지원 위젯 상태 — 탭·채팅방 선택·읽음 처리·안 읽은 수 공유
import { createContext, type ReactNode, useCallback, useContext, useEffect, useRef, useState } from "react"
import { useSession } from "../../../session"
import { readChatRoom } from "../api/chatApi"
import { useCommunityRealtime } from "../realtime/CommunityRealtimeProvider"
import { getUnreadSummary, readAllNotifications, readNotification } from "../api/notificationApi"
import { readNotes } from "../api/noteApi"
import type { UnreadNotificationSummary } from "../types"

export type SupportWidgetTab = "faq" | "inquiry" | "notes" | "chat"
type SupportWidgetContextValue = {
  isOpen: boolean;
  activeTab: SupportWidgetTab;
  selectedChatRoomId: string | null;
  unreadSummary: UnreadNotificationSummary;
  notificationUnreadCount: number;
  openWidget: (tab?: SupportWidgetTab, roomId?: string | null) => void;
  closeWidget: () => void;
  setActiveTab: (tab: SupportWidgetTab) => void;
  selectChatRoom: (roomId: string | null) => void;
  markNotesRead: () => Promise<void>;
  markChatRead: (roomId: string, lastReadMessageId: string) => Promise<void>;
  markNotificationRead: (notificationId: number) => Promise<void>;
  markAllRead: () => Promise<void>;
  refreshUnreadSummary: () => Promise<void>
}
const EMPTY_NOTIFICATION_SUMMARY: UnreadNotificationSummary = {
  note: 0,
  chat: 0,
  notification: 0,
  total: 0
}
const SUPPORT_WIDGET_CONTEXT = createContext<SupportWidgetContextValue | null>(null)

export function SupportWidgetProvider({ children }: { children: ReactNode }) {
  const session = useSession()
  const { status, onNotification } = useCommunityRealtime()
  const connected = status === "connected"
  const seenNotificationIds = useRef(new Set<number>())
  const unreadChatNotifications = useRef(new Set<string>())
  const summaryRequest = useRef(0)
  const [isOpen, setIsOpen] = useState(false)
  const [activeTab, setActiveTabState] = useState<SupportWidgetTab>("faq")
  const [selectedChatRoomId, setSelectedChatRoomId] = useState<string | null>(null)
  const [unreadSummary, setUnreadSummary] = useState(EMPTY_NOTIFICATION_SUMMARY)
  const authenticated = session.status === "ready" && session.authenticated

  // refreshUnreadSummary — GET /notifications/unread-summary
  const refreshUnreadSummary = useCallback(async () => {
    const requestId = ++summaryRequest.current
    if (!authenticated) {
      setUnreadSummary(EMPTY_NOTIFICATION_SUMMARY)
      return
    }
    try {
      const summary = await getUnreadSummary()
      if (requestId === summaryRequest.current) setUnreadSummary(summary)
    } catch {
      // 배지 갱신 실패는 현재 화면 사용을 막지 않는다.
    }
  }, [authenticated])

  // 로그인·연결이 바뀌면 안 읽은 수 조회, 화면 복귀·미연결 주기 갱신
  useEffect(() => {
    if (!authenticated) return undefined
    void refreshUnreadSummary()
    const interval = connected ? null : window.setInterval(refreshUnreadSummary, 30_000)
    // handleVisibilityChange — GET /notifications/unread-summary
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") void refreshUnreadSummary()
    }
    document.addEventListener("visibilitychange", handleVisibilityChange)
    return () => {
      if (interval !== null) window.clearInterval(interval)
      document.removeEventListener("visibilitychange", handleVisibilityChange)
    }
  }, [authenticated, connected, refreshUnreadSummary])

  // 로그아웃 시 배지를 비우고 알림 구독 중복 제거·제재 갱신 이벤트 반영
  useEffect(() => {
    if (!authenticated) {
      summaryRequest.current += 1
      seenNotificationIds.current.clear()
      unreadChatNotifications.current.clear()
      setUnreadSummary(EMPTY_NOTIFICATION_SUMMARY)
      return
    }
    return onNotification((notification) => {
      const id = Number(notification.notificationId)
      if (!Number.isFinite(id) || seenNotificationIds.current.has(id)) return
      seenNotificationIds.current.add(id)
      const roomKey = String(notification.targetId)
      const replacesChat = notification.type === "CHAT" && unreadChatNotifications.current.has(roomKey)
      if (notification.type === "CHAT") unreadChatNotifications.current.add(roomKey)
      setUnreadSummary((current) => ({
        ...current,
        notification: current.notification + (notification.read || replacesChat ? 0 : 1),
        note: current.note + (notification.type === "NOTE" ? 1 : 0),
        chat: current.chat + (notification.type === "CHAT" ? 1 : 0),
        total: current.total + (notification.type === "NOTE" || notification.type === "CHAT" ? 1 : 0)
      }))
      void refreshUnreadSummary()
      if (notification.type === "SANCTION") window.dispatchEvent(new Event("community:sanction-refresh"))
    })
  }, [authenticated, onNotification, refreshUnreadSummary])

  const setActiveTab = useCallback((tab: SupportWidgetTab) => {
    setActiveTabState(tab)
    if (tab !== "chat") setSelectedChatRoomId(null)
  }, [])
  const openWidget = useCallback((tab: SupportWidgetTab = "faq", roomId: string | null = null) => {
    setIsOpen(true)
    setActiveTabState(tab)
    setSelectedChatRoomId(tab === "chat" && roomId != null ? String(roomId) : null)
  }, [])

  // roomlink:open-chat 이벤트를 받으면 지정한 탭·대화방 열기
  useEffect(() => {
    const handleOpenFromEvent = (event: Event) => {
      const detail = (event as CustomEvent<{ tab?: SupportWidgetTab; roomId?: string }>).detail
      openWidget(detail?.tab ?? "faq", detail?.roomId ?? null)
    }
    window.addEventListener("roomlink:open-chat", handleOpenFromEvent)
    return () => window.removeEventListener("roomlink:open-chat", handleOpenFromEvent)
  }, [openWidget])

  // markNotesRead — PATCH /notes/read · GET /notifications/unread-summary
  const markNotesRead = useCallback(async () => {
    if (!authenticated) return
    await readNotes()
    await refreshUnreadSummary()
  }, [authenticated, refreshUnreadSummary])
  // markChatRead — PATCH /chat/rooms/{roomId}/read · GET /notifications/unread-summary
  const markChatRead = useCallback(async (roomId: string, lastReadMessageId: string) => {
    await readChatRoom(roomId, { lastReadMessageId })
    unreadChatNotifications.current.delete(String(roomId))
    window.dispatchEvent(new CustomEvent("community:chat-read", { detail: { roomId, lastReadMessageId } }))
    await refreshUnreadSummary()
  }, [refreshUnreadSummary])
  // markNotificationRead — PATCH /notifications/{notificationId}/read · GET /notifications/unread-summary
  const markNotificationRead = useCallback(async (notificationId: number) => {
    await readNotification(notificationId)
    await refreshUnreadSummary()
  }, [refreshUnreadSummary])
  // markAllRead — PATCH /notifications/read-all · GET /notifications/unread-summary
  const markAllRead = useCallback(async () => {
    await readAllNotifications()
    unreadChatNotifications.current.clear()
    await refreshUnreadSummary()
  }, [refreshUnreadSummary])

  return <SUPPORT_WIDGET_CONTEXT.Provider value={{
    isOpen,
    activeTab,
    selectedChatRoomId,
    unreadSummary,
    notificationUnreadCount: unreadSummary.notification,
    openWidget,
    closeWidget: () => setIsOpen(false),
    setActiveTab,
    selectChatRoom: setSelectedChatRoomId,
    markNotesRead,
    markChatRead,
    markNotificationRead,
    markAllRead,
    refreshUnreadSummary
  }}>
    {children}
  </SUPPORT_WIDGET_CONTEXT.Provider>
}

export function useSupportWidget() {
  const value = useContext(SUPPORT_WIDGET_CONTEXT)
  if (!value) throw new Error("useSupportWidget은 SupportWidgetProvider 안에서 사용해야 합니다.")
  return value
}
