// 커뮤니티 상수 — 신고 사유와 관리자 조치 표시 문구
import type { ReportAction, ReportReason, ReportTargetType, SanctionType } from "./types"

export const REPORT_REASON_LABELS: Record<ReportReason, string> = { ABUSE: "욕설·비방", HARASSMENT: "괴롭힘·스토킹", PRIVACY: "개인정보 노출", SPAM: "광고·도배", INAPPROPRIATE: "음란·부적절", HOUSE_RULE: "생활 규칙 위반", ETC: "기타" }
export const REPORT_ACTION_LABELS: Record<ReportAction, string> = { REJECT: "반려", HIDE_CONTENT: "숨김", DELETE_CONTENT: "삭제", WARNING: "경고", RESTRICTION: "이용 제한", SUSPENSION: "커뮤니티 정지", OFFLINE_RECORD: "매니저 면담·계약 검토 기록" }
export const SANCTION_TYPE_LABELS: Record<SanctionType, string> = { WARNING: "경고", RESTRICTION: "기간 이용 제한 1·3·7·30일", SUSPENSION: "해제 전까지 정지" }
export const REPORT_TARGET_LABELS: Record<ReportTargetType, string> = { POST: "게시글", COMMENT: "댓글", CHAT_MESSAGE: "채팅 메시지", USER: "사용자" }
