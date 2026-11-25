// 커뮤니티 API 모음 — 화면 요청·Gateway 경로 연결·개발용 가짜 응답
import { ADMIN_NOTES, ADMIN_REPORT_DETAILS, ADMIN_REPORTS, ADMIN_SANCTIONS, FAQ_ANSWERS_BY_BUILDING, FAQ_DOCUMENTS, FAQ_FALLBACK_ANSWER, FAQ_INDEX_SUMMARY, FAQ_REINDEX_COMPLETED, FAQ_REINDEX_STARTED, UPLOADED_FAQ_DOCUMENT } from "../mocks"
import type { AdminReportDetail, AdminReportFilter, AdminReportListResponse, AdminSanction, AdminSanctionFilter, AdminSanctionListResponse, FaqAskRequest, FaqDocumentUploadRequest, NoteParticipant, ReportActionRequest, ReportActionResponse, SanctionType } from "../types"
import { getUnreadSummary } from "./notificationApi"
import { CommunityApiError, request as apiRequest, USE_MOCK } from "./communityClient"

// 가짜 응답을 복제해 화면 수정이 원본 데이터에 섞이지 않도록 처리
const copy = <T,>(value: T): T => structuredClone(value)
// 가짜 데이터의 생성·변경 시간을 한국 시간 문자열로 만들기
const now = () => new Date().toLocaleString("sv-SE", { timeZone: "Asia/Seoul" }).replace(" ", "T")
const MOCK_BUILDINGS = [{ buildingId: 1, name: "망원 스테이" }, { buildingId: 2, name: "연남 하우스" }, { buildingId: 3, name: "성수 로프트" }]
const MOCK_FAQ_JOBS = new Map<string | number, number[]>()
let nextFaqJobId = 10
let activeMockFaqJob: number | null = null

// 지점 목록 → GET /community-service/admin/buildings → AdminBoardBuildingController.getBuildings
export async function getCommunityBuildings() {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/admin/buildings")
  return copy(MOCK_BUILDINGS)
}

export { getContractResidents as getResidents, getContractResidentDetail as getResidentDetail, getMoveSchedules } from "./residentApi"
// GET /notifications/unread-summary의 쪽지 수 → NotificationController.getUnreadSummary
export async function getAdminNoteBadgeCount(): Promise<number> {
  return (await getUnreadSummary()).note
}
// 수신자 가짜 검색만 제공 — 회원 검색 API는 아직 미구현
export async function searchNoteRecipients(query: string): Promise<NoteParticipant[]> {
  return copy(ADMIN_NOTES.map((note) => note.participant).filter((participant) => participant.id && participant.name.includes(query)))
}

// FAQ 문서 목록 → GET /community-service/admin/faq-documents → AdminFaqDocumentController.getDocuments
export async function getFaqDocuments(query: { scope?: string; buildingId?: number } = {}) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/admin/faq-documents", { query })
  const documents = FAQ_DOCUMENTS.map((item) => ({ ...item, buildingId: item.buildingId ?? MOCK_BUILDINGS.find((building) => building.name === item.buildingName)?.buildingId ?? null }))
    .filter((item) => (!query.scope || item.scope === query.scope) && (!query.buildingId || item.buildingId === query.buildingId))
  return copy({ summary: { lastIndexedAt: FAQ_INDEX_SUMMARY.lastReflectedAt || null, indexedCount: FAQ_DOCUMENTS.filter((item) => item.indexStatus === "COMPLETED").length, pendingCount: FAQ_DOCUMENTS.filter((item) => item.indexStatus === "PENDING" || item.indexStatus === "FAILED").length }, documents })
}
// GET /admin/faq-documents의 요약 → AdminFaqDocumentController.getDocuments
export async function getFaqIndexSummary() {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return (await getFaqDocuments()).summary
  return copy(FAQ_INDEX_SUMMARY)
}
// FAQ 문서 업로드 → POST /community-service/admin/faq-documents → AdminFaqDocumentController.upload
export async function uploadFaqDocument(body: FaqDocumentUploadRequest) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) {
    const form = new FormData()
    form.append("file", body.file); form.append("title", body.title); form.append("scope", body.scope)
    if (body.scope === "BUILDING" && body.buildingId !== undefined) form.append("buildingId", String(body.buildingId))
    return apiRequest("/admin/faq-documents", { method: "POST", body: form })
  }
  const documentId = Math.max(UPLOADED_FAQ_DOCUMENT.documentId - 1, ...FAQ_DOCUMENTS.map((item) => item.documentId)) + 1
  FAQ_DOCUMENTS.push({ documentId, title: body.title, scope: body.scope, buildingId: body.buildingId ?? null, buildingName: MOCK_BUILDINGS.find((building) => building.buildingId === body.buildingId)?.name ?? null, fileName: body.file.name, fileSize: body.file.size, fileType: body.file.name.split(".").at(-1)!.toUpperCase() as "PDF", uploadedAt: now(), indexStatus: "PENDING", indexError: null })
  return { ...copy(UPLOADED_FAQ_DOCUMENT), documentId, uploadedAt: now() }
}
// FAQ 문서 삭제 → DELETE /community-service/admin/faq-documents/{documentId} → AdminFaqDocumentController.delete
export async function deleteFaqDocument(documentId: number): Promise<void> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/admin/faq-documents/${documentId}`, { method: "DELETE" })
  const index = FAQ_DOCUMENTS.findIndex((item) => item.documentId === documentId)
  if (index >= 0) FAQ_DOCUMENTS.splice(index, 1)
}
// FAQ 문서 다운로드 → GET /community-service/admin/faq-documents/{documentId}/file → AdminFaqDocumentController.download
export async function downloadFaqDocument(documentId: number) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/admin/faq-documents/${documentId}/file`, { responseType: "blob" })
  const document = FAQ_DOCUMENTS.find((item) => item.documentId === documentId);
  return document ? { blob: new Blob([document.title], { type: "text/plain" }), fileName: document.fileName } : null
}
// FAQ 문서 다시 반영 → POST /community-service/admin/faq-documents/reindex → AdminFaqDocumentController.reindex
export async function reindexFaqDocuments(documentIds?: number[]) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/admin/faq-documents/reindex", { method: "POST", body: documentIds ? { documentIds } : {} })
  if (activeMockFaqJob !== null) throw new CommunityApiError(409, "REINDEX_IN_PROGRESS", "이미 반영 중이에요")
  const ids = documentIds ?? FAQ_DOCUMENTS.filter((document) => document.indexStatus !== "COMPLETED").map((document) => document.documentId)
  if (!ids.length) throw new CommunityApiError(400, "INVALID_REQUEST", "반영할 문서가 없어요")
  const jobId = nextFaqJobId++
  activeMockFaqJob = jobId
  MOCK_FAQ_JOBS.set(jobId, ids)
  FAQ_DOCUMENTS.forEach((document) => { if (ids.includes(document.documentId)) document.indexStatus = "PROCESSING" })
  return { ...copy(FAQ_REINDEX_STARTED), jobId }
}
// FAQ 반영 진행 조회 → GET /community-service/admin/faq-documents/reindex/{jobId} → AdminFaqDocumentController.getJob
export async function getFaqReindexJob(jobId: string | number, signal?: AbortSignal) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/admin/faq-documents/reindex/${jobId}`, { signal })
  const ids = MOCK_FAQ_JOBS.get(jobId) ?? []
  activeMockFaqJob = null
  FAQ_DOCUMENTS.forEach((document) => { if (ids.includes(document.documentId)) { document.indexStatus = "COMPLETED"; document.indexError = null } })
  FAQ_INDEX_SUMMARY.lastReflectedAt = now()
  return { ...copy(FAQ_REINDEX_COMPLETED), processedCount: ids.length, totalCount: ids.length, errorMessage: null }
}
// FAQ 질문 → POST /community-service/faq-bot/ask → FaqBotController.ask
export async function askFaqBot(body: FaqAskRequest) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/faq-bot/ask", { method: "POST", body })
  const unknown = /수영장|헬스장|택배 보관|문서에 없는/.test(body.question);
  const answer = copy(unknown ? FAQ_FALLBACK_ANSWER : FAQ_ANSWERS_BY_BUILDING[body.buildingId ?? 1] ?? FAQ_ANSWERS_BY_BUILDING[1])
  return { ...answer, answered: !answer.fallback, actions: [] }
}
// FAQ 질문 기록 → GET /community-service/admin/faq-bot/logs → AdminFaqLogController.getLogs
export async function getFaqQuestionLogs(query: { answered?: boolean; buildingId?: number; from?: string; to?: string; page?: number; size?: number } = {}) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/admin/faq-bot/logs", { query })
  return { summary: { totalCount: 0, unansweredCount: 0, cachedCount: 0, avgLatencyMs: 0 }, content: [], page: query.page ?? 0, size: query.size ?? 20, totalElements: 0, totalPages: 0 }
}

// 가짜 신고 목록에서 처리 상태별 개수 계산
const reportSummary = () => ({
  receivedCount: ADMIN_REPORTS.content.filter((item) => item.status === "RECEIVED").length,
  resolvedCount: ADMIN_REPORTS.content.filter((item) => item.status === "RESOLVED").length,
  rejectedCount: ADMIN_REPORTS.content.filter((item) => item.status === "REJECTED").length
})
// 미처리 신고 수 → GET /community-service/admin/reports → AdminReportController.getReports
export async function getAdminReportBadgeCount(): Promise<number> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return (await apiRequest("/admin/reports", { query: { status: "RECEIVED", page: 0, size: 1 } })).summary.receivedCount
  return reportSummary().receivedCount
}
// 신고 목록 → GET /community-service/admin/reports → AdminReportController.getReports
export async function getAdminReports(filter: AdminReportFilter = {}): Promise<AdminReportListResponse> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/admin/reports", { query: filter })
  let content = ADMIN_REPORTS.content.filter((item) => item.status === (filter.status ?? "RECEIVED"))
  if (filter.sourceType) content = content.filter((item) => item.sourceType === filter.sourceType)
  if (filter.targetType) content = content.filter((item) => item.targetType === filter.targetType)
  if (filter.buildingId) content = content.filter((item) => item.buildingId === filter.buildingId)
  content.sort((a, b) => filter.sort === "count" ? b.reportCount - a.reportCount : b.latestReportedAt.localeCompare(a.latestReportedAt))
  const page = filter.page ?? 0
  const size = filter.size ?? 20
  return copy({
    ...ADMIN_REPORTS,
    summary: reportSummary(),
    content: content.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: content.length,
    totalPages: content.length ? Math.ceil(content.length / size) : 0
  })
}
// 신고 상세 → GET /community-service/admin/reports/{reportId} → AdminReportController.getReport
export async function getAdminReportDetail(reportId: number): Promise<AdminReportDetail | undefined> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/admin/reports/${reportId}`)
  const saved = ADMIN_REPORT_DETAILS[reportId]
  if (saved) return copy(saved)
  const item = ADMIN_REPORTS.content.find((report) => report.reportId === reportId)
  if (!item) return undefined
  return copy({
    target: {
      targetType: item.targetType,
      targetId: item.targetId,
      sourceType: item.sourceType,
      sourceId: item.sourceId,
      sourceTitle: item.sourceTitle,
      snapshot: {
        content: item.contentPreview,
        authorName: item.targetUserName,
        writtenAt: item.latestReportedAt
      },
      currentStatus: item.status === "RESOLVED" ? "HIDDEN" : "ACTIVE"
    },
    targetUser: {
      userId: item.targetUserId,
      name: item.targetUserName,
      roomNumber: item.targetRoomNumber,
      buildingName: item.buildingName
    },
    reports: [
      {
        reportId: item.reportId,
        reasonType: item.reasonTypes[0],
        reasonDetail: null,
        createdAt: item.latestReportedAt
      }
    ],
    history: {
      reportedCount: item.reportCount,
      warningCount: 0,
      sanctions: []
    },
    status: item.status,
    action: item.status === "RESOLVED" ? "HIDE_CONTENT" : item.status === "REJECTED" ? "REJECT" : null,
    adminMemo: item.status === "RECEIVED" ? null : "프로토타입 처리 기록",
    handledByName: item.status === "RECEIVED" ? null : "커뮤니티 매니저",
    handledAt: item.status === "RECEIVED" ? null : item.latestReportedAt
  })
}
// 신고 조치 → POST /community-service/admin/reports/{reportId}/actions → AdminReportController.handle
export async function handleReport(reportId: number, request: ReportActionRequest): Promise<ReportActionResponse> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/admin/reports/${reportId}/actions`, { method: "POST", body: request })
  const report = ADMIN_REPORTS.content.find((item) => item.reportId === reportId)
  if (!report) throw new Error("REPORT_NOT_FOUND: 신고를 찾을 수 없습니다.")
  if (report.status !== "RECEIVED") throw new CommunityApiError(409, "ALREADY_HANDLED", "이미 처리된 신고입니다.")
  if (request.action === "RESTRICTION" && ![
    1,
    3,
    7,
    30
  ].includes(request.durationDays ?? 0)) throw new Error("INVALID_DURATION: 이용 제한 기간은 1·3·7·30일 중 하나여야 합니다.")
  const detail = await getAdminReportDetail(reportId)
  const handledAt = now()
  report.status = request.action === "REJECT" ? "REJECTED" : "RESOLVED"
  if (detail) ADMIN_REPORT_DETAILS[reportId] = {
    ...detail,
    status: report.status,
    action: request.action,
    adminMemo: request.adminMemo ?? null,
    handledByName: "커뮤니티 매니저",
    handledAt,
    target: {
      ...detail.target,
      currentStatus: request.action === "DELETE_CONTENT" ? "DELETED" : request.action === "HIDE_CONTENT" || request.hideContent ? "HIDDEN" : detail.target.currentStatus
    }
  }
  let sanctionId: number | null = null
  let endsAt: string | null = null
  if ([
    "WARNING",
    "RESTRICTION",
    "SUSPENSION"
  ].includes(request.action)) {
    sanctionId = Math.max(0, ...ADMIN_SANCTIONS.content.map((item) => item.sanctionId)) + 1
    endsAt = request.action === "RESTRICTION" ? new Date(Date.now() + (request.durationDays ?? 0) * 86_400_000).toLocaleString("sv-SE", { timeZone: "Asia/Seoul" }).replace(" ", "T") : null
    const sanctionType: SanctionType = request.action === "WARNING" ? "WARNING" : request.action === "RESTRICTION" ? "RESTRICTION" : "SUSPENSION"
    const sanction: AdminSanction = {
      sanctionId,
      userId: report.targetUserId,
      userName: report.targetUserName,
      roomNumber: report.targetRoomNumber,
      buildingName: report.buildingName,
      sanctionType,
      durationDays: request.action === "RESTRICTION" ? request.durationDays ?? null : null,
      reason: request.reason ?? "관리자 조치",
      reportId,
      startsAt: handledAt,
      endsAt,
      active: request.action !== "WARNING",
      createdByName: "커뮤니티 매니저",
      revokedAt: null,
      revokeReason: null
    }
    ADMIN_SANCTIONS.content.unshift(sanction)
    const savedDetail = ADMIN_REPORT_DETAILS[reportId]
    if (savedDetail) {
      savedDetail.history.sanctions.unshift(sanction)
      if (request.action === "WARNING") savedDetail.history.warningCount += 1
    }
  }
  return {
    reportId,
    handledReportCount: report.reportCount,
    status: report.status,
    action: request.action,
    sanctionId,
    endsAt,
    handledAt
  }
}
// 제재 목록 → GET /community-service/admin/sanctions → AdminReportController.getSanctions
export async function getAdminSanctions(filter: AdminSanctionFilter = {}): Promise<AdminSanctionListResponse> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/admin/sanctions", { query: filter })
  let content = ADMIN_SANCTIONS.content.filter((item) => filter.activeOnly === false || item.active)
  if (filter.sanctionType) content = content.filter((item) => item.sanctionType === filter.sanctionType)
  if (filter.keyword?.trim()) content = content.filter((item) => item.userName.includes(filter.keyword!.trim()))
  if (filter.buildingId) content = content.filter((item) => item.buildingName === MOCK_BUILDINGS.find((building) => building.buildingId === filter.buildingId)?.name)
  const page = filter.page ?? 0
  const size = filter.size ?? 20
  return copy({
    ...ADMIN_SANCTIONS,
    content: content.slice(page * size, (page + 1) * size),
    page, size, totalPages: Math.ceil(content.length / size),
    totalElements: content.length
  })
}
// 제재 해제 → PATCH /community-service/admin/sanctions/{sanctionId}/revoke → AdminReportController.revoke
export async function revokeSanction(sanctionId: number, revokeReason: string) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/admin/sanctions/${sanctionId}/revoke`, { method: "PATCH", body: { revokeReason } })
  const item = ADMIN_SANCTIONS.content.find((sanction) => sanction.sanctionId === sanctionId);
  if (!item) throw new Error("SANCTION_NOT_FOUND: 제재를 찾을 수 없습니다.");
  if (item.revokedAt || !item.active) throw new CommunityApiError(409, "SANCTION_ALREADY_REVOKED", "이미 해제된 제재입니다.")
  const revokedAt = now();
  item.active = false;
  item.revokedAt = revokedAt;
  item.revokeReason = revokeReason;
  return { sanctionId, revokedAt }
}
