import { useEffect, useState } from 'react'
import { useLocation } from 'react-router'
import { Building2, ChevronRight } from 'lucide-react'
import { fetchContracts } from '../api/contractApi'
import ContractStatusBadge from '../components/ContractStatusBadge'
import { formatDate, formatMoney } from '../utils/contractFormat'
import ContractReviewPage from './ContractReviewPage'

// 상태 드롭다운. value는 서버의 status 파라미터 값이고, 비우면 전체.
const FILTERS = [
    { value: '', label: '전체' },
    { value: 'REVIEW_PENDING', label: '검토 대기' },
    { value: 'SIGN_PENDING', label: '서명 대기' },
    { value: 'COMPLETED', label: '계약 완료' },
    { value: 'REJECTED', label: '반려' },
]

function ContractsView({ title }) {
    const [contracts, setContracts] = useState([])
    const [keyword, setKeyword] = useState('')
    const [group, setGroup] = useState('')
    const [status, setStatus] = useState('loading') // loading | done | error
    const [selectedId, setSelectedId] = useState(null) // 검토 화면으로 열린 계약 ID
    const [reloadKey, setReloadKey] = useState(0) // 검토 화면에서 돌아올 때 목록을 다시 불러오려고 올린다

    useEffect(() => {
        document.title = title
    }, [title])

    // 검색어나 필터가 바뀌면 서버에서 다시 받아온다. 타자를 치는 동안 요청이 쏟아지지 않게 0.3초 기다린다.
    useEffect(() => {
        let cancelled = false
        const timer = setTimeout(async () => {
            try {
                const data = await fetchContracts({ keyword: keyword.trim(), status: group })
                if (!cancelled) {
                    setContracts(data)
                    setStatus('done')
                }
            } catch {
                if (!cancelled) setStatus('error')
            }
        }, 300)
        return () => {
            cancelled = true
            clearTimeout(timer)
        }
    }, [keyword, group, reloadKey])

    if (selectedId !== null) {
        return (
            <ContractReviewPage
                applicationId={selectedId}
                onClose={() => {
                    setSelectedId(null)
                    setReloadKey((key) => key + 1)
                }}
            />
        )
    }

    return (
        <div className="max-w-5xl">
            <h1 className="text-2xl font-semibold text-main sm:text-3xl">계약된 문서</h1>
            <p className="mt-1 text-sm text-text/70">계약 문서를 확인하고 관리해요.</p>

            <div className="mt-6 flex gap-2">
                <input
                    type="search"
                    value={keyword}
                    onChange={(e) => setKeyword(e.target.value)}
                    placeholder="이름, 건물명, 호실 검색"
                    className="min-w-0 flex-1 rounded-lg border border-main/20 bg-white px-4 py-2.5 text-sm outline-none transition-colors hover:border-main/50 focus:border-main"
                />
                <select
                    value={group}
                    onChange={(e) => setGroup(e.target.value)}
                    className="cursor-pointer rounded-lg border border-main/20 bg-white px-3 py-2.5 text-sm outline-none transition-colors hover:border-main/50 focus:border-main"
                >
                    {FILTERS.map((item) => (
                        <option key={item.value} value={item.value}>
                            {item.label}
                        </option>
                    ))}
                </select>
            </div>

            {status === 'loading' && <p className="py-10 text-center text-sm text-text/70">불러오는 중이에요.</p>}
            {status === 'error' && (
                <p className="py-10 text-center text-sm text-red-700">계약서를 불러오지 못했어요.</p>
            )}
            {status === 'done' && contracts.length === 0 && (
                <p className="py-10 text-center text-sm text-text/70">표시할 계약서가 없어요.</p>
            )}

            <section className="mt-4 grid gap-3 sm:grid-cols-2">
                {contracts.map((contract) => (
                    <button
                        key={contract.applicationId}
                        type="button"
                        onClick={() => setSelectedId(contract.applicationId)}
                        className="group cursor-pointer rounded-lg border border-transparent bg-white p-4 text-left shadow-sm transition duration-200 ease-out hover:-translate-y-0.5 hover:border-main/40 hover:shadow-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-main active:translate-y-0 active:shadow-sm motion-reduce:transition-none motion-reduce:hover:translate-y-0"
                    >
                        <div className="flex items-start justify-between gap-2">
                            <span className="text-lg font-semibold transition-colors group-hover:text-main">
                                {contract.applicantName}
                            </span>
                            <ContractStatusBadge group={contract.statusGroup} label={contract.statusLabel} />
                        </div>
                        <p className="mt-2 flex items-center gap-1.5 text-sm text-text/70">
                            <Building2 size={14} />
                            {contract.buildingName} {contract.roomName}
                        </p>
                        <p className="mt-1 text-sm text-text/70">
                            {formatDate(contract.moveInDate)} ~ {formatDate(contract.moveOutDate)}
                        </p>
                        <div className="mt-3 flex items-end justify-between border-t border-main/10 pt-3 text-sm">
                            <div className="flex gap-6">
                                <div>
                                    <span className="block text-xs text-text/60">월세</span>
                                    <span className="font-medium">{formatMoney(contract.monthlyFee)}</span>
                                </div>
                                <div>
                                    <span className="block text-xs text-text/60">보증금</span>
                                    <span className="font-medium">{formatMoney(contract.deposit)}</span>
                                </div>
                            </div>
                            <ChevronRight
                                size={18}
                                className="-translate-x-1 text-main opacity-0 transition duration-200 group-hover:translate-x-0 group-hover:opacity-100"
                            />
                        </div>
                    </button>
                ))}
            </section>
        </div>
    )
}

// 사이드바에서 같은 메뉴를 다시 누르면 location.key가 바뀐다. 그 값을 key로 줘서 화면을 새로 만들고, 목록으로 돌아가게 한다.
function ContractsPage(props) {
    const location = useLocation()
    return <ContractsView key={location.key} {...props} />
}

export default ContractsPage