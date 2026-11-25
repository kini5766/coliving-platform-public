// 문서 서비스 양식 API 호출.
const BASE = `${import.meta.env.VITE_BACKEND_API_BASE_URL}/document-service/admin/forms`

async function request(path = '', options) {
    const res = await fetch(`${BASE}${path}`, { ...options, credentials: 'include' })
    if (!res.ok) {
        const error = new Error(`요청 실패 (${res.status})`)
        error.status = res.status // 상태 코드로 화면에서 안내 문구를 고른다
        // 문서 서비스가 { message }로 이유를 보내면(틀린 빈칸 이름, 삭제할 수 없는 양식 등) 화면에 그대로 보여준다
        try {
            error.serverMessage = (await res.json()).message || ''
        } catch {
            error.serverMessage = ''
        }
        throw error
    }
    return res.status === 204 ? null : res.json()
}

const withBody = (method, data) => ({
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
})

export const fetchForms = () => request()
export const createForm = (data) => request('', withBody('POST', data))
export const updateForm = (id, data) => request(`/${id}`, withBody('PUT', data))
export const deleteForm = (id) => request(`/${id}`, { method: 'DELETE' })