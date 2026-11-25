// 내 제재 상태 — 제재 API 조회와 서버 오류·알림 이벤트 반영
import { useCallback, useEffect, useState } from "react"
import { getMySanction } from "../api"
import { useCommunityViewer } from "../useCommunityViewer"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import { CommunityApiError } from "../api/communityClient"
import type { MySanction } from "../types"
const EMPTY_SANCTION: MySanction = {
  active: false,
  sanctionId: null,
  sanctionType: null,
  durationDays: null,
  reason: null,
  startsAt: null,
  endsAt: null
}
// 제재 API와 SANCTIONED 이벤트를 합쳐 입력 제한·배너 상태 제공
export function useMySanction(enabled = true) {
  const viewer = useCommunityViewer()
  const canLoad = enabled && viewer.isLoggedIn && viewer.isResident && !viewer.isManager
  // loadSanction — GET /sanctions/me
  const loadSanction = useCallback(() => getMySanction(), [])
  const { data, loading, error, refetch } = useCommunityQuery(loadSanction, { enabled: canLoad })
  const [serverSanction, setServerSanction] = useState<MySanction | null>(null)
  // 제재·화면 복귀 이벤트를 받아 내 제재를 재조회하고 즉시 배너 반영
  useEffect(() => {
    if (!canLoad) return
    // handleRefresh — GET /sanctions/me
    const handleRefresh = () => { setServerSanction(null); void refetch() }
    // handleSanctioned — GET /sanctions/me
    const handleSanctioned = (event: Event) => {
      const error = (event as CustomEvent<CommunityApiError>).detail
      setServerSanction({ ...EMPTY_SANCTION, active: true, sanctionType: error.body?.sanctionType ?? "RESTRICTION", endsAt: error.body?.endsAt ?? null, reason: error.detail })
      void refetch()
    }
    window.addEventListener("community:sanctioned", handleSanctioned)
    window.addEventListener("community:sanction-refresh", handleRefresh)
    window.addEventListener("focus", handleRefresh)
    return () => {
      window.removeEventListener("community:sanctioned", handleSanctioned)
      window.removeEventListener("community:sanction-refresh", handleRefresh)
      window.removeEventListener("focus", handleRefresh)
    }
  }, [canLoad, refetch])
  const sanction = canLoad ? serverSanction ?? data ?? EMPTY_SANCTION : EMPTY_SANCTION
  return { ...sanction, loading: canLoad && loading, error: canLoad ? error : null }
}
