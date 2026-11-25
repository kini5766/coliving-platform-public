// 관리자 인증 배지 — 게시글·댓글 작성자의 관리자 표시
import { useTranslation } from "react-i18next"

export function VerifiedBadge() {
  const { t } = useTranslation("community")
  const label = t("board.verifiedManager")

  return <span
    className="inline-flex shrink-0 text-main"
    title={label}
    aria-label={label}
    role="img"
  >
    <svg
      width="17"
      height="17"
      viewBox="0 0 18 18"
      aria-hidden="true"
    >
      <circle cx="9" cy="9" r="8" fill="currentColor" />
      <path d="m5.4 9.1 2.2 2.2 5-5" fill="none" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  </span>
}
