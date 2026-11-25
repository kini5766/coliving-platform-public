import {useCallback, useEffect, useRef, useState} from 'react'
import {fetchActivityHistory} from '../api/dashboardApi.js'
import {daySeparatorLabel, daysAgo, formatTime} from '../utils/activityDate.js'
import {statusOf, typeOf} from '../utils/activityLogMeta.js'

const PAGE_SIZE = 20

const FILTERS = [
    {value: null, label: '전체'},
    {value: 'CONTRACT', label: '계약'},
    {value: 'RESERVATION', label: '예약'},
    {value: 'MOVE_IN', label: '입주'},
    {value: 'PAYMENT', label: '결제'},
]

const COLUMNS = 'grid grid-cols-[3rem_3.5rem_minmax(0,1fr)_3.5rem] items-center gap-3'

// 같은 날짜끼리 묶는다. (목록은 이미 최근순이라 날짜가 바뀌는 지점에서 새 묶음을 만든다)
function groupByDay(items) {
    const groups = []
    for (const log of items) {
        const days = daysAgo(log.updatedAt)
        const last = groups[groups.length - 1]
        if (last && last.days === days) {
            last.logs.push(log)
        } else {
            groups.push({days, label: daySeparatorLabel(log.updatedAt), logs: [log]})
        }
    }
    return groups
}

function ActivityLogModal({buildingId, buildingName, onClose}) {
    const [type, setType] = useState(null)
    const [items, setItems] = useState([])
    const [page, setPage] = useState(0)
    const [hasNext, setHasNext] = useState(false)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)
    // 필터를 빠르게 바꿨을 때 늦게 도착한 이전 응답이 화면을 덮어쓰지 않게 한다.
    const requestRef = useRef(0)

    const load = useCallback(async (nextPage, replace) => {
        const requestId = ++requestRef.current
        setLoading(true)
        setError(null)
        try {
            const data = await fetchActivityHistory(buildingId, {type, page: nextPage, size: PAGE_SIZE})
            if (requestId !== requestRef.current) return
            setItems((prev) => {
                if (replace) return data.items
                // 불러오는 사이 새 로그가 쌓이면 같은 건이 다음 페이지에 또 올 수 있어서 거른다.
                const known = new Set(prev.map((log) => log.id))
                return [...prev, ...data.items.filter((log) => !known.has(log.id))]
            })
            setPage(nextPage)
            setHasNext(data.hasNext)
        } catch (e) {
            if (requestId === requestRef.current) setError(e.message)
        } finally {
            if (requestId === requestRef.current) setLoading(false)
        }
    }, [buildingId, type])

    // 건물·유형이 정해지거나 바뀌면 첫 페이지부터 다시 가져온다.
    useEffect(() => {
        setItems([])
        setPage(0)
        setHasNext(false)
        load(0, true)
    }, [load])

    // Esc로 닫기, 열려 있는 동안 뒤 화면 스크롤 잠금
    useEffect(() => {
        const onKeyDown = (event) => {
            if (event.key === 'Escape') onClose()
        }
        window.addEventListener('keydown', onKeyDown)
        const previousOverflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
        return () => {
            window.removeEventListener('keydown', onKeyDown)
            document.body.style.overflow = previousOverflow
        }
    }, [onClose])

    const groups = groupByDay(items)
    const firstLoading = loading && items.length === 0

    return (
        <div
            className="fixed inset-0 z-50 bg-black/40 sm:flex sm:items-center sm:justify-center sm:p-6"
            onClick={onClose}
        >
            {/* 모바일은 전체 화면, 그 이상은 가운데 팝업 */}
            <div
                role="dialog"
                aria-modal="true"
                aria-label="활동 로그"
                className="flex h-full w-full flex-col bg-white sm:h-[min(40rem,85vh)] sm:max-w-2xl sm:rounded-lg"
                onClick={(event) => event.stopPropagation()}
            >
                <div className="flex shrink-0 items-center justify-between border-b border-main/10 px-4 py-3">
                    <h2 className="text-base font-semibold text-text">
                        활동 로그{buildingName ? ` · ${buildingName}` : ''}
                    </h2>
                    <button
                        type="button"
                        onClick={onClose}
                        aria-label="닫기"
                        className="rounded px-2 py-1 text-xl leading-none text-text/60 hover:bg-text/10"
                    >
                        ×
                    </button>
                </div>

                <div className="flex shrink-0 flex-wrap gap-2 border-b border-main/10 px-4 py-3">
                    {FILTERS.map((filter) => {
                        const active = filter.value === type
                        return (
                            <button
                                key={filter.label}
                                type="button"
                                onClick={() => setType(filter.value)}
                                aria-pressed={active}
                                className={`rounded-full px-3 py-1 text-sm ${
                                    active ? 'bg-main text-background' : 'bg-text/10 text-text hover:bg-text/20'
                                }`}
                            >
                                {filter.label}
                            </button>
                        )
                    })}
                </div>

                <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
                    {firstLoading && (
                        <p className="py-12 text-center text-sm text-text/50">불러오는 중...</p>
                    )}

                    {!loading && !error && items.length === 0 && (
                        <p className="py-12 text-center text-sm text-text/50">활동 로그가 없습니다.</p>
                    )}

                    {groups.map((group) => (
                        <section key={`${group.days}-${group.logs[0].id}`}>
                            <h3 className="sticky top-0 bg-white py-2 text-xs font-medium text-text/50">
                                {group.label}
                            </h3>
                            {group.logs.map((log) => {
                                const logType = typeOf(log.activityType)
                                const logStatus = statusOf(log.status)
                                return (
                                    <div
                                        key={log.id}
                                        className={`${COLUMNS} min-h-9 border-b border-main/5 py-1 text-sm`}
                                    >
                                        <span className="whitespace-nowrap text-text/70">
                                            {formatTime(log.updatedAt)}
                                        </span>
                                        <span>
                                            <span className={`whitespace-nowrap rounded px-2 py-0.5 text-xs ${logType.className}`}>
                                                {logType.label}
                                            </span>
                                        </span>
                                        <span className="text-text">{log.content}</span>
                                        <span className={`whitespace-nowrap text-right font-medium ${logStatus.className}`}>
                                            {logStatus.label}
                                        </span>
                                    </div>
                                )
                            })}
                        </section>
                    ))}

                    {error && (
                        <div className="py-6 text-center text-sm">
                            <p className="mb-2 text-red-600">{error}</p>
                            <button
                                type="button"
                                onClick={() => load(items.length === 0 ? 0 : page + 1, items.length === 0)}
                                className="rounded border border-main/30 px-3 py-1 text-main hover:bg-main/10"
                            >
                                다시 시도
                            </button>
                        </div>
                    )}

                    {!error && hasNext && items.length > 0 && (
                        <div className="pt-4 text-center">
                            <button
                                type="button"
                                disabled={loading}
                                onClick={() => load(page + 1, false)}
                                className="rounded border border-main/30 px-4 py-2 text-sm text-main hover:bg-main/10 disabled:opacity-50"
                            >
                                {loading ? '불러오는 중...' : `${PAGE_SIZE}건 더 불러오기`}
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}

export default ActivityLogModal