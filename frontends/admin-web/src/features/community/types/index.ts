// 커뮤니티 타입 — API 응답·화면 상태·실시간 이벤트의 자료형
export type ResidentStatus = "입주 예정" | "거주 중" | "퇴거 예정" | "퇴거 완료"
export type ContractResident = {
  applicationId: number;
  applicationNo: string;
  applicantName: string;
  buildingName: string;
  roomName: string;
  moveInDate: string | null;
  moveOutDate: string | null;
  monthlyFee: number;
  deposit: number;
  status: "PAID" | "COMPLETED" | "TERMINATION_REQUESTED" | "TERMINATED"
}
export type ContractResidentDetail = ContractResident & {
  userId: number | null;
  applicantEmail: string | null;
  applicantPhone: string | null;
  applicantAddress: string | null;
  contractMonths: number
}
export type ContractMoveScheduleEvent = {
  applicationId: number;
  type: "in" | "out";
  label: string;
  date: string;
  buildingName: string
}
export type Resident = {
  name: string;
  branch: string;
  room: string;
  moveIn: string;
  expires: string;
  status: ResidentStatus;
  payment: string;
  tone: string
}
export type ResidentDetail = Resident & {
  phone: string;
  email: string;
  memo: string
}
export type MoveScheduleEvent = { type: "in" | "out"; label: string; date: string; buildingId: number; buildingName: string }

export type NoteParticipantType = "CANDIDATE" | "RESIDENT" | "MEMBER" | "GUEST_INQUIRY"
export type NoteStatus = "UNANSWERED" | "ANSWERED"
export type NoteThreadType = "CANDIDATE" | "RESIDENT" | "MEMBER" | "GUEST"
export type AdminNoteThreadSummary = {
  threadId: number;
  type: NoteThreadType;
  userId: number | null;
  name: string;
  lastContent: string;
  lastAt: string;
  unreadCount: number;
  status: NoteStatus
}
export type AdminNoteThreadListResponse = {
  content: AdminNoteThreadSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number
}
export type AdminNoteThreadDetail = {
  threadId: number;
  type: NoteThreadType;
  status: NoteStatus;
  memo: string | null;
  user: { userId: number; name: string; buildingName: string | null; roomNumber: string | null } | null;
  guest: { name: string; phone: string; email: string; privacyAgreedAt: string } | null;
  notes: { noteId: number; senderType: "USER" | "ADMIN"; content: string; createdAt: string; readAt: string | null }[]
}
export type NoteMessage = {
  id: number;
  senderType: "USER" | "ADMIN";
  content: string;
  sentAt: string;
  isRead: boolean
}
export type NoteParticipant = {
  id?: number;
  name: string;
  type: NoteParticipantType;
  avatarTone?: string;
  building?: string;
  room?: string;
  phone?: string;
  email?: string;
  privacyConsentAt?: string
}
export type AdminNote = {
  id: number;
  participant: NoteParticipant;
  status: NoteStatus;
  lastContent: string;
  lastSentAt: string;
  unreadCount: number;
  processingMemo: string;
  messages: NoteMessage[]
}

export type FaqDocumentScope = "COMMON" | "BUILDING"
export type FaqIndexStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED"
export type FaqDocumentFile = {
  documentId: number;
  title: string;
  scope: FaqDocumentScope;
  buildingName: string | null;
  fileName: string;
  fileType: "PDF" | "DOCX" | "TXT" | "MD";
  uploadedAt: string;
  indexStatus: FaqIndexStatus;
  indexError: string | null
  buildingId?: number | null;
  fileSize?: number;
  indexedAt?: string | null
}
export type FaqDocumentUploadRequest = {
  file: File;
  title: string;
  scope: FaqDocumentScope;
  buildingId?: number
}
export type FaqDocumentUploadResponse = { documentId: number; indexStatus: FaqIndexStatus }
export type FaqSource = { documentId: number; title: string }
export type FaqAskRequest = { question: string; buildingId?: number }
export type FaqAskResponse = {
  answer: string;
  sources: FaqSource[];
  fallback: boolean;
  cached: boolean
  answered?: boolean;
  actions?: string[]
}
export type FaqReindexResponse = { jobId: string | number; status: "PROCESSING" }
export type FaqReindexJobResponse = { status: "PROCESSING" | "COMPLETED" | "FAILED"; processedCount: number; totalCount?: number; errorMessage?: string | null }
export type FaqDocumentListResponse = { summary: { lastIndexedAt: string | null; indexedCount: number; pendingCount: number }; documents: FaqDocumentFile[] }
export type CommunityBuilding = { buildingId: number; name: string }
export type FaqQuestionLogResponse = {
  summary: { totalCount: number; unansweredCount: number; cachedCount: number; avgLatencyMs: number };
  content: { logId: number; question: string; buildingId: number | null; buildingName: string | null; answered: boolean; topScore: number | null; sources: FaqSource[]; cached: boolean; latencyMs: number; createdAt: string }[];
  page: number; size: number; totalElements: number; totalPages: number
}
export type FaqIndexSummary = {
  lastReflectedAt: string;
  reflectedCount: number;
  pendingChanges: number
}

export type ContentStatus = "ACTIVE" | "HIDDEN" | "DELETED"
export type ReportSourceType = "CHAT_ROOM" | "POST"
export type ReportTargetType = "POST" | "COMMENT" | "CHAT_MESSAGE" | "USER"
export type ReportReason = "ABUSE" | "HARASSMENT" | "PRIVACY" | "SPAM" | "INAPPROPRIATE" | "HOUSE_RULE" | "ETC"
export type ReportStatus = "RECEIVED" | "RESOLVED" | "REJECTED"
export type ReportAction = "REJECT" | "HIDE_CONTENT" | "DELETE_CONTENT" | "WARNING" | "RESTRICTION" | "SUSPENSION" | "OFFLINE_RECORD"
export type SanctionType = "WARNING" | "RESTRICTION" | "SUSPENSION"
export type AdminReportSummary = {
  receivedCount: number;
  resolvedCount: number;
  rejectedCount: number
}
export type AdminReportListItem = {
  reportId: number;
  targetType: ReportTargetType;
  targetId: string;
  sourceType: ReportSourceType;
  sourceId: number;
  sourceTitle: string | null;
  targetUserId: number;
  targetUserName: string;
  targetRoomNumber: string;
  buildingId: number;
  buildingName: string;
  contentPreview: string;
  reasonTypes: ReportReason[];
  reportCount: number;
  status: ReportStatus;
  latestReportedAt: string
}
export type AdminReportListResponse = {
  summary: AdminReportSummary;
  content: AdminReportListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number
}
export type AdminSanction = {
  sanctionId: number;
  userId: number;
  userName: string;
  roomNumber: string;
  buildingName: string;
  sanctionType: SanctionType;
  durationDays: number | null;
  reason: string;
  reportId: number | null;
  startsAt: string;
  endsAt: string | null;
  active: boolean;
  createdByName: string;
  revokedAt: string | null;
  revokeReason: string | null
}
export type AdminReportDetail = {
  target: {
    targetType: ReportTargetType;
    targetId: string;
    sourceType: ReportSourceType;
    sourceId: number;
    sourceTitle: string | null;
    snapshot: {
      content: string;
      authorName: string;
      writtenAt: string
    };
    currentStatus: ContentStatus
  };
  targetUser: {
    userId: number;
    name: string;
    roomNumber: string;
    buildingName: string
  };
  reports: {
    reportId: number;
    reasonType: ReportReason;
    reasonDetail: string | null;
    createdAt: string
  }[];
  history: {
    reportedCount: number;
    warningCount: number;
    sanctions: Pick<AdminSanction, "sanctionId" | "sanctionType" | "durationDays" | "reason" | "startsAt" | "endsAt" | "revokedAt">[]
  };
  status: ReportStatus;
  action: ReportAction | null;
  adminMemo: string | null;
  handledByName: string | null;
  handledAt: string | null
}
export type ReportActionRequest = {
  action: ReportAction;
  durationDays?: number | null;
  hideContent?: boolean;
  reason?: string | null;
  adminMemo?: string | null
}
export type ReportActionResponse = {
  reportId: number;
  handledReportCount: number;
  status: ReportStatus;
  action: ReportAction;
  sanctionId: number | null;
  endsAt: string | null;
  handledAt: string
}
export type AdminSanctionListResponse = {
  content: AdminSanction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number
}
export type AdminReportFilter = {
  status?: ReportStatus;
  sourceType?: ReportSourceType;
  targetType?: ReportTargetType;
  buildingId?: number;
  sort?: "latest" | "count";
  page?: number;
  size?: number
}
export type AdminSanctionFilter = {
  activeOnly?: boolean;
  sanctionType?: SanctionType;
  buildingId?: number;
  keyword?: string;
  page?: number;
  size?: number
}

export { REPORT_REASON_LABELS, REPORT_ACTION_LABELS, SANCTION_TYPE_LABELS, REPORT_TARGET_LABELS } from "../constants"
