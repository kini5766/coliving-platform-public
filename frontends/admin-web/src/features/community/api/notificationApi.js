// 알림 API — 목록·안 읽은 수·개별 읽음·모두 읽음
import { ADMIN_NOTES } from '../mocks'
import { request, USE_MOCK } from './communityClient'

// 알림 목록 → GET /community-service/notifications → NotificationController.getNotifications
export async function getNotifications(query = {}) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/notifications', { query })
  return { content: [], page: query.page ?? 0, size: query.size ?? 20, totalElements: 0, totalPages: 0 }
}

// 안 읽은 수 → GET /community-service/notifications/unread-summary → NotificationController.getUnreadSummary
export async function getUnreadSummary() {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/notifications/unread-summary')
  const note = ADMIN_NOTES.filter((item) => item.status === 'UNANSWERED').length
  return { note, chat: 0, notification: 0, total: 0 }
}

// 알림 읽음 → PATCH /community-service/notifications/{notificationId}/read → NotificationController.markRead
export async function readNotification(notificationId) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request(`/notifications/${encodeURIComponent(notificationId)}/read`, { method: 'PATCH' })
  return null
}

// 알림 모두 읽음 → PATCH /community-service/notifications/read-all → NotificationController.markAllRead
export async function readAllNotifications() {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/notifications/read-all', { method: 'PATCH' })
  return null
}
