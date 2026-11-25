// 채팅 이벤트 병합 — 메시지 ID 중복 제거와 시간순 대화 유지
import type { ChatMessage } from "../types"

// 메시지 ID로 중복 제거 후 전송 시각·ID 순으로 병합
export function mergeChatMessages(current: ChatMessage[], incoming: ChatMessage[]) {
  return [...new Map([...current, ...incoming].map((message) => [message.messageId, message])).values()].sort((a, b) => a.sentAt.localeCompare(b.sentAt) || a.messageId.localeCompare(b.messageId))
}
