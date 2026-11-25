// 접근 오류 판별 — 로그인·입주자 권한 오류를 일반 오류와 구분
import { CommunityApiError } from "./communityClient"

// 401·NOT_RESIDENT 접근 거절을 일반 조회 오류와 구분
export function isCommunityAccessError(error: unknown) {
  return error instanceof CommunityApiError && (error.status === 401 || error.code === "NOT_RESIDENT")
}
