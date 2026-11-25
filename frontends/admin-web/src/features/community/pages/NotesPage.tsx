// 관리자 쪽지함 — 대상별 대화·답장·처리 메모
import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react"
import { useSearchParams } from "react-router"
import { getAdminNoteThread, getAdminNoteThreads, replyAdminNoteThread, sendAdminNote, updateAdminNoteThread } from "../api/adminNoteApi"
import { searchNoteRecipients } from "../api"
import { Avatar, Badge, Button, Icon, SearchField, Select } from "../components"
import { NoteConversation } from "../components/NoteConversation"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import { useCommunityRealtime } from "../realtime/CommunityRealtimeProvider"
import type { AdminNoteThreadDetail, AdminNoteThreadSummary, NoteParticipant, NoteStatus, NoteThreadType } from "../types"

const TYPE_LABELS: Record<NoteThreadType, string> = {
  CANDIDATE: "후보자",
  RESIDENT: "입주자",
  MEMBER: "회원",
  GUEST: "비회원 문의"
}
const FIELD_CLASS = "w-full rounded-lg border border-main/15 bg-white px-3 py-2.5 text-sm text-text outline-none focus-visible:outline-2 focus-visible:outline-accent"
const PAGE_SIZE = 20
const displayDateTime = (value: string) => {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "numeric", minute: "2-digit" })
}

export default function NotesPage({ title }: { title: string }) {
  const [searchParams] = useSearchParams()
  const linkedThreadId = Number(searchParams.get("threadId"))
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [typeFilter, setTypeFilter] = useState<NoteThreadType | "">("")
  const [statusFilter, setStatusFilter] = useState<NoteStatus | "">("")
  const [keyword, setKeyword] = useState("")
  const [page, setPage] = useState(0)
  const [reply, setReply] = useState("")
  const [processingStatus, setProcessingStatus] = useState<NoteStatus>("UNANSWERED")
  const [processingMemo, setProcessingMemo] = useState("")
  const [writeOpen, setWriteOpen] = useState(false)
  const [recipientQuery, setRecipientQuery] = useState("")
  const [recipientResults, setRecipientResults] = useState<NoteParticipant[]>([])
  const [recipient, setRecipient] = useState<NoteParticipant | null>(null)
  const [newContent, setNewContent] = useState("")
  const [actionError, setActionError] = useState("")
  const [noteScrollKey, setNoteScrollKey] = useState(0)

  // 화면 제목이 바뀌면 브라우저 제목 반영
  useEffect(() => { document.title = title }, [title])

  // loadThreads — GET /admin/note-threads
  const loadThreads = useCallback(() => getAdminNoteThreads({
    type: typeFilter || undefined,
    status: statusFilter || undefined,
    keyword: keyword.trim() || undefined,
    page,
    size: PAGE_SIZE
  }), [keyword, page, statusFilter, typeFilter])
  const threadQuery = useCommunityQuery(loadThreads)
  const threads = useMemo(() => (threadQuery.data?.content ?? []) as AdminNoteThreadSummary[], [threadQuery.data])

  // 목록이 바뀌면 기존 선택을 유지하거나 첫 쪽지 선택
  useEffect(() => {
    if (linkedThreadId > 0) return
    if (!threads.some((thread) => thread.threadId === selectedId)) setSelectedId(threads[0]?.threadId ?? null)
  }, [selectedId, threads, linkedThreadId])
  // URL threadId가 바뀌면 해당 쪽지 상세 선택
  useEffect(() => { if (linkedThreadId > 0) setSelectedId(linkedThreadId) }, [linkedThreadId])

  // loadDetail — GET /admin/note-threads/{threadId}
  const loadDetail = useCallback(() => selectedId === null ? Promise.resolve(null) : getAdminNoteThread(selectedId), [selectedId])
  const detailQuery = useCommunityQuery(loadDetail, { enabled: selectedId !== null })
  const selected = detailQuery.data as AdminNoteThreadDetail | null
  const selectedThreadId = selected?.threadId
  const selectedStatus = selected?.status
  const selectedMemo = selected?.memo
  const { status: realtimeStatus, onNotification } = useCommunityRealtime()
  const realtimeConnected = realtimeStatus === "connected"
  const refetchThreads = threadQuery.refetch
  const refetchDetail = detailQuery.refetch
  // 실시간 알림을 받으면 쪽지 목록·선택한 대화 재조회
  useEffect(() => onNotification((notification) => {
    void refetchThreads()
    if (notification.targetType === "NOTE_THREAD" && String(notification.targetId) === String(selectedId)) void refetchDetail()
  }), [onNotification, refetchThreads, refetchDetail, selectedId])
  // 연결·선택이 바뀌면 재조회하고 실시간 미연결 시 30초마다 갱신
  useEffect(() => {
    void refetchThreads()
    if (selectedId !== null) void refetchDetail()
    if (realtimeConnected) return
    const interval = window.setInterval(() => { void refetchThreads(); if (selectedId !== null) void refetchDetail() }, 30_000)
    return () => window.clearInterval(interval)
  }, [realtimeConnected, refetchThreads, refetchDetail, selectedId])

  // 선택한 쪽지의 처리 상태·메모를 편집 입력에 반영
  useEffect(() => {
    if (selectedThreadId == null || !selectedStatus) return
    setProcessingStatus(selectedStatus)
    setProcessingMemo(selectedMemo ?? "")
  }, [selectedThreadId, selectedStatus, selectedMemo])
  // 쪽지 ID가 바뀌면 목록·메뉴 배지를 갱신해 읽음 반영
  useEffect(() => {
    if (selectedThreadId == null) return
    void refetchThreads()
    window.dispatchEvent(new Event("admin-badge-refresh"))
  }, [selectedThreadId, refetchThreads])

  // 수신자 검색어가 바뀌면 가짜 회원 검색 결과 갱신
  useEffect(() => {
    // TODO: user-service 회원 검색 API가 생기면 목 검색을 실제 API로 교체한다.
    void searchNoteRecipients(recipientQuery).then(setRecipientResults)
  }, [recipientQuery])

  // refresh — GET /admin/note-threads · GET /admin/note-threads/{threadId}
  const refresh = async () => {
    await Promise.all([threadQuery.refetch(), detailQuery.refetch()])
    window.dispatchEvent(new Event("admin-badge-refresh"))
  }
  // handleSendReply — POST /admin/note-threads/{threadId}/notes · GET /admin/note-threads
  const handleSendReply = async (event: FormEvent) => {
    event.preventDefault()
    if (!selected || selected.type === "GUEST" || !reply.trim()) return
    setActionError("")
    try {
      await replyAdminNoteThread(selected.threadId, { content: reply.trim() })
      setReply("")
      setProcessingStatus("ANSWERED")
      await refresh()
      setNoteScrollKey((value) => value + 1)
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : "답장을 보내지 못했습니다.")
    }
  }
  // handleSaveProcessing — PATCH /admin/note-threads/{threadId} · GET /admin/note-threads
  const handleSaveProcessing = async () => {
    if (!selected) return
    setActionError("")
    try {
      await updateAdminNoteThread(selected.threadId, { status: processingStatus, memo: processingMemo })
      await refresh()
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : "처리 정보를 저장하지 못했습니다.")
    }
  }
  // handleSendNewNote — POST /admin/notes · GET /admin/note-threads
  const handleSendNewNote = async (event: FormEvent) => {
    event.preventDefault()
    if (!recipient?.id || !newContent.trim()) return
    setActionError("")
    try {
      const response = await sendAdminNote({ recipientUserId: recipient.id, content: newContent.trim() })
      setWriteOpen(false)
      setRecipient(null)
      setRecipientQuery("")
      setNewContent("")
      const updated = await threadQuery.refetch()
      setSelectedId(updated?.content?.some((thread: AdminNoteThreadSummary) => thread.threadId === response.threadId) ? response.threadId : updated?.content?.[0]?.threadId ?? null)
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : "쪽지를 보내지 못했습니다.")
    }
  }

  return <div className="mx-auto flex h-[calc(100dvh-5rem)] max-w-[1500px] flex-col overflow-hidden">
    <header className="mb-5 flex shrink-0 items-end justify-between gap-4">
      <div>
        <p className="mb-2 text-xs font-bold tracking-[0.16em] text-accent">NOTES</p>
        <h1 className="text-2xl font-semibold text-main sm:text-3xl">{title}</h1>
      </div>
      {/* 쪽지 쓰기 버튼 — setWriteOpen (화면 상태·상위 콜백 반영) */}
      <Button icon="edit" onClick={() => setWriteOpen(true)}>쪽지 쓰기</Button>
    </header>

    {actionError && <ErrorNotice message={actionError} />}
    <div className="grid min-h-0 flex-1 overflow-hidden rounded-lg border border-main/15 bg-white shadow-sm lg:grid-cols-[340px_minmax(420px,1fr)_280px] lg:grid-rows-1">
      {/* 상세·보조 영역 — 상위 조회 결과·선택 상태 표시 */}
      <aside className="flex min-h-0 flex-col border-main/15 lg:border-r">
        <div className="shrink-0 space-y-3 border-b border-main/15 p-4">
          {/* 이름, 내용 검색 검색 입력 — setKeyword → GET /admin/note-threads */}
          <SearchField value={keyword} onChange={(value) => { setKeyword(value); setPage(0) }} placeholder="이름, 내용 검색" />
          {/* 유형 선택 필터 — setTypeFilter → GET /admin/note-threads */}
          <Select label="유형" value={typeFilter} onChange={(value) => { setTypeFilter(value as NoteThreadType | ""); setPage(0) }}>
            <option value="">전체</option>
            {Object.entries(TYPE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </Select>
          {/* 처리 상태 선택 필터 — setStatusFilter → GET /admin/note-threads */}
          <Select label="처리 상태" value={statusFilter} onChange={(value) => { setStatusFilter(value as NoteStatus | ""); setPage(0) }}>
            <option value="">전체</option>
            <option value="UNANSWERED">미답변</option>
            <option value="ANSWERED">답변 완료</option>
          </Select>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto">
          {threadQuery.loading && !threadQuery.data && <LoadingState />}
          {threadQuery.error && <QueryError error={threadQuery.error} onRetry={threadQuery.refetch} />}
          {/* 버튼 — setSelectedId (화면 상태·상위 콜백 반영) */}
          {!threadQuery.error && threads.map((thread) => <button
            className={`flex w-full items-center gap-3 border-b border-main/15 p-4 text-left focus-visible:outline-2 focus-visible:outline-accent ${selectedId === thread.threadId ? "bg-background" : "bg-white"}`}
            key={thread.threadId}
            onClick={() => setSelectedId(thread.threadId)}
          >
            {thread.type === "GUEST" ? <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-background text-main"><Icon name="user" /></span> : <Avatar name={thread.name} />}
            <span className="min-w-0 flex-1">
              <span className="flex items-center gap-2">
                <strong className="text-sm text-text">{thread.name}</strong>
                <small className="text-xs text-text/70">{TYPE_LABELS[thread.type]}</small>
                <time className="ml-auto text-xs text-text/70">{displayDateTime(thread.lastAt)}</time>
              </span>
              <span className="mt-1 block truncate text-xs text-text/70">{thread.lastContent}</span>
            </span>
            {thread.unreadCount > 0 && <b className="flex h-5 min-w-5 items-center justify-center rounded-full bg-accent px-1 text-xs text-text">{thread.unreadCount}</b>}
          </button>)}
          {!threadQuery.loading && !threadQuery.error && threads.length === 0 && <EmptyState />}
        </div>
        <footer className="flex shrink-0 items-center justify-between border-t border-main/15 p-3 text-xs text-text/70">
          {/* 이전 버튼 — setPage (화면 상태·상위 콜백 반영) */}
          <button disabled={page === 0} onClick={() => setPage((value) => value - 1)} className="font-semibold text-main disabled:opacity-40">이전</button>
          <span>{(threadQuery.data?.totalPages ?? 0) === 0 ? 0 : page + 1} / {threadQuery.data?.totalPages ?? 0}</span>
          {/* 다음 버튼 — setPage (화면 상태·상위 콜백 반영) */}
          <button disabled={page + 1 >= (threadQuery.data?.totalPages ?? 0)} onClick={() => setPage((value) => value + 1)} className="font-semibold text-main disabled:opacity-40">다음</button>
        </footer>
      </aside>

      {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
      {!selected ? <section className="hidden min-h-0 items-center justify-center lg:flex">{detailQuery.loading ? <LoadingState /> : detailQuery.error ? <QueryError error={detailQuery.error} onRetry={detailQuery.refetch} /> : <EmptyState select />}</section> : <section className="flex min-h-0 min-w-0 flex-col border-main/15 lg:border-r">
        <header className="flex shrink-0 items-center gap-3 border-b border-main/15 p-4">
          <Avatar name={selected.user?.name ?? selected.guest?.name ?? "-"} />
          <div className="min-w-0 flex-1">
            <strong className="block text-sm text-text">{selected.user?.name ?? selected.guest?.name}</strong>
            <span className="text-xs text-text/70">{TYPE_LABELS[selected.type]}</span>
          </div>
          <Badge type={selected.status === "UNANSWERED" ? "warning" : "success"}>{selected.status === "UNANSWERED" ? "미답변" : "답변 완료"}</Badge>
        </header>
        {/* 날짜·말풍선·읽음 대화 — 상위 조회 결과·선택 상태 표시 */}
        <NoteConversation
          notes={selected.notes}
          outgoingSender="ADMIN"
          forceScrollKey={noteScrollKey}
          resetKey={selected.threadId}
          outgoingStatus={(note) => note.readAt ? "읽음" : "안 읽음"}
        />
        {/* 입력 제출 — handleSendReply → POST /admin/note-threads/{threadId}/notes · GET /admin/note-threads */}
        {selected.type === "GUEST" ? <div className="flex shrink-0 items-center justify-center gap-2 border-t border-main/15 bg-background p-4 text-sm text-text/70"><Icon name="phone" size={17} />비회원 문의는 전화 또는 이메일로 답변해 주세요</div> : <form className="flex shrink-0 gap-3 border-t border-main/15 p-4" onSubmit={handleSendReply}>
          {/* 답장 내용을 입력하세요 내용 입력 — setReply (화면 상태·상위 콜백 반영) */}
          <textarea className={`${FIELD_CLASS} min-h-20 flex-1 resize-none`} value={reply} onChange={(event) => setReply(event.target.value)} placeholder="답장 내용을 입력하세요" />
          {/* 답장 보내기 버튼 — handleSendReply → POST /admin/note-threads/{threadId}/notes · GET /admin/note-threads */}
          <Button type="submit" icon="send" disabled={!reply.trim()}>답장 보내기</Button>
        </form>}
      </section>}

      {/* 상세·보조 영역 — 상위 조회 결과·선택 상태 표시 */}
      {selected && <aside className="hidden min-h-0 min-w-0 overflow-y-auto lg:block">
        <div className="flex flex-col items-center border-b border-main/15 p-6 text-center">
          <Avatar name={selected.user?.name ?? selected.guest?.name ?? "-"} size="xl" />
          <h2 className="mt-3 text-lg font-semibold text-text">{selected.user?.name ?? selected.guest?.name}</h2>
          <div className="mt-2"><Badge type={selected.type === "GUEST" ? "warning" : "info"}>{TYPE_LABELS[selected.type]}</Badge></div>
        </div>
        <div className="space-y-3 border-b border-main/15 p-5 text-sm">
          {selected.guest ? <><Info label="전화번호" value={selected.guest.phone} /><Info label="이메일" value={selected.guest.email} /><Info label="개인정보 동의" value={displayDateTime(selected.guest.privacyAgreedAt)} /></> : <><Info label="건물" value={selected.user?.buildingName ?? undefined} /><Info label="호실" value={selected.user?.roomNumber ?? undefined} /></>}
        </div>
        <div className="space-y-4 p-5">
          {/* 선택 필터 — setProcessingStatus (화면 상태·상위 콜백 반영) */}
          <label className="block text-xs font-semibold text-text/70">처리 상태<select className={`${FIELD_CLASS} mt-2`} value={processingStatus} onChange={(event) => setProcessingStatus(event.target.value as NoteStatus)}><option value="UNANSWERED">미답변</option><option value="ANSWERED">답변 완료</option></select></label>
          {/* 처리 내용을 기록하세요 내용 입력 — setProcessingMemo (화면 상태·상위 콜백 반영) */}
          <label className="block text-xs font-semibold text-text/70">처리 메모<textarea className={`${FIELD_CLASS} mt-2 min-h-24 resize-none`} value={processingMemo} onChange={(event) => setProcessingMemo(event.target.value)} placeholder="처리 내용을 기록하세요" /></label>
          {/* 저장 버튼 — handleSaveProcessing → PATCH /admin/note-threads/{threadId} · GET /admin/note-threads */}
          <Button className="w-full" onClick={() => void handleSaveProcessing()}>저장</Button>
        </div>
      </aside>}
    </div>

    {writeOpen && <div className="fixed inset-0 z-50 flex items-center justify-center bg-main/15 p-4" onMouseDown={() => setWriteOpen(false)}>
      {/* 입력 제출 — handleSendNewNote → POST /admin/notes · GET /admin/note-threads */}
      <form className="w-full max-w-lg overflow-hidden rounded-lg border border-main/15 bg-white shadow-xl" onSubmit={handleSendNewNote} onMouseDown={(event) => event.stopPropagation()}>
        {/* 닫기 버튼 — setWriteOpen (화면 상태·상위 콜백 반영) */}
        <header className="flex items-start justify-between border-b border-main/15 p-5"><div><p className="mb-1 text-xs font-bold tracking-[0.16em] text-accent">NEW NOTE</p><h2 className="text-lg font-semibold text-main">쪽지 쓰기</h2></div><button type="button" className="rounded-lg p-2 text-text/70" onClick={() => setWriteOpen(false)} aria-label="닫기"><Icon name="close" /></button></header>
        <div className="space-y-4 p-5">
          {/* 회원 이름을 입력하세요 입력창 — setRecipientQuery (화면 상태·상위 콜백 반영) */}
          <label className="block text-xs font-semibold text-text/70">받는 사람 검색<input className={`${FIELD_CLASS} mt-2`} value={recipientQuery} onChange={(event) => { setRecipientQuery(event.target.value); setRecipient(null) }} placeholder="회원 이름을 입력하세요" /></label>
          {/* 버튼 — setRecipient (화면 상태·상위 콜백 반영) */}
          {recipientQuery && !recipient && <div className="max-h-44 overflow-y-auto rounded-lg border border-main/15">{recipientResults.map((item) => <button type="button" className="flex w-full items-center gap-3 border-b border-main/15 p-3 text-left" key={item.id} onClick={() => { setRecipient(item); setRecipientQuery(item.name) }}><Avatar name={item.name} size="sm" /><span><strong className="block text-sm text-text">{item.name}</strong><small className="text-xs text-text/70">{item.building}</small></span></button>)}</div>}
          {/* 쪽지 내용을 입력하세요 내용 입력 — setNewContent (화면 상태·상위 콜백 반영) */}
          <label className="block text-xs font-semibold text-text/70">내용<textarea required className={`${FIELD_CLASS} mt-2 min-h-28 resize-none`} value={newContent} onChange={(event) => setNewContent(event.target.value)} placeholder="쪽지 내용을 입력하세요" /></label>
        </div>
        {/* 취소 버튼 — setWriteOpen (화면 상태·상위 콜백 반영) */}
        {/* 보내기 버튼 — handleSendNewNote → POST /admin/notes · GET /admin/note-threads */}
        <footer className="flex justify-end gap-2 border-t border-main/15 bg-background p-4"><Button variant="secondary" onClick={() => setWriteOpen(false)}>취소</Button><Button type="submit" disabled={!recipient?.id || !newContent.trim()}>보내기</Button></footer>
      </form>
    </div>}
  </div>
}

function Info({ label, value }: { label: string; value?: string }) {
  return <div className="flex items-start justify-between gap-3"><span className="text-text/70">{label}</span><strong className="text-right text-text">{value ?? "-"}</strong></div>
}
function EmptyState({ select = false }: { select?: boolean }) {
  return <div className="flex min-h-64 flex-col items-center justify-center p-6 text-center text-text/70"><span className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-background text-main"><Icon name="mail" size={24} /></span><strong className="text-sm text-text">{select ? "쪽지를 선택해 주세요" : "쪽지가 없습니다"}</strong></div>
}
function LoadingState() {
  return <div className="p-8 text-center text-sm text-text/70">불러오는 중...</div>
}
function QueryError({ error, onRetry }: { error: unknown; onRetry: () => Promise<unknown> }) {
  // 다시 불러오기 버튼 — onRetry (화면 상태·상위 콜백 반영)
  return <div className="p-6 text-center text-sm text-text/70"><p>{error instanceof Error ? error.message : "목록을 불러오지 못했습니다."}</p><button className="mt-2 font-semibold text-main" onClick={() => void onRetry()}>다시 불러오기</button></div>
}
function ErrorNotice({ message }: { message: string }) {
  return <p className="mb-4 rounded-lg border border-accent/40 bg-accent/15 p-3 text-sm text-text">{message}</p>
}
