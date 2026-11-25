// 커뮤니티 API 모음 — 화면 요청·Gateway 경로 연결·개발용 가짜 응답
import { BLOCK_LIST, COMMUNITY_BUILDINGS, COMMUNITY_POST_DETAILS, COMMUNITY_POSTS, INACTIVE_MY_SANCTION } from "../mocks"
import type { AuthorType, BlockListResponse, CommunityCommentRequest, CommunityPostDetail, CommunityPostRequest, CommunitySummary, CreateBlockRequest, CreateBlockResponse, CreateReportRequest, CreateReportResponse, MySanction, PostCategory } from "../types"
import { CommunityApiError, request as apiRequest, USE_MOCK } from "./communityClient"
import { COMMUNITY_MOCK_VIEWER } from "../mockViewer"

// 가짜 응답을 복제해 화면 수정이 원본 데이터에 섞이지 않도록 처리
const copy = <T,>(value: T): T => structuredClone(value)
const MOCK_CURRENT_USER_ID = USE_MOCK ? COMMUNITY_MOCK_VIEWER === "MANAGER" ? 9001 : 5001 : null
// 가짜 API 오류 코드를 HTTP 상태와 CommunityApiError로 변환
const error = (code: string, message: string) => new CommunityApiError(code.startsWith("ALREADY_") ? 409 : code.endsWith("NOT_FOUND") ? 404 : code.startsWith("CANNOT_") ? 403 : 400, code, message)
// 가짜 데이터의 생성·변경 시간을 한국 시간 문자열로 만들기
const now = () => new Date().toLocaleString("sv-SE", { timeZone: "Asia/Seoul" }).replace(" ", "T")
// community:blocks-changed 이벤트로 게시글·채팅·차단 목록 갱신 요청
const notifyBlocksChanged = () => window.dispatchEvent(new Event("community:blocks-changed"))
type MockAuthor = { authorId: number; authorType: "USER" | "ADMIN"; authorName: string | null; authorRoomNumber: string | null }
let blocks = copy(BLOCK_LIST.blocks)
let nextPostId = 9
let nextCommentId = 99
let nextReportId = 81
const reportedTargets = new Set<string>(["COMMENT:13"])

// 게시글 목록 → GET /community-service/posts → PostController.getPosts
export async function getCommunityPosts(params: {
  buildingId?: number;
  category?: PostCategory;
  keyword?: string;
  page?: number;
  size?: number
} = {}) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/posts", { query: params })
  const keyword = params.keyword?.trim().toLowerCase() ?? ""
  const building = COMMUNITY_BUILDINGS.find((item) => item.buildingId === params.buildingId) ?? COMMUNITY_BUILDINGS[0]
  const blockedIds = new Set(blocks.map((item) => item.blockedUserId))
  const content = COMMUNITY_POSTS.content.filter((post) => !post.authorId || !blockedIds.has(post.authorId)).filter((post) => (!params.category || post.category === params.category) && (!keyword || post.title.toLowerCase().includes(keyword)))
  const page = params.page ?? 0
  const size = params.size ?? 20
  return copy({
    ...COMMUNITY_POSTS,
    buildingId: building.buildingId,
    buildingName: building.name,
    pinned: COMMUNITY_POSTS.pinned.map((post) => post.authorType === "ADMIN" ? { ...post, authorName: `${building.name} 매니저` } : post),
    content: content.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: content.length,
    totalPages: Math.ceil(content.length / size)
  })
}

// 게시글 상세 → GET /community-service/posts/{postId} → PostController.getPost
export async function getCommunityPost(postId: number, currentUserId: number | null = MOCK_CURRENT_USER_ID, buildingId?: number): Promise<CommunityPostDetail | undefined> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/posts/${postId}`)
  const post = COMMUNITY_POST_DETAILS[postId]
  if (!post) throw error("POST_NOT_FOUND", "게시글을 찾을 수 없습니다.")
  const buildingName = COMMUNITY_BUILDINGS.find((item) => item.buildingId === buildingId)?.name
  // managerName — API 내부 응답·상태 계산 도우미(직접 HTTP 호출 없음)
  const managerName = (authorType: AuthorType, authorName: string | null) => authorType === "ADMIN" && buildingName ? `${buildingName} 매니저` : authorName
  const blockedIds = new Set(blocks.map((item) => item.blockedUserId))
  // redact — API 내부 응답·상태 계산 도우미(직접 HTTP 호출 없음)
  const redact = <T extends CommunityPostDetail["comments"][number] | CommunityPostDetail["comments"][number]["replies"][number]>(item: T): T => item.authorId !== null && blockedIds.has(item.authorId) ? {
    ...item,
    authorId: null,
    authorType: null,
    authorName: null,
    authorRoomNumber: null,
    content: null,
    blocked: true
  } : { ...item, blocked: false }
  return copy({
    ...post,
    authorName: managerName(post.authorType, post.authorName) ?? "익명",
    isMine: post.authorId === currentUserId,
    blocked: post.authorId !== null && blockedIds.has(post.authorId),
    viewCount: post.viewCount + 1,
    comments: post.comments.map((comment) => {
      const redactedComment = redact(comment)
      return {
        ...redactedComment,
        authorName: managerName(redactedComment.authorType, redactedComment.authorName),
        isMine: comment.authorId === currentUserId,
        replies: comment.replies.map((reply) => {
          const redactedReply = redact(reply)
          return {
            ...redactedReply,
            authorName: managerName(redactedReply.authorType, redactedReply.authorName),
            isMine: reply.authorId === currentUserId
          }
        })
      }
    })
  })
}

// 지점 목록 → GET /community-service/admin/buildings → AdminBoardBuildingController.getBuildings
export async function getCommunityBuildings() {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/admin/buildings")
  return copy(COMMUNITY_BUILDINGS)
}

// 내 활동·인기글 → GET /community-service/me/summary → MySummaryController.getMySummary
export async function getCommunitySummary(): Promise<CommunitySummary> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/me/summary")
  return copy({ name: "김민서", buildingName: "망원 스테이", roomNumber: "302호", postCount: COMMUNITY_POSTS.content.filter((post) => post.authorId === MOCK_CURRENT_USER_ID).length, receivedCommentCount: 18, sanction: INACTIVE_MY_SANCTION.active ? INACTIVE_MY_SANCTION : null, popularPosts: [...COMMUNITY_POSTS.content].sort((a, b) => b.viewCount - a.viewCount).slice(0, 3) })
}

// 게시글 등록 → POST /community-service/posts → PostController.create
export async function createCommunityPost(request: CommunityPostRequest, author: {
  authorId: number;
  authorType: Exclude<AuthorType, null>;
  authorName: string | null;
  authorRoomNumber: string | null
}) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/posts", { method: "POST", body: request })
  const postId = nextPostId++;
  COMMUNITY_POST_DETAILS[postId] = {
    postId,
    ...request,
    authorId: author.authorId,
    authorType: author.authorType,
    authorName: author.authorName ?? "익명",
    authorRoomNumber: author.authorRoomNumber,
    viewCount: 0,
    createdAt: now(),
    updatedAt: null,
    isMine: true,
    blocked: false,
    comments: []
  };
  const detail = COMMUNITY_POST_DETAILS[postId]
  const summary = { ...detail, authorId: detail.authorId ?? undefined, commentCount: 0, isNew: true }
  if (request.category === "NOTICE") COMMUNITY_POSTS.pinned.unshift(summary)
  else COMMUNITY_POSTS.content.unshift(summary)
  return { postId, createdAt: detail.createdAt }
}
// 게시글 수정 → PUT /community-service/posts/{postId} → PostController.update
export async function updateCommunityPost(postId: number, request: CommunityPostRequest) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/posts/${postId}`, { method: "PUT", body: request })
  const post = COMMUNITY_POST_DETAILS[postId];
  if (!post) return undefined;
  Object.assign(post, request, { updatedAt: now() });
  const summary = [...COMMUNITY_POSTS.content, ...COMMUNITY_POSTS.pinned].find((item) => item.postId === postId)
  if (summary) Object.assign(summary, request)
  return copy(post)
}
// 게시글 삭제 → DELETE /community-service/posts/{postId} → PostController.delete
export async function deleteCommunityPost(postId: number): Promise<void> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/posts/${postId}`, { method: "DELETE" })
  delete COMMUNITY_POST_DETAILS[postId]
  COMMUNITY_POSTS.content = COMMUNITY_POSTS.content.filter((post) => post.postId !== postId)
  COMMUNITY_POSTS.pinned = COMMUNITY_POSTS.pinned.filter((post) => post.postId !== postId)
}
// 댓글·답글 등록 → POST /community-service/posts/{postId}/comments → PostController.createComment
export async function createCommunityComment(postId: number, request: CommunityCommentRequest, author?: MockAuthor) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/posts/${postId}/comments`, { method: "POST", body: request })
  const post = COMMUNITY_POST_DETAILS[postId]
  if (!post) throw error("POST_NOT_FOUND", "게시글을 찾을 수 없습니다.")
  const parent = request.parentCommentId === null ? null : post.comments.find((comment) => comment.commentId === request.parentCommentId)
  if (request.parentCommentId !== null && !parent) throw error("REPLY_DEPTH_EXCEEDED", "답글에는 답글을 작성할 수 없습니다.")
  const response = { commentId: nextCommentId++, parentCommentId: request.parentCommentId, createdAt: now() }
  const comment = { ...response, ...(author ?? { authorId: MOCK_CURRENT_USER_ID!, authorType: "USER" as const, authorName: "김민서", authorRoomNumber: "302호" }), content: request.content, status: "ACTIVE" as const, blocked: false, isMine: true }
  if (parent) parent.replies.push(comment)
  else post.comments.push({ ...comment, replies: [] })
  return response
}
// 댓글·답글 수정 → PUT /community-service/comments/{commentId} → CommentController.update
export async function updateCommunityComment(commentId: number, request: { content: string }): Promise<void> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/comments/${commentId}`, { method: "PUT", body: request })
  Object.values(COMMUNITY_POST_DETAILS).forEach((post) => post.comments.forEach((comment) => {
    if (comment.commentId === commentId) comment.content = request.content;
    comment.replies.forEach((reply) => {
      if (reply.commentId === commentId) reply.content = request.content
    })
  }))
}
// 댓글·답글 삭제 → DELETE /community-service/comments/{commentId} → CommentController.delete
export async function deleteCommunityComment(commentId: number): Promise<void> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest(`/comments/${commentId}`, { method: "DELETE" })
  Object.values(COMMUNITY_POST_DETAILS).forEach((post) => post.comments.forEach((comment) => {
    const target = comment.commentId === commentId ? comment : comment.replies.find((reply) => reply.commentId === commentId);
    if (target) Object.assign(target, {
      authorId: null,
      authorType: null,
      authorName: null,
      authorRoomNumber: null,
      content: null,
      status: "DELETED",
      isMine: false
    })
  }))
}

// 가짜 신고 대상이 현재 사용자 본인 글·댓글인지 검사
const isOwnTarget = (request: CreateReportRequest) => {
  if (MOCK_CURRENT_USER_ID === null) return false
  if (request.targetType === "USER") return Number(request.targetId) === MOCK_CURRENT_USER_ID
  if (request.targetType === "POST") return COMMUNITY_POST_DETAILS[Number(request.targetId)]?.authorId === MOCK_CURRENT_USER_ID
  if (request.targetType === "COMMENT") return Object.values(COMMUNITY_POST_DETAILS).some((post) => post.comments.some((comment) => (comment.commentId === Number(request.targetId) && comment.authorId === MOCK_CURRENT_USER_ID) || comment.replies.some((reply) => reply.commentId === Number(request.targetId) && reply.authorId === MOCK_CURRENT_USER_ID)))
  return false
}
// 신고 접수 → POST /community-service/reports → ReportController.create
export async function createReport(request: CreateReportRequest): Promise<CreateReportResponse> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/reports", { method: "POST", body: request })
  const key = `${request.targetType}:${request.targetId}`;
  if (isOwnTarget(request)) throw error("CANNOT_REPORT_SELF", "본인은 신고할 수 없습니다.");
  if (reportedTargets.has(key)) throw error("ALREADY_REPORTED", "이미 신고한 대상입니다.");
  reportedTargets.add(key);
  return {
    reportId: nextReportId++,
    status: "RECEIVED",
    createdAt: now()
  }
}
// 차단 목록 → GET /community-service/blocks → BlockController.getBlocks
export async function getBlocks(): Promise<BlockListResponse> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/blocks")
  return copy({ blocks })
}
// 사용자 차단 → POST /community-service/blocks → BlockController.block
export async function createBlock(request: CreateBlockRequest): Promise<CreateBlockResponse> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) {
    const response = await apiRequest("/blocks", { method: "POST", body: request })
    notifyBlocksChanged()
    return response
  }
  if (MOCK_CURRENT_USER_ID !== null && request.blockedUserId === MOCK_CURRENT_USER_ID) throw error("CANNOT_BLOCK_SELF", "본인은 차단할 수 없습니다.");
  if (blocks.some((item) => item.blockedUserId === request.blockedUserId)) throw error("ALREADY_BLOCKED", "이미 차단한 사용자입니다.");
  const users: Record<number, { name: string; room: string | null }> = {
    5002: { name: "박준영", room: "201호" },
    5003: { name: "정하은", room: "304호" },
    5004: { name: "윤가영", room: null }
  };
  const blockedAt = now();
  blocks.push({
    blockedUserId: request.blockedUserId,
    name: users[request.blockedUserId]?.name ?? "알 수 없는 사용자",
    roomNumber: users[request.blockedUserId]?.room ?? null,
    profileImageUrl: null,
    blockedAt
  });
  notifyBlocksChanged()
  return { blockedUserId: request.blockedUserId, blockedAt }
}
// 차단 해제 → DELETE /community-service/blocks/{blockedUserId} → BlockController.unblock
export async function deleteBlock(blockedUserId: number): Promise<void> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) {
    await apiRequest(`/blocks/${blockedUserId}`, { method: "DELETE" })
    notifyBlocksChanged()
    return
  }
  if (!blocks.some((item) => item.blockedUserId === blockedUserId)) throw error("BLOCK_NOT_FOUND", "차단 내역을 찾을 수 없습니다.");
  blocks = blocks.filter((item) => item.blockedUserId !== blockedUserId)
  notifyBlocksChanged()
}
// 내 제재 조회 → GET /community-service/sanctions/me → ReportController.getMySanction
export async function getMySanction(): Promise<MySanction> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return apiRequest("/sanctions/me")
  return copy(INACTIVE_MY_SANCTION)
}
export { getChatRooms, getChatRoomMessages, openChatRoom, readChatRoom, sendChatMessage, leaveChatRoom } from "./chatApi"
