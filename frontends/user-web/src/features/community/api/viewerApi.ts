// 커뮤니티 회원 API — 서버의 회원·입주자·관리자 권한 확인
import { request, USE_MOCK } from "./communityClient"
import { COMMUNITY_MOCK_VIEWER } from "../mockViewer"

export type CommunityMe = {
  userId: number;
  known: boolean;
  name: string | null;
  roles: string[];
  resident: boolean;
  manager: boolean;
  buildingId: number | null;
  buildingName: string | null;
  roomNumber: string | null
}

// 회원·입주자 권한 확인 → GET /community-service/me → CommunityMeController.getMe
export async function getCommunityMe(): Promise<CommunityMe> {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request("/me")
  const manager = COMMUNITY_MOCK_VIEWER === "MANAGER"
  return {
    userId: manager ? 9001 : 5001,
    known: true,
    name: manager ? "망원 스테이 매니저" : "김민서",
    roles: [manager ? "ADMIN" : "RESIDENT"],
    resident: !manager,
    manager,
    buildingId: manager ? null : 1,
    buildingName: manager ? null : "망원 스테이",
    roomNumber: manager ? null : "302호"
  }
}
