// Gateway 공통 클라이언트 — 서비스 경로·쿠키·오류·파일 응답 처리
const BACKEND_API_BASE_URL = String(import.meta.env.VITE_BACKEND_API_BASE_URL ?? '').replace(/\/$/, '')

export const USE_MOCK = import.meta.env.VITE_COMMUNITY_USE_MOCK === 'true'

export class CommunityApiError extends Error {
  constructor(status, code, detail, body = null) {
    super(detail)
    this.name = 'CommunityApiError'
    this.status = status
    this.code = code
    this.detail = detail
    this.body = body
  }
}

// Content-Disposition에서 한글 파일명을 추출(HTTP 호출 없음)
export function downloadFileName(disposition) {
  const encoded = disposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) { try { return decodeURIComponent(encoded.trim()) } catch { /* fall through */ } }
  return disposition?.match(/filename="([^"]+)"/i)?.[1] ?? disposition?.match(/filename=([^;]+)/i)?.[1]?.trim() ?? null
}

// community-service Gateway 요청 — 쿠키·401·제재·접근 거절 이벤트 처리
export async function request(path, { method = 'GET', query, body, responseType = 'json', signal } = {}) {
  const search = new URLSearchParams()
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const url = `${BACKEND_API_BASE_URL}/community-service${path}${search.size ? `?${search}` : ''}`

  try {
    const multipart = typeof FormData !== 'undefined' && body instanceof FormData
    const response = await fetch(url, {
      method,
      credentials: 'include',
      headers: body === undefined || multipart ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
      signal,
    })
    if (response.status === 204) return null
    if (response.ok && responseType === 'blob') return { blob: await response.blob(), fileName: downloadFileName(response.headers.get('Content-Disposition')) }
    const text = await response.text()
    let responseBody = null
    if (text) {
      try {
        responseBody = JSON.parse(text)
      } catch {
        responseBody = { detail: text }
      }
    }
    if (response.ok) return responseBody
    const status = responseBody?.status ?? response.status
    const detail = response.status === 401 ? '로그인이 필요합니다' : responseBody?.detail ?? '요청을 처리할 수 없어요'
    const error = new CommunityApiError(status, responseBody?.code ?? (response.status === 401 ? 'UNAUTHORIZED' : 'HTTP_ERROR'), detail, responseBody)
    // SANCTIONED → community:sanctioned 이벤트 → useMySanction의 제재 배너 갱신
    if (error.code === 'SANCTIONED') window.dispatchEvent(new CustomEvent('community:sanctioned', { detail: error }))
    if (response.status === 401 || error.code === 'NOT_RESIDENT') window.dispatchEvent(new CustomEvent('community:access-denied', { detail: response.status === 401 ? 'UNAUTHORIZED' : error.code }))
    throw error
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    if (error instanceof CommunityApiError) throw error
    throw new CommunityApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없어요', null)
  }
}
