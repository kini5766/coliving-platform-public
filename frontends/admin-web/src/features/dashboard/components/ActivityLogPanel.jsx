import { useLayoutEffect, useRef, useState } from 'react'
import { dayAgoLabel, formatTime } from '../utils/activityDate.js'
import { statusOf, typeOf } from '../utils/activityLogMeta.js'

const COLUMNS = 'grid grid-cols-[3.5rem_3.5rem_minmax(0,1fr)_3.5rem] items-center gap-3'

// 행 최소 높이(px). 아래 행의 min-h-9(2.25rem)와 같아야 한다.
const ROW_MIN_PX = 36

function ActivityLogPanel({ logs, onMore }) {
    const listRef = useRef(null)
    const headerRef = useRef(null)
    // 카드 안에 온전히 들어가는 행 수. 아직 재지 못했으면 null (전부 그리고 넘치는 건 가린다)
    const [maxRows, setMaxRows] = useState(null)
    const hasLogs = logs.length > 0

    // 목록 영역의 높이가 바뀔 때마다(창 크기, 옆 호실 카드 높이 변화) 들어갈 행 수를 다시 센다.
    // 더보기 푸터는 목록 영역 바깥에 있어서, 푸터가 차지하는 높이는 자동으로 빠진다.
    useLayoutEffect(() => {
        const list = listRef.current
        if (!list) return undefined

        const update = () => {
            const headerHeight = headerRef.current?.offsetHeight ?? 0
            const rows = Math.floor((list.clientHeight - headerHeight) / ROW_MIN_PX)
            setMaxRows(Math.max(0, rows))
        }

        update()
        const observer = new ResizeObserver(update)
        observer.observe(list)
        return () => observer.disconnect()
    }, [hasLogs])

    // 넘치는 로그는 스크롤 대신 잘라낸다. (최신순으로 오므로 오래된 것부터 사라진다)
    const visibleLogs = maxRows == null ? logs : logs.slice(0, maxRows)

    return (
        // 이 카드는 높이를 직접 정하지 않고, 옆 호실 현황 카드 높이를 그대로 따라간다.
        // 안쪽(absolute)은 바깥 높이에 영향을 주지 않으므로 로그 개수가 늘어도 호실 카드에 빈칸이 생기지 않는다.
        <section className="relative min-h-60 rounded-lg border border-main/20 bg-white">
            <div className="absolute inset-0 flex flex-col p-4">
                <h2 className="mb-3 shrink-0 text-base font-semibold text-text">최근 활동 로그</h2>

                {!hasLogs ? (
                    <p className="flex flex-1 items-center justify-center text-sm text-text/50">
                        최근 활동이 없습니다.
                    </p>
                ) : (
                    <>
                        <div ref={listRef} className="flex min-h-0 flex-1 flex-col overflow-hidden">
                            <div
                                ref={headerRef}
                                className={`${COLUMNS} shrink-0 border-b border-main/10 bg-white pb-2 text-xs text-text/50`}
                            >
                                <span>시간</span>
                                <span>유형</span>
                                <span>내용</span>
                                <span className="text-right">상태</span>
                            </div>

                            {visibleLogs.map((log) => {
                                const type = typeOf(log.activityType)
                                const status = statusOf(log.status)
                                const dayLabel = dayAgoLabel(log.updatedAt)
                                return (
                                    <div
                                        key={log.id}
                                        className={`${COLUMNS} max-h-14 min-h-9 flex-1 shrink-0 border-b border-main/5 text-sm`}
                                    >
                                        <span className="whitespace-nowrap text-text/70">
                                            {dayLabel || formatTime(log.updatedAt)}
                                        </span>
                                        <span>
                                            <span className={`whitespace-nowrap rounded px-2 py-0.5 text-xs ${type.className}`}>
                                                {type.label}
                                            </span>
                                        </span>
                                        <span className="text-text">{log.content}</span>
                                        <span className={`whitespace-nowrap text-right font-medium ${status.className}`}>
                                            {status.label}
                                        </span>
                                    </div>
                                )
                            })}
                        </div>

                        <button
                            type="button"
                            onClick={onMore}
                            className="shrink-0 border-t border-main/10 pt-2 text-center text-sm text-main hover:underline"
                        >
                            더보기 ›
                        </button>
                    </>
                )}
            </div>
        </section>
    )
}

export default ActivityLogPanel