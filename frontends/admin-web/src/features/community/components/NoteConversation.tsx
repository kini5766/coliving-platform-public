// 대화 내역 — 날짜·말풍선·읽음 표시와 새 메시지 스크롤
import { useMemo } from "react"
import { useStickToBottom } from "../hooks/useStickToBottom"

export type ConversationNote = {
  noteId: number;
  senderType: "USER" | "ADMIN";
  content: string;
  createdAt: string;
  readAt: string | null
}

type Props = {
  notes: ConversationNote[];
  outgoingSender: ConversationNote["senderType"];
  forceScrollKey?: number;
  resetKey?: string | number | null;
  outgoingStatus?: (note: ConversationNote) => string
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
const displayTime = (value: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" })
}

export function NoteConversation({ notes, outgoingSender, forceScrollKey = 0, resetKey = null, outgoingStatus }: Props) {
  const sortedNotes = useMemo(() => [...notes].sort((left, right) => timestamp(left.createdAt) - timestamp(right.createdAt) || left.noteId - right.noteId), [notes])
  const contentKey = sortedNotes.map((note) => `${note.noteId}:${note.createdAt}`).join("|")
  const { containerRef, handleScroll, hasNewItems, scrollToBottom } = useStickToBottom({ contentKey, forceScrollKey, resetKey })

  return <div className="relative min-h-0 flex-1 bg-background">
    <div ref={containerRef} onScroll={handleScroll} className="h-full overflow-y-auto p-5">
      {sortedNotes.map((note, index) => {
        const outgoing = note.senderType === outgoingSender
        const showDate = index === 0 || dateKey(sortedNotes[index - 1].createdAt) !== dateKey(note.createdAt)
        const meta = <span className="max-w-20 shrink-0 text-[10px] leading-4 text-text/60">
          {outgoing && outgoingStatus && <span className="block text-right">{outgoingStatus(note)}</span>}
          <time className="whitespace-nowrap">{displayTime(note.createdAt)}</time>
        </span>

        return <div key={note.noteId}>
          {showDate && <DateDivider value={note.createdAt} />}
          <div className={`mb-2 flex items-end gap-2 ${outgoing ? "justify-end" : "justify-start"}`}>
            {outgoing && meta}
            <p className={`max-w-[75%] whitespace-pre-wrap break-words rounded-2xl px-4 py-2.5 text-sm leading-6 shadow-sm ${outgoing ? "rounded-br-md bg-main text-white" : "rounded-bl-md bg-text/5 text-text"}`}>
              {note.content}
            </p>
            {!outgoing && meta}
          </div>
        </div>
      })}
    </div>
    {/* 새 쪽지 ↓ 버튼 — scrollToBottom (화면 상태·상위 콜백 반영) */}
    {hasNewItems && <button type="button" onClick={() => scrollToBottom("smooth")} className="absolute bottom-4 left-1/2 -translate-x-1/2 rounded-full bg-main px-3 py-1.5 text-xs font-semibold text-white shadow-lg focus-visible:outline-2 focus-visible:outline-accent">
      새 쪽지 ↓
    </button>}
  </div>
}

function DateDivider({ value }: { value: string }) {
  return <div className="my-5 flex items-center gap-3" role="separator">
    <span className="h-px flex-1 bg-main/15" />
    <time className="shrink-0 text-[11px] text-text/60">{displayDate(value)}</time>
    <span className="h-px flex-1 bg-main/15" />
  </div>
}
