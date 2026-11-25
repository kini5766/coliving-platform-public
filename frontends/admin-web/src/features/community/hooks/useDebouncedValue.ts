// 입력 지연 훅 — 검색어가 잠시 유지된 뒤 조회 조건에 반영
import { useEffect, useState } from "react"
export function useDebouncedValue<T>(value: T, delay = 300) {
  const [debounced, setDebounced] = useState(value)
  // 입력값이 바뀐 뒤 지정 시간만큼 유지되면 검색값 반영
  useEffect(() => { const timer = setTimeout(() => setDebounced(value), delay); return () => clearTimeout(timer) }, [value, delay])
  return debounced
}
