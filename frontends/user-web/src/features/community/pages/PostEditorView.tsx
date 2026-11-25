// 게시글 상세·작성·수정 — 댓글·답글과 작성자 채팅·신고·차단
import { type FormEvent, type ReactNode, useCallback, useEffect, useMemo, useState } from "react"
import { useNavigate, useParams, useSearchParams } from "react-router"
import { useTranslation } from "react-i18next"
import { createCommunityComment, createCommunityPost, deleteCommunityComment, deleteCommunityPost, getCommunityBuildings, getCommunityPost, openChatRoom, updateCommunityComment, updateCommunityPost } from "../api"
import { Avatar, Badge, Button, Icon } from "../components"
import { BlockConfirmModal } from "../components/BlockConfirmModal"
import { CommunityRestricted } from "../components/CommunityRestricted"
import { ReportModal } from "../components/ReportModal"
import { SanctionBanner } from "../components/SanctionBanner"
import { useMySanction } from "../components/useMySanction"
import { VerifiedBadge } from "../components/VerifiedBadge"
import { CommunityQueryState } from "../components/CommunityQueryState"
import { isCommunityAccessError } from "../api/communityErrors"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import { CommunityApiError } from "../api/communityClient"
import { communityDisplayName, useCurrentUser } from "../currentUser"
import type { AuthorType, CommunityBuilding, CommunityComment, CommunityPostDetail, CommunityPostRequest, CommunityReply, ReportSourceType, ReportTargetType } from "../types"

export type PostEditorMode = "view" | "create" | "edit"
type ReportSelection = {
  sourceType: ReportSourceType;
  sourceId: number;
  targetType: ReportTargetType;
  targetId: string;
  targetName: string;
  targetUserId: number
}
type BlockSelection = {
  userId: number;
  name: string;
  from: "post" | "comment"
}
const INPUT_CLASS = "w-full rounded-lg border border-main/15 bg-white px-3 py-2 text-sm text-text focus-visible:outline-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:bg-background"
const dateTime = (value: string, locale: string) => new Date(value).toLocaleString(locale, {
  year: "numeric",
  month: "long",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit"
})

export default function PostEditorView({ mode, title }: { mode: PostEditorMode; title: string }) {
  const { t, i18n } = useTranslation("community");
  const user = useCurrentUser();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { postId } = useParams();
  const isManager = user.role === "ADMIN";
  const sanction = useMySanction(!isManager);
  const sanctionActive = !isManager && (sanction.active || sanction.loading);
  const queryBuildingId = Number(searchParams.get("buildingId"));
  const numericPostId = Number(postId)
  const canLoad = user.isLoggedIn && (user.role === "RESIDENT" || isManager)
  // loadPost — GET /posts/{postId}
  const loadPost = useCallback(() => getCommunityPost(numericPostId, user.userId, isManager ? queryBuildingId : undefined), [numericPostId, user.userId, isManager, queryBuildingId])
  const detailQuery = useCommunityQuery(loadPost, { enabled: canLoad && mode !== "create" && Number.isFinite(numericPostId) })
  const post = detailQuery.data as CommunityPostDetail | null
  // loadBuildings — GET /admin/buildings
  const loadBuildings = useCallback(() => getCommunityBuildings(), [])
  const buildingsQuery = useCommunityQuery(loadBuildings, { enabled: canLoad && isManager })
  const buildings = useMemo(() => (buildingsQuery.data ?? []) as CommunityBuilding[], [buildingsQuery.data])
  const validBuilding = buildings.some((building) => building.buildingId === queryBuildingId)
  const [form, setForm] = useState<CommunityPostRequest>({
    category: isManager ? "NOTICE" : "FREE",
    title: "",
    content: ""
  });
  const [commentText, setCommentText] = useState("");
  const [replyTo, setReplyTo] = useState<number | null>(null);
  const [replyText, setReplyText] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingText, setEditingText] = useState("");
  const [profile, setProfile] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<"leave" | "delete" | null>(null);
  const [menu, setMenu] = useState(false);
  const [reportTarget, setReportTarget] = useState<ReportSelection | null>(null);
  const [blockTarget, setBlockTarget] = useState<BlockSelection | null>(null);
  const [toast, setToast] = useState("")
  const [actionError, setActionError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)
  const [deleteCommentId, setDeleteCommentId] = useState<number | null>(null)
  // 모드·게시글·언어가 바뀌면 브라우저 제목 반영
  useEffect(() => {
    document.title = mode === "create" ? t(isManager ? "post.createNoticeTitle" : "post.createTitle", { defaultValue: title }) : mode === "edit" ? t("post.editTitle", { defaultValue: title }) : post?.title ?? title
  }, [
    i18n.language,
    isManager,
    mode,
    post?.title,
    t,
    title
  ])
  // 수정할 게시글을 받으면 폼에 반영, 새 글이면 역할별 분류 적용
  useEffect(() => {
    if (mode === "edit" && post?.postId === numericPostId) setForm({ category: post.category, title: post.title, content: post.content })
    if (mode === "create") setForm((current) => ({ ...current, category: isManager ? "NOTICE" : "FREE" }))
  }, [mode, post, numericPostId, isManager])
  // 관리자의 지점 선택이 없거나 유효하지 않으면 첫 지점을 URL에 반영
  useEffect(() => {
    if (!isManager || !buildings.length || validBuilding) return
    setSearchParams((current) => { const next = new URLSearchParams(current); next.set("buildingId", String(buildings[0].buildingId)); return next }, { replace: true })
  }, [isManager, buildings, validBuilding, setSearchParams])
  // 상세 조회가 POST_NOT_FOUND이면 커뮤니티 목록으로 이동
  useEffect(() => {
    if (detailQuery.error instanceof CommunityApiError && detailQuery.error.code === "POST_NOT_FOUND") navigate("/community" + (isManager && queryBuildingId > 0 ? `?buildingId=${queryBuildingId}` : ""), { replace: true })
  }, [detailQuery.error, navigate, isManager, queryBuildingId])
  const dirty = useMemo(() => mode === "create" ? Boolean(form.title.trim() || form.content.trim() || form.category !== (isManager ? "NOTICE" : "FREE")) : mode === "edit" ? Boolean(post && (form.category !== post.category || form.title !== post.title || form.content !== post.content)) : false, [
    form,
    isManager,
    mode,
    post
  ])
  // 작성 내용이 바뀌어 저장 전이면 창 닫기 이탈 확인 등록
  useEffect(() => {
    if (!dirty) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload)
  }, [dirty])
  const accessError = [detailQuery.error, buildingsQuery.error, actionError].find(isCommunityAccessError)
  if (accessError) return <CommunityQueryState error={accessError} />
  if (user.loading) return <CommunityQueryState loading />
  if (user.role !== "RESIDENT" && !isManager) return <CommunityRestricted />
  const buildingQuery = isManager && Number.isFinite(queryBuildingId) ? `?buildingId=${queryBuildingId}` : "";
  const selectedBuildingName = buildings.find((item) => item.buildingId === queryBuildingId)?.name ?? user.buildingName;
  const showToast = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 2400)
  }
  const handleActionError = (error: unknown) => {
    setActionError(error)
    if (error instanceof CommunityApiError && error.code === "POST_NOT_FOUND") navigate(`/community${buildingQuery}`, { replace: true })
  }
  const handleLeave = () => {
    if (dirty) setConfirm("leave"); else navigate(mode === "edit" ? `/community/posts/${numericPostId}${buildingQuery}` : `/community${buildingQuery}`)
  }
  // refresh — GET /posts/{postId}
  const refresh = async () => {
    await detailQuery.refetch()
  }
  // handleAfterBlock — GET /posts/{postId}
  const handleAfterBlock = async (userId: number, from: "post" | "comment") => {
    showToast(t("post.blockedToast"));
    if (from === "post" || post?.authorId === userId) navigate(`/community${buildingQuery}`); else await refresh()
  }
  // handleSubmitPost — POST /posts · PUT /posts/{postId}
  const handleSubmitPost = async (event: FormEvent) => {
    event.preventDefault();
    if (!form.title.trim() || !form.content.trim() || sanctionActive || submitting || (isManager && !validBuilding)) return;
    setSubmitting(true)
    setActionError(null)
    try {
      const request = { category: isManager ? "NOTICE" as const : form.category, title: form.title.trim(), content: form.content.trim(), ...(isManager ? { buildingId: queryBuildingId } : {}) }
      if (mode === "create") {
        const result = await createCommunityPost(request, {
          authorId: user.userId,
          authorType: isManager ? "ADMIN" : "USER",
          authorName: isManager ? `${selectedBuildingName} 매니저` : user.name || null,
          authorRoomNumber: isManager ? null : user.roomNumber
        });
        navigate(`/community/posts/${result.postId}${buildingQuery}`)
      } else {
        await updateCommunityPost(numericPostId, request);
        await refresh()
        navigate(`/community/posts/${numericPostId}${buildingQuery}`)
      }
    } catch (error) {
      handleActionError(error)
    } finally {
      setSubmitting(false)
    }
  }
  // handleStartChat — POST /chat/rooms
  const handleStartChat = async (authorId: number) => {
    if (sanctionActive || isManager) return;
    try {
      const room = await openChatRoom({
        partnerUserId: authorId,
        sourceType: "POST",
        sourcePostId: numericPostId
      });
      setProfile(null);
      window.dispatchEvent(new CustomEvent("roomlink:open-chat", { detail: { tab: "chat", roomId: room.roomId } }))
    } catch (caught) {
      if (caught instanceof CommunityApiError && caught.code === "CHAT_NOT_AVAILABLE") showToast(t("post.chatUnavailable"))
      else if (caught instanceof Error) showToast(caught.message)
    }
  }
  // handleSubmitComment — POST /posts/{postId}/comments · GET /posts/{postId}
  const handleSubmitComment = async (event: FormEvent) => {
    event.preventDefault();
    const content = commentText.trim();
    if (!post || !content || sanctionActive || submitting) return;
    setSubmitting(true)
    setActionError(null)
    try {
      await createCommunityComment(post.postId, { content, parentCommentId: null }, { authorId: user.userId, authorType: isManager ? "ADMIN" : "USER", authorName: isManager ? `${selectedBuildingName} 매니저` : user.name, authorRoomNumber: isManager ? null : user.roomNumber })
      setCommentText("")
      await refresh()
    } catch (error) { handleActionError(error) }
    finally { setSubmitting(false) }
  }
  // handleSubmitReply — POST /posts/{postId}/comments · GET /posts/{postId}
  const handleSubmitReply = async (commentId: number) => {
    const content = replyText.trim();
    if (!post || !content || sanctionActive || submitting) return;
    setSubmitting(true)
    setActionError(null)
    try {
      await createCommunityComment(post.postId, { content, parentCommentId: commentId }, { authorId: user.userId, authorType: isManager ? "ADMIN" : "USER", authorName: isManager ? `${selectedBuildingName} 매니저` : user.name, authorRoomNumber: isManager ? null : user.roomNumber })
      setReplyText("")
      setReplyTo(null)
      await refresh()
    } catch (error) { handleActionError(error) }
    finally { setSubmitting(false) }
  }
  // handleSaveComment — PUT /comments/{commentId} · GET /posts/{postId}
  const handleSaveComment = async () => {
    if (!editingId || !editingText.trim() || sanctionActive || submitting) return;
    setSubmitting(true)
    setActionError(null)
    try {
      await updateCommunityComment(editingId, { content: editingText.trim() })
      setEditingId(null)
      await refresh()
    } catch (error) { handleActionError(error) }
    finally { setSubmitting(false) }
  }
  // handleRemoveComment — DELETE /comments/{commentId} · GET /posts/{postId}
  const handleRemoveComment = async (id: number) => {
    if (submitting) return
    setSubmitting(true)
    setActionError(null)
    try {
      await deleteCommunityComment(id)
      setDeleteCommentId(null)
      await refresh()
    } catch (error) { handleActionError(error); setDeleteCommentId(null) }
    finally { setSubmitting(false) }
  }
  const handleRequestBlock = (userId: number, name: string, from: "post" | "comment") => {
    setProfile(null);
    setMenu(false);
    setBlockTarget({
      userId,
      name,
      from
    })
  }
  const handleReportUser = (id: number, name: string) => {
    setProfile(null)
    setReportTarget({ sourceType: "POST", sourceId: numericPostId, targetType: "USER", targetId: String(id), targetName: name, targetUserId: id })
  }
  const commentBody = (item: CommunityComment | CommunityReply, reply: boolean): ReactNode => {
    if (item.status === "DELETED") return <p className="text-sm text-text/70">
      {t("post.deletedComment")}
    </p>
    if (item.status === "HIDDEN") return <p className="text-sm text-text/70">{t("post.hiddenComment")}</p>
    if (item.blocked) return <div className="flex items-center gap-2 rounded-lg bg-background p-3 text-sm text-text/70">
      <Icon
        name="lock"
        size={13}
      />
      {t("post.blockedComment")}
    </div>
    if (editingId === item.commentId) return <div className="flex gap-2">
      {/* 입력창 — setEditingText (화면 상태·상위 콜백 반영) */}
      <input
        className={INPUT_CLASS}
        value={editingText}
        maxLength={1000}
        onChange={(event) => setEditingText(event.target.value)}
      />
      {/* common.save 버튼 — handleSaveComment → PUT /comments/{commentId} · GET /posts/{postId} */}
      <Button
        className="min-h-8 px-3"
        disabled={submitting || sanctionActive || !editingText.trim()}
        onClick={() => void handleSaveComment()}
      >
        {t("common.save")}
      </Button>
      {/* 취소 버튼 — setEditingId (화면 상태·상위 콜백 반영) */}
      <Button
        className="min-h-8 px-3"
        variant="ghost"
        onClick={() => setEditingId(null)}
      >
        {t("common.cancel")}
      </Button>
    </div>
    return <>
      <p className="text-sm leading-6 text-text">
        {item.content}
      </p>
      <div className="mt-2 flex gap-3 text-xs font-semibold text-text/70">
        {/* 답글 버튼 — setReplyTo (화면 상태·상위 콜백 반영) */}
        {!reply && <button
          disabled={sanctionActive}
          onClick={() => {
            setReplyTo(replyTo === item.commentId ? null : item.commentId);
            setReplyText("")
          }}
          className="focus-visible:outline-2 focus-visible:outline-accent"
        >
          {t("post.reply")}
        </button>}
        {/* post.reportButton 버튼 — setReportTarget (화면 상태·상위 콜백 반영) */}
        {!isManager && item.authorType !== "ADMIN" && !item.isMine && item.authorId !== null && <button
          onClick={() => setReportTarget({
            sourceType: "POST",
            sourceId: post!.postId,
            targetType: "COMMENT",
            targetId: String(item.commentId),
            targetName: communityDisplayName(item.authorName, t("common.anonymous")),
            targetUserId: item.authorId!
          })}
          className="focus-visible:outline-2 focus-visible:outline-accent"
        >
          {t("post.reportButton")}
        </button>}
        {item.isMine && <>
          {/* 수정 버튼 — setEditingId (화면 상태·상위 콜백 반영) */}
          <button
            disabled={sanctionActive}
            onClick={() => {
              setEditingId(item.commentId);
              setEditingText(item.content ?? "")
            }}
          >
            {t("common.edit")}
          </button>
          {/* 삭제 버튼 — setDeleteCommentId (화면 상태·상위 콜백 반영) */}
          <button disabled={submitting} onClick={() => setDeleteCommentId(item.commentId)}>
            {t("common.delete")}
          </button>
        </>}
      </div>
    </>
  }
  if (mode !== "create" && (!post || post.postId !== numericPostId || detailQuery.error)) return <main className="px-4 py-6 sm:px-8 md:px-12"><CommunityQueryState loading={detailQuery.loading} error={detailQuery.error} onRetry={detailQuery.refetch} /></main>
  if (mode === "edit" && !post?.isMine) return <main className="px-4 py-6 sm:px-8 md:px-12"><CommunityQueryState error={new Error("본인이 작성한 게시글만 수정할 수 있어요.")} /><Button onClick={() => navigate(`/community/posts/${numericPostId}${buildingQuery}`)}>게시글로 돌아가기</Button></main>
  if (mode === "view" && post?.blocked) return <main className="px-4 py-6 sm:px-8 md:px-12">
    {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
    <section className="flex min-h-72 flex-col items-center justify-center rounded-lg border border-main/15 bg-white p-8 text-center">
      <span className="rounded-full bg-main/10 p-4 text-main">
        <Icon
          name="lock"
          size={26}
        />
      </span>
      <h1 className="mt-4 text-2xl font-semibold text-main sm:text-3xl">
        {t("post.blockedTitle")}
      </h1>
      <p className="mt-2 text-sm text-text/70">
        {t("post.blockedDescription")}
      </p>
      {/* post.toList 버튼 — navigate (관련 화면 이동) */}
      <Button
        className="mt-5"
        onClick={() => navigate(`/community${buildingQuery}`)}
      >
        {t("post.toList")}
      </Button>
    </section>
  </main>
  if (mode === "create" || mode === "edit") return <main className="px-4 py-6 sm:px-8 md:px-12">
    <CommunityQueryState error={actionError ?? buildingsQuery.error ?? sanction.error} loading={isManager && buildingsQuery.loading} onRetry={buildingsQuery.refetch} />
    {isManager && <ManagerViewing buildingName={selectedBuildingName} />}
    <Back onClick={handleLeave}>
      {t("post.back")}
    </Back>
    {!isManager && <SanctionBanner sanction={sanction} />}
    {/* 입력 제출 — handleSubmitPost → POST /posts · PUT /posts/{postId} */}
    <form
      onSubmit={handleSubmitPost}
      className="mx-auto max-w-3xl rounded-lg border border-main/15 bg-white p-5 sm:p-8"
    >
      <p className="text-xs font-bold tracking-[0.16em] text-accent">
        {t("common.eyebrow")}
      </p>
      <h1 className="mt-2 text-2xl font-semibold text-main sm:text-3xl">
        {t(mode === "create" ? isManager ? "post.createNoticeTitle" : "post.createTitle" : "post.editTitle")}
      </h1>
      <p className="mt-2 text-sm text-text/70">
        {t(isManager ? "post.noticeEditorDescription" : "post.editorDescription", { building: selectedBuildingName })}
      </p>
      <div className="mt-7 space-y-5">
        {isManager && <FormField label={t("board.buildingSelect")}>
          {/* 입력창 — 입력값을 상위 폼·조회 조건에 반영 */}
          <input
            readOnly
            value={selectedBuildingName}
            className={INPUT_CLASS}
          />
        </FormField>}
        <FormField label={t("post.category")}>
          {/* 선택 필터 — setForm (화면 상태·상위 콜백 반영) */}
          <select
            disabled={isManager || sanctionActive}
            value={form.category}
            onChange={(event) => setForm({ ...form, category: event.target.value as CommunityPostRequest["category"] })}
            className={INPUT_CLASS}
          >
            {isManager && <option value="NOTICE">
              {t("common.categories.NOTICE")}
            </option>}
            {!isManager && <>
            <option value="FREE">
              {t("common.categories.FREE")}
            </option>
            <option value="SHARE">
              {t("common.categories.SHARE")}
            </option>
            <option value="MEETUP">
              {t("common.categories.MEETUP")}
            </option>
            </>}
          </select>
        </FormField>
        <FormField label={t("post.titleLabel")}>
          {/* 입력창 — setForm (화면 상태·상위 콜백 반영) */}
          <input
            disabled={sanctionActive}
            value={form.title}
            maxLength={100}
            onChange={(event) => setForm({ ...form, title: event.target.value })}
            className={INPUT_CLASS}
            placeholder={t(sanctionActive ? "post.restrictedPlaceholder" : "post.titlePlaceholder")}
          />
        </FormField>
        <FormField label={t("post.contentLabel")}>
          {/* 내용 입력 — setForm (화면 상태·상위 콜백 반영) */}
          <textarea
            disabled={sanctionActive}
            value={form.content}
            maxLength={5000}
            onChange={(event) => setForm({ ...form, content: event.target.value })}
            className={`${INPUT_CLASS} min-h-64 resize-y`}
            placeholder={t(sanctionActive ? "post.restrictedPlaceholder" : "post.contentPlaceholder")}
          />
        </FormField>
      </div>
      <footer className="mt-6 flex justify-end gap-2">
        {/* 취소 버튼 — handleLeave (화면 상태·상위 콜백 반영) */}
        <Button
          variant="secondary"
          onClick={handleLeave}
        >
          {t("common.cancel")}
        </Button>
        {/* 버튼 — handleSubmitPost → POST /posts · PUT /posts/{postId} */}
        <Button
          type="submit"
          disabled={submitting || sanctionActive || (isManager && !validBuilding) || !form.title.trim() || !form.content.trim()}
        >
          {t(mode === "create" ? "common.submit" : "post.completeEdit")}
        </Button>
      </footer>
    </form>
    {confirm === "leave" && <ConfirmModal
      title={t("post.leaveTitle")}
      description={t("post.leaveDescription")}
      confirmLabel={t("post.leave")}
      onCancel={() => setConfirm(null)}
      onConfirm={() => navigate(mode === "edit" ? `/community/posts/${numericPostId}${buildingQuery}` : `/community${buildingQuery}`)}
    />}
  </main>
  // 화면 본문 — setProfile (화면 상태·상위 콜백 반영)
  return <main
    className="px-4 py-6 sm:px-8 md:px-12"
    onClick={() => {
      setProfile(null);
      setMenu(false)
    }}
  >
    <CommunityQueryState error={actionError ?? buildingsQuery.error ?? sanction.error} onRetry={buildingsQuery.refetch} />
    {isManager && <ManagerViewing buildingName={selectedBuildingName} />}
    <Back onClick={() => navigate(`/community${buildingQuery}`)}>
      {t("post.toList")}
    </Back>
    {!isManager && <SanctionBanner sanction={sanction} />}
    <article
      className="rounded-lg border border-main/15 bg-white p-5 sm:p-8"
      onClick={(event) => event.stopPropagation()}
    >
      <header className="flex items-start justify-between gap-4">
        <div>
          <Badge type={post!.category === "NOTICE" ? "accent" : "info"}>
            {t(`common.categories.${post!.category}`)}
          </Badge>
          <h1 className="mt-3 text-2xl font-semibold text-main sm:text-3xl">
            {post!.title}
          </h1>
        </div>
        {post!.isMine ? <div className="flex gap-2">
          {/* 수정 버튼 — navigate (관련 화면 이동) */}
          <Button
            variant="ghost"
            icon="edit"
            disabled={sanctionActive}
            onClick={() => navigate(`/community/posts/${post!.postId}/edit${buildingQuery}`)}
          >
            {t("common.edit")}
          </Button>
          {/* 삭제 버튼 — setConfirm (화면 상태·상위 콜백 반영) */}
          <Button
            variant="ghost"
            icon="trash"
            onClick={() => setConfirm("delete")}
          >
            {t("common.delete")}
          </Button>
        </div> : !isManager && post!.authorType !== "ADMIN" && post!.category !== "NOTICE" && post!.authorId !== null && <div className="relative">
          {/* post.postMenu 버튼 — setMenu (화면 상태·상위 콜백 반영) */}
          <button
            aria-label={t("post.postMenu")}
            onClick={() => setMenu(!menu)}
            className="rounded-lg p-2 text-text/70 focus-visible:outline-2 focus-visible:outline-accent"
          >
            <Icon name="more" />
          </button>
          {menu && <div className="absolute top-full right-0 z-10 mt-1 w-44 rounded-lg border border-main/15 bg-white p-1">
            {/* post.reportPost 버튼 — setReportTarget (화면 상태·상위 콜백 반영) */}
            <button
              onClick={() => setReportTarget({
                sourceType: "POST",
                sourceId: post!.postId,
                targetType: "POST",
                targetId: String(post!.postId),
                targetName: communityDisplayName(post!.authorName, t("common.anonymous")),
                targetUserId: post!.authorId!
              })}
              className="flex w-full items-center gap-2 rounded px-3 py-2 text-sm hover:bg-background"
            >
              <Icon
                name="flag"
                size={13}
              />
              {t("post.reportPost")}
            </button>
            {/* post.blockAuthor 버튼 — handleRequestBlock (화면 상태·상위 콜백 반영) */}
            <button
              onClick={() => handleRequestBlock(post!.authorId!, communityDisplayName(post!.authorName, t("common.anonymous")), "post")}
              className="flex w-full items-center gap-2 rounded px-3 py-2 text-sm hover:bg-background"
            >
              <Icon
                name="lock"
                size={13}
              />
              {t("post.blockAuthor")}
            </button>
          </div>}
        </div>}
      </header>
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-b border-main/15 pb-5">
        <Author
          authorId={post!.authorId}
          authorType={post!.authorType}
          name={communityDisplayName(post!.authorName, t("common.anonymous"))}
          room={post!.authorRoomNumber}
          popupKey="post"
          open={profile}
          onOpenChange={setProfile}
          onChat={handleStartChat}
          onBlock={(id, name) => handleRequestBlock(id, name, "post")}
          onReport={handleReportUser}
          disabled={sanctionActive}
          currentUserId={user.userId}
          viewerIsManager={isManager}
        />
        <span className="flex items-center gap-1 text-xs text-text/70">
          {dateTime(post!.createdAt, i18n.resolvedLanguage ?? i18n.language)}
          ·
          <Icon
            name="eye"
            size={13}
          />
          {t("post.views", { count: post!.viewCount })}
        </span>
      </div>
      <div className="min-h-44 py-8 text-sm leading-7 text-text">
        {post!.content}
      </div>
    </article>


    {/* 내용 영역 — event.stopPropagation (화면 상태·상위 콜백 반영) */}
    <section
      className="mt-5 rounded-lg border border-main/15 bg-white p-5 sm:p-8"
      onClick={(event) => event.stopPropagation()}
    >
      <h2 className="font-semibold text-main">
        {t("post.commentCount", { count: post!.comments.reduce((count, item) => count + 1 + item.replies.length, 0) })}
      </h2>
      {/* 입력 제출 — handleSubmitComment → POST /posts/{postId}/comments · GET /posts/{postId} */}
      <form
        onSubmit={handleSubmitComment}
        className="mt-4 flex gap-3"
      >
        <Avatar
          name={user.name}
          size="sm"
          manager={isManager}
        />
        {/* 내용 입력 — setCommentText (화면 상태·상위 콜백 반영) */}
        <textarea
          disabled={sanctionActive}
          value={commentText}
          maxLength={1000}
          onChange={(event) => setCommentText(event.target.value)}
          className={`${INPUT_CLASS} min-w-0 flex-1 min-h-20 resize-y`}
          placeholder={t(sanctionActive ? "post.restrictedPlaceholder" : "post.commentPlaceholder")}
        />
        {/* 등록 버튼 — handleSubmitComment → POST /posts/{postId}/comments · GET /posts/{postId} */}
        <Button
          type="submit"
          disabled={submitting || sanctionActive || !commentText.trim()}
          className="shrink-0 self-stretch whitespace-nowrap px-5"
        >
          {t("common.submit")}
        </Button>
      </form>
      <div className="mt-6 divide-y divide-main/15">
        {post!.comments.map((comment) => <div
          key={comment.commentId}
          className={`py-5 ${comment.authorType === "ADMIN" ? "border-l-2 border-main bg-main/5 px-4" : ""}`}
        >
          <Comment
            item={comment}
            body={commentBody(comment, false)}
            profile={profile}
            onProfileChange={setProfile}
            onChat={handleStartChat}
            onBlock={(id, name) => handleRequestBlock(id, name, "comment")}
            onReport={handleReportUser}
            disabled={sanctionActive}
            currentUserId={user.userId}
            viewerIsManager={isManager}
            locale={i18n.resolvedLanguage ?? i18n.language}
          />
          {replyTo === comment.commentId && <div className="mt-3 flex gap-2 pl-8">
            <Icon
              name="arrowRight"
              size={14}
            />
            {/* 입력창 — setReplyText (화면 상태·상위 콜백 반영) */}
            <input
              className={`${INPUT_CLASS} min-w-0 flex-1`}
              disabled={sanctionActive}
              value={replyText}
              maxLength={1000}
              onChange={(event) => setReplyText(event.target.value)}
              placeholder={t(sanctionActive ? "post.restrictedPlaceholder" : "post.replyPlaceholder")}
              autoFocus
            />
            {/* 등록 버튼 — handleSubmitReply → POST /posts/{postId}/comments · GET /posts/{postId} */}
            <Button
              disabled={submitting || sanctionActive || !replyText.trim()}
              className="shrink-0 whitespace-nowrap"
              onClick={() => void handleSubmitReply(comment.commentId)}
            >
              {t("common.submit")}
            </Button>
          </div>}
          {comment.replies.map((reply) => <div
            key={reply.commentId}
            className={`mt-4 ml-8 rounded-lg p-4 ${reply.authorType === "ADMIN" ? "border-l-2 border-main bg-main/5" : "bg-background"}`}
          >
            <Comment
              item={reply}
              body={commentBody(reply, true)}
              profile={profile}
              onProfileChange={setProfile}
              onChat={handleStartChat}
              onBlock={(id, name) => handleRequestBlock(id, name, "comment")}
              onReport={handleReportUser}
              disabled={sanctionActive}
              currentUserId={user.userId}
              viewerIsManager={isManager}
              locale={i18n.resolvedLanguage ?? i18n.language}
            />
          </div>)}
        </div>)}
      </div>
    </section>


    {confirm === "delete" && <ConfirmModal
      danger
      title={t("post.deleteTitle")}
      description={t("post.deleteDescription")}
      confirmLabel={t("common.delete")}
      onCancel={() => setConfirm(null)}
      onConfirm={async () => {
        if (submitting) return
        setSubmitting(true)
        setActionError(null)
        try {
          await deleteCommunityPost(post!.postId)
          navigate(`/community${buildingQuery}`)
        } catch (error) { handleActionError(error); setConfirm(null) }
        finally { setSubmitting(false) }
      }}
    />}
    {deleteCommentId !== null && <ConfirmModal danger title="댓글을 삭제할까요?" description="달린 답글은 유지됩니다." confirmLabel={t("common.delete")} onCancel={() => setDeleteCommentId(null)} onConfirm={() => void handleRemoveComment(deleteCommentId)} />}
    {reportTarget && <ReportModal
      {...reportTarget}
      onClose={() => setReportTarget(null)}
      onReported={(blocked) => {
        const value = reportTarget;
        setReportTarget(null);
        if (blocked) void handleAfterBlock(value.targetUserId, value.targetType === "POST" ? "post" : "comment")
      }}
    />}
    {blockTarget && <BlockConfirmModal
      name={blockTarget.name}
      blockedUserId={blockTarget.userId}
      onClose={() => setBlockTarget(null)}
      onBlocked={() => {
        const value = blockTarget;
        setBlockTarget(null);
        void handleAfterBlock(value.userId, value.from)
      }}
    />}
    {toast && <div className="fixed right-6 bottom-20 z-50 rounded-lg bg-main px-5 py-3 text-sm font-semibold text-background">
      {toast}
    </div>}

  </main>
}

function Back({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  // 버튼 — onClick (화면 상태·상위 콜백 반영)
  return <button
    onClick={onClick}
    className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-main focus-visible:outline-2 focus-visible:outline-accent"
  >
    <Icon
      name="chevronLeft"
      size={17}
    />
    {children}
  </button>
}
function FormField({ label, children }: { label: string; children: ReactNode }) {
  return <label className="block text-sm font-semibold text-text">
    <span className="mb-2 block">
      {label}
    </span>
    {children}
  </label>
}
function ManagerViewing({ buildingName }: { buildingName: string }) {
  const { t } = useTranslation("community")
  return <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-main/5 px-3 py-1.5 text-xs font-semibold text-main">
    <VerifiedBadge />
    {t("board.viewingAsManager")}
    ·
    {buildingName}
  </div>
}
function ConfirmModal({ title, description, confirmLabel, danger = false, onCancel, onConfirm }: {
  title: string;
  description: string;
  confirmLabel: string;
  danger?: boolean;
  onCancel: () => void;
  onConfirm: () => void
}) {
  const { t } = useTranslation("community");
  return <div
    className="fixed inset-0 z-50 flex items-center justify-center bg-text/40 p-4"
    onMouseDown={onCancel}
  >
    {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
    <section
      role="dialog"
      aria-modal="true"
      onMouseDown={(event) => event.stopPropagation()}
      className="w-full max-w-md rounded-lg border border-main/15 bg-white p-6 text-center"
    >
      <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-accent/20 text-main">
        <Icon name={danger ? "trash" : "edit"} />
      </span>
      <h2 className="mt-4 text-xl font-semibold text-main">
        {title}
      </h2>
      <p className="mt-2 text-sm text-text/70">
        {description}
      </p>
      <div className="mt-6 flex justify-center gap-2">
        {/* 취소 버튼 — onCancel (화면 상태·상위 콜백 반영) */}
        <Button
          variant="secondary"
          onClick={onCancel}
        >
          {t("common.cancel")}
        </Button>
        {/* 버튼 — onConfirm (화면 상태·상위 콜백 반영) */}
        <Button
          variant={danger ? "danger" : "primary"}
          onClick={onConfirm}
        >
          {confirmLabel}
        </Button>
      </div>
    </section>
  </div>
}
type AuthorProps = {
  authorId: number | null;
  authorType: AuthorType;
  name: string | null;
  room?: string | null;
  popupKey: string;
  open: string | null;
  onOpenChange: (value: string | null) => void;
  onChat: (id: number) => void;
  onBlock: (id: number, name: string) => void;
  onReport: (id: number, name: string) => void;
  disabled: boolean;
  currentUserId: number;
  viewerIsManager: boolean
}
function Author({ authorId, authorType, name, room, popupKey, open, onOpenChange, onChat, onBlock, onReport, disabled, currentUserId, viewerIsManager }: AuthorProps) {
  const { t } = useTranslation("community");
  if (!name) return null;
  const other = !viewerIsManager && authorType !== "ADMIN" && authorId !== null && authorId !== currentUserId;
  return <span className="relative flex items-center gap-2">
    <Avatar
      name={name}
      size="sm"
      manager={authorType === "ADMIN"}
    />
    {/* 버튼 — onOpenChange (화면 상태·상위 콜백 반영) */}
    <button
      disabled={!other}
      onClick={() => onOpenChange(open === popupKey ? null : popupKey)}
      className="text-sm font-semibold text-text focus-visible:outline-2 focus-visible:outline-accent"
    >
      {name}
    </button>
    {authorType === "ADMIN" && <VerifiedBadge />}
    {authorType !== "ADMIN" && room && <small className="text-text/70">
      {room}
    </small>}
    {/* post.chat 버튼 — onChat (화면 상태·상위 콜백 반영) */}
    {other && <button
      disabled={disabled}
      onClick={() => onChat(authorId)}
      className="ml-2 flex items-center gap-1 text-xs font-semibold text-main disabled:opacity-50"
    >
      <Icon
        name="message"
        size={13}
      />
      {t("post.chat")}
    </button>}
    {open === popupKey && other && <span className="absolute top-full left-8 z-10 mt-2 w-48 rounded-lg border border-main/15 bg-white p-3">
      <strong className="block text-sm text-text">
        {name}
      </strong>
      <small className="text-text/70">
        {room}
      </small>
      <span className="mt-3 flex gap-2">
        {/* post.chat 버튼 — onChat (화면 상태·상위 콜백 반영) */}
        <button
          disabled={disabled}
          onClick={() => onChat(authorId)}
          className="text-xs font-semibold text-main"
        >
          {t("post.chat")}
        </button>
        {/* 차단 버튼 — onBlock (화면 상태·상위 콜백 반영) */}
        <button
          onClick={() => onBlock(authorId, name)}
          className="text-xs font-semibold text-accent"
        >
          {t("blockModal.block")}
        </button>
        {/* post.reportButton 버튼 — onReport (화면 상태·상위 콜백 반영) */}
        <button onClick={() => onReport(authorId, name)} className="text-xs font-semibold text-accent">{t("post.reportButton")}</button>
      </span>
    </span>}
  </span>
}
function Comment({ item, body, profile, onProfileChange, onChat, onBlock, onReport, disabled, currentUserId, viewerIsManager, locale }: {
  item: CommunityComment | CommunityReply;
  body: ReactNode;
  profile: string | null;
  onProfileChange: (value: string | null) => void;
  onChat: (id: number) => void;
  onBlock: (id: number, name: string) => void;
  onReport: (id: number, name: string) => void;
  disabled: boolean;
  currentUserId: number;
  viewerIsManager: boolean;
  locale: string
}) {
  const { t } = useTranslation("community")
  const visible = item.status === "ACTIVE" && !item.blocked
  const label = visible ? communityDisplayName(item.authorName, t("common.anonymous")) : item.authorName
  return <div className="flex gap-3">
    <div className="min-w-0 flex-1">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <Author
          authorId={item.authorId}
          authorType={item.authorType}
          name={label}
          room={item.authorRoomNumber}
          popupKey={`comment-${item.commentId}`}
          open={profile}
          onOpenChange={onProfileChange}
          onChat={onChat}
          onBlock={onBlock}
          onReport={onReport}
          disabled={disabled}
          currentUserId={currentUserId}
          viewerIsManager={viewerIsManager}
        />
        <time className="text-xs text-text/70">
          {dateTime(item.createdAt, locale)}
        </time>
      </div>
      {body}
    </div>
  </div>
}
