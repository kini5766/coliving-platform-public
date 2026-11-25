// 활동 로그의 시간·날짜 표시.

export function formatTime(iso) {
    return new Date(iso).toLocaleTimeString('ko-KR', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    })
}

export function formatMonthDay(iso) {
    const date = new Date(iso)
    return `${date.getMonth() + 1}/${date.getDate()}`
}

function startOfDay(date) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

// 오늘 기준으로 며칠 전인지. 오늘은 0이고, 시계 오차로 미래가 되면 0으로 본다.
export function daysAgo(iso) {
    const diff = Math.round((startOfDay(new Date()) - startOfDay(new Date(iso))) / 86400000)
    return Math.max(0, diff)
}

// 카드용: 오늘이 아닌 건에만 "1일 전", "2일 전"을 붙인다. 오늘이면 빈 문자열.
export function dayAgoLabel(iso) {
    const days = daysAgo(iso)
    return days === 0 ? '' : `${days}일 전`
}

// 팝업 날짜 구분선용: "오늘", "1일 전 · 10/27"
export function daySeparatorLabel(iso) {
    const days = daysAgo(iso)
    return days === 0 ? '오늘' : `${days}일 전 · ${formatMonthDay(iso)}`
}