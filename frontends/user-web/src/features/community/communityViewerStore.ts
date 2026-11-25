// 커뮤니티 사용자 저장소 — 세션별 회원 조회·캐시·구독·재시도
import { CommunityApiError } from "./api/communityClient"
import { getCommunityMe, type CommunityMe } from "./api/viewerApi"

type ViewerSnapshot = {
  userKey: string | null;
  status: "idle" | "loading" | "ready" | "error";
  data: CommunityMe | null;
  error: unknown;
  unauthorized: boolean
}
type SessionIdentity = { status: string; authenticated: boolean; userId: number | string | null }

const EMPTY_VIEWER: ViewerSnapshot = { userKey: null, status: "idle", data: null, error: null, unauthorized: false }
const VIEWER_LISTENERS = new Set<() => void>()
let snapshot = EMPTY_VIEWER
let requestGeneration = 0
let pendingRequest: Promise<void> | null = null

function publish(next: ViewerSnapshot) {
  snapshot = next
  VIEWER_LISTENERS.forEach((listener) => listener())
}

function clearViewer() {
  requestGeneration += 1
  pendingRequest = null
  publish(EMPTY_VIEWER)
}

// loadViewer — GET /me
function loadViewer(userKey: string) {
  if (snapshot.userKey === userKey && pendingRequest) return pendingRequest
  const generation = ++requestGeneration
  publish({ ...EMPTY_VIEWER, userKey, status: "loading" })
  pendingRequest = getCommunityMe().then((data) => {
    if (generation === requestGeneration) publish({ ...EMPTY_VIEWER, userKey, status: "ready", data })
  }).catch((error: unknown) => {
    if (generation === requestGeneration) publish({ ...EMPTY_VIEWER, userKey, status: "error", error, unauthorized: error instanceof CommunityApiError && error.status === 401 })
  }).finally(() => {
    if (generation === requestGeneration) pendingRequest = null
  })
  return pendingRequest
}

export function syncCommunityViewerSession(session: SessionIdentity) {
  if (session.status !== "ready") return
  if (!session.authenticated) {
    if (snapshot !== EMPTY_VIEWER) clearViewer()
    return
  }
  const userKey = String(session.userId ?? "authenticated")
  if (snapshot.userKey !== userKey || snapshot.status === "idle") void loadViewer(userKey)
}

export function retryCommunityViewer() {
  return snapshot.userKey ? loadViewer(snapshot.userKey) : Promise.resolve()
}

export function getCommunityViewerSnapshot() {
  return snapshot
}

export function subscribeCommunityViewer(listener: () => void) {
  VIEWER_LISTENERS.add(listener)
  return () => { VIEWER_LISTENERS.delete(listener) }
}

// 공통 세션 갱신 이벤트를 한 번만 구독한다. 로그아웃 전 요청은 세대 번호로 무효화한다.
if (typeof window !== "undefined") {
  // handleSessionRefresh — GET /me
  const handleSessionRefresh = (event: Event) => {
    const signedOut = (event as CustomEvent<{ signedOut?: boolean }>).detail?.signedOut
    const userKey = snapshot.userKey
    clearViewer()
    if (!signedOut && userKey) void loadViewer(userKey)
  }
  window.addEventListener("coliving-session-refresh", handleSessionRefresh)
  if (import.meta.hot) import.meta.hot.dispose(() => window.removeEventListener("coliving-session-refresh", handleSessionRefresh))
}
