// 사용자 알림 — 실시간 목록·읽음 처리·관련 화면 이동
import { useCallback, useEffect, useRef, useState } from "react"
import { useNavigate } from "react-router"
import { useTranslation } from "react-i18next"
import { getNotifications } from "../api/notificationApi"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import type { NotificationType, UserNotification } from "../types"
import { Icon, type IconName } from "."
import { useSupportWidget } from "./SupportWidgetContext"
import { useCommunityRealtime } from "../realtime/CommunityRealtimeProvider"
import { mergeNotification } from "../realtime/notificationEvents"

const ICONS: Record<NotificationType, IconName> = {
  NOTE: "mail",
  CHAT: "message",
  COMMENT: "users",
  REPLY: "users",
  SANCTION: "lock",
  REPORT: "flag"
}
const displayTime = (value: string, locale: string) => new Date(value).toLocaleString(locale, {
  month: "numeric",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit"
})

export function NotificationPanel({ onClose }: { onClose: () => void }) {
  const { t, i18n } = useTranslation("community");
  const navigate = useNavigate();
  const { openWidget, markNotificationRead, markAllRead } = useSupportWidget();
  const [actionError, setActionError] = useState("")
  const eventVersion = useRef(0)
  const pendingEvents = useRef<{ version: number; notification: UserNotification }[]>([])
  // loadNotifications — GET /notifications
  const loadNotifications = useCallback(async () => {
    const version = eventVersion.current
    const response = await getNotifications({ page: 0, size: 20 })
    const content = pendingEvents.current.filter((event) => event.version > version).reduce((items, event) => mergeNotification(items, event.notification), response.content)
    return { ...response, content }
  }, [])
  const { data, loading, error, refetch, setData } = useCommunityQuery(loadNotifications, { initialData: { content: [] } })
  const notifications = (data?.content ?? []) as UserNotification[]
  const unread = notifications.some((item) => !item.read)
  const { onNotification, status } = useCommunityRealtime()
  const connected = status === "connected"
  // 실시간 알림을 받으면 조회 중 이벤트도 보관해 목록에 병합
  useEffect(() => onNotification((event) => {
    pendingEvents.current = [...pendingEvents.current.slice(-199), { version: ++eventVersion.current, notification: event as UserNotification }]
    setData((current: typeof data) => ({ ...current, content: mergeNotification(current?.content ?? [], event as UserNotification) }))
  }), [onNotification, setData])
  // 연결이 바뀌면 목록 재조회, 미연결 시 30초마다 갱신
  useEffect(() => {
    void refetch()
    if (connected) return
    const interval = window.setInterval(() => void refetch(), 30_000)
    return () => window.clearInterval(interval)
  }, [connected, refetch])
  // 채팅 읽음 이벤트를 받으면 알림 목록 재조회
  useEffect(() => {
    // handleChatRead — GET /notifications
    const handleChatRead = () => { void refetch() }
    window.addEventListener("community:chat-read", handleChatRead)
    return () => window.removeEventListener("community:chat-read", handleChatRead)
  }, [refetch])
  // handleOpen — PATCH /notifications/{notificationId}/read
  const handleOpen = async (notification: UserNotification) => {
    setActionError("")
    try {
      await markNotificationRead(notification.notificationId)
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : "알림을 처리하지 못했어요.")
      return
    }
    setData((current: typeof data) => current ? {
      ...current,
      content: current.content.map((item: UserNotification) => item.notificationId === notification.notificationId ? { ...item, read: true } : item)
    } : current)
    onClose();
    if (notification.targetType === "NOTE_THREAD") openWidget("notes")
    else if (notification.targetType === "CHAT_ROOM") openWidget("chat", notification.targetId)
    else if (notification.targetType === "POST") navigate(`/community/posts/${notification.targetId}`)
    else if (notification.targetType === "SANCTION") {
      window.dispatchEvent(new Event("community:sanction-refresh"))
      navigate("/community?sanction=1#community-sanction")
    }
    // REPORT 알림의 관리자 화면 이동은 admin-web의 AdminNotificationMenu에서 처리한다.
  }
  // handleReadAll — PATCH /notifications/read-all · GET /notifications
  const handleReadAll = async () => {
    setActionError("")
    try {
      await markAllRead()
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : "알림을 처리하지 못했어요.")
      return
    }
    setData((current: typeof data) => current ? {
      ...current,
      content: current.content.map((item: UserNotification) => ({ ...item, read: true }))
    } : current)
    void refetch()
  }
  return <>
    <header className="flex items-center justify-between border-b border-main/15 px-3 pb-3">
      <strong className="text-sm text-main">
        {t("notifications.title")}
      </strong>
      {/* notifications.readAll 버튼 — handleReadAll → PATCH /notifications/read-all · GET /notifications */}
      <button
        disabled={!unread}
        onClick={handleReadAll}
        className="text-xs font-semibold text-main disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-accent"
      >
        {t("notifications.readAll")}
      </button>
    </header>
    {actionError && <p className="px-3 py-2 text-xs text-accent">{actionError}</p>}
    <div className="max-h-80 overflow-y-auto">
      {/* 버튼 — handleOpen → PATCH /notifications/{notificationId}/read */}
      {loading && notifications.length === 0 ? <p className="p-5 text-center text-sm text-text/70">불러오는 중...</p> : error ? <div className="p-5 text-center text-sm text-text/70">
        <p>{error instanceof Error ? error.message : "알림을 불러오지 못했어요."}</p>
        {/* 다시 불러오기 버튼 — refetch → GET /notifications */}
        <button className="mt-2 font-semibold text-main" onClick={() => void refetch()}>다시 불러오기</button>
      </div> : notifications.length === 0 ? <p className="p-5 text-center text-sm text-text/70">
        {t("notifications.empty")}
      </p> : notifications.map((notification) => <button
        key={notification.notificationId}
        onClick={() => handleOpen(notification)}
        className={`relative flex w-full gap-3 border-b border-main/15 p-3 text-left focus-visible:outline-2 focus-visible:outline-accent ${notification.read ? "bg-white" : "bg-background"}`}
      >
        <span className="grid h-9 w-9 shrink-0 self-start place-items-center rounded-full bg-main/10 text-main">
          <Icon
            name={ICONS[notification.type]}
            size={17}
          />
        </span>
        <span className="min-w-0 flex-1">
          <strong className="block text-xs text-text">
            {notification.title}
          </strong>
          <span className="mt-1 block text-xs leading-5 text-text/70">
            {notification.message}
          </span>
          <time className="mt-1 block text-[10px] text-text/70">
            {displayTime(notification.createdAt, i18n.resolvedLanguage ?? i18n.language)}
          </time>
        </span>
        {!notification.read && <i className="mt-2 h-2 w-2 shrink-0 rounded-full bg-accent" />}
      </button>)}
    </div>
  </>
}
