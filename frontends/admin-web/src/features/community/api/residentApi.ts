// 계약 서비스 연결 — 관리자 계약 목록·상세와 입주·퇴거 일정 조회
import { RESIDENTS } from "../mocks"
import type { ContractResident, ContractResidentDetail } from "../types"
import { buildMoveSchedules } from "../utils/residentStatus"
import { CommunityApiError, serviceRequest, USE_MOCK } from "./communityClient"

const CONTRACT_STATUSES = ["PAID", "COMPLETED", "TERMINATION_REQUESTED", "TERMINATED"] as const
// 기존 가짜 입주자를 계약 API의 목록 필드로 변환
const mockContracts = (): ContractResident[] => RESIDENTS.map((resident, index) => ({
  applicationId: index + 1,
  applicationNo: `MOCK-${String(index + 1).padStart(4, "0")}`,
  applicantName: resident.name,
  buildingName: resident.branch,
  roomName: resident.room,
  moveInDate: resident.moveIn.replace(/\.\s*/g, "-"),
  moveOutDate: resident.expires.replace(/\.\s*/g, "-"),
  monthlyFee: 650000,
  deposit: 1000000,
  status: resident.status === "입주 예정" ? "PAID" : resident.status === "퇴거 완료" ? "TERMINATED" : resident.status === "퇴거 예정" ? "TERMINATION_REQUESTED" : "COMPLETED"
}))

// 계약 입주자 목록 → GET /contract-service/admin/applications → AdminApplicationController.list
export async function getContractResidents(): Promise<ContractResident[]> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (USE_MOCK) return structuredClone(mockContracts())
  return serviceRequest("contract-service", "/admin/applications", { query: { statuses: CONTRACT_STATUSES.join(",") } })
}

// 계약 입주자 상세 → GET /contract-service/admin/applications/{applicationId} → AdminApplicationController.detail
export async function getContractResidentDetail(applicationId: number): Promise<ContractResidentDetail> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return serviceRequest("contract-service", `/admin/applications/${applicationId}`)
  const resident = mockContracts().find((item) => item.applicationId === applicationId)
  if (!resident) throw new CommunityApiError(404, "APPLICATION_NOT_FOUND", "계약을 찾을 수 없어요")
  return { ...resident, userId: applicationId, applicantPhone: "010-2847-6931", applicantEmail: "minseo.kim@email.com", applicantAddress: "서울특별시", contractMonths: 12 }
}

// GET /contract-service/admin/applications → AdminApplicationController.list 후 월·지점 계산
export async function getMoveSchedules(filter: { year: number; month: number; buildingName?: string }) {
  return buildMoveSchedules(await getContractResidents(), filter)
}
