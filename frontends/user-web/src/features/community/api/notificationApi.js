// 알림 API — 목록·안 읽은 수·개별 읽음·모두 읽음
import { UNREAD_NOTIFICATION_SUMMARY, USER_NOTES, USER_NOTIFICATIONS } from '../mocks'
import { request, USE_MOCK } from './communityClient'

// 가짜 응답을 복제해 화면 수정이 원본 데이터에 섞이지 않도록 처리
const copy = (value) => structuredClone(value)

// 가짜 알림 목록에서 안 읽은 종류별 숫자 계산
function mockSummary() {
  const notification = USER_NOTIFICATIONS.content.filter((item) => !item.read).length
  const note = USER_NOTES.unreadCount
  return { ...UNREAD_NOTIFICATION_SUMMARY, note, notification, total: note + UNREAD_NOTIFICATION_SUMMARY.chat }
}

// 알림 목록 → GET /community-service/notifications → NotificationController.getNotifications
export async function getNotifications(query = {}) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/notifications', { query })
  let content = USER_NOTIFICATIONS.content
  if (query.unreadOnly === true) content = content.filter((item) => !item.read)
  return copy({ ...USER_NOTIFICATIONS, content })
}

// 안 읽은 수 → GET /community-service/notifications/unread-summary → NotificationController.getUnreadSummary
export async function getUnreadSummary() {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/notifications/unread-summary')
  return copy(mockSummary())
}

// 알림 읽음 → PATCH /community-service/notifications/{notificationId}/read → NotificationController.markRead
export async function readNotification(notificationId) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request(`/notifications/${encodeURIComponent(notificationId)}/read`, { method: 'PATCH' })
  const item = USER_NOTIFICATIONS.content.find((notification) => notification.notificationId === notificationId)
  if (item) item.read = true
  return null
}

// 알림 모두 읽음 → PATCH /community-service/notifications/read-all → NotificationController.markAllRead
export async function readAllNotifications() {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/notifications/read-all', { method: 'PATCH' })
  USER_NOTIFICATIONS.content.forEach((item) => { item.read = true })
  return null
}
