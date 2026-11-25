// 현재 사용자 — 세션과 커뮤니티 권한을 화면용 사용자 정보로 변환
import { useTranslation } from "react-i18next"
import type { UserRole } from "./types"
import { useCommunityViewer } from "./useCommunityViewer"

export type CurrentUser = {
  role: UserRole;
  userId: number;
  name: string;
  buildingName: string;
  roomNumber: string;
  loading: boolean;
  isLoggedIn: boolean;
  known: boolean | null;
  error: unknown;
  retry: () => Promise<void>
}

export function communityDisplayName(nickname: string | null | undefined, anonymous: string) {
  const trimmed = typeof nickname === "string" ? nickname.trim() : ""
  return trimmed || anonymous
}

export function useCurrentUser(): CurrentUser {
  const viewer = useCommunityViewer()
  const { t } = useTranslation("community")
  return {
    role: viewer.isManager ? "ADMIN" : viewer.isResident ? "RESIDENT" : "MEMBER",
    userId: viewer.userId,
    name: communityDisplayName(viewer.name, t("common.anonymous")),
    buildingName: viewer.buildingName,
    roomNumber: viewer.roomNumber,
    loading: viewer.loading,
    isLoggedIn: viewer.isLoggedIn,
    known: viewer.known,
    error: viewer.error,
    retry: viewer.retry
  }
}
