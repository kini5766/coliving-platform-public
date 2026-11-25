// STOMP 클라이언트 — 쿠키 인증·재연결·알림과 채팅 구독·메시지 전송
import { Client } from "@stomp/stompjs"

export type RealtimeEvent = Record<string, any>
export type ConnectionStatus = "connecting" | "connected" | "disconnected"
type Listener = (event: RealtimeEvent) => void

// Gateway HTTP 주소를 community-service/ws의 WS·WSS 주소로 변환
export function communityWebSocketUrl(base: string) {
  const url = new URL(base || window.location.origin, window.location.origin)
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:"
  url.pathname = url.pathname.replace(/\/$/, "") + "/community-service/ws"
  url.search = ""
  url.hash = ""
  return url.toString()
}

// STOMP 연결·재연결·종류별 구독과 전송 기능 제공
export function createCommunityRealtime(base: string, admin: boolean, onStatus: (status: ConnectionStatus) => void) {
  const listeners = { notification: new Set<Listener>(), chat: new Set<Listener>(), chatEvent: new Set<Listener>(), error: new Set<Listener>() }
  const emit = (kind: keyof typeof listeners, event: RealtimeEvent) => listeners[kind].forEach((listener) => listener(event))
  const subscribe = (kind: keyof typeof listeners) => (listener: Listener) => {
    listeners[kind].add(listener)
    return () => { listeners[kind].delete(listener) }
  }
  let active = false
  const client = new Client({
    brokerURL: communityWebSocketUrl(base),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    connectionTimeout: 10000,
    // Browser WebSocket handshake carries the HttpOnly session cookie.
    connectHeaders: {},
    beforeConnect: () => { if (active) onStatus("connecting") },
    onConnect: () => {
      if (!active) return
      const destinations: [string, keyof typeof listeners][] = [
        // /user/queue/notifications — 개인 알림 — NotificationPushListener.onNotificationCreated가 커밋 후 전송
        ["/user/queue/notifications", "notification"],
        // /user/queue/chat — 새 메시지 — ChatMessagePushListener.onMessageSent가 커밋 후 전송
        ["/user/queue/chat", "chat"],
        // /user/queue/chat-events — 상대 나가기 — ChatMessagePushListener.onRoomLeft가 커밋 후 전송
        ["/user/queue/chat-events", "chatEvent"],
        // /user/queue/errors — 전송 오류 — ChatWebSocketController.handleCommunityException 등 예외 처리
        ["/user/queue/errors", "error"]
      ]
      // /topic/admin/notifications — 관리자 공용 알림 — NotificationPushListener.onNotificationCreated가 전송
      if (admin) destinations.push(["/topic/admin/notifications", "notification"])
      destinations.forEach(([destination, kind]) => client.subscribe(destination, (frame) => {
        if (!active) return
        let event: RealtimeEvent
        try { event = JSON.parse(frame.body) } catch { return }
        if (event && typeof event === "object") emit(kind, event)
      }))
      onStatus("connected")
    },
    onWebSocketClose: () => { if (active) onStatus("disconnected") },
    onWebSocketError: () => { if (active) onStatus("disconnected") },
    onDisconnect: () => { if (active) onStatus("disconnected") },
    onStompError: (frame) => {
      if (!active) return
      emit("error", { code: "STOMP_ERROR", message: frame.headers.message || "실시간 연결을 확인해 주세요." })
      onStatus("disconnected")
      client.forceDisconnect()
    }
  })
  return {
    onNotification: subscribe("notification"),
    onChatMessage: subscribe("chat"),
    onChatEvent: subscribe("chatEvent"),
    onError: subscribe("error"),
    start: () => { active = true; onStatus("connecting"); client.activate() },
    stop: () => { active = false; onStatus("disconnected"); return client.deactivate() },
    sendChatMessage: (roomId: string | number, content: string) => {
      if (!active || !client.connected) return false
      try {
        // STOMP /app/chat/{roomId} → ChatWebSocketController.send; 끊기면 화면에서 REST 사용
        client.publish({ destination: `/app/chat/${roomId}`, body: JSON.stringify({ content }) })
        return true
      } catch { return false }
    }
  }
}

