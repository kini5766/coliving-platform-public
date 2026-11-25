// 관리자 메뉴 배지 — 알림·화면 복귀·연결 중단 시 숫자 갱신
import { useEffect, useState } from "react"
import { navigation } from "../../../navigation"
import { useCommunityRealtime } from "../realtime/CommunityRealtimeProvider"

type BadgeCounts = Record<string, number>

export function useNavBadges(): BadgeCounts {
  const [counts, setCounts] = useState<BadgeCounts>({})
  const { status, onNotification } = useCommunityRealtime()
  const connected = status === "connected"

  // 연결·알림·화면 복귀에 메뉴 배지를 조회, 미연결 시 30초마다 갱신
  useEffect(() => {
    let active = true
    const items = navigation.flatMap((section) => section.items).filter((item) => typeof item.badge === "function")
    const handleRefresh = async () => {
      const results = await Promise.allSettled(items.map(async (item) => [item.to, await item.badge!()] as const))
      if (!active) return
      setCounts(Object.fromEntries(results.flatMap((result) => result.status === "fulfilled" ? [result.value] : [])))
    }
    const handleRefreshEvent = () => {
      void handleRefresh()
    }
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") void handleRefresh()
    }
    void handleRefresh()
    const interval = connected ? null : window.setInterval(handleRefresh, 30_000)
    const unsubscribe = onNotification(() => void handleRefresh())
    window.addEventListener("admin-badge-refresh", handleRefreshEvent)
    document.addEventListener("visibilitychange", handleVisibilityChange)
    return () => {
      active = false;
      if (interval !== null) window.clearInterval(interval);
      unsubscribe()
      window.removeEventListener("admin-badge-refresh", handleRefreshEvent)
      document.removeEventListener("visibilitychange", handleVisibilityChange)
    }
  }, [connected, onNotification])

  return counts
}
