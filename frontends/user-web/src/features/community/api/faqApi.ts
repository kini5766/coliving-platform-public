// FAQ 질문 API — 질문을 보내 답변·출처·후속 안내 조회
import { request, USE_MOCK } from "./communityClient"
import { FAQ_ITEMS } from "../mocks"
export type FaqAnswer = { answered: boolean; answer: string; sources: { documentId: number; title: string }[]; actions: ("NOTE" | "INQUIRY")[]; cached: boolean }

// FAQ 질문 → POST /community-service/faq-bot/ask → FaqBotController.ask
export async function askFaqBot({ question, buildingId }: { question: string; buildingId?: number }): Promise<FaqAnswer> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request("/faq-bot/ask", { method: "POST", body: { question, ...(buildingId === undefined ? {} : { buildingId }) } })
  const match = FAQ_ITEMS.find((item) => item.enabled && (item.question.includes(question) || question.includes(item.question)))
  return { answered: Boolean(match), answer: match?.answer ?? "해당 내용은 규정에서 찾지 못했어요. 매니저에게 문의해 주세요.", sources: match ? [{ documentId: match.id, title: "공통 이용 규정" }] : [], actions: match ? [] : ["INQUIRY", "NOTE"], cached: false }
}
