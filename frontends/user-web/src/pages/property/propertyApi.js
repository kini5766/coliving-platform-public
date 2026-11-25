// 매물 서비스 API 호출 모음 (사용자 화면용, 비회원도 호출 가능)
// 게이트웨이(8081) → /property-service/** → property-service(8083) 로 전달됩니다.
const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL
const API = `${BACKEND_API_BASE_URL}/property-service/property`

async function request(path) {
  const response = await fetch(`${API}${path}`, {
    headers: { Accept: 'application/json' },
    credentials: 'include',
  })
  if (!response.ok) {
    // 화면에서 언어에 맞는 문구로 바꿔 보여 주도록 상태 코드만 담아 던집니다.
    const error = new Error(`request failed (${response.status})`)
    error.status = response.status
    throw error
  }
  return response.json()
}

/** 건물·호실 검색 목록: GET /property/buildings */
export function searchBuildings(params) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  })
  return request(`/buildings?${query.toString()}`)
}

/** 건물 상세(공용시설·호실 목록 포함): GET /property/buildings/{id} */
export function getBuilding(id) {
  return request(`/buildings/${id}`)
}

/** 호실 상세: GET /property/rooms/{id} */
export function getRoom(id) {
  return request(`/rooms/${id}`)
}

/** 서버가 준 이미지 URL(/property-service/...)을 화면에서 쓸 전체 주소로 바꾼다. 없으면 null */
export function imageSrc(url) {
  return url ? `${BACKEND_API_BASE_URL}${url}` : null
}