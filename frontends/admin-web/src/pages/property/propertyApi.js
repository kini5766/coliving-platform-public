// 매물 서비스 API 호출 모음 (관리자 화면용)
// 게이트웨이(8081)의 /*/admin/** 경로는 ADMIN 권한이 있어야 호출됩니다.
const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL
const API = `${BACKEND_API_BASE_URL}/property-service/admin`

async function request(path, init) {
  const response = await fetch(`${API}${path}`, {
    ...init,
    credentials: 'include',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!response.ok) {
    throw new Error(`요청에 실패했습니다. (${response.status})`)
  }
  const text = await response.text()
  return text ? JSON.parse(text) : undefined
}

const send = (method, body) => ({ method, body: body === undefined ? undefined : JSON.stringify(body) })

// ── 건물 ───────────────────────────────────────────
/** 관리 중인 건물 리스트: GET /admin/buildings */
export const getAdminBuildings = (page, size) => request(`/buildings?page=${page}&size=${size}`)

/** 수정 화면용 건물 상세: GET /admin/buildings/{id} */
export const getAdminBuilding = (id) => request(`/buildings/${id}`)

/** 건물 등록: POST /admin/buildings */
export const createBuilding = (body) => request('/buildings', send('POST', body))

/** 건물 수정: PUT /admin/buildings/{id} */
export const updateBuilding = (id, body) => request(`/buildings/${id}`, send('PUT', body))

/** 건물 삭제(소프트): DELETE /admin/buildings/{id} */
export const deleteBuilding = (id) => request(`/buildings/${id}`, send('DELETE'))

/** 건물·언어별 계약서 양식 설정: PUT /admin/buildings/{id}/contract-forms */
export const saveContractForms = (id, forms) => request(`/buildings/${id}/contract-forms`, send('PUT', { forms }))

// ── 호실 ───────────────────────────────────────────
/** 호실 상세: GET /admin/rooms/{id} */
export const getAdminRoom = (id) => request(`/rooms/${id}`)

/** 호실 등록: POST /admin/rooms */
export const createRoom = (body) => request('/rooms', send('POST', body))

/** 호실 수정: PUT /admin/rooms/{id} */
export const updateRoom = (id, body) => request(`/rooms/${id}`, send('PUT', body))

/** 호실 삭제(소프트): DELETE /admin/rooms/{id} */
export const deleteRoom = (id) => request(`/rooms/${id}`, send('DELETE'))


// ── 건물 대표 이미지 ───────────────────────────────
/** 서버가 준 이미지 URL(/property-service/...)을 화면에서 쓸 전체 주소로 바꾼다. */
export const imageSrc = (url) => (url ? `${BACKEND_API_BASE_URL}${url}` : null)

/** 대표 이미지 업로드(교체): POST /admin/buildings/{id}/image (multipart, 필드 file) */
export async function uploadBuildingImage(id, file) {
  const body = new FormData()
  body.append('file', file)
  // FormData 는 브라우저가 Content-Type(경계값 포함)을 직접 붙이므로 헤더를 지정하지 않는다.
  const response = await fetch(`${API}/buildings/${id}/image`, { method: 'POST', body, credentials: 'include' })
  if (response.status === 413) throw new Error('이미지는 5MB 이하만 올릴 수 있습니다.')
  if (response.status === 415) throw new Error('jpg, png, webp, gif 이미지만 올릴 수 있습니다.')
  if (!response.ok) throw new Error(`이미지 업로드에 실패했습니다. (${response.status})`)
  return response.json()
}

/** 대표 이미지 삭제: DELETE /admin/buildings/{id}/image */
export const deleteBuildingImage = (id) => request(`/buildings/${id}/image`, send('DELETE'))