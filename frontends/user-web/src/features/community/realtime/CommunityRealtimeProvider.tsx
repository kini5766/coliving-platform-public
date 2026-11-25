// 실시간 연결 제공 — 세션에 맞춰 STOMP 연결과 구독 생명주기 관리
import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from "react"
import { useSession } from "../../../session"
import { USE_MOCK } from "../api/communityClient"
import { createCommunityRealtime, type ConnectionStatus } from "./realtimeClient"

type RealtimeValue = ReturnType<typeof createCommunityRealtime> & { status: ConnectionStatus }
const COMMUNITY_REALTIME_CONTEXT = createContext<RealtimeValue | null>(null)

export function CommunityRealtimeProvider({ children }: { children: ReactNode }) {
  const session = useSession()
  const identity = session.userId ?? null
  const [connection, setConnection] = useState<{ identity: typeof identity; status: ConnectionStatus }>({ identity, status: "disconnected" })
  const realtime = useMemo(() => createCommunityRealtime(String(import.meta.env.VITE_BACKEND_API_BASE_URL ?? ""), false, (status) => setConnection({ identity, status })), [identity])
  const status = connection.identity === identity ? connection.status : "disconnected"
  const authenticated = session.status === "ready" && session.authenticated
  // 세션의 연결 허용 상태가 바뀌면 STOMP를 시작하고 종료 시 구독 해제
  useEffect(() => {
    // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터 모드이므로 실시간 연결 생략
    if (!authenticated || USE_MOCK) return
    realtime.start()
    return () => { void realtime.stop() }
  }, [authenticated, realtime])
  const value = useMemo(() => ({ ...realtime, status: authenticated ? status : "disconnected" as const }), [realtime, authenticated, status])
  return <COMMUNITY_REALTIME_CONTEXT.Provider value={value}>{children}</COMMUNITY_REALTIME_CONTEXT.Provider>
}

export function useCommunityRealtime() {
  const context = useContext(COMMUNITY_REALTIME_CONTEXT)
  if (!context) throw new Error("CommunityRealtimeProvider 안에서 사용해 주세요.")
  return context
}

