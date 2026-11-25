// 공통 조회 훅 — 로딩·오류·재조회와 오래된 응답 무효화
import { useCallback, useEffect, useRef, useState } from 'react'

// 최근 요청만 반영하며 재조회 동안 기존 data 유지
export function useCommunityQuery(queryFn, { enabled = true, initialData = null } = {}) {
  const [data, setData] = useState(initialData)
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState(null)
  const requestIdRef = useRef(0)

  const refetch = useCallback(async () => {
    if (!enabled) return null
    const requestId = ++requestIdRef.current
    setLoading(true)
    setError(null)
    try {
      const result = await queryFn()
      if (requestId !== requestIdRef.current) return null
      setData(result)
      return result
    } catch (caught) {
      if (requestId === requestIdRef.current) setError(caught)
      return null
    } finally {
      if (requestId === requestIdRef.current) setLoading(false)
    }
  }, [enabled, queryFn])

  // 조회 함수·활성 여부가 바뀌면 실행하고 이전 응답 무효화
  useEffect(() => {
    void refetch()
    return () => { requestIdRef.current += 1 }
  }, [refetch])

  return { data, loading, error, refetch, setData }
}
