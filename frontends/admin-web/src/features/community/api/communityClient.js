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

// community-service 요청 위임 — 쿠키·401·공통 오류 처리 재사용
export function request(path, options = {}) {
  return serviceRequest('community-service', path, options)
}

// 서비스별 Gateway 요청 — credentials: include·401·네트워크 오류 공통 처리
export async function serviceRequest(service, path, { method = 'GET', query, body, responseType = 'json', signal } = {}) {
  const search = new URLSearchParams()
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const url = `${BACKEND_API_BASE_URL}/${service}${path}${search.size ? `?${search}` : ''}`

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
    throw new CommunityApiError(status, responseBody?.code ?? (response.status === 401 ? 'UNAUTHORIZED' : 'HTTP_ERROR'), detail, responseBody)
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    if (error instanceof CommunityApiError) throw error
    throw new CommunityApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없어요', null)
  }
}
