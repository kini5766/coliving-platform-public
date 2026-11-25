// 입주·퇴거 달력 — 계약 목록을 한 번 조회하고 월·지점별 일정 계산
import { type CSSProperties, useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useNavigate } from "react-router"
import { getCommunityBuildings } from "../api"
import { getContractResidents } from "../api/residentApi"
import { Badge, Button, Icon, PageHeader, Select } from "../components"
import { ContractQueryState } from "../components/ContractQueryState"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import type { CommunityBuilding, ContractResident } from "../types"
import { buildMoveSchedules } from "../utils/residentStatus"

type Props = { title: string }
const getKoreaToday = () => {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: "Asia/Seoul", year: "numeric", month: "numeric", day: "numeric" }).formatToParts(new Date())
  const part = (type: string) => Number(parts.find((item) => item.type === type)?.value)
  return { year: part("year"), month: part("month"), day: part("day") }
}

export default function CalendarPage({ title }: Props) {
  const navigate = useNavigate()
  const today = getKoreaToday()
  const [view, setView] = useState(() => getKoreaToday())
  const { year, month } = view
  const [selectedDay, setSelectedDay] = useState(() => getKoreaToday().day)
  const [branch, setBranch] = useState("")
  const [pickerOpen, setPickerOpen] = useState(false)
  const [pickerYear, setPickerYear] = useState(today.year)
  const pickerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  // loadBuildings — GET /admin/buildings
  const loadBuildings = useCallback(() => getCommunityBuildings(), [])
  const buildingsQuery = useCommunityQuery(loadBuildings)
  const buildings = (buildingsQuery.data ?? []) as CommunityBuilding[]
  // loadResidents — GET /contract-service/admin/applications
  const loadResidents = useCallback(() => getContractResidents(), [])
  const residentsQuery = useCommunityQuery(loadResidents)
  const events = useMemo(() => buildMoveSchedules((residentsQuery.data ?? []) as ContractResident[], { year, month, buildingName: branch || undefined }), [residentsQuery.data, year, month, branch])

  // 화면 제목이 바뀌면 브라우저 제목 반영
  useEffect(() => { document.title = title }, [title])
  // 보는 달이 바뀌면 이번 달은 오늘, 다른 달은 1일 선택
  useEffect(() => {
    const current = getKoreaToday()
    setSelectedDay(year === current.year && month === current.month ? current.day : 1)
  }, [year, month])
  // 월 선택 창이 열리면 선택 월에 초점 이동·바깥 클릭·Esc 닫기 등록
  useEffect(() => {
    if (!pickerOpen) return
    const focusedMonth = pickerRef.current?.querySelector<HTMLButtonElement>('button[aria-pressed="true"]')
      ?? pickerRef.current?.querySelector<HTMLButtonElement>('button[data-month]')
    focusedMonth?.focus()
    const outside = (event: PointerEvent) => {
      if (!pickerRef.current?.contains(event.target as Node)) setPickerOpen(false)
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); setPickerOpen(false); triggerRef.current?.focus() }
    }
    document.addEventListener("pointerdown", outside)
    document.addEventListener("keydown", escape)
    return () => {
      document.removeEventListener("pointerdown", outside)
      document.removeEventListener("keydown", escape)
    }
  }, [pickerOpen])
  const moveMonth = (offset: number) => {
    const date = new Date(Date.UTC(year, month - 1 + offset, 1))
    setView({ year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, day: 1 })
    setPickerOpen(false)
  }
  const firstWeekday = new Date(Date.UTC(year, month - 1, 1)).getUTCDay()
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const calendarDays = Array.from({ length: Math.ceil((firstWeekday + daysInMonth) / 7) * 7 }, (_, index) => {
    const date = new Date(Date.UTC(year, month - 1, index - firstWeekday + 1))
    return { day: date.getUTCDate(), current: date.getUTCMonth() === month - 1 }
  })
  const selectedEvents = events[selectedDay] ?? []
  const monthEvents = Object.values(events).flat()
  const residentsById = useMemo(() => new Map(((residentsQuery.data ?? []) as ContractResident[]).map((resident) => [resident.applicationId, resident])), [residentsQuery.data])
  const selectedWeekday = new Intl.DateTimeFormat("ko-KR", { weekday: "short", timeZone: "UTC" }).format(new Date(Date.UTC(year, month - 1, selectedDay)))
  const selectedGroups = (["in", "out"] as const).map((type) => ({ type, label: type === "in" ? "입주" : "퇴거", events: selectedEvents.filter((event) => event.type === type) }))

  return <div>

    {/* 화면 제목·상단 동작 */}
    {/* 선택 필터 — setBranch → buildMoveSchedules(추가 API 호출 없음) */}
    <PageHeader
      eyebrow="SCHEDULE"
      title={title}
      description="입주와 퇴거 일정을 한눈에 확인하세요."
      actions={<Select
        value={branch}
        onChange={setBranch}
      >
        <option value="">전체 지점</option>
        {buildings.map((building) => <option key={building.buildingId} value={building.name}>{building.name}</option>)}
      </Select>}
    />


    <div className="mb-4 flex flex-wrap items-center gap-3">

      {/* 이전 달 버튼 — moveMonth (화면 상태·상위 콜백 반영) */}
      <button
        aria-label="이전 달"
        onClick={() => moveMonth(-1)}
        className="rounded-lg border border-main/15 bg-white p-2 text-main focus-visible:outline-2 focus-visible:outline-accent"
      >
        <Icon name="chevronLeft" />
      </button>
      <div className="relative" ref={pickerRef}>
        {/* 년 월 버튼 — setPickerYear (화면 상태·상위 콜백 반영) */}
        <button ref={triggerRef} type="button" aria-expanded={pickerOpen} aria-controls="calendar-month-picker" onClick={() => {
          setPickerYear(Math.max(today.year - 3, Math.min(today.year + 2, year)))
          setPickerOpen((open) => !open)
        }} className="inline-flex items-center gap-2 rounded-lg px-2 py-2 text-lg font-semibold text-main focus-visible:outline-2 focus-visible:outline-accent">
          {year}년 {month}월 <Icon name="chevronDown" size={16} />
        </button>
        {pickerOpen && <div id="calendar-month-picker" role="group" aria-label="연도와 월 선택" className="absolute top-full left-0 z-20 mt-2 w-64 rounded-lg border border-main/15 bg-white p-4 shadow-lg" onBlur={(event) => {
          if (event.relatedTarget && !event.currentTarget.parentElement?.contains(event.relatedTarget as Node)) setPickerOpen(false)
        }}>
          {/* 연도 선택 필터 — setPickerYear (화면 상태·상위 콜백 반영) */}
          <Select label="연도" value={pickerYear} onChange={(value) => setPickerYear(Number(value))}>
            {Array.from({ length: 6 }, (_, index) => today.year - 3 + index).map((value) => <option key={value} value={value}>{value}년</option>)}
          </Select>
          <div className="mt-3 grid grid-cols-3 gap-2">
            {/* 월 버튼 — setView (화면 상태·상위 콜백 반영) */}
            {Array.from({ length: 12 }, (_, index) => index + 1).map((value) => <Button key={value} data-month={value} aria-pressed={pickerYear === year && value === month} variant={pickerYear === year && value === month ? "primary" : "secondary"} className="whitespace-nowrap px-2" onClick={() => {
              setView({ year: pickerYear, month: value, day: 1 }); setPickerOpen(false); triggerRef.current?.focus()
            }}>{value}월</Button>)}
          </div>
        </div>}
      </div>
      {/* 다음 달 버튼 — moveMonth (화면 상태·상위 콜백 반영) */}
      <button
        aria-label="다음 달"
        onClick={() => moveMonth(1)}
        className="rounded-lg border border-main/15 bg-white p-2 text-main focus-visible:outline-2 focus-visible:outline-accent"
      >
        <Icon name="chevronRight" />
      </button>
      {/* 오늘 버튼 — getKoreaToday (화면 상태·상위 콜백 반영) */}
      <Button variant="secondary" onClick={() => { const current = getKoreaToday(); setView(current); setSelectedDay(current.day); setPickerOpen(false) }}>오늘</Button>
      {/* 새로고침 버튼 — residentsQuery.refetch → GET /contract-service/admin/applications */}
      <Button variant="secondary" icon="refresh" onClick={() => void residentsQuery.refetch()}>새로고침</Button>
      <span className="text-sm text-text/70">이번 달 입주 {monthEvents.filter((event) => event.type === "in").length}건 · 퇴거 {monthEvents.filter((event) => event.type === "out").length}건</span>


      <div className="ml-auto flex items-center gap-4 text-xs font-semibold text-text/70">
        <span className="flex items-center gap-1.5">
          <i className="h-2.5 w-2.5 rounded-full bg-main" />
          입주</span>
        <span className="flex items-center gap-1.5">
          <i className="h-2.5 w-2.5 rounded-full bg-accent" />
          퇴거</span>
      </div>

    </div>


    <ContractQueryState loading={residentsQuery.loading && !residentsQuery.data} error={residentsQuery.error} onRetry={residentsQuery.refetch} />
    <ContractQueryState error={buildingsQuery.error} onRetry={buildingsQuery.refetch} />
    <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_300px]" style={{ "--calendar-height": `${calendarDays.length / 7 * 120 + 42}px` } as CSSProperties}>

      {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
      <section className="overflow-hidden rounded-lg border border-main/15 bg-white">

        <div className="grid grid-cols-7 border-b border-main/15 bg-background text-center text-xs font-semibold text-text/70">
          {[
            "일",
            "월",
            "화",
            "수",
            "목",
            "금",
            "토"
          ].map((day) => <span
            key={day}
            className="py-3"
          >
            {day}
          </span>)}
        </div>


        <div className="grid grid-cols-7">
          {calendarDays.map(({ day, current }, index) => {
            const dayEvents = current ? events[day] ?? [] : []
            const selected = current && selectedDay === day
            const inCount = dayEvents.filter((event) => event.type === "in").length
            const outCount = dayEvents.length - inCount
            // 고정 높이 날짜 칸 — 날짜·더 보기 선택은 setSelectedDay, 추가 API 호출 없음
            return <div
              key={`${day}-${index}`}
              role="group"
              aria-label={`${month}월 ${day}일 일정`}
              onClick={() => { if (current) setSelectedDay(day) }}
              className={`flex h-24 min-w-0 flex-col overflow-hidden border-r border-b border-main/15 p-1.5 text-left sm:h-[120px] ${!current ? "bg-background text-text/40" : selected ? "bg-main/10" : "cursor-pointer bg-white hover:bg-background"}`}
            >
              {/* 날짜 선택 — setSelectedDay로 오른쪽에 그날 일정 전체 표시 */}
              <button type="button" disabled={!current} aria-label={`${year}년 ${month}월 ${day}일`} aria-pressed={selected} onClick={() => setSelectedDay(day)} className={`inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold focus-visible:outline-2 focus-visible:outline-accent ${!current ? "text-text/40" : current && year === today.year && month === today.month && day === today.day ? "bg-main text-white" : "text-text/70"}`}>
                {day}
              </button>
              {/* 데스크톱 일정 미리보기 — 최대 두 건, 전체 내용은 title로 확인 */}
              <div className="mt-1 hidden min-w-0 space-y-1 sm:block">
                {dayEvents.slice(0, 2).map((event) => <span key={`${event.applicationId}-${event.type}`} title={`${event.type === "in" ? "입주" : "퇴거"} · ${event.label}`} className={`flex h-5 min-w-0 items-center gap-1 rounded px-1.5 text-[11px] font-semibold ${event.type === "in" ? "bg-main/15 text-main" : "bg-accent/20 text-text"}`}>
                  <i aria-hidden="true" className={`h-1.5 w-1.5 shrink-0 rounded-full ${event.type === "in" ? "bg-main" : "bg-accent"}`} />
                  <span className="shrink-0">{event.type === "in" ? "입주" : "퇴거"}</span>
                  <span className="min-w-0 truncate">{event.label}</span>
                </span>)}
              </div>
              {/* 모바일 일정 요약 — 이름 대신 입주·퇴거 건수만 표시 */}
              <div className="mt-1 space-y-0.5 text-[10px] leading-3 sm:hidden">
                {inCount > 0 && <span className="flex items-center gap-1 text-main" aria-label={`입주 ${inCount}건`}><i aria-hidden="true" className="h-1 w-1 shrink-0 rounded-full bg-main" />입 {inCount}</span>}
                {outCount > 0 && <span className="flex items-center gap-1 text-text" aria-label={`퇴거 ${outCount}건`}><i aria-hidden="true" className="h-1 w-1 shrink-0 rounded-full bg-accent" />퇴 {outCount}</span>}
              </div>
              {/* 더 보기 — setSelectedDay로 숨긴 일정까지 오른쪽 목록에 표시 */}
              {dayEvents.length > 2 && <button type="button" onClick={() => setSelectedDay(day)} aria-label={`${month}월 ${day}일 일정 ${dayEvents.length - 2}건 더 보기`} className="mt-auto w-fit shrink-0 rounded px-1 text-[10px] font-semibold leading-4 text-main hover:bg-main/10 focus-visible:outline-2 focus-visible:outline-accent sm:text-[11px]">+{dayEvents.length - 2}건</button>}
            </div>
          })}
        </div>

      </section>


      {/* 상세·보조 영역 — 상위 조회 결과·선택 상태 표시 */}
      <aside className="flex h-[min(640px,calc(100dvh-120px))] min-h-0 flex-col gap-4 xl:h-[min(var(--calendar-height),calc(100dvh-160px))]">

        {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
        <section className="h-36 shrink-0 rounded-lg border border-main/15 bg-white p-4">
          <h3 className="text-xs font-semibold text-text/70">{month}월 일정</h3>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <Summary
              label="입주"
              count={monthEvents.filter((event) => event.type === "in").length}
              tone="main"
            />
            <Summary
              label="퇴거"
              count={monthEvents.filter((event) => event.type === "out").length}
              tone="accent"
            />
          </div>
        </section>


        {/* 선택 날짜 패널 — 제목·건수는 고정하고 아래 일정 목록만 스크롤 */}
        <section className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-main/15 bg-white">
          <header className="shrink-0 border-b border-main/15 p-4">
            <p className="text-xs font-semibold text-text/70">선택한 날짜 일정</p>
            <h3 className="mt-1 text-lg font-semibold text-main">{month}월 {selectedDay}일 ({selectedWeekday})</h3>
            <p className="mt-2 text-xs text-text/70">입주 {selectedGroups[0].events.length} · 퇴거 {selectedGroups[1].events.length}</p>
          </header>
          {/* 날짜 일정 목록 — 입주 먼저·퇴거 다음으로 묶어 전체 표시 */}
          <div className="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-contain p-4">
            {selectedEvents.length ? selectedGroups.filter((group) => group.events.length > 0).map((group) => <section key={group.type} aria-label={`${group.label} 일정`}>
              <h4 className="mb-2"><Badge type={group.type === "in" ? "success" : "warning"}>{group.label} {group.events.length}건</Badge></h4>
              <div className="space-y-2">{group.events.map((event) => {
                const resident = residentsById.get(event.applicationId)
                const location = `${event.buildingName} · ${resident?.roomName ?? "-"}`
                const period = `${resident?.moveInDate ?? "-"} ~ ${resident?.moveOutDate ?? "-"}`
                // 일정 항목 — navigate로 입주자 관리 이동 후 applicationId의 계약 상세 조회
                return <button key={`${event.applicationId}-${event.type}`} type="button" onClick={() => navigate(`/operations/residents?applicationId=${event.applicationId}`)} className={`block w-full min-w-0 rounded-lg border border-main/15 border-l-2 p-3 text-left hover:bg-background focus-visible:outline-2 focus-visible:outline-accent ${group.type === "in" ? "border-l-main" : "border-l-accent"}`}>
                  <strong className="block truncate text-sm text-main" title={resident?.applicantName ?? event.label}>{resident?.applicantName ?? event.label}</strong>
                  <span className="mt-1 block truncate text-xs text-text/70" title={location}>{location}</span>
                  <span className="mt-1 block truncate text-xs text-text/70" title={`계약 기간 ${period}`}>{period}</span>
                </button>
              })}</div>
            </section>) : <div className="flex min-h-40 flex-col items-center justify-center gap-3 text-center text-text/70">
              <span className="rounded-full bg-main/10 p-3 text-main">
                <Icon
                  name="calendar"
                  size={28}
                />
              </span>
              <p className="text-sm">이 날은 일정이 없어요</p>
            </div>}
          </div>

        </section>

      </aside>

    </div>

  </div>
}

function Summary({ label, count, tone }: {
  label: string;
  count: number;
  tone: "main" | "accent"
}) {
  return <div className="h-20 rounded-lg bg-background p-3">
    <span className={`text-xs font-semibold ${tone === "main" ? "text-main" : "text-accent"}`}>
      {label}
    </span>
    <strong className="mt-2 block text-2xl text-text">
      {count}
      <small className="ml-1 text-xs font-normal text-text/70">건</small>
    </strong>
  </div>
}
