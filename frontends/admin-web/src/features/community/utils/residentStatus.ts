// 계약 화면 계산 — 한국 날짜·입주 상태·D-day·월별 일정을 한곳에서 계산
import type { ContractMoveScheduleEvent, ContractResident, ResidentStatus } from "../types"

// 주어진 시각을 Asia/Seoul 날짜 문자열로 변환
export function getKoreaDate(now = new Date()): string {
  return now.toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" })
}

// 날짜의 연·월·일 숫자로 일수 계산 — 날짜 문자열의 UTC 파싱 방지
export function remainingDays(date: string | null, today: string): number | null {
  if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{4}-\d{2}-\d{2}$/.test(today)) return null
  const serial = (value: string) => {
    const [year, month, day] = value.split("-").map(Number)
    return Date.UTC(year, month - 1, day) / 86400000
  }
  return serial(date) - serial(today)
}

// 오늘을 인자로 받아 계약 상태·퇴거까지 남은 날을 화면 상태와 D-day로 계산
export function getResidentState(resident: Pick<ContractResident, "status" | "moveOutDate">, today: string): { status: ResidentStatus; dDay: string | null } {
  const days = remainingDays(resident.moveOutDate, today)
  const status: ResidentStatus = resident.status === "PAID" ? "입주 예정"
    : resident.status === "TERMINATED" ? "퇴거 완료"
    : resident.status === "TERMINATION_REQUESTED" || (days !== null && days >= 0 && days <= 30) ? "퇴거 예정" : "거주 중"
  const dDay = status !== "퇴거 예정" || days === null ? null : days === 0 ? "D-day" : days > 0 ? `D-${days}` : `D+${Math.abs(days)}`
  return { status, dDay }
}

// 이미 조회한 계약에서 월·지점별 입주·퇴거 일정 계산(추가 HTTP 호출 없음)
export function buildMoveSchedules(residents: ContractResident[], { year, month, buildingName }: { year: number; month: number; buildingName?: string }): Record<number, ContractMoveScheduleEvent[]> {
  const prefix = `${year}-${String(month).padStart(2, "0")}-`
  const events: Record<number, ContractMoveScheduleEvent[]> = {}
  for (const resident of residents) {
    if (buildingName && resident.buildingName !== buildingName) continue
    for (const [type, date] of [["in", resident.moveInDate], ["out", resident.moveOutDate]] as const) {
      if (!date?.startsWith(prefix)) continue
      const day = Number(date.slice(8, 10))
      const label = `${resident.applicantName} · ${buildingName ? "" : `${resident.buildingName} `}${resident.roomName}`
      const event = { applicationId: resident.applicationId, type, date, label, buildingName: resident.buildingName }
      if (!events[day]) events[day] = []
      events[day].push(event)
    }
  }
  return events
}
