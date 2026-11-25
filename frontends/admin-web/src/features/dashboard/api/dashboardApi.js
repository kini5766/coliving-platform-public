// 대시보드 서비스 API 호출.
const BASE = `${import.meta.env.VITE_BACKEND_API_BASE_URL}/dashboard-service/admin`

async function get(path, params) {
    const query = new URLSearchParams(params).toString()
    const res = await fetch(`${BASE}${path}?${query}`, { credentials: 'include' })
    if (!res.ok) {
        const error = new Error(`요청 실패 (${res.status})`)
        error.status = res.status // 상태 코드 구분
        throw error
    }
    return res.json()
}

export const fetchRooms = (buildingId) => get('/rooms', { buildingId })

export const fetchActivityLogs = (buildingId, size = 10) =>
    get('/activity-logs', { buildingId, size })

// 더보기 팝업용. 유형을 주면 그 유형만, 안 주면 전체를 한 페이지씩 가져온다.
export const fetchActivityHistory = (buildingId, {type = null, page = 0, size = 20} = {}) =>
    get('/activity-logs/history', type ? {buildingId, type, page, size} : {buildingId, page, size})

export const fetchSummary = (buildingId, month) =>
    get('/summary', month ? { buildingId, month } : { buildingId })

export const fetchMonitoringSummary = () => get('/monitoring/summary')
export const fetchMonitoringCharts = () => get('/monitoring/charts')