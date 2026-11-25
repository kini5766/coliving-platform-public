/**
 * @author 조휘재
 * 작성일: 2026-10-30
 */

import { useEffect, useState } from 'react'

const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

/** 서버 로그아웃이 성공한 뒤에만 끝난다. 실패하면 상태 코드를 던진다. */
export async function logout() {
  const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/user/logout`, {
    method: 'POST',
    credentials: 'include',
  })
  if (!response.ok) {
    throw new Error(String(response.status))
  }
}

/** 관리자 세션을 한 번 확인한다. 403이면 권한 없음이고, 요청이 취소되면 상태를 바꾸지 않는다. */
export function useSession() {
  const [session, setSession] = useState({ status: 'loading', authenticated: false, forbidden: false })

  useEffect(() => {
    const controller = new AbortController()

    async function load() {
      try {
        const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/admin/session`, {
          credentials: 'include',
          signal: controller.signal,
        })
        setSession({
          status: 'ready',
          authenticated: response.ok,
          forbidden: response.status === 403,
        })
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setSession({ status: 'ready', authenticated: false, forbidden: false })
      }
    }

    load()
    return () => controller.abort()
  }, [])

  return session
}
