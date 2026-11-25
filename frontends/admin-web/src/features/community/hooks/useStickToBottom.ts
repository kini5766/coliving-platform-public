// 대화 스크롤 훅 — 하단 유지·방 변경 초기화·새 메시지 안내
import { useCallback, useLayoutEffect, useRef, useState } from "react"

const BOTTOM_THRESHOLD = 48

type StickToBottomOptions = {
  active?: boolean;
  contentKey: string;
  forceScrollKey?: number;
  resetKey?: string | number | null
}

// 하단 근처일 때만 자동 스크롤하고 위에서 읽는 중이면 새 항목 안내
export function useStickToBottom({ active = true, contentKey, forceScrollKey = 0, resetKey = null }: StickToBottomOptions) {
  const containerRef = useRef<HTMLDivElement>(null)
  const nearBottomRef = useRef(true)
  const previousContentKeyRef = useRef(contentKey)
  const previousForceScrollKeyRef = useRef(forceScrollKey)
  const previousResetKeyRef = useRef(resetKey)
  const wasActiveRef = useRef(false)
  const [hasNewItems, setHasNewItems] = useState(false)

  const scrollToBottom = useCallback((behavior: ScrollBehavior = "auto") => {
    const container = containerRef.current
    if (!container) return
    container.scrollTo({ top: container.scrollHeight, behavior })
    nearBottomRef.current = true
    setHasNewItems(false)
  }, [])

  const handleScroll = useCallback(() => {
    const container = containerRef.current
    if (!container) return
    const nearBottom = container.scrollHeight - container.scrollTop - container.clientHeight <= BOTTOM_THRESHOLD
    nearBottomRef.current = nearBottom
    if (nearBottom) setHasNewItems(false)
  }, [])

  // 대화·방·강제 이동 키가 바뀌면 하단 위치와 새 항목 안내를 화면 그리기 전에 반영
  useLayoutEffect(() => {
    if (!active) {
      wasActiveRef.current = false
      return
    }
    const opened = !wasActiveRef.current
    const reset = previousResetKeyRef.current !== resetKey
    const forced = previousForceScrollKeyRef.current !== forceScrollKey
    const contentChanged = previousContentKeyRef.current !== contentKey

    if (opened || reset || forced || (contentChanged && nearBottomRef.current)) scrollToBottom()
    else if (contentChanged) setHasNewItems(true)

    wasActiveRef.current = true
    previousContentKeyRef.current = contentKey
    previousForceScrollKeyRef.current = forceScrollKey
    previousResetKeyRef.current = resetKey
  }, [active, contentKey, forceScrollKey, resetKey, scrollToBottom])

  return { containerRef, handleScroll, hasNewItems, scrollToBottom }
}
