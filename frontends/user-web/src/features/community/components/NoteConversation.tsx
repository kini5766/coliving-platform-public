// 대화 내역 — 날짜·말풍선·읽음 표시와 새 메시지 스크롤
import { type ReactNode, useCallback, useEffect, useLayoutEffect, useMemo, useRef } from "react"
import { useStickToBottom } from "../hooks/useStickToBottom"

export type ConversationNote = {
  noteId: number | string;
  senderType: "USER" | "ADMIN";
  content: string;
  createdAt: string;
  readAt: string | null
  footer?: ReactNode
}

type Props = {
  notes: ConversationNote[];
  outgoingSender: ConversationNote["senderType"];
  active?: boolean;
  forceScrollKey?: number;
  resetKey?: string | number | null;
  locale?: string;
  outgoingStatus?: (note: ConversationNote) => string;
  onLoadOlder?: () => void;
  hasMore?: boolean;
  loadingOlder?: boolean;
  onVisibleMessage?: (messageId: string) => void;
  onMessageAction?: (note: ConversationNote) => void;
  newItemsLabel?: string;
  showDates?: boolean;
  showTimes?: boolean
}

const timestamp = (value: string) => {
  const parsed = new Date(value).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}
const dateKey = (value: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}
const displayDate = (value: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric" })
}
const displayTime = (value: string, locale: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString(locale, { hour: "numeric", minute: "2-digit" })
}

export function NoteConversation({ notes, outgoingSender, active = true, forceScrollKey = 0, resetKey = null, locale = "ko-KR", outgoingStatus, onLoadOlder, hasMore, loadingOlder, onVisibleMessage, onMessageAction, newItemsLabel = "새 쪽지 ↓", showDates = true, showTimes = true }: Props) {
  const sortedNotes = useMemo(() => [...notes].sort((left, right) => timestamp(left.createdAt) - timestamp(right.createdAt) || (typeof left.noteId === "number" && typeof right.noteId === "number" ? left.noteId - right.noteId : String(left.noteId).localeCompare(String(right.noteId)))), [notes])
  const contentKey = onLoadOlder ? String(sortedNotes.at(-1)?.noteId ?? "") : sortedNotes.map((note) => `${note.noteId}:${note.createdAt}`).join("|")
  const { containerRef, handleScroll, hasNewItems, scrollToBottom } = useStickToBottom({ active, contentKey, forceScrollKey, resetKey })
  const previousLayout = useRef<{ first: string; firstOffset: number; top: number; resetKey: typeof resetKey } | null>(null)
  const longPressTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const handleCancelLongPress = () => { if (longPressTimer.current) clearTimeout(longPressTimer.current) }
  const handleVisibleMessage = useCallback(() => {
    const container = containerRef.current
    if (!container || !active || document.visibilityState !== "visible" || !onVisibleMessage) return
    const bounds = container.getBoundingClientRect()
    const visible = [...container.querySelectorAll<HTMLElement>("[data-message-id]")].filter((element) => {
      const rect = element.getBoundingClientRect()
      return rect.bottom > bounds.top && rect.top < bounds.bottom
    })
    const last = visible.at(-1)?.dataset.messageId
    if (last) onVisibleMessage(last)
  }, [active, containerRef, onVisibleMessage])
  // 대화 배치가 바뀌면 이전 메시지 위치를 복원하고 화면에 보이는 메시지 확인
  useLayoutEffect(() => {
    const container = containerRef.current
    if (!container) return
    const first = String(sortedNotes[0]?.noteId ?? "")
    const previous = previousLayout.current
    const elements = [...container.querySelectorAll<HTMLElement>("[data-message-id]")]
    const offset = (element: HTMLElement) => element.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop
    const anchor = previous && elements.find((element) => element.dataset.messageId === previous.first)
    if (onLoadOlder && previous && anchor && previous.resetKey === resetKey && previous.first !== first) container.scrollTop = previous.top + offset(anchor) - previous.firstOffset
    previousLayout.current = { first, firstOffset: elements[0] ? offset(elements[0]) : 0, top: container.scrollTop, resetKey }
    handleVisibleMessage()
  }, [sortedNotes, onLoadOlder, resetKey, forceScrollKey, containerRef, handleVisibleMessage])
  // 탭이 다시 보이면 노출 메시지 읽음 검사, 종료 시 이벤트·길게 누르기 정리
  useEffect(() => {
    document.addEventListener("visibilitychange", handleVisibleMessage)
    return () => { document.removeEventListener("visibilitychange", handleVisibleMessage); handleCancelLongPress() }
  }, [handleVisibleMessage])
  const handleConversationScroll = () => {
    handleScroll()
    if (previousLayout.current && containerRef.current) previousLayout.current.top = containerRef.current.scrollTop
    handleVisibleMessage()
    if (containerRef.current && containerRef.current.scrollTop < 40 && hasMore && !loadingOlder) onLoadOlder?.()
  }

  return <div className="relative min-h-0 flex-1 bg-background">
    <div ref={containerRef} onScroll={handleConversationScroll} className="h-full overflow-y-auto px-3 py-4 sm:px-4 [overflow-anchor:none]">
      {/* 버튼 — onLoadOlder (화면 상태·상위 콜백 반영) */}
      {hasMore && <button type="button" disabled={loadingOlder} onClick={onLoadOlder} className="mb-3 w-full text-xs font-semibold text-main">{loadingOlder ? "불러오는 중…" : "이전 메시지 더 보기"}</button>}
      {sortedNotes.map((note, index) => {
        const outgoing = note.senderType === outgoingSender
        const showDate = index === 0 || dateKey(sortedNotes[index - 1].createdAt) !== dateKey(note.createdAt)
        const meta = <span className="max-w-20 shrink-0 text-[10px] leading-4 text-text/60">
          {outgoing && outgoingStatus && <span className="block text-right">{outgoingStatus(note)}</span>}
          <time className="whitespace-nowrap">{displayTime(note.createdAt, locale)}</time>
        </span>

        return <div key={note.noteId}>
          {showDates && showDate && <DateDivider value={note.createdAt} />}
          <div data-message-id={String(note.noteId)} className={`mb-2 flex items-end gap-1.5 ${outgoing ? "justify-end" : "justify-start"}`}
            onContextMenu={(event) => { if (!outgoing && onMessageAction) { event.preventDefault(); onMessageAction(note) } }}
            onPointerDown={(event) => { if (event.pointerType === "touch" && !outgoing && onMessageAction) longPressTimer.current = setTimeout(() => onMessageAction(note), 600) }}
            onPointerUp={handleCancelLongPress} onPointerMove={handleCancelLongPress} onPointerCancel={handleCancelLongPress} onPointerLeave={handleCancelLongPress}
          >
            {outgoing && showTimes && meta}
            <div className={`max-w-[78%] whitespace-pre-wrap break-words rounded-2xl px-3 py-2 text-sm leading-5 shadow-sm ${outgoing ? "rounded-br-md bg-main text-white" : "rounded-bl-md bg-text/5 text-text"}`}>
              <p>{note.content}</p>
              {note.footer}
            </div>
            {!outgoing && showTimes && meta}
            {/* 메시지 신고 버튼 — onMessageAction (화면 상태·상위 콜백 반영) */}
            {!outgoing && onMessageAction && <button type="button" onClick={() => onMessageAction(note)} className="text-[10px] text-text/50" aria-label="메시지 신고">⋯</button>}
          </div>
        </div>
      })}
    </div>
    {/* 버튼 — scrollToBottom (화면 상태·상위 콜백 반영) */}
    {hasNewItems && <button type="button" onClick={() => scrollToBottom("smooth")} className="absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-main px-3 py-1.5 text-xs font-semibold text-white shadow-lg focus-visible:outline-2 focus-visible:outline-accent">
      {newItemsLabel}
    </button>}
  </div>
}

function DateDivider({ value }: { value: string }) {
  return <div className="my-4 flex items-center gap-3" role="separator">
    <span className="h-px flex-1 bg-main/15" />
    <time className="shrink-0 text-[11px] text-text/60">{displayDate(value)}</time>
    <span className="h-px flex-1 bg-main/15" />
  </div>
}
