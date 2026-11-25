// 커뮤니티 타입 — API 응답·화면 상태·실시간 이벤트의 자료형
export type UserRole = "RESIDENT" | "ADMIN" | "MEMBER"
export type AuthorType = "USER" | "ADMIN" | null
export type CommunityBuilding = { buildingId: number; name: string }
export type PostCategory = "NOTICE" | "FREE" | "SHARE" | "MEETUP"
export type ContentStatus = "ACTIVE" | "HIDDEN" | "DELETED"
export type ReportSourceType = "CHAT_ROOM" | "POST"
export type ReportTargetType = "POST" | "COMMENT" | "CHAT_MESSAGE" | "USER"
export type ReportReason = "ABUSE" | "HARASSMENT" | "PRIVACY" | "SPAM" | "INAPPROPRIATE" | "HOUSE_RULE" | "ETC"
export type ReportStatus = "RECEIVED" | "RESOLVED" | "REJECTED"
export type SanctionType = "WARNING" | "RESTRICTION" | "SUSPENSION"

export type CommunityPostSummary = {
  postId: number;
  category: PostCategory;
  title: string;
  authorId?: number;
  authorType: AuthorType;
  authorName: string;
  authorRoomNumber?: string | null;
  viewCount: number;
  commentCount: number;
  createdAt: string;
  isNew?: boolean
}
export type CommunityPostListResponse = {
  buildingId: number;
  buildingName: string;
  pinned: CommunityPostSummary[];
  content: CommunityPostSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number
}
export type CommunityReply = {
  commentId: number;
  authorId: number | null;
  authorType: AuthorType;
  authorName: string | null;
  authorRoomNumber?: string | null;
  content: string | null;
  status: ContentStatus;
  blocked: boolean;
  isMine: boolean;
  createdAt: string
}
export type CommunityComment = {
  commentId: number;
  authorId: number | null;
  authorType: AuthorType;
  authorName: string | null;
  authorRoomNumber?: string | null;
  content: string | null;
  status: ContentStatus;
  blocked: boolean;
  isMine: boolean;
  createdAt: string;
  replies: CommunityReply[]
}
export type CommunityPostDetail = {
  postId: number;
  category: PostCategory;
  title: string;
  content: string;
  authorId: number | null;
  authorType: AuthorType;
  authorName: string;
  authorRoomNumber: string | null;
  viewCount: number;
  createdAt: string;
  updatedAt: string | null;
  isMine: boolean;
  blocked: boolean;
  comments: CommunityComment[]
}
export type CommunityPostRequest = {
  category: PostCategory;
  title: string;
  content: string;
  buildingId?: number
}
export type CommunityPostCreateResponse = { postId: number }
export type CommunitySummary = {
  name: string;
  buildingName: string;
  roomNumber: string | null;
  postCount: number;
  receivedCommentCount: number;
  sanction: MySanction | null;
  popularPosts: { postId: number; title: string; viewCount: number; commentCount: number }[]
}
export type CommunityCommentRequest = { content: string; parentCommentId: number | null }
export type CommunityCommentCreateResponse = { commentId: number; createdAt: string }

export type CreateReportRequest = {
  sourceType: ReportSourceType;
  sourceId: number;
  targetType: ReportTargetType;
  targetId: string;
  reasonType: ReportReason;
  reasonDetail?: string | null
}
export type CreateReportResponse = {
  reportId: number;
  status: ReportStatus;
  createdAt: string
}
export type BlockedUser = {
  blockedUserId: number;
  name: string;
  roomNumber: string | null;
  profileImageUrl: string | null;
  blockedAt: string
}
export type BlockListResponse = { blocks: BlockedUser[] }
export type CreateBlockRequest = { blockedUserId: number }
export type CreateBlockResponse = { blockedUserId: number; blockedAt: string }
export type MySanction = {
  active: boolean;
  sanctionId: number | null;
  sanctionType: SanctionType | null;
  durationDays: number | null;
  reason: string | null;
  startsAt: string | null;
  endsAt: string | null
}
export type ChatRoomResponse = { roomId: string; created: boolean }
export type FaqItem = {
  id: number;
  category: "비용" | "입주" | "계약" | "시설";
  question: string;
  answer: string;
  branch: string;
  enabled: boolean
}
export type UserNote = {
  noteId: number;
  senderType: "USER" | "ADMIN";
  senderName: string;
  content: string;
  createdAt: string;
  readAt: string | null
}
export type UserNoteListResponse = {
  unreadCount: number;
  content: UserNote[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number
}
export type GuestInquiryRequest = {
  name: string;
  phone: string;
  email: string;
  content: string;
  privacyAgreed: boolean
}
export type ChatPartner = {
  userId: number;
  name: string;
  profileImageUrl: string | null;
  roomNumber: string
}
export type ChatRoom = {
  roomId: string;
  partner: ChatPartner;
  lastMessage: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
  readOnly: boolean;
  partnerLeft?: boolean
}
export type ChatMessage = {
  messageId: string;
  senderId: number;
  content: string | null;
  status?: ContentStatus;
  sentAt: string
}
export type ChatMessageListResponse = { messages: ChatMessage[]; hasMore: boolean }
export type NotificationType = "NOTE" | "CHAT" | "COMMENT" | "REPLY" | "SANCTION" | "REPORT"
export type NotificationTargetType = "NOTE_THREAD" | "CHAT_ROOM" | "POST" | "SANCTION" | "REPORT"
export type UserNotification = {
  notificationId: number;
  type: NotificationType;
  title: string;
  message: string;
  targetType: NotificationTargetType;
  targetId: string;
  createdAt: string;
  read: boolean
}
export type NotificationListResponse = {
  content: UserNotification[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number
}
export type UnreadNotificationSummary = {
  note: number;
  chat: number;
  notification: number;
  total: number
}

export const REPORT_REASON_LABELS: Record<ReportReason, string> = {
  ABUSE: "욕설·비방",
  HARASSMENT: "괴롭힘·스토킹",
  PRIVACY: "개인정보 노출",
  SPAM: "광고·도배",
  INAPPROPRIATE: "음란·부적절",
  HOUSE_RULE: "생활 규칙 위반",
  ETC: "기타"
}
export const CATEGORY_LABELS: Record<PostCategory, string> = {
  NOTICE: "공지",
  FREE: "자유",
  SHARE: "나눔",
  MEETUP: "모임"
}
