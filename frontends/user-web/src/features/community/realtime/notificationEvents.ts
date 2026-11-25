// 알림 이벤트 병합 — 알림 ID·채팅방 기준 중복 제거
import type { UserNotification } from "../types"

// 동일 알림 ID와 채팅방의 알림을 최신 항목으로 병합
export function mergeNotification(items: UserNotification[], incoming: UserNotification) {
  return [incoming, ...items.filter((item) => item.notificationId !== incoming.notificationId && !(incoming.type === "CHAT" && item.type === "CHAT" && !item.read && String(item.targetId) === String(incoming.targetId)))]
}
