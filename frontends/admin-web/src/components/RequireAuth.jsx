/**
 * @author 조휘재
 * 작성일: 2026-10-30
 */

import { Navigate, Outlet } from 'react-router'
import { useSession } from '../session'

/** 관리자 세션을 확인한다. 없으면 로그인으로 보내고, 권한이 아니면 그 안내를 같이 넘긴다. 확인 중에는 빈 화면만 둔다. */
function RequireAuth() {
  const session = useSession()

  if (session.status === 'loading') {
    return <main className="min-h-dvh bg-background" />
  }

  if (!session.authenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={session.forbidden ? { adminRequired: true } : null}
      />
    )
  }

  return <Outlet />
}

export default RequireAuth
