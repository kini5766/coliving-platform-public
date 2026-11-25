// 고객 지원 위젯 — 역할별 FAQ·쪽지·1:1 채팅·비회원 문의
import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { CommunityApiError } from "../api/communityClient"
import { createGuestInquiry } from "../api/guestInquiryApi"
import { getNotes, sendNote } from "../api/noteApi"
import { useCurrentUser } from "../currentUser"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import type { GuestInquiryRequest, UserNote } from "../types"
import { Icon } from "."
import { ChatPanel } from "./ChatPanel"
import { FaqBotPanel, useFaqConversation } from "./FaqBotPanel"
import { useCommunityRealtime } from "../realtime/CommunityRealtimeProvider"
import { NoteConversation } from "./NoteConversation"
import { CommunityQueryState } from "./CommunityQueryState"
import { useSupportWidget, type SupportWidgetTab } from "./SupportWidgetContext"

const ROLE_TABS: Record<"RESIDENT" | "MEMBER" | "ADMIN", SupportWidgetTab[]> = {
  MEMBER: ["faq", "notes"],
  ADMIN: ["faq", "notes"],
  RESIDENT: [
    "faq",
    "notes",
    "chat"
  ]
}
const EMPTY_INQUIRY: GuestInquiryRequest = {
  name: "",
  phone: "",
  email: "",
  content: "",
  privacyAgreed: false
}
const FIELD = "w-full rounded-lg border border-main/15 bg-white px-3 py-2 text-sm text-text focus-visible:outline-2 focus-visible:outline-accent disabled:bg-background"

export function SupportWidget() {
  const { t, i18n } = useTranslation("community")
  const locale = i18n.resolvedLanguage ?? i18n.language
  const user = useCurrentUser()
  const authenticated = user.isLoggedIn
  const { status: realtimeStatus, onNotification } = useCommunityRealtime()
  const realtimeConnected = realtimeStatus === "connected"
  const { isOpen, activeTab, unreadSummary, closeWidget, setActiveTab, markNotesRead, refreshUnreadSummary } = useSupportWidget()
  const faqConversation = useFaqConversation()
  const [noteText, setNoteText] = useState("")
  const [noteActionError, setNoteActionError] = useState("")
  const [noteScrollKey, setNoteScrollKey] = useState(0)
  const [inquiry, setInquiry] = useState(EMPTY_INQUIRY)
  const [inquirySent, setInquirySent] = useState(false)
  const [inquiryError, setInquiryError] = useState("")
  const [inquirySubmitting, setInquirySubmitting] = useState(false)
  const [privacyOpen, setPrivacyOpen] = useState(false)
  const tabs = useMemo<SupportWidgetTab[]>(() => user.loading ? ["faq"] : authenticated ? ROLE_TABS[user.role] : ["faq", "inquiry"], [authenticated, user.loading, user.role])
  const inquiryValid = inquiry.name.trim() && inquiry.phone.trim() && inquiry.email.trim() && inquiry.content.trim() && inquiry.privacyAgreed
  // loadNotes — GET /notes
  const loadNotes = useCallback(() => getNotes({ page: 0, size: 30 }), [])
  const noteQuery = useCommunityQuery(loadNotes, { enabled: authenticated && isOpen && activeTab === "notes" })
  const refetchNotes = noteQuery.refetch
  const notes = (noteQuery.data?.content ?? []) as UserNote[]

  // 쪽지 탭을 열거나 연결이 바뀌면 조회, 미연결 시 30초마다 갱신
  useEffect(() => {
    if (!authenticated || !isOpen || activeTab !== "notes") return undefined
    void refetchNotes()
    if (realtimeConnected) return undefined
    const interval = window.setInterval(() => void refetchNotes(), 30_000)
    return () => window.clearInterval(interval)
  }, [activeTab, authenticated, isOpen, refetchNotes, realtimeConnected])
  // 쪽지 알림을 받고 쪽지 탭이 열려 있으면 목록 재조회
  useEffect(() => onNotification((notification) => { if (notification.type === "NOTE" && isOpen && activeTab === "notes") void refetchNotes() }), [onNotification, isOpen, activeTab, refetchNotes])

  // 역할 변경으로 현재 탭을 쓸 수 없으면 FAQ 탭 선택
  useEffect(() => {
    if (!tabs.includes(activeTab)) setActiveTab("faq")
  }, [
    activeTab,
    setActiveTab,
    tabs
  ])
  // 쪽지 탭에 대화가 표시되면 모두 읽음 처리와 배지 갱신
  useEffect(() => {
    if (isOpen && activeTab === "notes" && noteQuery.data) {
      void markNotesRead().catch((error) => setNoteActionError(error instanceof Error ? error.message : "읽음 처리에 실패했어요."))
    }
  }, [
    activeTab,
    isOpen,
    markNotesRead,
    noteQuery.data
  ])
  if (!isOpen) return null

  // handleSendNote — POST /notes · GET /notifications/unread-summary
  const handleSendNote = async (event: FormEvent) => {
    event.preventDefault();
    const content = noteText.trim();
    if (!content) return;
    setNoteActionError("")
    try {
      await sendNote({ content })
      setNoteText("")
      await refetchNotes()
      setNoteScrollKey((value) => value + 1)
      await refreshUnreadSummary()
    } catch (caught) {
      setNoteActionError(caught instanceof Error ? caught.message : "쪽지를 보내지 못했어요.")
    }
  }
  const handleReloadNotes = async () => {
    await refetchNotes()
    setNoteScrollKey((value) => value + 1)
  }
  // handleSubmitInquiry — POST /guest-inquiries
  const handleSubmitInquiry = async (event: FormEvent) => {
    event.preventDefault();
    if (!inquiryValid) return;
    setInquirySubmitting(true)
    setInquiryError("")
    try {
      await createGuestInquiry(inquiry)
      setInquirySent(true)
    } catch (caught) {
      setInquiryError(caught instanceof CommunityApiError && (caught.status === 429 || caught.code === "TOO_MANY_REQUESTS") ? "10분 후 다시 시도해 주세요." : caught instanceof Error ? caught.message : "문의를 접수하지 못했어요.")
    } finally {
      setInquirySubmitting(false)
    }
  }
  // widget.ariaLabel 내용 영역 — 상위 조회 결과·선택 상태 표시
  return <section
    aria-label={t("widget.ariaLabel")}
    className="fixed right-4 bottom-20 z-40 flex h-[min(650px,calc(100dvh-7rem))] w-[min(390px,calc(100vw-2rem))] flex-col overflow-hidden rounded-lg border border-main/15 bg-white shadow-xl sm:right-6"
  >

    <div
      className="grid bg-background"
      style={{ gridTemplateColumns: `repeat(${tabs.length}, minmax(0, 1fr))` }}
    >
      {tabs.map((tab) => {
        const count = tab === "notes" ? unreadSummary.note : tab === "chat" ? unreadSummary.chat : 0;
        // 버튼 — setActiveTab (화면 상태·상위 콜백 반영)
        return <button
          key={tab}
          onClick={() => setActiveTab(tab)}
          className={`relative border-b-2 px-2 py-3 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-accent ${activeTab === tab ? "border-main text-main" : "border-transparent text-text/70"}`}
        >
          {t(`widget.tabs.${tab}`)}
          {count > 0 && <span className="ml-1 rounded-full bg-accent px-1.5 py-0.5 text-[10px] text-text">
            {count}
          </span>}
        </button>
      })}
    </div>


    <header className="flex min-h-16 items-center gap-3 border-b border-main/15 px-4">

      <>
        <span className="rounded-full bg-main/10 p-2 text-main">
          <Icon
            name={activeTab === "faq" ? "bot" : activeTab === "chat" ? "message" : "mail"}
            size={20}
          />
        </span>
        <div className="min-w-0 flex-1">
          <strong className="block text-sm text-main">
            {t(activeTab === "faq" ? "widget.helper" : activeTab === "inquiry" ? "widget.tabs.inquiry" : activeTab === "notes" ? "widget.managerNotes" : "widget.residentChat")}
          </strong>
          <span className="text-xs text-text/70">
            {t(activeTab === "faq" ? "widget.autoReply" : activeTab === "notes" ? "widget.notesSubtitle" : activeTab === "chat" ? "widget.chatSubtitle" : "widget.inquirySubtitle")}
          </span>
        </div>
      </>


      {/* 닫기 버튼 — closeWidget (화면 상태·상위 콜백 반영) */}
      <button
        aria-label={t("widget.close")}
        onClick={closeWidget}
        className="rounded p-2 text-text/70 focus-visible:outline-2 focus-visible:outline-accent"
      >
        <Icon name="close" />
      </button>

    </header>



    {user.error && user.isLoggedIn && <div className="shrink-0 p-2"><CommunityQueryState error={user.error} onRetry={user.retry} /></div>}
    {user.known === false && <p className="shrink-0 p-3 text-sm text-text/70">커뮤니티 회원 정보를 찾을 수 없어요. 관리자에게 문의해 주세요.</p>}
    {/* FAQ 상담 — setNoteText (화면 상태·상위 콜백 반영) */}
    {activeTab === "faq" && <FaqBotPanel conversation={faqConversation} authenticated={authenticated} onAction={(action, question) => {
      if (action === "NOTE") { setNoteText(question); setActiveTab("notes") }
      else { setInquiry((current) => ({ ...current, content: question })); setInquirySent(false); setActiveTab("inquiry") }
    }} />}

    {activeTab === "inquiry" && <div
      className="flex-1 overflow-y-auto p-4"
    >
      {/* 입력 제출 — handleSubmitInquiry → POST /guest-inquiries */}
      {inquirySent ? <div className="flex h-full flex-col items-center justify-center text-center">
        <span className="rounded-full bg-main/10 p-3 text-main">
          <Icon
            name="mail"
            size={24}
          />
        </span>
        <strong className="mt-4 text-text">
          {t("widget.inquiryDone")}
        </strong>
        <p className="mt-1 text-sm text-text/70">
          {t("widget.inquiryReply")}
        </p>
        {/* widget.newInquiry 보조 버튼 — setInquiry (화면 상태·상위 콜백 반영) */}
        <SmallButton onClick={() => {
          setInquiry(EMPTY_INQUIRY);
          setInquirySent(false);
          setInquiryError("")
        }}>
          {t("widget.newInquiry")}
        </SmallButton>
      </div> : <form
        onSubmit={handleSubmitInquiry}
        className="space-y-3"
      >
        <p className="rounded-lg bg-background p-3 text-xs text-text/70">
          {t("widget.inquiryGuide")}
        </p>
        <WidgetField label={t("widget.name")}>
          {/* 입력창 — setInquiry (화면 상태·상위 콜백 반영) */}
          <input
            className={FIELD}
            value={inquiry.name}
            onChange={(event) => setInquiry({ ...inquiry, name: event.target.value })}
          />
        </WidgetField>
        <WidgetField label={t("widget.phone")}>
          {/* 010-0000-0000 입력창 — setInquiry (화면 상태·상위 콜백 반영) */}
          <input
            className={FIELD}
            value={inquiry.phone}
            onChange={(event) => setInquiry({ ...inquiry, phone: event.target.value })}
            placeholder="010-0000-0000"
          />
        </WidgetField>
        <WidgetField label={t("widget.email")}>
          {/* 입력창 — setInquiry (화면 상태·상위 콜백 반영) */}
          <input
            className={FIELD}
            type="email"
            value={inquiry.email}
            onChange={(event) => setInquiry({ ...inquiry, email: event.target.value })}
          />
        </WidgetField>
        <WidgetField label={t("widget.inquiryContent")}>
          {/* 내용 입력 — setInquiry (화면 상태·상위 콜백 반영) */}
          <textarea
            className={`${FIELD} min-h-20 resize-y`}
            value={inquiry.content}
            onChange={(event) => setInquiry({ ...inquiry, content: event.target.value })}
          />
        </WidgetField>
        <label className="flex items-center gap-2 text-xs text-text">
          {/* 입력창 — setInquiry (화면 상태·상위 콜백 반영) */}
          <input
            type="checkbox"
            className="accent-main"
            checked={inquiry.privacyAgreed}
            onChange={(event) => setInquiry({ ...inquiry, privacyAgreed: event.target.checked })}
          />
          {t("widget.privacyConsent")}
        </label>
        {/* widget.details 버튼 — setPrivacyOpen (화면 상태·상위 콜백 반영) */}
        <button
          type="button"
          onClick={() => setPrivacyOpen(!privacyOpen)}
          className="text-xs font-semibold text-main"
        >
          {t("widget.details")}

          <Icon
            name="chevronDown"
            size={12}
          />
        </button>
        {privacyOpen && <p className="rounded-lg bg-background p-3 text-xs text-text/70">
          {t("widget.privacyDetail")}
        </p>}
        {inquiryError && <p className="rounded-lg bg-accent/20 p-3 text-xs text-text">{inquiryError}</p>}
        {/* 버튼 — handleSubmitInquiry → POST /guest-inquiries */}
        <button
          disabled={!inquiryValid || inquirySubmitting}
          className="w-full rounded-lg bg-main py-2.5 text-sm font-semibold text-background disabled:opacity-50"
        >
          {inquirySubmitting ? t("common.processing") : t("widget.submitInquiry")}
        </button>
      </form>}
    </div>}


    {activeTab === "notes" && <>
      <div className="shrink-0 bg-background px-4 pt-3 text-center text-xs text-text/70">망원 스테이 매니저</div>
      {noteQuery.loading && !noteQuery.data && <p className="shrink-0 bg-background p-3 text-center text-xs text-text/70">불러오는 중...</p>}
      {noteQuery.error && <div className="shrink-0 bg-background px-4 pt-3 text-center text-xs text-text/70">
        <p>{noteQuery.error instanceof Error ? noteQuery.error.message : "쪽지를 불러오지 못했어요."}</p>
        {/* 다시 불러오기 버튼 — handleReloadNotes (화면 상태·상위 콜백 반영) */}
        <button className="mt-1 font-semibold text-main" onClick={() => void handleReloadNotes()}>다시 불러오기</button>
      </div>}
      {noteActionError && <p className="shrink-0 bg-accent/20 p-3 text-center text-xs text-text">{noteActionError}</p>}
      {/* 날짜·말풍선·읽음 대화 — 상위 조회 결과·선택 상태 표시 */}
      <NoteConversation
        notes={notes}
        outgoingSender="USER"
        active={isOpen && activeTab === "notes"}
        forceScrollKey={noteScrollKey}
        locale={locale}
        outgoingStatus={(note) => t(note.readAt ? "widget.read" : "widget.sent")}
      />
      {/* widget.notePlaceholder 메시지 작성 — setNoteText·handleSendNote → POST /notes · GET /notifications/unread-summary */}
      <Composer
        value={noteText}
        onValueChange={setNoteText}
        onSubmit={handleSendNote}
        placeholder={t("widget.notePlaceholder")}
      />
    </>}


    {/* 입주자 채팅 */}
    {activeTab === "chat" && <ChatPanel />}

  </section>
}

function SmallButton({ children, onClick }: { children: React.ReactNode; onClick: () => void }) {
  // 버튼 — onClick (화면 상태·상위 콜백 반영)
  return <button type="button" onClick={onClick} className="mt-3 rounded-lg border border-main/15 bg-white px-3 py-2 text-xs font-semibold text-main">{children}</button>
}
function WidgetField({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block text-xs font-semibold text-text">
    {label}

    <b className="text-accent">*</b>
    <span className="mt-1 block">
      {children}
    </span>
  </label>
}
function Composer({ value, onValueChange, onSubmit, placeholder, disabled = false }: {
  value: string;
  onValueChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
  placeholder: string;
  disabled?: boolean
}) {
  const { t } = useTranslation("community");
  // 입력 제출 — onSubmit (화면 상태·상위 콜백 반영)
  return <form
    onSubmit={onSubmit}
    className="flex gap-2 border-t border-main/15 p-3"
  >
    {/* 입력창 — onValueChange (화면 상태·상위 콜백 반영) */}
    <input
      disabled={disabled}
      value={value}
      onChange={(event) => onValueChange(event.target.value)}
      placeholder={placeholder}
      className={FIELD}
    />
    {/* 보내기 버튼 — onSubmit (화면 상태·상위 콜백 반영) */}
    <button
      disabled={disabled || !value.trim()}
      aria-label={t("widget.send")}
      className="rounded-lg bg-main p-2 text-background disabled:opacity-50"
    >
      <Icon
        name="send"
        size={18}
      />
    </button>
  </form>
}
