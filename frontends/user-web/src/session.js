/**
 * @author 조휘재
 * 작성일: 2026-10-29
 */

import { useEffect, useState } from 'react'

const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL
const SESSION_REFRESH = 'coliving-session-refresh'
const EMPTY_SESSION = { status: 'ready', authenticated: false, userId: null, name: null, nickname: null, roles: [] }

/**
 * 세션을 다시 읽게 알린다. signedOut 이면 빈 세션으로 먼저 바꾼다.
 * @author simoon7
 * 작성일: 2026-11-03
 */
export function refreshSession({ signedOut = false } = {}) {
  window.dispatchEvent(new CustomEvent(SESSION_REFRESH, { detail: { signedOut } }))
}

/** 서버 로그아웃이 되면 세션을 비운다. 실패하면 상태 코드를 던지고 세션은 그대로 둔다. */
export async function logout() {
  const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/user/logout`, {
    method: 'POST',
    credentials: 'include',
  })
  if (!response.ok) {
    throw new Error(String(response.status))
  }
  refreshSession({ signedOut: true })
}

/** 회원 세션을 읽는다. 새로고침 신호가 오면 다시 읽고, 역할 앞의 ROLE_ 은 뺀다. 요청이 취소되면 상태를 바꾸지 않는다. */
export function useSession() {
  const [session, setSession] = useState({ ...EMPTY_SESSION, status: 'loading' })
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    const onRefresh = (event) => {
      if (event instanceof CustomEvent && event.detail?.signedOut) setSession(EMPTY_SESSION)
      setRefreshKey((value) => value + 1)
    }
    window.addEventListener(SESSION_REFRESH, onRefresh)
    return () => window.removeEventListener(SESSION_REFRESH, onRefresh)
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    async function load() {
      try {
        const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/user/session`, {
          credentials: 'include',
          signal: controller.signal,
        })
        let userId = null
        let name = null
        let nickname = null
        let roles = []
        if (response.ok) {
          const body = await response.json().catch(() => null)
          const value = body && typeof body.nickname === 'string' ? body.nickname.trim() : ''
          nickname = value || null
          const displayName = body && typeof body.name === 'string' ? body.name.trim() : ''
          name = displayName || nickname
          const numericUserId = Number(body?.userId)
          userId = body?.userId != null && Number.isFinite(numericUserId) ? numericUserId : null
          roles = Array.isArray(body?.roles)
            ? body.roles.filter((role) => typeof role === 'string').map((role) => role.replace(/^ROLE_/, ''))
            : []
        }
        setSession({ status: 'ready', authenticated: response.ok, userId, name, nickname, roles })
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setSession(EMPTY_SESSION)
      }
    }

    load()
    return () => controller.abort()
  }, [refreshKey])

  return session
}
