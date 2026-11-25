// 커뮤니티 접근 제어 — 로그인·입주자·관리자 권한과 서버 거절 확인
import { type ReactNode, useEffect, useState } from "react"
import { useLocation, useNavigate } from "react-router"
import { useCommunityViewer } from "../useCommunityViewer"
import { Button, Icon } from "."

// 서버 권한과 접근 거절 이벤트로 로그인·입주자 기능 접근 제어
export function CommunityGate({ children }: { children: ReactNode }) {
  const viewer = useCommunityViewer()
  const location = useLocation()
  const navigate = useNavigate()
  const accessScope = `${location.pathname}:${viewer.isLoggedIn}:${viewer.isResident}:${viewer.isManager}`
  const [accessDenial, setAccessDenial] = useState<{ scope: string; code: string } | null>(null)
  const serverDenied = accessDenial?.scope === accessScope ? accessDenial.code : null
  // 접근 범위가 바뀌면 서버의 접근 거절 이벤트 구독·해제
  useEffect(() => {
    const handleAccessDenied = (event: Event) => setAccessDenial({ scope: accessScope, code: (event as CustomEvent<string>).detail })
    window.addEventListener("community:access-denied", handleAccessDenied)
    return () => window.removeEventListener("community:access-denied", handleAccessDenied)
  }, [accessScope])

  if (viewer.loading) return <CommunityAccessMessage message="로그인 상태를 확인하고 있어요." />
  if (!viewer.isLoggedIn || serverDenied === "UNAUTHORIZED") return <CommunityAccessMessage
    message="로그인 후 이용할 수 있어요"
    action={<Button onClick={() => navigate("/login", { state: { from: `${location.pathname}${location.search}` } })}>로그인 화면으로</Button>}
  />
  if (viewer.error) return <CommunityAccessMessage
    message={viewer.error instanceof Error ? viewer.error.message : "커뮤니티 회원 정보를 불러오지 못했어요."}
    action={<Button onClick={() => void viewer.retry()}>다시 시도</Button>}
  />
  if (viewer.known === false) return <CommunityAccessMessage message="커뮤니티 회원 정보를 찾을 수 없어요. 관리자에게 문의해 주세요." />
  if ((!viewer.isResident && !viewer.isManager) || serverDenied === "NOT_RESIDENT") return <CommunityAccessMessage message="입주자만 이용할 수 있어요" />

  return children
}

export function CommunityAccessMessage({ message, action }: { message: string; action?: ReactNode }) {
  // 화면 본문 — 상위 조회 결과·선택 상태 표시
  return <main className="px-4 py-6 sm:px-8 md:px-12">
    {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
    <section className="flex min-h-72 flex-col items-center justify-center rounded-lg border border-main/15 bg-white p-8 text-center">
      <span className="rounded-full bg-main/10 p-4 text-main"><Icon name="lock" size={26} /></span>
      <h1 className="mt-4 text-2xl font-semibold text-main">{message}</h1>
      {action && <div className="mt-5">{action}</div>}
    </section>
  </main>
}
