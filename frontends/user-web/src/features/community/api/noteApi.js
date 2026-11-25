// 사용자 쪽지 API — 대화 목록·전송·읽음
import { USER_NOTES } from '../mocks'
import { request, USE_MOCK } from './communityClient'

// 가짜 응답을 복제해 화면 수정이 원본 데이터에 섞이지 않도록 처리
const copy = (value) => structuredClone(value)
let nextNoteId = Math.max(0, ...USER_NOTES.content.map((note) => note.noteId)) + 1

// 사용자 쪽지 목록 → GET /community-service/notes → NoteController.getMyNotes
export async function getNotes(query = {}) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/notes', { query })
  return copy(USER_NOTES)
}

// 사용자 쪽지 보내기 → POST /community-service/notes → NoteController.send
export async function sendNote(noteRequest) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/notes', { method: 'POST', body: noteRequest })
  const createdAt = new Date().toISOString().slice(0, 19)
  const response = { noteId: nextNoteId++, createdAt }
  USER_NOTES.content.push({
    ...response,
    senderType: 'USER',
    senderName: '김민서',
    content: noteRequest.content,
    readAt: null,
  })
  USER_NOTES.totalElements = USER_NOTES.content.length
  return copy(response)
}

// 쪽지 모두 읽음 → PATCH /community-service/notes/read → NoteController.readAll
export async function readNotes() {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/notes/read', { method: 'PATCH' })
  const readAt = new Date().toISOString().slice(0, 19)
  USER_NOTES.content.forEach((note) => {
    if (note.senderType === 'ADMIN' && !note.readAt) note.readAt = readAt
  })
  USER_NOTES.unreadCount = 0
  return null
}
