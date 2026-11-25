import Diff from './Diff.jsx'

function Card({ title, value, children }) {
    return (
        <div className="rounded-lg border border-main/20 bg-white p-5 text-center">
            <p className="text-xs text-text/70">{title}</p>
            <p className="mt-1 text-2xl font-bold text-text sm:text-3xl">{value}</p>
            {children}
        </div>
    )
}

// 값이 없으면(옛 집계 데이터) 단위 없이 "-"만 보여준다
const count = (value, unit) => (value == null ? '-' : `${value}${unit}`)

function SummaryCards({ cards }) {
    return (
        <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
            <Card title="입주율" value={`${cards.occupancyRate}%`}>
                <Diff value={cards.occupancyRateDiff} unit="%p" label="지난달 대비" />
            </Card>
            <Card title="계약 승인 대기" value={count(cards.pendingApprovalCount, '건')}>
                <p className="mt-1 text-xs text-text/50">처리 대기 중인 신청</p>
            </Card>
            <Card title="입주 예정" value={count(cards.moveInScheduledCount, '건')}>
                <p className="mt-1 text-xs text-text/50">입주를 앞둔 호실</p>
            </Card>
            <Card title="퇴거 예정" value={count(cards.moveOutScheduledCount, '건')}>
                <p className="mt-1 text-xs text-text/50">퇴거를 앞둔 호실</p>
            </Card>
        </div>
    )
}

export default SummaryCards