// 관리자 알림 — 안 읽은 수·목록·신고와 쪽지 상세 이동
import { useCallback, useEffect, useState } from "react"
import { useNavigate } from "react-router"
import { getNotifications, getUnreadSummary, readAllNotifications, readNotification } from "../api/notificationApi"
import { useCommunityRealtime } from "../realtime/CommunityRealtimeProvider"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import { CommunityQueryState } from "./CommunityFeedback"

type AdminNotification = { notificationId: number; title: string; message: string; targetType: string; targetId: string | number; read: boolean }
export function AdminNotificationMenu() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [actionError, setActionError] = useState<unknown>(null)
  const { status, onNotification } = useCommunityRealtime()
  const connected = status === "connected"
  // loadUnread — GET /notifications/unread-summary
  const loadUnread = useCallback(() => getUnreadSummary(), [])
  const unreadQuery = useCommunityQuery(loadUnread)
  // loadList — GET /notifications
  const loadList = useCallback(() => getNotifications({ page: 0, size: 20 }), [])
  const listQuery = useCommunityQuery(loadList, { enabled: open })
  const refetchUnread = unreadQuery.refetch
  const refetchList = listQuery.refetch
  const setList = listQuery.setData
  // 관리자 알림을 받으면 안 읽은 수와 열린 목록 갱신
  useEffect(() => onNotification((notification) => {
    void refetchUnread()
    if (open) {
      setList((current: { content: AdminNotification[] } | null) => ({ ...current, content: [notification, ...(current?.content ?? []).filter((item) => item.notificationId !== notification.notificationId)].slice(0, 20) }))
      void refetchList()
    }
  }), [onNotification, refetchUnread, refetchList, open, setList])
  // 연결·메뉴 열림이 바뀌면 조회, 미연결 시 30초마다 갱신
  useEffect(() => {
    void refetchUnread(); if (open) void refetchList()
    if (connected) return
    const interval = window.setInterval(() => { void refetchUnread(); if (open) void refetchList() }, 30_000)
    return () => window.clearInterval(interval)
  }, [connected, refetchUnread, refetchList, open])
  // handleOpen — PATCH /notifications/{notificationId}/read
  const handleOpen = async (notification: AdminNotification) => {
    setActionError(null)
    try {
      await readNotification(notification.notificationId)
      await refetchUnread(); setOpen(false)
      if (notification.targetType === "REPORT") navigate(`/operations/reports?reportId=${encodeURIComponent(notification.targetId)}`)
      else if (notification.targetType === "NOTE_THREAD") navigate(`/operations/messages?threadId=${encodeURIComponent(notification.targetId)}`)
    } catch (error) { setActionError(error) }
  }
  // handleReadAll — PATCH /notifications/read-all
  const handleReadAll = async () => {
    setActionError(null)
    try { await readAllNotifications(); await Promise.all([refetchUnread(), refetchList()]) }
    catch (error) { setActionError(error) }
  }
  return <div className="fixed top-3 right-4 z-40">
    {/* 관리자 알림 버튼 — setOpen (화면 상태·상위 콜백 반영) */}
    <button aria-label="관리자 알림" aria-expanded={open} onClick={() => setOpen((value) => !value)} className="rounded-lg border border-main/15 bg-white px-3 py-2 text-xs font-semibold text-main shadow-sm">알림{(unreadQuery.data?.notification ?? 0) > 0 && <span className="ml-2 rounded-full bg-accent px-1.5 text-text">{unreadQuery.data.notification}</span>}</button>
    {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
    {/* 모두 읽음 버튼 — handleReadAll → PATCH /notifications/read-all */}
    {/* 버튼 — handleOpen → PATCH /notifications/{notificationId}/read */}
    {open && <section className="mt-2 w-[min(360px,calc(100vw-2rem))] overflow-hidden rounded-lg border border-main/15 bg-white shadow-xl"><header className="flex justify-between border-b border-main/15 p-3 text-sm text-main"><strong>관리자 알림</strong><button onClick={() => void handleReadAll()}>모두 읽음</button></header><CommunityQueryState loading={listQuery.loading} error={actionError ?? listQuery.error ?? unreadQuery.error} onRetry={refetchList} /><div className="max-h-96 overflow-y-auto">{(listQuery.data?.content ?? []).map((notification: AdminNotification) => <button key={notification.notificationId} onClick={() => void handleOpen(notification)} className={`block w-full border-b border-main/15 p-3 text-left text-sm ${notification.read ? "bg-white" : "bg-main/5"}`}><strong className="text-main">{notification.title}</strong><p className="mt-1 text-xs text-text/70">{notification.message}</p></button>)}{!listQuery.loading && !listQuery.data?.content?.length && <p className="p-5 text-center text-sm text-text/60">알림이 없어요.</p>}</div></section>}
  </div>
}
