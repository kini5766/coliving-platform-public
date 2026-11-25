// 채팅 REST API — 채팅방 생성·목록·이력·읽음·나가기
import { request, USE_MOCK } from "./communityClient"
import { CHAT_MESSAGES, CHAT_ROOMS, UNREAD_NOTIFICATION_SUMMARY, USER_NOTIFICATIONS } from "../mocks"
import type { ChatMessage, ChatMessageListResponse, ChatRoom } from "../types"

// 가짜 응답을 복제해 화면 수정이 원본 데이터에 섞이지 않도록 처리
const copy = <T,>(value: T): T => structuredClone(value)
// normalizeRoom — API 내부 응답·상태 계산 도우미(직접 HTTP 호출 없음)
const normalizeRoom = (room: ChatRoom): ChatRoom => ({ ...room, roomId: String(room.roomId) })
// path — API 내부 응답·상태 계산 도우미(직접 HTTP 호출 없음)
const path = (roomId: string | number) => `/chat/rooms/${encodeURIComponent(roomId)}`
// 가짜 데이터의 생성·변경 시간을 한국 시간 문자열로 만들기
const now = () => new Date().toLocaleString("sv-SE", { timeZone: "Asia/Seoul" }).replace(" ", "T")

// 채팅방 목록 → GET /community-service/chat/rooms → ChatController.getRooms
export async function getChatRooms(): Promise<ChatRoom[]> {
  const rooms = USE_MOCK ? copy(CHAT_ROOMS) : await request("/chat/rooms")
  return rooms.map(normalizeRoom)
}

// 1:1 채팅 시작 → POST /community-service/chat/rooms → ChatController.openRoom
export async function openChatRoom(body: { partnerUserId: number; sourceType: "POST" | "PROFILE"; sourcePostId?: number }) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request("/chat/rooms", { method: "POST", body })
  const room = CHAT_ROOMS.find((item) => item.partner.userId === body.partnerUserId)
  if (room) return { roomId: room.roomId, created: false }
  const roomId = String(Date.now())
  CHAT_ROOMS.unshift({ roomId, partner: { userId: body.partnerUserId, name: "이웃", profileImageUrl: null, roomNumber: "" }, lastMessage: null, lastMessageAt: now(), unreadCount: 0, readOnly: false, partnerLeft: false })
  CHAT_MESSAGES[roomId] = { messages: [], hasMore: false }
  return { roomId, created: true }
}

// 채팅 내역 → GET /community-service/chat/rooms/{roomId}/messages → ChatController.getMessages
export async function getChatRoomMessages(roomId: string, query: { before?: string; size?: number } = {}): Promise<ChatMessageListResponse> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request(`${path(roomId)}/messages`, { query: { size: 30, ...query } })
  const all = CHAT_MESSAGES[roomId]?.messages ?? []
  const end = query.before ? Math.max(0, all.findIndex((message) => message.messageId === query.before)) : all.length
  const start = Math.max(0, end - (query.size ?? 30))
  return copy({ messages: all.slice(start, end), hasMore: start > 0 })
}

// 채팅 보내기(REST) → POST /community-service/chat/rooms/{roomId}/messages → ChatController.sendMessage
export async function sendChatMessage(roomId: string, body: { content: string }, mockSenderId = 5001): Promise<ChatMessage> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request(`${path(roomId)}/messages`, { method: "POST", body })
  const message: ChatMessage = { messageId: `local_${Date.now()}`, senderId: mockSenderId, content: body.content, status: "ACTIVE", sentAt: now() }
  const list = CHAT_MESSAGES[roomId] ?? (CHAT_MESSAGES[roomId] = { messages: [], hasMore: false })
  list.messages.push(message)
  const room = CHAT_ROOMS.find((item) => item.roomId === roomId)
  if (room) Object.assign(room, { lastMessage: message.content, lastMessageAt: message.sentAt })
  return copy(message)
}

// 채팅 읽음 → PATCH /community-service/chat/rooms/{roomId}/read → ChatController.markRead
export async function readChatRoom(roomId: string, body: { lastReadMessageId: string }) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request(`${path(roomId)}/read`, { method: "PATCH", body })
  const room = CHAT_ROOMS.find((item) => item.roomId === roomId)
  if (room) { UNREAD_NOTIFICATION_SUMMARY.chat = Math.max(0, UNREAD_NOTIFICATION_SUMMARY.chat - room.unreadCount); room.unreadCount = 0 }
  USER_NOTIFICATIONS.content.forEach((item) => { if (item.targetType === "CHAT_ROOM" && String(item.targetId) === roomId) item.read = true })
  return null
}

// 채팅방 나가기 → POST /community-service/chat/rooms/{roomId}/leave → ChatController.leaveRoom
export async function leaveChatRoom(roomId: string) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request(`${path(roomId)}/leave`, { method: "POST" })
  const index = CHAT_ROOMS.findIndex((room) => room.roomId === roomId)
  if (index >= 0) CHAT_ROOMS.splice(index, 1)
  delete CHAT_MESSAGES[roomId]
  return null
}
