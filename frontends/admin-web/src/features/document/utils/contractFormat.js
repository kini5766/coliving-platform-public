// 'YYYY-MM-DD'를 'YYYY.MM.DD'로 바꾼다.
export function formatDate(isoDate) {
    return isoDate ? isoDate.replaceAll('-', '.') : ''
}

// 원 단위 금액을 만 원 단위로 보여준다. 만 원으로 딱 떨어지지 않으면 원 단위로 쓴다.
export function formatMoney(amount) {
    if (amount % 10000 === 0) return `${(amount / 10000).toLocaleString()}만`
    return `${amount.toLocaleString()}원`
}

// 금액을 원 단위 그대로 보여준다. (예: 1,741,000원)
export function formatWon(amount) {
    return `${amount.toLocaleString()}원`
}