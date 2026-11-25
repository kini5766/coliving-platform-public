// 조회 상태 안내 — 로딩·권한 오류·서버 오류·재시도 표시
import { useLocation, useNavigate } from "react-router"
import { CommunityApiError } from "../api/communityClient"
import { Button } from "."
import { CommunityAccessMessage } from "./CommunityGate"

export function CommunityQueryState({ loading = false, error, onRetry }: { loading?: boolean; error?: unknown; onRetry?: () => unknown }) {
  const navigate = useNavigate()
  const location = useLocation()
  if (error instanceof CommunityApiError && error.status === 401) return <CommunityAccessMessage
    message="로그인 후 이용할 수 있어요"
    action={<Button onClick={() => navigate("/login", { state: { from: location.pathname + location.search } })}>로그인 화면으로</Button>}
  />
  if (error instanceof CommunityApiError && error.code === "NOT_RESIDENT") return <CommunityAccessMessage message="입주자만 이용할 수 있어요" />
  if (!loading && !error) return null
  return <div role={error ? "alert" : "status"} className="rounded-lg border border-main/15 bg-white p-4 text-center text-sm text-text/70">
    <p>{error instanceof Error ? error.message : loading ? "불러오는 중이에요." : "요청을 처리하지 못했어요."}</p>
    {/* 다시 불러오기 버튼 — onRetry (화면 상태·상위 콜백 반영) */}
    {error && onRetry && <button className="mt-2 font-semibold text-main" onClick={() => void onRetry()}>다시 불러오기</button>}
  </div>
}
