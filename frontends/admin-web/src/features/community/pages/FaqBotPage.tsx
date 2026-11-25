// FAQ 봇 관리 — 문서 업로드·반영·미리보기·질문 기록
import { type FormEvent, useCallback, useEffect, useRef, useState } from "react"
import { askFaqBot, deleteFaqDocument, downloadFaqDocument, getCommunityBuildings, getFaqDocuments, getFaqQuestionLogs, getFaqReindexJob, reindexFaqDocuments, uploadFaqDocument } from "../api"
import { CommunityApiError } from "../api/communityClient"
import { Badge, Button, PageHeader, Select } from "../components"
import { CommunityQueryState, CommunityToast, ConfirmDialog, Pagination, useCommunityToast } from "../components/CommunityFeedback"
import { NoteConversation } from "../components/NoteConversation"
import { useCommunityQuery } from "../hooks/useCommunityQuery"
import type { CommunityBuilding, FaqAskResponse, FaqDocumentFile, FaqDocumentListResponse, FaqDocumentScope, FaqQuestionLogResponse, FaqReindexJobResponse } from "../types"

const STATUS_LABELS = { PENDING: "대기", PROCESSING: "처리 중", COMPLETED: "완료", FAILED: "실패" }
const FIELD_CLASS = "w-full rounded-lg border border-main/15 bg-white px-3 py-2 text-sm text-text outline-none focus-visible:outline-2 focus-visible:outline-accent"
const MAX_FILE_SIZE = 30 * 1024 * 1024
const dateTime = (value?: string | null) => value ? new Date(value).toLocaleString("ko-KR", { hour12: false }) : "-"
const localDate = (date: Date) => date.toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" })

export default function FaqBotPage({ title }: { title: string }) {
  const [tab, setTab] = useState<"documents" | "preview" | "logs">("documents")
  const [scopeFilter, setScopeFilter] = useState("")
  const [buildingFilter, setBuildingFilter] = useState("")
  const [uploadOpen, setUploadOpen] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [documentTitle, setDocumentTitle] = useState("")
  const [scope, setScope] = useState<FaqDocumentScope>("COMMON")
  const [buildingId, setBuildingId] = useState("")
  const [uploading, setUploading] = useState(false)
  const [fileError, setFileError] = useState("")
  const [deleteTarget, setDeleteTarget] = useState<FaqDocumentFile | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [starting, setStarting] = useState(false)
  const [jobId, setJobId] = useState<string | number | null>(null)
  const [job, setJob] = useState<FaqReindexJobResponse | null>(null)
  const [previewBuilding, setPreviewBuilding] = useState("")
  const [question, setQuestion] = useState("")
  const [preview, setPreview] = useState<{ id: number; question: string; createdAt: string; response: FaqAskResponse }[]>([])
  const [asking, setAsking] = useState(false)
  const askingRef = useRef(false)
  const [unansweredOnly, setUnansweredOnly] = useState(true)
  const [logBuilding, setLogBuilding] = useState("")
  const [from, setFrom] = useState(() => localDate(new Date(Date.now() - 6 * 86400000)))
  const [to, setTo] = useState(() => localDate(new Date()))
  const [page, setPage] = useState(0)
  const { toast, showToast } = useCommunityToast()
  // loadBuildings — GET /admin/buildings
  const loadBuildings = useCallback(() => getCommunityBuildings(), [])
  const buildingQuery = useCommunityQuery(loadBuildings)
  const buildings = (buildingQuery.data ?? []) as CommunityBuilding[]
  // loadDocuments — GET /admin/faq-documents
  const loadDocuments = useCallback(() => getFaqDocuments({ scope: scopeFilter || undefined, buildingId: buildingFilter ? Number(buildingFilter) : undefined }), [scopeFilter, buildingFilter])
  const documentsQuery = useCommunityQuery(loadDocuments)
  const refetchDocuments = documentsQuery.refetch
  const documents = documentsQuery.data as FaqDocumentListResponse | null
  // loadLogs — GET /admin/faq-bot/logs
  const loadLogs = useCallback(() => getFaqQuestionLogs({ answered: unansweredOnly ? false : undefined, buildingId: logBuilding ? Number(logBuilding) : undefined, from, to, page, size: 20 }), [unansweredOnly, logBuilding, from, to, page])
  const logsQuery = useCommunityQuery(loadLogs, { enabled: tab === "logs" })
  const logs = logsQuery.data as FaqQuestionLogResponse | null
  // 화면 제목이 바뀌면 브라우저 제목 반영
  useEffect(() => { document.title = title }, [title])
  // 반영 작업 ID가 생기면 2.5초마다 진행 조회, 종료 후 문서 목록 갱신
  useEffect(() => {
    if (jobId === null) return
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    // handlePoll — GET /admin/faq-documents/reindex/{jobId}
    const handlePoll = async () => {
      try {
        const progress = await getFaqReindexJob(jobId, controller.signal)
        if (controller.signal.aborted) return
        setJob(progress)
        if (progress.status === "COMPLETED" || progress.status === "FAILED") {
          setJobId(null)
          showToast(progress.status === "FAILED" ? "일부 문서가 실패했어요" : "업데이트 반영을 완료했어요")
          await refetchDocuments()
          return
        }
      } catch (error) {
        if (controller.signal.aborted) return
        setError(error)
      }
      if (!controller.signal.aborted) timer = setTimeout(handlePoll, 2500)
    }
    timer = setTimeout(handlePoll, 2500)
    return () => { controller.abort(); if (timer) clearTimeout(timer) }
  }, [jobId, refetchDocuments, showToast])

  const handleChooseFile = (selected?: File) => {
    setFile(null); setFileError("")
    if (!selected) return
    if (!/\.(pdf|docx|txt|md)$/i.test(selected.name)) { setFileError("PDF·DOCX·TXT·MD 파일만 올릴 수 있어요."); return }
    if (selected.size > MAX_FILE_SIZE) { setFileError("파일은 30MB 이하로 올려 주세요."); return }
    setFile(selected)
    if (!documentTitle) setDocumentTitle(selected.name.replace(/\.[^.]+$/, "").slice(0, 100))
  }
  // handleUpload — POST /admin/faq-documents
  const handleUpload = async (event: FormEvent) => {
    event.preventDefault()
    if (!file || !documentTitle.trim() || uploading || (scope === "BUILDING" && !buildingId)) return
    setUploading(true); setFileError("")
    try {
      await uploadFaqDocument({ file, title: documentTitle.trim(), scope, ...(scope === "BUILDING" ? { buildingId: Number(buildingId) } : {}) })
      setUploadOpen(false); setFile(null); setDocumentTitle("")
      showToast("문서를 업로드했어요"); await refetchDocuments()
    } catch (error) { setFileError(error instanceof Error ? error.message : "업로드하지 못했어요.") }
    finally { setUploading(false) }
  }
  // handleDelete — DELETE /admin/faq-documents/{documentId}
  const handleDelete = async () => {
    if (!deleteTarget) return
    setError(null)
    try { await deleteFaqDocument(deleteTarget.documentId); setDeleteTarget(null); showToast("문서를 삭제했어요"); await refetchDocuments() }
    catch (error) { setError(error); setDeleteTarget(null) }
  }
  // handleDownload — GET /admin/faq-documents/{documentId}/file
  const handleDownload = async (document: FaqDocumentFile) => {
    setError(null)
    try {
      const response = await downloadFaqDocument(document.documentId)
      if (!response) return
      const url = URL.createObjectURL(response.blob)
      const anchor = window.document.createElement("a")
      anchor.href = url; anchor.download = response.fileName || document.fileName
      window.document.body.append(anchor); anchor.click(); anchor.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (error) { setError(error) }
  }
  // handleReindex — POST /admin/faq-documents/reindex
  const handleReindex = async (documentIds?: number[]) => {
    if (starting || jobId !== null) return
    setStarting(true); setError(null)
    try {
      const response = await reindexFaqDocuments(documentIds)
      setJob({ status: "PROCESSING", processedCount: 0, totalCount: documentIds?.length ?? documents?.summary.pendingCount ?? 0 })
      setJobId(response.jobId); await refetchDocuments()
    } catch (error) {
      if (error instanceof CommunityApiError && error.code === "REINDEX_IN_PROGRESS") { showToast("이미 반영 중이에요"); await refetchDocuments() }
      else if (error instanceof CommunityApiError && error.status === 400) { showToast("반영할 문서가 없어요"); await refetchDocuments() }
      else setError(error)
    } finally { setStarting(false) }
  }
  // handleAsk — POST /faq-bot/ask
  const handleAsk = async (event: FormEvent) => {
    event.preventDefault()
    if (!question.trim() || question.trim().length > 300 || askingRef.current) return
    askingRef.current = true; setAsking(true); setError(null)
    const text = question.trim()
    try {
      const response = await askFaqBot({ question: text, ...(previewBuilding ? { buildingId: Number(previewBuilding) } : {}) })
      setPreview((current) => [...current, { id: current.length + 1, question: text, response, createdAt: new Date().toLocaleString("sv-SE", { timeZone: "Asia/Seoul" }).replace(" ", "T") }])
      setQuestion("")
    } catch (error) { setError(error) }
    finally { askingRef.current = false; setAsking(false) }
  }
  const buildingOptions = buildings.map((building) => <option key={building.buildingId} value={building.buildingId}>{building.name}</option>)
  return <div>
    {/* 화면 제목·상단 동작 */}
    <PageHeader eyebrow="FAQ BOT" title={title} description="문서를 관리하고 답변과 질문 기록을 확인해요." />
    {/* 탭·메뉴 */}
    {/* 버튼 — setTab (화면 상태·상위 콜백 반영) */}
    <nav className="mb-5 flex gap-2 border-b border-main/15">{([["documents", "문서"], ["preview", "미리보기"], ["logs", "질문 기록"]] as const).map(([value, label]) => <Button key={value} variant={tab === value ? "primary" : "ghost"} onClick={() => { setTab(value); setError(null) }}>{label}</Button>)}</nav>
    <CommunityQueryState error={error ?? buildingQuery.error} onRetry={buildingQuery.refetch} />
    {job && <p role="status" className="mb-4 rounded-lg bg-main/10 p-3 text-sm text-main">{job.status === "PROCESSING" ? `반영 중 ${job.processedCount}/${job.totalCount ?? 0}` : job.status === "FAILED" ? "일부 문서가 실패했어요" : "반영 완료"}{job.errorMessage && ` · ${job.errorMessage}`}</p>}
    {tab === "documents" && <>
      <div className="mb-4 grid gap-3 sm:grid-cols-3">{[["마지막 반영 일시", dateTime(documents?.summary.lastIndexedAt)], ["반영 완료 수", documents?.summary.indexedCount ?? 0], ["반영 안 된 변경 수", documents?.summary.pendingCount ?? 0]].map(([label, value]) => <div key={label} className="rounded-lg border border-main/15 bg-white p-4"><small className="text-text/70">{label}</small><strong className="mt-2 block text-lg text-main">{value}</strong></div>)}</div>
      {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
      <section className="overflow-hidden rounded-lg border border-main/15 bg-white">
        <div className="flex flex-wrap items-end gap-3 p-4">
          {/* 범위 선택 필터 — setScopeFilter → GET /admin/faq-documents */}
          <Select label="범위" value={scopeFilter} onChange={setScopeFilter}><option value="">전체</option><option value="COMMON">공통</option><option value="BUILDING">건물</option></Select>
          {/* 건물 선택 필터 — setBuildingFilter → GET /admin/faq-documents */}
          <Select label="건물" value={buildingFilter} onChange={setBuildingFilter}><option value="">전체</option>{buildingOptions}</Select>
          {/* 업데이트 반영 버튼 — handleReindex → POST /admin/faq-documents/reindex */}
          <Button className="sm:ml-auto" disabled={starting || jobId !== null || !(documents?.summary.pendingCount) || documentsQuery.loading} onClick={() => void handleReindex()}>업데이트 반영</Button>
          {/* 문서 업로드 버튼 — setUploadOpen (화면 상태·상위 콜백 반영) */}
          <Button onClick={() => { setUploadOpen(true); setFileError("") }}>문서 업로드</Button>
        </div>
        <CommunityQueryState loading={documentsQuery.loading && !documentsQuery.data} error={documentsQuery.error} onRetry={refetchDocuments} />
        {/* 조회 목록 — 상위 조회 결과·선택 상태 표시 */}
        <div className="overflow-x-auto"><table className="w-full min-w-[1100px] text-left text-sm"><thead className="bg-background text-xs text-text/70"><tr>{["제목·파일", "범위·건물", "업로드 일시", "상태", "관리"].map((label) => <th className="p-3" key={label}>{label}</th>)}</tr></thead><tbody>{documents?.documents.map((document) => <tr key={document.documentId} className="border-t border-main/15"><td className="p-3"><strong>{document.title}</strong><small className="mt-1 block text-text/60">{document.fileName}{document.fileSize !== undefined && ` · ${(document.fileSize / 1024).toFixed(0)} KB`}</small></td><td className="p-3">{document.scope === "COMMON" ? "공통" : document.buildingName}</td><td className="p-3">{dateTime(document.uploadedAt)}</td><td className="p-3"><span title={document.indexError ?? undefined}><Badge type={document.indexStatus === "FAILED" ? "warning" : document.indexStatus === "COMPLETED" ? "success" : "info"}>{STATUS_LABELS[document.indexStatus]}</Badge></span>{document.indexError && <details className="mt-2 text-xs text-accent"><summary>실패 사유</summary>{document.indexError}</details>}</td><td className="p-3"><div className="flex items-center gap-2">
          {/* 다운로드 버튼 — handleDownload → GET /admin/faq-documents/{documentId}/file */}
          <Button variant="secondary" icon="download" className="h-8 min-h-8! shrink-0 whitespace-nowrap px-2.5" aria-label={`${document.title} 다운로드`} onClick={() => void handleDownload(document)}>다운로드</Button>
          {/* 다시 반영 버튼 — handleReindex → POST /admin/faq-documents/reindex */}
          <Button variant="secondary" icon="refresh" className="h-8 min-h-8! shrink-0 whitespace-nowrap px-2.5" aria-label={`${document.title} 다시 반영`} disabled={starting || jobId !== null || document.indexStatus === "PROCESSING"} onClick={() => void handleReindex([document.documentId])}>{document.indexStatus === "PROCESSING" ? "반영 중" : "다시 반영"}</Button>
          {/* 삭제 버튼 — setDeleteTarget (화면 상태·상위 콜백 반영) */}
          <span className="ml-2 border-l border-main/15 pl-3"><Button variant="danger" icon="trash" className="h-8 min-h-8! shrink-0 whitespace-nowrap px-2.5" aria-label={`${document.title} 삭제`} onClick={() => setDeleteTarget(document)}>삭제</Button></span>
        </div></td></tr>)}</tbody></table></div>
        {!documentsQuery.loading && documents?.documents.length === 0 && <p className="p-8 text-center text-sm text-text/60">조건에 맞는 문서가 없어요.</p>}
      </section>
    </>}
    {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
    {tab === "preview" && <section className="flex h-[600px] min-h-0 flex-col overflow-hidden rounded-lg border border-main/15 bg-white">
      {/* 미리보기 건물 선택 필터 — setPreviewBuilding (화면 상태·상위 콜백 반영) */}
      <div className="shrink-0 p-4"><Select label="미리보기 건물" value={previewBuilding} onChange={(value) => { if (!asking) { setPreviewBuilding(value); setPreview([]) } }}><option value="">공통만</option>{buildingOptions}</Select></div>
      {/* 날짜·말풍선·읽음 대화 — 상위 조회 결과·선택 상태 표시 */}
      <NoteConversation notes={preview.flatMap((exchange) => [{ noteId: exchange.id * 2, senderType: "USER" as const, content: exchange.question, createdAt: exchange.createdAt, readAt: null }, { noteId: exchange.id * 2 + 1, senderType: "ADMIN" as const, content: exchange.response.answer + (exchange.response.sources.length ? "\n\n출처: " + exchange.response.sources.map((source) => source.title).join(", ") : ""), createdAt: exchange.createdAt, readAt: null }])} outgoingSender="USER" />
      {asking && <p role="status" className="shrink-0 px-4 py-2 text-sm text-text/60">답변을 찾는 중…</p>}
      {/* 입력 제출 — handleAsk → POST /faq-bot/ask */}
      {/* 미리보기 질문 입력창 — setQuestion (화면 상태·상위 콜백 반영) */}
      {/* 질문 버튼 — handleAsk → POST /faq-bot/ask */}
      <form onSubmit={handleAsk} className="flex shrink-0 gap-3 border-t border-main/15 p-4"><input aria-label="미리보기 질문" maxLength={300} value={question} onChange={(event) => setQuestion(event.target.value)} disabled={asking} className={FIELD_CLASS} placeholder="질문을 입력하세요" /><Button type="submit" disabled={asking || !question.trim()}>질문</Button></form>
    </section>}
    {tab === "logs" && <>
      {/* 입력창 — setUnansweredOnly → GET /admin/faq-bot/logs */}
      {/* 건물 선택 필터 — setLogBuilding → GET /admin/faq-bot/logs */}
      {/* 입력창 — setFrom → GET /admin/faq-bot/logs */}
      {/* 입력창 — setTo → GET /admin/faq-bot/logs */}
      <div className="mb-4 flex flex-wrap items-end gap-3"><label className="text-sm"><input type="checkbox" checked={unansweredOnly} onChange={(event) => { setUnansweredOnly(event.target.checked); setPage(0) }} /> 답 못 한 질문만</label><Select label="건물" value={logBuilding} onChange={(value) => { setLogBuilding(value); setPage(0) }}><option value="">전체</option>{buildingOptions}</Select><label className="text-xs">시작일<input type="date" value={from} max={to} onChange={(event) => { setFrom(event.target.value); setPage(0) }} className={FIELD_CLASS} /></label><label className="text-xs">종료일<input type="date" value={to} min={from} onChange={(event) => { setTo(event.target.value); setPage(0) }} className={FIELD_CLASS} /></label></div>
      <div className="mb-4 grid gap-3 sm:grid-cols-4">{[["전체 질문", logs?.summary.totalCount ?? 0], ["답 못 한 질문", logs?.summary.unansweredCount ?? 0], ["캐시 답변 수", logs?.summary.cachedCount ?? 0], ["평균 응답 시간", `${logs?.summary.avgLatencyMs ?? 0} ms`]].map(([label, value]) => <div className="rounded-lg border border-main/15 bg-white p-4" key={label}><small className="text-text/60">{label}</small><strong className="mt-2 block text-lg text-main">{value}</strong></div>)}</div>
      <CommunityQueryState loading={logsQuery.loading && !logsQuery.data} error={logsQuery.error} onRetry={logsQuery.refetch} />
      {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
      {/* 조회 목록 — 상위 조회 결과·선택 상태 표시 */}
      <section className="overflow-hidden rounded-lg border border-main/15 bg-white"><div className="overflow-x-auto"><table className="w-full min-w-[950px] text-left text-sm"><thead className="bg-background"><tr>{["질문", "건물", "답변 여부", "최고 유사도", "출처", "응답 시간", "일시"].map((label) => <th className="p-3" key={label}>{label}</th>)}</tr></thead><tbody>{logs?.content.map((log) => <tr key={log.logId} className="border-t border-main/15"><td className="max-w-80 whitespace-pre-wrap break-words p-3">{log.question}</td><td className="p-3">{log.buildingName ?? "공통"}</td><td className="p-3">{log.answered ? "답변함" : "답 못 함"}</td><td className="p-3">{log.topScore == null ? "-" : log.topScore.toFixed(2)}</td><td className="p-3">{log.sources.map((source) => source.title).join(", ") || "-"}</td><td className="p-3">{log.latencyMs} ms</td><td className="p-3">{dateTime(log.createdAt)}</td></tr>)}</tbody></table></div>{!logsQuery.loading && logs?.content.length === 0 && <p className="p-8 text-center text-sm text-text/60">질문 기록이 없어요.</p>}<Pagination page={page} totalPages={logs?.totalPages ?? 0} onChange={setPage} /></section>
    </>}
    {/* 입력 제출 — handleUpload → POST /admin/faq-documents */}
    {uploadOpen && <div className="fixed inset-0 z-50 flex items-center justify-center bg-text/40 p-4"><form onSubmit={handleUpload} role="dialog" aria-modal="true" className="w-full max-w-lg space-y-4 rounded-lg bg-white p-6">
      <h2 className="text-lg font-semibold text-main">문서 업로드</h2>
      {/* 입력창 — handleChooseFile (화면 상태·상위 콜백 반영) */}
      <label className="block rounded-lg border border-dashed border-main/30 p-4 text-sm" onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); handleChooseFile(event.dataTransfer.files[0]) }}>파일(PDF·DOCX·TXT·MD, 30MB)<input type="file" accept=".pdf,.docx,.txt,.md" onChange={(event) => handleChooseFile(event.target.files?.[0])} className="mt-2 block w-full text-xs" />{file?.name}</label>
      {/* 입력창 — setDocumentTitle (화면 상태·상위 콜백 반영) */}
      <label className="block text-sm">제목<input maxLength={100} required value={documentTitle} onChange={(event) => setDocumentTitle(event.target.value)} className={FIELD_CLASS} /></label>
      {/* 범위 선택 필터 — setScope (화면 상태·상위 콜백 반영) */}
      <Select label="범위" value={scope} onChange={(value) => setScope(value as FaqDocumentScope)}><option value="COMMON">공통</option><option value="BUILDING">건물</option></Select>
      {/* 건물(필수) 선택 필터 — setBuildingId (화면 상태·상위 콜백 반영) */}
      {scope === "BUILDING" && <Select label="건물(필수)" value={buildingId} onChange={setBuildingId}><option value="">선택하세요</option>{buildingOptions}</Select>}
      {fileError && <p role="alert" className="text-sm text-accent">{fileError}</p>}
      {/* 취소 버튼 — setUploadOpen (화면 상태·상위 콜백 반영) */}
      {/* 버튼 — handleUpload → POST /admin/faq-documents */}
      <footer className="flex justify-end gap-2"><Button disabled={uploading} variant="secondary" onClick={() => setUploadOpen(false)}>취소</Button><Button type="submit" disabled={uploading || !file || !documentTitle.trim() || (scope === "BUILDING" && !buildingId)}>{uploading ? "업로드 중…" : "업로드"}</Button></footer>
    </form></div>}
    {deleteTarget && <ConfirmDialog title="문서를 삭제할까요?" description="삭제하면 봇이 이 문서를 더 이상 참고하지 않아요" onClose={() => setDeleteTarget(null)} onConfirm={handleDelete} />}
    <CommunityToast message={toast} />
  </div>
}
