// 1:1 채팅 — 입주자 전용 채팅방 목록과 대화창(고객 지원 위젯)
import { type FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react"
import { getChatRooms, getChatRoomMessages, leaveChatRoom, sendChatMessage } from "../api/chatApi"
import { CommunityApiError } from "../api/communityClient"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import { useCommunityRealtime } from "../realtime/CommunityRealtimeProvider"
import { mergeChatMessages } from "../realtime/chatEvents"
import { useCommunityViewer } from "../useCommunityViewer"
import type { ChatMessage, ChatRoom } from "../types"
import { Avatar, Button, Icon } from "."
import { CommunityGate } from "./CommunityGate"
import { CommunityQueryState } from "./CommunityQueryState"
import { NoteConversation, type ConversationNote } from "./NoteConversation"
import { ReportModal } from "./ReportModal"
import { BlockConfirmModal } from "./BlockConfirmModal"
import { SanctionBanner } from "./SanctionBanner"
import { useMySanction } from "./useMySanction"
import { useSupportWidget } from "./SupportWidgetContext"

const sortRooms = (rooms: ChatRoom[]) => [...rooms].sort((a, b) => (b.lastMessageAt ?? "").localeCompare(a.lastMessageAt ?? ""))
const time = (value: string | null) => value ? new Date(value).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit", hour12: false }) : ""

export function ChatPanel() {
  return <CommunityGate><ResidentChatPanel /></CommunityGate>
}

function ResidentChatPanel() {
  const viewer = useCommunityViewer()
  const { selectedChatRoomId, selectChatRoom, refreshUnreadSummary } = useSupportWidget()
  const { status, onChatMessage, onChatEvent } = useCommunityRealtime()
  const connected = status === "connected"
  // loadRooms — GET /chat/rooms
  const loadRooms = useCallback(() => getChatRooms(), [])
  const { data, loading, error, refetch, setData } = useCommunityQuery(loadRooms, { enabled: viewer.isResident && !viewer.isManager })
  const rooms = useMemo(() => sortRooms((data ?? []) as ChatRoom[]), [data])
  const room = rooms.find((item) => item.roomId === selectedChatRoomId)
  const seenMessages = useRef(new Set<string>())
  // 입주자 권한·연결이 바뀌면 방 목록 재조회, 미연결 시 30초마다 갱신
  useEffect(() => {
    if (!viewer.isResident || viewer.isManager) return
    void refetch()
    if (connected) return
    const interval = window.setInterval(() => void refetch(), 30_000)
    return () => window.clearInterval(interval)
  }, [connected, refetch, viewer.isResident, viewer.isManager])
  // 새 메시지를 받으면 미리보기·안 읽은 수를 반영하고 방 목록 보완
  useEffect(() => onChatMessage((event) => {
    if (!event.messageId || seenMessages.current.has(event.messageId)) return
    seenMessages.current.add(event.messageId)
    const roomId = String(event.roomId)
    setData((items: ChatRoom[] | null) => items?.map((item) => item.roomId === roomId ? {
      ...item, lastMessage: event.status === "HIDDEN" || event.status === "DELETED" ? null : event.content,
      lastMessageAt: event.sentAt, unreadCount: item.unreadCount + (event.senderId === viewer.userId ? 0 : 1)
    } : item) ?? items)
    // 즉시 미리보기를 갱신한 뒤 프로필·안 읽은 수를 서버 값으로 보완한다.
    // 진행 중이던 이전 목록 요청도 무효화해 새 이벤트를 덮어쓰지 않게 한다.
    void refetch()
  }), [onChatMessage, setData, viewer.userId, refetch])
  // 상대방 나가기 이벤트를 받으면 해당 방을 읽기 전용으로 표시
  useEffect(() => onChatEvent((event) => {
    if (event.type !== "PARTNER_LEFT") return
    setData((items: ChatRoom[] | null) => items?.map((item) => item.roomId === String(event.roomId) ? { ...item, partnerLeft: true, readOnly: true } : item) ?? items)
  }), [onChatEvent, setData])
  // 차단 목록이 바뀌면 열린 방을 닫고 채팅방 목록 재조회
  useEffect(() => {
    // handleBlocksChanged — GET /chat/rooms/{roomId}/messages
    const handleBlocksChanged = () => { selectChatRoom(null); void refetch() }
    window.addEventListener("community:blocks-changed", handleBlocksChanged)
    return () => window.removeEventListener("community:blocks-changed", handleBlocksChanged)
  }, [refetch, selectChatRoom])
  // handleRead — GET /chat/rooms/{roomId}/messages
  const handleRead = useCallback(() => { void refetch() }, [refetch])
  // handleLeave — GET /chat/rooms/{roomId}/messages · GET /notifications/unread-summary
  const handleLeave = useCallback(async () => { selectChatRoom(null); await refetch(); await refreshUnreadSummary() }, [selectChatRoom, refetch, refreshUnreadSummary])

  if (viewer.isManager) return <p className="p-4 text-sm text-text/70">입주자만 채팅을 이용할 수 있어요.</p>
  if (selectedChatRoomId && room) return <ChatRoomView key={room.roomId} room={room} userId={viewer.userId} onRead={handleRead} onLeave={handleLeave} />
  return <div className="min-h-0 flex-1 overflow-y-auto">
    <CommunityQueryState loading={loading && !data} error={error} onRetry={refetch} />
    {/* 목록으로 버튼 — selectChatRoom (화면 상태·상위 콜백 반영) */}
    {selectedChatRoomId && !loading && !room && <div className="p-4 text-sm text-text/70"><p>대화방을 찾을 수 없어요.</p><Button onClick={() => selectChatRoom(null)}>목록으로</Button></div>}
    {!loading && !error && rooms.length === 0 && <p className="p-8 text-center text-sm text-text/70">게시글 작성자 메뉴에서 1:1 채팅을 시작해 보세요.</p>}
    {/* 버튼 — selectChatRoom (화면 상태·상위 콜백 반영) */}
    {rooms.map((item) => <button key={item.roomId} onClick={() => selectChatRoom(item.roomId)} className="flex w-full items-center gap-3 border-b border-main/15 p-4 text-left hover:bg-background">
      <Avatar name={item.partner.name} />
      <span className="min-w-0 flex-1"><strong className="text-sm text-text">{item.partner.name} <small className="font-normal text-text/60">{item.partner.roomNumber}</small>{item.readOnly && <small className="ml-1 rounded bg-main/10 px-1 text-[10px] font-normal text-main">읽기 전용</small>}</strong>
        <span className="mt-1 block truncate text-xs text-text/70">{item.partnerLeft ? "상대방이 대화방을 나갔어요" : item.lastMessage ?? (item.readOnly ? "읽기 전용 대화방" : "대화를 시작해 보세요")}</span></span>
      <span className="shrink-0 text-right text-[10px] text-text/60"><time>{time(item.lastMessageAt)}</time>{item.unreadCount > 0 && <b className="ml-auto mt-1 block w-fit rounded-full bg-accent px-1.5 text-text">{item.unreadCount}</b>}</span>
    </button>)}
  </div>
}

function ChatRoomView({ room, userId, onRead, onLeave }: { room: ChatRoom; userId: number; onRead: () => void; onLeave: () => Promise<void> }) {
  const { selectChatRoom, markChatRead } = useSupportWidget()
  const { status, onChatMessage, onChatEvent, onError, sendChatMessage: sendRealtime } = useCommunityRealtime()
  const connected = status === "connected"
  const sanction = useMySanction()
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [hasMore, setHasMore] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [content, setContent] = useState("")
  const [actionError, setActionError] = useState<unknown>(null)
  const [readOnly, setReadOnly] = useState(room.readOnly || room.partnerLeft)
  const [partnerLeft, setPartnerLeft] = useState(room.partnerLeft)
  const [menuOpen, setMenuOpen] = useState(false)
  const [report, setReport] = useState<{ type: "USER" | "CHAT_MESSAGE"; id: string } | null>(null)
  const [blocking, setBlocking] = useState(false)
  const [leaving, setLeaving] = useState(false)
  const [leaveSubmitting, setLeaveSubmitting] = useState(false)
  const [sending, setSending] = useState(false)
  const [scrollKey, setScrollKey] = useState(0)
  const initialHistoryLoaded = useRef(false)
  const messagesRef = useRef<ChatMessage[]>([])
  // 메시지가 바뀌면 비동기 조회용 참조에 최신 내역 보관
  useEffect(() => { messagesRef.current = messages }, [messages])
  const mounted = useRef(true)
  const olderPending = useRef(false)
  const readTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const sendTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const pendingContent = useRef("")
  const lastReadId = useRef("")
  const scheduledReadId = useRef("")
  // loadMessages — GET /chat/rooms/{roomId}/messages
  const loadMessages = useCallback(async () => {
    const knownIds = new Set(messagesRef.current.map((message) => message.messageId))
    let result = await getChatRoomMessages(room.roomId, { size: 30 })
    let page = result
    // 재연결 사이에 30개 넘게 쌓여도 기존 대화와 만날 때까지 빠진 구간을 복구한다.
    while (mounted.current && knownIds.size && page.hasMore && page.messages.length && !page.messages.some((message) => knownIds.has(message.messageId))) {
      const before = page.messages[0].messageId
      page = await getChatRoomMessages(room.roomId, { before, size: 30 })
      result = { messages: mergeChatMessages(page.messages, result.messages), hasMore: page.hasMore }
      if (page.messages[0]?.messageId === before) break
    }
    return result
  }, [room.roomId])
  const { data, loading, error, refetch } = useCommunityQuery(loadMessages)
  // 조회한 이력을 기존 메시지와 병합하고 첫 조회의 더 보기 상태 반영
  useEffect(() => {
    if (!data) return
    setMessages((current) => mergeChatMessages(current, data.messages))
    if (!initialHistoryLoaded.current) { setHasMore(data.hasMore); initialHistoryLoaded.current = true }
  }, [data])
  // 방이 읽기 전용이거나 상대가 나가면 대화 입력 제한 반영
  useEffect(() => { if (room.readOnly || room.partnerLeft) setReadOnly(true); if (room.partnerLeft) setPartnerLeft(true) }, [room.readOnly, room.partnerLeft])
  // 실시간 재연결 시 이력을 복구하고 미연결 시 30초마다 조회
  useEffect(() => {
    if (connected) { void refetch(); return }
    const interval = window.setInterval(() => void refetch(), 30_000)
    return () => window.clearInterval(interval)
  }, [connected, refetch])
  // 현재 방 메시지를 받으면 병합하고 자신의 전송 대기 상태 해제
  useEffect(() => onChatMessage((event) => {
    if (String(event.roomId) !== room.roomId) return
    setMessages((current) => mergeChatMessages(current, [event as ChatMessage]))
    if (event.senderId === userId && event.content === pendingContent.current) {
      pendingContent.current = ""
      setSending(false)
      setScrollKey((key) => key + 1)
      if (sendTimer.current) clearTimeout(sendTimer.current)
    }
  }), [onChatMessage, room.roomId, userId])
  // 현재 방에서 상대가 나갔다는 이벤트를 받으면 입력 제한
  useEffect(() => onChatEvent((event) => {
    if (String(event.roomId) === room.roomId && event.type === "PARTNER_LEFT") { setPartnerLeft(true); setReadOnly(true) }
  }), [onChatEvent, room.roomId])
  const handleSendError = useCallback((event: Record<string, any>) => {
    if (event.roomId != null && String(event.roomId) !== room.roomId) return
    if (event.roomId == null && event.code === "STOMP_ERROR") return
    const failure = new CommunityApiError(403, event.code, event.detail ?? event.message ?? "메시지를 보내지 못했어요.", event)
    setActionError(failure)
    if (event.code === "CHAT_READ_ONLY" || event.code === "ROOM_NOT_FOUND") setReadOnly(true)
    if (event.code === "SANCTIONED") window.dispatchEvent(new CustomEvent("community:sanctioned", { detail: failure }))
    const failedContent = pendingContent.current
    if (failedContent) setContent((current) => current || failedContent)
    pendingContent.current = ""
    setSending(false)
    if (sendTimer.current) clearTimeout(sendTimer.current)
  }, [room.roomId])
  // 전송 오류 이벤트를 구독해 오류 안내·입력 복구 처리
  useEffect(() => onError(handleSendError), [onError, handleSendError])
  // 대화창을 닫을 때 읽음·전송 대기 타이머 정리
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; if (readTimer.current) clearTimeout(readTimer.current); if (sendTimer.current) clearTimeout(sendTimer.current) }
  }, [])
  // handleVisibleMessage — PATCH /chat/rooms/{roomId}/read
  const handleVisibleMessage = useCallback((id: string) => {
    if (id <= lastReadId.current || document.visibilityState !== "visible") return
    scheduledReadId.current = id > scheduledReadId.current ? id : scheduledReadId.current
    if (readTimer.current) return
    readTimer.current = setTimeout(async () => {
      readTimer.current = null
      if (!mounted.current || document.visibilityState !== "visible") return
      const visibleId = scheduledReadId.current
      try { await markChatRead(room.roomId, visibleId); lastReadId.current = visibleId; onRead() }
      catch (error) { if (mounted.current) setActionError(error) }
    }, 1000)
  }, [room.roomId, markChatRead, onRead])
  // handleLoadOlder — GET /chat/rooms/{roomId}/messages
  const handleLoadOlder = useCallback(async () => {
    if (olderPending.current || !hasMore || !messages[0]) return
    olderPending.current = true; setLoadingOlder(true)
    try {
      const older = await getChatRoomMessages(room.roomId, { before: messages[0].messageId, size: 30 })
      if (mounted.current) { setMessages((current) => mergeChatMessages(current, older.messages)); setHasMore(older.hasMore) }
    } catch (error) { if (mounted.current) setActionError(error) }
    finally { olderPending.current = false; if (mounted.current) setLoadingOlder(false) }
  }, [hasMore, messages, room.roomId])
  // handleSend — STOMP /app/chat/{roomId}（미연결 시 REST 전송）
  const handleSend = async (event: FormEvent) => {
    event.preventDefault()
    const text = content.trim()
    if (!text || text.length > 1000 || sending || readOnly || sanction.active) return
    setActionError(null); setSending(true); pendingContent.current = text
    setContent(""); setScrollKey((key) => key + 1)
    if (sendRealtime(room.roomId, text)) {
      sendTimer.current = setTimeout(() => { if (mounted.current) { setSending(false); setActionError(new Error("전송 결과를 확인하지 못했어요. 다시 불러오기로 대화를 확인해 주세요.")); void refetch() } }, 10000)
      return
    }
    try {
      const sent = await sendChatMessage(room.roomId, { content: text }, userId)
      if (mounted.current) { setMessages((current) => mergeChatMessages(current, [sent])); pendingContent.current = ""; setSending(false); setScrollKey((key) => key + 1); onRead() }
    } catch (error) {
      if (!mounted.current) return
      if (error instanceof CommunityApiError) handleSendError({ ...error.body, code: error.code, detail: error.detail, roomId: room.roomId })
      else { setActionError(error); setContent(text); setSending(false) }
    }
  }
  // handleLeave — POST /chat/rooms/{roomId}/leave
  const handleLeave = async () => {
    if (leaveSubmitting) return
    setLeaveSubmitting(true)
    try { await leaveChatRoom(room.roomId); await onLeave() }
    catch (error) { setActionError(error); setLeaving(false) }
    finally { if (mounted.current) setLeaveSubmitting(false) }
  }
  const notes = useMemo<ConversationNote[]>(() => messages.map((message) => ({
    noteId: message.messageId, senderType: message.senderId === userId ? "USER" : "ADMIN",
    content: message.status === "HIDDEN" ? "관리자가 숨긴 메시지입니다" : message.status === "DELETED" ? "삭제된 메시지입니다" : message.content ?? "",
    createdAt: message.sentAt, readAt: null
  })), [messages, userId])
  return <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
    <header className="relative flex shrink-0 items-center gap-2 border-b border-main/15 px-3 py-2">
      {/* 채팅 목록으로 버튼 — selectChatRoom (화면 상태·상위 콜백 반영) */}
      <button aria-label="채팅 목록으로" onClick={() => selectChatRoom(null)}><Icon name="chevronLeft" /></button><Avatar name={room.partner.name} size="sm" />
      <div className="min-w-0 flex-1"><strong className="text-sm text-text">{room.partner.name}</strong><small className="ml-2 text-text/60">{room.partner.roomNumber}</small>{status !== "connected" && <p className="text-[10px] text-text/50">연결 중…</p>}</div>
      {/* 채팅방 메뉴 버튼 — setMenuOpen (화면 상태·상위 콜백 반영) */}
      <button aria-label="채팅방 메뉴" onClick={() => setMenuOpen((value) => !value)}><Icon name="more" /></button>
      {menuOpen && <div className="absolute top-full right-2 z-20 w-40 rounded-lg border border-main/15 bg-white p-1 shadow-lg">
        {/* 이 사용자 신고 버튼 — setReport (화면 상태·상위 콜백 반영) */}
        <button className="block w-full p-2 text-left text-xs" onClick={() => { setReport({ type: "USER", id: String(room.partner.userId) }); setMenuOpen(false) }}>이 사용자 신고</button>
        {/* 차단하기 버튼 — setBlocking (화면 상태·상위 콜백 반영) */}
        <button className="block w-full p-2 text-left text-xs" onClick={() => { setBlocking(true); setMenuOpen(false) }}>차단하기</button>
        {/* 나가기 버튼 — setLeaving (화면 상태·상위 콜백 반영) */}
        <button className="block w-full p-2 text-left text-xs text-accent" onClick={() => { setLeaving(true); setMenuOpen(false) }}>나가기</button>
      </div>}
    </header>
    {partnerLeft && <p className="shrink-0 bg-background p-2 text-center text-xs text-text/60">상대방이 대화방을 나갔어요</p>}
    <SanctionBanner sanction={sanction} />
    {(actionError || error || (loading && !data)) && <CommunityQueryState error={actionError ?? error} loading={loading && !data} onRetry={() => { setActionError(null); return refetch() }} />}
    {/* 날짜·말풍선·읽음 대화 — handleLoadOlder → GET /chat/rooms/{roomId}/messages */}
    <NoteConversation notes={notes} outgoingSender="USER" resetKey={room.roomId} forceScrollKey={scrollKey} onLoadOlder={handleLoadOlder} hasMore={hasMore} loadingOlder={loadingOlder} onVisibleMessage={handleVisibleMessage} newItemsLabel="새 메시지 ↓"
      onMessageAction={(note) => { const original = messages.find((message) => message.messageId === note.noteId); if (!original?.status || original.status === "ACTIVE") setReport({ type: "CHAT_MESSAGE", id: String(note.noteId) }) }} />
    {/* 입력 제출 — handleSend → STOMP /app/chat/{roomId}（미연결 시 REST 전송） */}
    {readOnly ? <p className="shrink-0 border-t border-main/15 p-3 text-center text-xs text-text/60">읽기 전용 대화방이에요.</p> : <form onSubmit={handleSend} className="flex shrink-0 gap-2 border-t border-main/15 p-3">
      {/* 메시지 입력창 — setContent (화면 상태·상위 콜백 반영) */}
      <input aria-label="메시지" maxLength={1000} value={content} onChange={(event) => setContent(event.target.value)} disabled={sanction.active || sending} placeholder={sanction.active ? "이용 제한 중에는 작성할 수 없어요" : "메시지를 입력하세요"} className="min-w-0 flex-1 rounded-lg border border-main/15 px-3 py-2 text-sm disabled:bg-background" />
      {/* 보내기 버튼 — handleSend → STOMP /app/chat/{roomId}（미연결 시 REST 전송） */}
      <button disabled={sanction.active || sending || !content.trim()} aria-label="보내기" className="rounded-lg bg-main p-2 text-white disabled:opacity-50"><Icon name="send" size={18} /></button>
    </form>}
    {report && <ReportModal sourceType="CHAT_ROOM" sourceId={Number(room.roomId)} targetType={report.type} targetId={report.id} targetName={room.partner.name} targetUserId={room.partner.userId} onClose={() => setReport(null)} onReported={(blocked) => { setReport(null); if (blocked) void onLeave() }} />}
    {blocking && <BlockConfirmModal name={room.partner.name} blockedUserId={room.partner.userId} onClose={() => setBlocking(false)} onBlocked={() => { setBlocking(false); void onLeave() }} />}
    {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
    {/* 취소 버튼 — setLeaving (화면 상태·상위 콜백 반영) */}
    {/* 나가기 버튼 — handleLeave → POST /chat/rooms/{roomId}/leave */}
    {leaving && <div className="absolute inset-0 z-30 flex items-center justify-center bg-text/40 p-4"><section role="dialog" aria-modal="true" className="rounded-lg bg-white p-5 text-sm text-text"><p>나가면 대화 내용이 사라지고 다시 들어올 수 없어요</p><div className="mt-4 flex justify-end gap-2"><Button variant="secondary" disabled={leaveSubmitting} onClick={() => setLeaving(false)}>취소</Button><Button disabled={leaveSubmitting} onClick={() => void handleLeave()}>나가기</Button></div></section></div>}
  </div>
}
