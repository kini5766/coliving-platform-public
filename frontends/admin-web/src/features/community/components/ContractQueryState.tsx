// 계약 조회 상태 — 403 관리자 안내와 네트워크·서버 오류 재시도
import { CommunityApiError } from "../api/communityClient"
import { CommunityQueryState } from "./CommunityFeedback"

export function ContractQueryState({ loading, error, onRetry }: { loading?: boolean; error?: unknown; onRetry: () => unknown }) {
  const forbidden = error instanceof CommunityApiError && Number(error.status) === 403
  return <CommunityQueryState loading={loading} error={forbidden ? new Error("관리자만 볼 수 있어요") : error} onRetry={forbidden ? undefined : onRetry} />
}
