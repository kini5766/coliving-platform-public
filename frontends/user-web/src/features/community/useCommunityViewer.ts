// 커뮤니티 사용자 훅 — 세션 동기화와 공유 권한 상태 구독
import { useEffect, useSyncExternalStore } from "react"
import { useSession } from "../../session"
import { getCommunityViewerSnapshot, retryCommunityViewer, subscribeCommunityViewer, syncCommunityViewerSession } from "./communityViewerStore"

export function useCommunityViewer() {
  const session = useSession()
  const snapshot = useSyncExternalStore(subscribeCommunityViewer, getCommunityViewerSnapshot)
  const { status, authenticated, userId } = session

  // 세션 식별자가 바뀌면 공유 커뮤니티 사용자 상태 동기화
  useEffect(() => {
    syncCommunityViewerSession({ status, authenticated, userId })
  }, [status, authenticated, userId])

  const authenticatedSession = status === "ready" && authenticated
  const matchesSession = snapshot.userKey === String(userId ?? "authenticated")
  const data = authenticatedSession && matchesSession ? snapshot.data : null
  const unauthorized = matchesSession && snapshot.unauthorized

  return {
    loading: status === "loading" || (authenticatedSession && (!matchesSession || snapshot.status === "idle" || snapshot.status === "loading")),
    isLoggedIn: authenticatedSession && !unauthorized,
    isResident: data?.known === true && data.resident === true,
    isManager: data?.known === true && data.manager === true,
    known: data?.known ?? null,
    userId: data?.userId ?? userId ?? 0,
    name: data?.name ?? "",
    buildingId: data?.buildingId ?? null,
    buildingName: data?.buildingName ?? "",
    roomNumber: data?.roomNumber ?? "",
    error: authenticatedSession && matchesSession ? snapshot.error : null,
    retry: retryCommunityViewer
  }
}
