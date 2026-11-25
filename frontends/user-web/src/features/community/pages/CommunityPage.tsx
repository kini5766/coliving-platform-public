// 커뮤니티 게시판 — 지점별 공지·게시글·내 활동·인기글
import { useCallback, useEffect, useMemo, useState } from "react"
import { useNavigate, useSearchParams } from "react-router"
import { useTranslation } from "react-i18next"
import { getBlocks, getCommunityBuildings, getCommunityPosts, getCommunitySummary } from "../api"
import { Avatar, Badge, Button, Icon, PageHeader, SearchField } from "../components"
import { VerifiedBadge } from "../components/VerifiedBadge"
import { CommunityRestricted } from "../components/CommunityRestricted"
import { SanctionBanner } from "../components/SanctionBanner"
import { useMySanction } from "../components/useMySanction"
import { CommunityQueryState } from "../components/CommunityQueryState"
import { isCommunityAccessError } from "../api/communityErrors"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import { communityDisplayName, useCurrentUser } from "../currentUser"
import type { AuthorType, CommunityBuilding, CommunityPostListResponse, CommunitySummary, PostCategory } from "../types"

const CATEGORIES: (PostCategory | undefined)[] = [
  undefined,
  "FREE",
  "SHARE",
  "MEETUP"
]
const displayDate = (value: string, locale: string) => new Date(value).toLocaleDateString(locale, { month: "numeric", day: "numeric" })

export default function CommunityPage({ title }: { title: string }) {
  const { t, i18n } = useTranslation("community");
  const user = useCurrentUser();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const isManager = user.role === "ADMIN";
  const sanction = useMySanction(!isManager)
  const queryBuildingId = Number(searchParams.get("buildingId"));
  const [tab, setTab] = useState<PostCategory | undefined>();
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(0)
  const canLoad = user.isLoggedIn && (user.role === "RESIDENT" || isManager)
  // loadBuildings — GET /admin/buildings
  const loadBuildings = useCallback(() => getCommunityBuildings(), [])
  const buildingQueryState = useCommunityQuery(loadBuildings, { enabled: canLoad && isManager })
  const buildings = useMemo(() => (buildingQueryState.data ?? []) as CommunityBuilding[], [buildingQueryState.data])
  const validBuilding = buildings.some((building) => building.buildingId === queryBuildingId)
  // loadPosts — GET /posts
  const loadPosts = useCallback(() => getCommunityPosts({ buildingId: isManager ? queryBuildingId : undefined, category: tab, keyword, page, size: 20 }), [isManager, queryBuildingId, tab, keyword, page])
  const postQuery = useCommunityQuery(loadPosts, { enabled: canLoad && (!isManager || validBuilding) })
  const response = postQuery.data as CommunityPostListResponse | null
  const buildingDisplayName = isManager ? buildings.find((building) => building.buildingId === queryBuildingId)?.name ?? response?.buildingName ?? "" : user.buildingName
  // loadSummary — GET /me/summary
  const loadSummary = useCallback(() => getCommunitySummary(), [])
  const summaryQuery = useCommunityQuery(loadSummary, { enabled: canLoad && !isManager })
  const summary = summaryQuery.data as CommunitySummary | null
  // loadBlocks — GET /blocks
  const loadBlocks = useCallback(() => getBlocks(), [])
  const blockQuery = useCommunityQuery(loadBlocks, { enabled: canLoad && !isManager })
  const refetchPosts = postQuery.refetch
  const refetchBlocks = blockQuery.refetch
  const refetchSummary = summaryQuery.refetch
  const blockedCount = blockQuery.data?.blocks.length ?? 0
  const activeSanction = sanction.active ? sanction : summary?.sanction ?? sanction
  const popular = summary?.popularPosts ?? []
  // 지점·언어가 바뀌면 브라우저 제목 반영
  useEffect(() => {
    document.title = t("board.title", { building: buildingDisplayName, defaultValue: title })
  }, [
    i18n.language,
    buildingDisplayName,
    t,
    title,
    user.buildingName
  ])
  // 관리자의 지점 선택이 유효하지 않으면 첫 지점을 URL에 반영
  useEffect(() => {
    if (!isManager || !buildings.length || validBuilding) return
    setSearchParams((current) => { const next = new URLSearchParams(current); next.set("buildingId", String(buildings[0].buildingId)); return next }, { replace: true })
  }, [
    isManager,
    buildings,
    validBuilding,
    setSearchParams
  ])
  // 차단 변경 이벤트를 받으면 게시글·차단 수·내 활동 재조회
  useEffect(() => {
    const handleBlocksChanged = () => { void refetchPosts(); void refetchBlocks(); void refetchSummary() }
    window.addEventListener("community:blocks-changed", handleBlocksChanged)
    return () => window.removeEventListener("community:blocks-changed", handleBlocksChanged)
  }, [refetchPosts, refetchBlocks, refetchSummary])
  const accessError = [postQuery.error, buildingQueryState.error, summaryQuery.error, blockQuery.error].find(isCommunityAccessError)
  if (accessError) return <CommunityQueryState error={accessError} />
  if (user.loading) return <CommunityQueryState loading />
  if (user.role !== "RESIDENT" && !isManager) return <CommunityRestricted />
  const buildingQuery = isManager && Number.isFinite(queryBuildingId) ? `?buildingId=${queryBuildingId}` : "";
  // 화면 본문 — 상위 조회 결과·선택 상태 표시
  return <main className="px-4 py-6 sm:px-8 md:px-12">

    {isManager && <div className="mb-4 flex flex-wrap items-center gap-3">
      <span className="inline-flex items-center gap-2 rounded-full bg-main/5 px-3 py-1.5 text-xs font-semibold text-main">
        <VerifiedBadge />
        {t("board.viewingAsManager")}
        ·
        {buildingDisplayName}
      </span>
      <label className="flex items-center gap-2 text-sm font-semibold text-text">
        {t("board.buildingSelect")}
        {/* 선택 필터 — setPage → GET /posts */}
        <select
          value={Number.isFinite(queryBuildingId) ? queryBuildingId : ""}
          onChange={(event) => { setPage(0); const next = new URLSearchParams(searchParams); next.set("buildingId", event.target.value); setSearchParams(next) }}
          className="rounded-lg border border-main/15 bg-white px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-accent"
        >
          {buildings.map((building) => <option
            key={building.buildingId}
            value={building.buildingId}
          >
            {building.name}
          </option>)}
        </select>
      </label>
    </div>}

    {/* 제목 화면 제목·상단 동작 */}
    {/* 버튼 — navigate (관련 화면 이동) */}
    <PageHeader
      eyebrow={t("common.eyebrow")}
      title={t("board.title", { building: buildingDisplayName })}
      description={t("board.description")}
      actions={<Button
        icon="edit"
        disabled={isManager ? !validBuilding : activeSanction.active || summaryQuery.loading || sanction.loading}
        onClick={() => navigate(`/community/write${buildingQuery}`)}
      >
        {t(isManager ? "board.writeNotice" : "board.write")}
      </Button>}
    />


    {!isManager && <SanctionBanner sanction={activeSanction} />}
    {!isManager && <CommunityQueryState error={sanction.error} />}
    {!isManager && searchParams.has("sanction") && !activeSanction.active && <p id="community-sanction" role="status" className="mb-5 rounded-lg border border-main/15 bg-white p-4 text-sm text-text">현재 진행 중인 커뮤니티 이용 제한이 없어요.</p>}
    <CommunityQueryState error={buildingQueryState.error} loading={isManager && buildingQueryState.loading} onRetry={buildingQueryState.refetch} />
    {isManager && !buildingQueryState.loading && !buildingQueryState.error && buildings.length === 0 && <p className="mb-4 text-sm text-text/70">선택할 수 있는 건물이 없어요.</p>}


    <div className={`grid gap-5 ${isManager ? "" : "xl:grid-cols-[minmax(0,1fr)_300px]"}`}>

      {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
      <section className="overflow-hidden rounded-lg border border-main/15 bg-white">
        <div className="flex flex-col gap-3 border-b border-main/15 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap gap-1">
            {/* 버튼 — setTab → GET /posts */}
            {CATEGORIES.map((value) => <button
              key={value ?? "ALL"}
              onClick={() => { setTab(value); setPage(0) }}
              className={`rounded-lg px-3 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-accent ${tab === value ? "bg-main text-background" : "text-text/70 hover:bg-background"}`}
            >
              {value ? t(`common.categories.${value}`) : t("board.all")}
            </button>)}
          </div>
          <div className="w-full sm:w-64">
            {/* 검색 검색 입력 — setKeyword → GET /posts */}
            <SearchField
              value={keyword}
              onChange={(value) => { setKeyword(value); setPage(0) }}
              placeholder={t("board.search")}
            />
          </div>
        </div>


        <CommunityQueryState error={postQuery.error} loading={postQuery.loading} onRetry={postQuery.refetch} />
        <div className="border-b border-main/15 bg-background p-3">
          {/* 버튼 — navigate (관련 화면 이동) */}
          {!postQuery.loading && !postQuery.error && response?.pinned.map((post) => <button
            key={post.postId}
            onClick={() => navigate(`/community/posts/${post.postId}${buildingQuery}`)}
            className="flex w-full items-center gap-3 rounded-lg border border-main/15 bg-white p-4 text-left focus-visible:outline-2 focus-visible:outline-accent"
          >
            <span className="rounded-full bg-accent/20 p-2 text-main">
              <Icon
                name="pin"
                size={16}
              />
            </span>
            <span className="min-w-0 flex-1">
              <Badge type="accent">
                {t("board.buildingNotice")}
              </Badge>
              <strong className="mt-1 block truncate text-sm text-text">
                {post.title}
              </strong>
              <small className="flex items-center gap-1 text-text/70">
                <AuthorName name={communityDisplayName(post.authorName, t("common.anonymous"))} authorType={post.authorType} />
                ·
                {displayDate(post.createdAt, i18n.resolvedLanguage ?? i18n.language)}
              </small>
            </span>
            <span className="flex items-center gap-1 text-xs text-text/70">
              <Icon
                name="eye"
                size={14}
              />
              {post.viewCount}
            </span>
            <Icon
              name="chevronRight"
              size={16}
            />
          </button>)}
        </div>


        <div>
          {/* 버튼 — navigate (관련 화면 이동) */}
          {!postQuery.loading && !postQuery.error && response?.content.map((post) => <button
            key={post.postId}
            onClick={() => navigate(`/community/posts/${post.postId}${buildingQuery}`)}
            className="grid w-full grid-cols-[56px_minmax(0,1fr)_auto] items-center gap-3 border-b border-main/15 p-4 text-left hover:bg-background focus-visible:outline-2 focus-visible:outline-accent"
          >
            <span className="text-xs font-semibold text-main">
              {t(`common.categories.${post.category}`)}
            </span>
            <span className="min-w-0">
              <strong className="flex items-center gap-2 truncate text-sm text-text">
                {post.title}
                {post.isNew && <i className="rounded bg-accent px-1.5 py-0.5 text-[10px] not-italic text-text">N</i>}
              </strong>
              <span className="mt-1 flex items-center gap-2 text-xs text-text/70">
                <AuthorName name={communityDisplayName(post.authorName, t("common.anonymous"))} authorType={post.authorType} />
                {post.authorType !== "ADMIN" && post.authorRoomNumber && <Badge type="accent">
                  {post.authorRoomNumber}
                </Badge>}
                {displayDate(post.createdAt, i18n.resolvedLanguage ?? i18n.language)}
              </span>
            </span>
            <span className="flex gap-3 text-xs text-text/70">
              <i className="flex items-center gap-1 not-italic">
                <Icon
                  name="eye"
                  size={14}
                />
                {post.viewCount}
              </i>
              <i className="flex items-center gap-1 not-italic">
                <Icon
                  name="message"
                  size={14}
                />
                {post.commentCount}
              </i>
            </span>
          </button>)}
          {!postQuery.loading && !postQuery.error && response && response.content.length === 0 && <div className="p-16 text-center text-sm text-text/70">
            {t("board.empty")}
          </div>}
        </div>


        <footer className="flex items-center justify-center gap-3 p-4">
          {/* board.previous 버튼 — setPage → GET /posts */}
          <Button
            variant="ghost"
            icon="chevronLeft"
            disabled={page === 0 || postQuery.loading}
            onClick={() => setPage((value) => value - 1)}
          >
            {t("board.previous")}
          </Button>
          <span className="text-sm font-semibold text-main">{response?.totalPages ? page + 1 : 0} / {response?.totalPages ?? 0}</span>
          {/* board.next 버튼 — setPage → GET /posts */}
          <Button variant="ghost" disabled={postQuery.loading || page + 1 >= (response?.totalPages ?? 0)} onClick={() => setPage((value) => value + 1)}>
            {t("board.next")}

            <Icon
              name="chevronRight"
              size={15}
            />
          </Button>
        </footer>

      </section>


      {/* 상세·보조 영역 — 상위 조회 결과·선택 상태 표시 */}
      {!isManager && <aside className="space-y-4">
        <CommunityQueryState error={summaryQuery.error} loading={summaryQuery.loading} onRetry={summaryQuery.refetch} />
        <CommunityQueryState error={blockQuery.error} onRetry={blockQuery.refetch} />
        {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
        <section className="rounded-lg border border-main/15 bg-white p-5">
          <p className="text-xs font-bold tracking-[0.14em] text-accent">MY COMMUNITY</p>
          <div className="mt-4 flex items-center gap-3">
            <Avatar
              name={user.name}
              size="lg"
            />
            <div>
              <h2 className="font-semibold text-main">
                {t("board.greeting", { name: user.name })}
              </h2>
              <p className="text-xs text-text/70">
                {user.buildingName}
                ·
                {user.roomNumber}
              </p>
            </div>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-2 rounded-lg bg-background p-3 text-center text-xs text-text/70">
            <span>
              <strong className="block text-lg text-main">{summary?.postCount ?? "—"}</strong>
              {t("board.writtenPosts")}
            </span>
            <span>
              <strong className="block text-lg text-main">{summary?.receivedCommentCount ?? "—"}</strong>
              {t("board.receivedComments")}
            </span>
          </div>
          {/* board.blockManagement 버튼 — navigate (관련 화면 이동) */}
          <button
            onClick={() => navigate("/community/blocks")}
            className="mt-4 flex w-full items-center gap-2 rounded-lg px-2 py-2 text-sm text-text/70 hover:bg-background focus-visible:outline-2 focus-visible:outline-accent"
          >
            <Icon
              name="lock"
              size={13}
            />
            {t("board.blockManagement")}

            <strong className="ml-auto text-main">
              {blockedCount}
            </strong>
            <Icon
              name="chevronRight"
              size={13}
            />
          </button>
        </section>


        {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
        <section className="rounded-lg border border-main/15 bg-white p-5">
          <div className="flex items-center justify-between">
            <p className="text-xs font-bold tracking-[0.14em] text-accent">
              {t("board.popular")}
            </p>
            {/* board.viewAll 버튼 — setTab → GET /posts */}
            <button
              onClick={() => {
                setTab(undefined);
                setKeyword("");
                setPage(0)
              }}
              className="text-xs font-semibold text-main focus-visible:outline-2 focus-visible:outline-accent"
            >
              {t("board.viewAll")}
            </button>
          </div>
          <div className="mt-3">
            {/* 버튼 — navigate (관련 화면 이동) */}
            {popular.map((post, index) => <button
              key={post.postId}
              onClick={() => navigate(`/community/posts/${post.postId}`)}
              className="flex w-full gap-3 border-t border-main/15 py-3 text-left first:border-0 focus-visible:outline-2 focus-visible:outline-accent"
            >
              <strong className="text-accent">0
                {index + 1}
              </strong>
              <span className="min-w-0">
                <strong className="block truncate text-sm text-text">
                  {post.title}
                </strong>
                <small className="text-text/70">
                  {t("board.views", { count: post.viewCount })}
                  ·
                  {t("board.comments", { count: post.commentCount })}
                </small>
              </span>
            </button>)}
          </div>
        </section>


        <div className="flex items-center gap-3 rounded-lg bg-main p-4 text-background">
          <Icon name="users" />
          <div>
            <strong className="text-sm">
              {t("board.guide")}
            </strong>
            <p className="text-xs opacity-80">
              {t("board.guideDescription")}
            </p>
          </div>
          <Icon name="chevronRight" />
        </div>

      </aside>}

    </div>

  </main>
}

function AuthorName({ name, authorType }: { name: string; authorType: AuthorType }) {
  return <span className="inline-flex min-w-0 items-center gap-1">
    <span className="truncate">{name}</span>
    {authorType === "ADMIN" && <VerifiedBadge />}
  </span>
}
