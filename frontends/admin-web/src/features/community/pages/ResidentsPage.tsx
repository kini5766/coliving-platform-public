// 입주자 관리 — 계약 목록 조회·필터·10개씩 표시·계약 ID로 상세 열기
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useSearchParams } from "react-router"
import { getCommunityBuildings } from "../api"
import { getContractResidentDetail, getContractResidents } from "../api/residentApi"
import { Avatar, Badge, Button, PageHeader, SearchField, Select } from "../components"
import { ContractQueryState } from "../components/ContractQueryState"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import type { CommunityBuilding, ContractResident, ContractResidentDetail } from "../types"
import { getKoreaDate, getResidentState } from "../utils/residentStatus"

const PAGE_SIZE = 10
const money = (value: number) => `${value.toLocaleString("ko-KR")}원`
const statusType = (status: string) => status === "거주 중" ? "success" : status === "입주 예정" ? "info" : status === "퇴거 예정" ? "warning" : "neutral"

export default function ResidentsPage({ title }: { title: string }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const linkedId = Number(searchParams.get("applicationId"))
  const selectedId = Number.isSafeInteger(linkedId) && linkedId > 0 ? linkedId : null
  const [query, setQuery] = useState("")
  const [statusFilter, setStatusFilter] = useState("퇴거 완료 제외")
  const [branchFilter, setBranchFilter] = useState("")
  const [page, setPage] = useState(0)
  const tableTopRef = useRef<HTMLDivElement>(null)
  const locatedSelectionRef = useRef<number | null>(null)
  const previousPageRef = useRef(0)
  // loadResidents — GET /contract-service/admin/applications
  const loadResidents = useCallback(() => getContractResidents(), [])
  const residentsQuery = useCommunityQuery(loadResidents)
  // loadBuildings — GET /admin/buildings
  const loadBuildings = useCallback(() => getCommunityBuildings(), [])
  const buildingsQuery = useCommunityQuery(loadBuildings)
  // loadDetail — GET /contract-service/admin/applications/{applicationId}
  const loadDetail = useCallback(() => selectedId === null ? Promise.resolve(null) : getContractResidentDetail(selectedId), [selectedId])
  const detailQuery = useCommunityQuery(loadDetail, { enabled: selectedId !== null })
  const residents = useMemo(() => (residentsQuery.data ?? []) as ContractResident[], [residentsQuery.data])
  const buildings = (buildingsQuery.data ?? []) as CommunityBuilding[]
  const detail = detailQuery.data as ContractResidentDetail | null
  const selected = detail?.applicationId === selectedId ? detail : null
  const today = getKoreaDate()

  // 화면 제목이 바뀌면 브라우저 제목 반영
  useEffect(() => { document.title = title }, [title])

  const filtered = useMemo(() => residents.filter((resident) => {
    const state = getResidentState(resident, today)
    const keyword = query.trim().toLowerCase()
    return (resident.applicantName?.toLowerCase().includes(keyword) || resident.roomName?.toLowerCase().includes(keyword))
      && (!branchFilter || resident.buildingName === branchFilter)
      && (statusFilter === "전체 상태" || (statusFilter === "퇴거 완료 제외" ? state.status !== "퇴거 완료" : state.status === statusFilter))
  }), [residents, today, query, branchFilter, statusFilter])
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE)
  const currentPage = Math.min(page, Math.max(0, totalPages - 1))
  const visible = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE)
  const firstPageNumber = Math.max(0, Math.min(currentPage - 2, totalPages - 5))
  const pageNumbers = Array.from({ length: Math.min(5, totalPages) }, (_, index) => firstPageNumber + index)
  // URL의 새 applicationId가 목록에 도착하면 해당 페이지로 이동하고 가려진 필터 해제
  useEffect(() => {
    if (selectedId === null) { locatedSelectionRef.current = null; return }
    if (!residentsQuery.data || locatedSelectionRef.current === selectedId) return
    const residentIndex = residents.findIndex((resident) => resident.applicationId === selectedId)
    if (residentIndex < 0) return
    const filteredIndex = filtered.findIndex((resident) => resident.applicationId === selectedId)
    if (filteredIndex < 0) {
      setQuery(""); setBranchFilter(""); setStatusFilter("전체 상태")
    }
    setPage(Math.floor((filteredIndex < 0 ? residentIndex : filteredIndex) / PAGE_SIZE))
    locatedSelectionRef.current = selectedId
  }, [selectedId, residentsQuery.data, residents, filtered])
  // 표시 페이지가 바뀌면 표 시작 위치로 부드럽게 이동, 상세의 sticky 여백 유지
  useEffect(() => {
    if (previousPageRef.current !== currentPage) tableTopRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })
    previousPageRef.current = currentPage
  }, [currentPage])
  const selectResident = (applicationId: number) => {
    setSearchParams((current) => { const next = new URLSearchParams(current); next.set("applicationId", String(applicationId)); return next })
  }

  return <div>
    {/* 화면 제목·상단 동작 */}
    {/* 새로고침 버튼 — residentsQuery.refetch → GET /contract-service/admin/applications */}
    <PageHeader eyebrow="RESIDENTS" title={title} description="전체 지점의 입주 현황과 계약 정보를 확인하세요." actions={<Button variant="secondary" icon="refresh" onClick={() => void residentsQuery.refetch()}>새로고침</Button>} />
    <ContractQueryState error={buildingsQuery.error} onRetry={buildingsQuery.refetch} />
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
      {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
      <section className="overflow-hidden rounded-lg border border-main/15 bg-white">
        <div className="flex flex-wrap items-center gap-3 border-b border-main/15 p-4">
          {/* 상태 선택 필터 — setStatusFilter (목록 필터·1페이지, 추가 API 호출 없음) */}
          <Select label="상태" value={statusFilter} onChange={(value) => { setStatusFilter(value); setPage(0) }}>
            {["퇴거 완료 제외", "전체 상태", "입주 예정", "거주 중", "퇴거 예정", "퇴거 완료"].map((value) => <option key={value}>{value}</option>)}
          </Select>
          {/* 지점 선택 필터 — setBranchFilter (목록 필터·1페이지, 추가 API 호출 없음) */}
          <Select label="지점" value={branchFilter} onChange={(value) => { setBranchFilter(value); setPage(0) }}>
            <option value="">전체 지점</option>
            {buildings.map((building) => <option key={building.buildingId} value={building.name}>{building.name}</option>)}
          </Select>
          {/* 이름, 호실 검색 검색 입력 — setQuery (목록 필터·1페이지, 추가 API 호출 없음) */}
          <div className="w-full sm:ml-auto sm:w-60"><SearchField value={query} onChange={(value) => { setQuery(value); setPage(0) }} placeholder="이름, 호실 검색" /></div>
        </div>
        <ContractQueryState loading={residentsQuery.loading && !residentsQuery.data} error={residentsQuery.error} onRetry={residentsQuery.refetch} />
        <div ref={tableTopRef} className="scroll-mt-24 overflow-x-auto">
          {/* 조회 목록 — 상위 조회 결과·선택 상태 표시 */}
          <table className="w-full min-w-[900px] text-left text-sm">
            <thead className="bg-background text-xs text-text/70"><tr>{["이름", "지점", "호실", "입주일", "계약 종료일", "상태", "월세"].map((label) => <th key={label} className="px-4 py-3">{label}</th>)}</tr></thead>
            <tbody>{visible.map((resident) => {
              const state = getResidentState(resident, today)
              return <tr key={resident.applicationId} onClick={() => selectResident(resident.applicationId)} className={`cursor-pointer border-t border-main/15 ${selectedId === resident.applicationId ? "bg-main/10" : "hover:bg-background"}`}>
                {/* 계약 상세 버튼 — selectResident → GET /contract-service/admin/applications/{applicationId} */}
                <td className="px-4 py-2.5"><button className="flex items-center gap-2 text-left font-semibold text-text focus-visible:outline-2 focus-visible:outline-accent" onClick={(event) => { event.stopPropagation(); selectResident(resident.applicationId) }} aria-label={`${resident.applicantName} 계약 상세`}><Avatar name={resident.applicantName ?? "-"} size="sm" />{resident.applicantName ?? "-"}</button></td>
                <td className="px-4 py-2.5">{resident.buildingName}</td>
                <td className="px-4 py-2.5">{resident.roomName}</td>
                <td className="whitespace-nowrap px-4 py-2.5">{resident.moveInDate ?? "-"}</td>
                <td className="whitespace-nowrap px-4 py-2.5">{resident.moveOutDate ?? "-"}</td>
                <td className="px-4 py-2.5"><div className="flex flex-wrap gap-1"><Badge type={statusType(state.status)}>{state.status}</Badge>{state.dDay && <Badge type="accent">{state.dDay}</Badge>}</div></td>
                <td className="whitespace-nowrap px-4 py-2.5">{money(resident.monthlyFee)}</td>
              </tr>
            })}</tbody>
          </table>
        </div>
        {!residentsQuery.error && residentsQuery.data && filtered.length === 0 && <p className="p-8 text-center text-sm text-text/70">조건에 맞는 입주자가 없습니다.</p>}
        {/* 페이지 이동 — setPage로 10명씩 표시하고 표 위로 스크롤, 추가 API 호출 없음 */}
        <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-main/15 p-4">
          <p className="text-xs text-text/70">총 {filtered.length}명 중 {filtered.length ? currentPage * PAGE_SIZE + 1 : 0}–{Math.min((currentPage + 1) * PAGE_SIZE, filtered.length)}</p>
          <nav aria-label="입주자 목록 페이지 이동" className="flex flex-wrap items-center gap-1">
            <Button variant="secondary" className="h-8 min-h-8! whitespace-nowrap px-2" disabled={currentPage === 0} onClick={() => setPage(0)} aria-label="처음 페이지">« 처음</Button>
            <Button variant="secondary" className="h-8 min-h-8! whitespace-nowrap px-2" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)} aria-label="이전 페이지">‹ 이전</Button>
            {pageNumbers.map((number) => <Button key={number} variant={number === currentPage ? "primary" : "secondary"} className="h-8 min-h-8! min-w-8 px-2" aria-label={`${number + 1}페이지`} aria-current={number === currentPage ? "page" : undefined} onClick={() => setPage(number)}>{number + 1}</Button>)}
            <Button variant="secondary" className="h-8 min-h-8! whitespace-nowrap px-2" disabled={totalPages === 0 || currentPage >= totalPages - 1} onClick={() => setPage(currentPage + 1)} aria-label="다음 페이지">다음 ›</Button>
            <Button variant="secondary" className="h-8 min-h-8! whitespace-nowrap px-2" disabled={totalPages === 0 || currentPage >= totalPages - 1} onClick={() => setPage(totalPages - 1)} aria-label="마지막 페이지">마지막 »</Button>
          </nav>
        </footer>
      </section>
      {/* 상세·보조 영역 — 상위 조회 결과·선택 상태 표시 */}
      {/* 입주자 상세 — 큰 화면에서는 따라오고 긴 내용은 패널 안에서 스크롤 */}
      <aside className="flex h-fit max-h-[calc(100dvh-3rem)] flex-col overflow-hidden rounded-lg border border-main/15 bg-white xl:sticky xl:top-6">
        <h2 className="shrink-0 border-b border-main/15 p-5 text-sm font-semibold text-main">입주자 상세</h2>
        <div className="min-h-0 overflow-y-auto overscroll-contain">
        {selectedId === null ? <p className="p-8 text-center text-sm text-text/70">입주자를 선택해 주세요.</p> : <>
          <ContractQueryState loading={detailQuery.loading || (!selected && !detailQuery.error)} error={detailQuery.error} onRetry={detailQuery.refetch} />
          {selected && !detailQuery.error && <div className="p-5">
            <div className="mb-5 flex items-center gap-3"><Avatar name={selected.applicantName ?? "-"} /><strong className="text-lg text-main">{selected.applicantName}</strong><Badge type={statusType(getResidentState(selected, today).status)}>{getResidentState(selected, today).status}</Badge></div>
            <dl className="space-y-3 text-sm">{[
              ["연락처", selected.applicantPhone], ["이메일", selected.applicantEmail], ["주소", selected.applicantAddress],
              ["지점", selected.buildingName], ["호실", selected.roomName], ["계약 기간", `${selected.contractMonths}개월`],
              ["입주일~퇴거일", `${selected.moveInDate ?? "-"} ~ ${selected.moveOutDate ?? "-"}`],
              ["월세", money(selected.monthlyFee)], ["보증금", money(selected.deposit)], ["신청번호", selected.applicationNo]
            ].map(([label, value]) => <div key={label} className="grid grid-cols-[90px_minmax(0,1fr)] gap-3"><dt className="text-text/70">{label}</dt><dd className="break-words text-right text-text">{value || "-"}</dd></div>)}</dl>
            <p className="mt-5 rounded-lg bg-background p-3 text-xs leading-5 text-text/70">계약 정보 변경은 계약 관리 화면에서 할 수 있어요</p>
          </div>}
        </>}
        </div>
      </aside>
    </div>
  </div>
}
