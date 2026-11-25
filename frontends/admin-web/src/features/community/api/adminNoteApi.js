// 관리자 쪽지 API — 대화 목록·상세·답장·처리 정보 저장
import { ADMIN_NOTES } from '../mocks'
import { request, USE_MOCK } from './communityClient'

// 가짜 응답을 복제해 화면 수정이 원본 데이터에 섞이지 않도록 처리
const copy = (value) => structuredClone(value)
// 가짜 비회원 문의 유형을 API의 GUEST 값으로 변환
const apiType = (type) => type === 'GUEST_INQUIRY' ? 'GUEST' : type
// 가짜 데이터의 날짜·시각 숫자를 두 자리로 맞추기
const pad = (value) => String(value).padStart(2, '0')
// 가짜 쪽지의 표시 시각을 API 날짜 문자열로 변환
function toApiDate(value, fallback = '2025-10-18T09:00:00') {
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(value ?? '')) return value
  const matched = String(value ?? '').match(/(\d{4})\.\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(오전|오후)\s*(\d{1,2}):(\d{2})/)
  if (!matched) return fallback
  let hour = Number(matched[5]) % 12
  if (matched[4] === '오후') hour += 12
  return `${matched[1]}-${pad(matched[2])}-${pad(matched[3])}T${pad(hour)}:${matched[6]}:00`
}

// 가짜 관리자 쪽지를 목록 응답 구조로 변환
function toSummary(note) {
  return {
    threadId: note.id,
    type: apiType(note.participant.type),
    userId: note.participant.id ?? null,
    name: note.participant.name,
    lastContent: note.lastContent,
    lastAt: toApiDate(note.messages.at(-1)?.sentAt),
    unreadCount: note.unreadCount,
    status: note.status,
  }
}

// 가짜 관리자 쪽지를 대화·회원·비회원 상세 응답으로 변환
function toDetail(note) {
  const guest = note.participant.type === 'GUEST_INQUIRY'
  return {
    threadId: note.id,
    type: apiType(note.participant.type),
    status: note.status,
    memo: note.processingMemo || null,
    user: guest ? null : {
      userId: note.participant.id,
      name: note.participant.name,
      buildingName: note.participant.building ?? null,
      roomNumber: note.participant.room ?? null,
    },
    guest: guest ? {
      name: note.participant.name,
      phone: note.participant.phone,
      email: note.participant.email,
      privacyAgreedAt: toApiDate(note.participant.privacyConsentAt),
    } : null,
    notes: note.messages.map((message) => ({
      noteId: message.id,
      senderType: message.senderType,
      content: message.content,
      createdAt: toApiDate(message.sentAt),
      readAt: message.isRead ? toApiDate(message.sentAt) : null,
    })),
  }
}

// 관리자 쪽지 목록 → GET /community-service/admin/note-threads → AdminNoteController.getThreads
export async function getAdminNoteThreads(query = {}) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/admin/note-threads', { query })
  let content = ADMIN_NOTES.map(toSummary)
  if (query.type) content = content.filter((item) => item.type === query.type)
  if (query.status) content = content.filter((item) => item.status === query.status)
  if (query.keyword) content = content.filter((item) => item.name.includes(query.keyword) || item.lastContent.includes(query.keyword))
  const page = query.page ?? 0
  const size = query.size ?? 20
  return copy({
    content: content.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: content.length,
    totalPages: content.length ? Math.ceil(content.length / size) : 0,
  })
}

// 관리자 쪽지 상세·읽음 → GET /community-service/admin/note-threads/{threadId} → AdminNoteController.getThread
export async function getAdminNoteThread(threadId) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request(`/admin/note-threads/${encodeURIComponent(threadId)}`)
  const note = ADMIN_NOTES.find((item) => item.id === threadId)
  if (!note) return null
  note.unreadCount = 0
  return copy(toDetail(note))
}

// 관리자 답장 → POST /community-service/admin/note-threads/{threadId}/notes → AdminNoteController.reply
export async function replyAdminNoteThread(threadId, noteRequest) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request(`/admin/note-threads/${encodeURIComponent(threadId)}/notes`, { method: 'POST', body: noteRequest })
  const note = ADMIN_NOTES.find((item) => item.id === threadId)
  const createdAt = new Date().toISOString().slice(0, 19)
  const noteId = Date.now()
  if (note) {
    note.status = 'ANSWERED'
    note.lastContent = noteRequest.content
    note.lastSentAt = createdAt
    note.messages.push({ id: noteId, senderType: 'ADMIN', content: noteRequest.content, sentAt: createdAt, isRead: false })
  }
  return { noteId, createdAt, threadStatus: 'ANSWERED' }
}

// 처리 상태·메모 저장 → PATCH /community-service/admin/note-threads/{threadId} → AdminNoteController.update
export async function updateAdminNoteThread(threadId, updateRequest) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request(`/admin/note-threads/${encodeURIComponent(threadId)}`, { method: 'PATCH', body: updateRequest })
  const note = ADMIN_NOTES.find((item) => item.id === threadId)
  if (note) {
    if (updateRequest.status !== undefined) note.status = updateRequest.status
    if (updateRequest.memo !== undefined) note.processingMemo = updateRequest.memo
  }
  return {
    threadId,
    status: note?.status ?? updateRequest.status,
    memo: note?.processingMemo ?? updateRequest.memo ?? null,
    updatedAt: new Date().toISOString().slice(0, 19),
  }
}

// 관리자 새 쪽지 → POST /community-service/admin/notes → AdminNoteController.create
export async function sendAdminNote(noteRequest) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/admin/notes', { method: 'POST', body: noteRequest })
  const existing = ADMIN_NOTES.find((item) => item.participant.id === noteRequest.recipientUserId)
  const createdAt = new Date().toISOString().slice(0, 19)
  const threadId = existing?.id ?? Math.max(0, ...ADMIN_NOTES.map((item) => item.id)) + 1
  const noteId = Date.now()
  if (existing) {
    existing.status = 'ANSWERED'
    existing.lastContent = noteRequest.content
    existing.lastSentAt = createdAt
    existing.messages.push({ id: noteId, senderType: 'ADMIN', content: noteRequest.content, sentAt: createdAt, isRead: false })
  }
  return { threadId, noteId, createdAt }
}
