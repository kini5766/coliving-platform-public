import Diff from './Diff.jsx'
import { formatEok, formatMan } from '../utils/format'

function Card({ title, value, children }) {
    return (
        <div className="rounded-lg border border-main/20 bg-white p-5 text-center">
            <p className="text-xs text-text/70">{title}</p>
            <p className="mt-1 text-2xl font-bold text-text sm:text-3xl">{value}</p>
            {children}
        </div>
    )
}

function FinanceCards({ finance }) {
    return (
        <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
            <Card title="이번 달 수납액" value={formatMan(finance.monthCollectedAmount)}>
                <Diff value={finance.collectedChangeRate} unit="%" label="지난달 대비" />
            </Card>
            <Card title="이번 달 환불" value={formatMan(finance.monthRefundedAmount)}>
                <p className="mt-1 text-xs text-text/50">환불 {finance.monthRefundedCount}건</p>
            </Card>
            <Card title="실 수납액" value={formatMan(finance.netCollectedAmount)}>
                <p className="mt-1 text-xs text-text/50">수납액 − 환불</p>
            </Card>
            <Card title="보증금 보관액" value={formatEok(finance.depositHeldAmount)}>
                <p className="mt-1 text-xs text-text/50">현재 보관 중인 금액</p>
            </Card>
        </div>
    )
}

export default FinanceCards