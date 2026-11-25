// 활동 로그 유형·상태 표시 정보. 카드(ActivityLogPanel)와 더보기 팝업(ActivityLogModal)이 함께 쓴다.
export const TYPE = {
    CONTRACT: {label: '계약', className: 'bg-main/15 text-main'},
    RESERVATION: {label: '예약', className: 'bg-accent/30 text-text'},
    MOVE_IN: {label: '입주', className: 'bg-main text-background'},
    PAYMENT: {label: '결제', className: 'bg-text/10 text-text'},
}

export const STATUS = {
    COMPLETED: {label: '완료', className: 'text-main'},
    PENDING: {label: '대기', className: 'text-accent'},
    PROCESSING: {label: '처리중', className: 'text-text/60'},
}

export function typeOf(activityType) {
    return TYPE[activityType] ?? {label: activityType, className: 'bg-text/10 text-text'}
}

export function statusOf(status) {
    return STATUS[status] ?? {label: status, className: 'text-text/60'}
}