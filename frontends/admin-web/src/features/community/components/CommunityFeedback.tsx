// 관리자 공통 피드백 — 조회 상태·토스트·확인 창·페이지 이동
import { useCallback, useEffect, useRef, useState } from "react"
import { createPortal } from "react-dom"
import { Button } from "."

export function CommunityQueryState({ loading, error, onRetry }: { loading?: boolean; error?: unknown; onRetry?: () => unknown }) {
  if (!loading && !error) return null
  return <div role={error ? "alert" : "status"} className="my-3 rounded-lg border border-main/15 bg-white p-3 text-sm text-text/70">
    {error instanceof Error ? error.message : loading ? "불러오는 중…" : "요청을 처리하지 못했어요."}
    {/* 다시 시도 버튼 — onRetry (화면 상태·상위 콜백 반영) */}
    {error && onRetry && <button onClick={() => void onRetry()} className="ml-3 font-semibold text-main">다시 시도</button>}
  </div>
}
export function useCommunityToast() {
  const [toast, setToast] = useState("")
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  // 토스트 훅을 해제할 때 자동 닫기 타이머 정리
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current) }, [])
  const showToast = useCallback((message: string) => {
    if (timer.current) clearTimeout(timer.current)
    setToast(message)
    timer.current = setTimeout(() => setToast(""), 4000)
  }, [])
  return { toast, showToast }
}
export function CommunityToast({ message }: { message: string }) {
  return message ? <div role="status" className="fixed right-6 bottom-6 z-[70] max-w-md rounded-lg bg-main px-5 py-3 text-sm text-white shadow-lg">{message}</div> : null
}
export function ConfirmDialog({ title, description, onClose, onConfirm }: { title: string; description: string; onClose: () => void; onConfirm: () => Promise<void> }) {
  const [submitting, setSubmitting] = useState(false)
  // 내용 영역 — 상위 조회 결과·선택 상태 표시
  // 취소 버튼 — onClose (화면 상태·상위 콜백 반영)
  // 확인 버튼 — setSubmitting (화면 상태·상위 콜백 반영)
  return createPortal(<div className="fixed inset-0 z-[60] flex items-center justify-center bg-text/40 p-4"><section role="dialog" aria-modal="true" className="w-full max-w-md rounded-lg bg-white p-6 text-text"><h2 className="text-lg font-semibold text-main">{title}</h2><p className="mt-3 text-sm">{description}</p><footer className="mt-5 flex justify-end gap-2"><Button disabled={submitting} variant="secondary" onClick={onClose}>취소</Button><Button disabled={submitting} onClick={async () => { if (submitting) return; setSubmitting(true); try { await onConfirm() } finally { setSubmitting(false) } }}>확인</Button></footer></section></div>, document.body)
}
export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  // 이전 버튼 — onChange (화면 상태·상위 콜백 반영)
  // 다음 버튼 — onChange (화면 상태·상위 콜백 반영)
  return <footer className="flex items-center justify-center gap-3 border-t border-main/15 p-3 text-sm"><Button variant="ghost" disabled={page === 0} onClick={() => onChange(page - 1)}>이전</Button><span>{totalPages ? page + 1 : 0} / {totalPages}</span><Button variant="ghost" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>다음</Button></footer>
}
