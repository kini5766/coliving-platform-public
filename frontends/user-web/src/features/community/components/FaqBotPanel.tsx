// FAQ 상담 — 질문·답변·출처 표시와 쪽지·문의 연결
import { type FormEvent, useRef, useState } from "react"
import { askFaqBot, type FaqAnswer } from "../api/faqApi"
import { CommunityApiError } from "../api/communityClient"
import { NoteConversation, type ConversationNote } from "./NoteConversation"
import { Icon } from "."

export function useFaqConversation() {
  const [exchanges, setExchanges] = useState<{ id: number; question: string; createdAt: string; response?: FaqAnswer }[]>([])
  const [question, setQuestion] = useState("")
  const [asking, setAsking] = useState(false)
  const [error, setError] = useState("")
  const pending = useRef(false)
  const nextId = useRef(1)
  // handleAsk — POST /faq-bot/ask
  const handleAsk = async (event: FormEvent) => {
    event.preventDefault()
    const text = question.trim()
    if (!text || text.length > 300 || pending.current) return
    pending.current = true; setAsking(true); setError("")
    const id = nextId.current++
    const createdAt = new Date().toLocaleString("sv-SE", { timeZone: "Asia/Seoul" }).replace(" ", "T")
    setExchanges((items) => [...items, { id, question: text, createdAt }])
    setQuestion("")
    try {
      const response = await askFaqBot({ question: text })
      setExchanges((items) => items.map((item) => item.id === id ? { ...item, response } : item))
    } catch (error) {
      setError(error instanceof CommunityApiError && (error.status === 429 || error.code === "TOO_MANY_REQUESTS") ? "질문이 너무 많아요. 1분 후 다시 물어봐 주세요" : error instanceof Error ? error.message : "답변을 불러오지 못했어요.")
      setQuestion(text)
    } finally { pending.current = false; setAsking(false) }
  }
  return { exchanges, question, setQuestion, asking, error, handleAsk }
}

export function FaqBotPanel({ conversation, onAction, authenticated }: { conversation: ReturnType<typeof useFaqConversation>; onAction: (action: "NOTE" | "INQUIRY", question: string) => void; authenticated: boolean }) {
  const { exchanges, question, setQuestion, asking, error, handleAsk } = conversation
  const notes: ConversationNote[] = exchanges.flatMap((exchange) => {
    const stamp = exchange.createdAt
    const response = exchange.response
    return [{ noteId: exchange.id * 2, senderType: "USER" as const, content: exchange.question, createdAt: stamp, readAt: null }, ...(response ? [{
      noteId: exchange.id * 2 + 1, senderType: "ADMIN" as const, content: response.answer, createdAt: stamp, readAt: null,
      footer: <>{response.sources.length > 0 && <p className="mt-2 text-xs opacity-70">출처: {response.sources.map((source) => source.title).join(", ")}</p>}
        {/* 버튼 — onAction (화면 상태·상위 콜백 반영) */}
        {!response.answered && response.actions.filter((action) => authenticated ? action === "NOTE" : action === "INQUIRY").map((action) => <button key={action} type="button" onClick={() => onAction(action, exchange.question)} className="mt-2 block rounded-lg border border-main/20 px-2 py-1 text-xs font-semibold text-main">{action === "NOTE" ? "매니저에게 쪽지로 문의" : "문의 남기기"}</button>)}</>
    }] : [])]
  })
  return <>
    {exchanges.length === 0 && <p className="px-4 pt-4 text-sm text-text/70">궁금한 내용을 물어봐 주세요.</p>}
    {/* 날짜·말풍선·읽음 대화 — 상위 조회 결과·선택 상태 표시 */}
    <NoteConversation notes={notes} outgoingSender="USER" showDates={false} showTimes={false} newItemsLabel="새 답변 ↓" />
    {asking && <p role="status" className="shrink-0 px-4 py-2 text-xs text-text/70">답변을 찾는 중…</p>}
    {error && <p role="alert" className="shrink-0 px-4 py-2 text-xs text-accent">{error}</p>}
    {/* 입력 제출 — handleAsk → POST /faq-bot/ask */}
    <form onSubmit={handleAsk} className="shrink-0 border-t border-main/15 p-3">
      {/* FAQ 질문 입력창 — setQuestion (화면 상태·상위 콜백 반영) */}
      {/* 질문 보내기 버튼 — handleAsk → POST /faq-bot/ask */}
      <div className="flex gap-2"><input aria-label="FAQ 질문" maxLength={300} value={question} disabled={asking} onChange={(event) => setQuestion(event.target.value)} placeholder="질문을 입력하세요" className="min-w-0 flex-1 rounded-lg border border-main/15 px-3 py-2 text-sm" /><button aria-label="질문 보내기" disabled={asking || !question.trim()} className="rounded-lg bg-main p-2 text-white disabled:opacity-50"><Icon name="send" /></button></div>
      <p className="mt-2 text-[11px] text-text/60">개인정보(연락처·계좌 등)는 입력하지 마세요</p>
    </form>
  </>
}
