import {useEffect, useState} from 'react'
import {getAdminBuildings} from '../../../pages/property/propertyApi.js'
import {fetchActivityLogs, fetchRooms, fetchSummary} from '../api/dashboardApi.js'
import ActivityLogModal from '../components/ActivityLogModal.jsx'
import ActivityLogPanel from '../components/ActivityLogPanel.jsx'
import CollectionChart from '../components/CollectionChart.jsx'
import FilterBar from '../components/FilterBar.jsx'
import FinanceCards from '../components/FinanceCards.jsx'
import OccupancyTrendChart from '../components/OccupancyTrendChart.jsx'
import RoomStatusPanel from '../components/RoomStatusPanel.jsx'
import SummaryCards from '../components/SummaryCards.jsx'
import {formatTime} from '../utils/activityDate.js'
import {recentMonths} from '../utils/buildings.js'

// 호실 현황·활동 로그·요약을 화면을 켜 둔 채로 다시 가져오는 간격(ms)
const REFRESH_INTERVAL_MS = 30000

function DashboardPage({title}) {
    const [buildings, setBuildings] = useState([])
    const [buildingId, setBuildingId] = useState(null)
    const [month, setMonth] = useState(recentMonths(1)[0]) // 기본값: 이번 달

    const [summary, setSummary] = useState(null)
    const [summaryEmpty, setSummaryEmpty] = useState(false) // 해당 월 집계 데이터 없음(404)
    const [rooms, setRooms] = useState([])
    const [logs, setLogs] = useState([])
    const [logModalOpen, setLogModalOpen] = useState(false)
    const [error, setError] = useState(null)
    const [lastUpdated, setLastUpdated] = useState(null) // 마지막으로 데이터를 받아온 시각(ISO)
    // 호실 동기화(= 새 건물의 스냅샷 생성)가 끝난 건물. 요약은 이 값이 현재 건물과 같을 때만 조회한다.
    const [syncedBuildingId, setSyncedBuildingId] = useState(null)

    useEffect(() => {
        document.title = title
    }, [title])

    // 매물 서비스의 삭제되지 않은 건물 목록. 관리 API는 한 페이지에 100건까지다.
    useEffect(() => {
        let cancelled = false
        async function loadBuildings() {
            try {
                const first = await getAdminBuildings(0, 100)
                const all = [...(first.content ?? [])]
                const totalPages = first.totalPages ?? 1
                for (let page = 1; page < totalPages; page += 1) {
                    const next = await getAdminBuildings(page, 100)
                    all.push(...(next.content ?? []))
                }
                if (cancelled) return
                const options = all
                    .map((building) => ({id: building.id, name: building.name}))
                    .sort((a, b) => a.id - b.id)
                setBuildings(options)
                setBuildingId((current) => (
                    options.some((building) => building.id === current) ? current : (options[0]?.id ?? null)
                ))
                setError(null)
            } catch (e) {
                if (!cancelled) setError(e.message)
            }
        }
        loadBuildings()
        return () => {
            cancelled = true
        }
    }, [])

    // 호실 현황, 활동 로그: 건물이 바뀔 때만 다시 조회
    useEffect(() => {
        if (buildingId == null) return
        let cancelled = false
        Promise.all([fetchRooms(buildingId), fetchActivityLogs(buildingId)])
            .then(([roomData, logData]) => {
                if (cancelled) return
                setRooms(roomData)
                setLogs(logData)
                setLastUpdated(new Date().toISOString())
                setError(null)
            })
            .catch((e) => !cancelled && setError(e.message))
            .finally(() => {
                // 실패해도 요약 조회까지 막지 않는다.
                if (!cancelled) setSyncedBuildingId(buildingId)
            })
        return () => {
            cancelled = true
        }
    }, [buildingId])

    // 지표 카드, 재무 요약, 추이: 건물이나 월이 바뀌면 다시 조회
    // 새 건물은 호실 조회 때 스냅샷이 만들어지므로, 그 조회가 끝난 뒤에 요약을 부른다.
    useEffect(() => {
        if (buildingId == null || syncedBuildingId !== buildingId) return
        let cancelled = false
        fetchSummary(buildingId, month)
            .then((data) => {
                if (cancelled) return
                setSummary(data)
                setSummaryEmpty(false)
                setError(null)
            })
            .catch((e) => {
                if (cancelled) return
                if (e.status === 404) {
                    setSummary(null)
                    setSummaryEmpty(true)
                } else {
                    setError(e.message)
                }
            })
        return () => {
            cancelled = true
        }
    }, [buildingId, month, syncedBuildingId])

    // 자동 갱신: 호실 현황, 활동 로그, 요약을 REFRESH_INTERVAL_MS마다 다시 조회한다.
    // - 처음 조회(위 두 효과)가 끝난 뒤부터 시작하고, 건물이나 월이 바뀌면 이전 갱신은 버리고 새로 시작한다.
    // - 다른 탭을 보는 동안은 멈추고, 돌아오면 바로 한 번 갱신한 뒤 주기를 다시 돌린다.
    // - 이전 갱신이 끝나기 전이면 이번 주기는 건너뛴다.
    // - 갱신이 실패하면 지금 보이는 데이터와 에러 표시를 그대로 둔다.
    useEffect(() => {
        if (buildingId == null || syncedBuildingId !== buildingId) return undefined
        let cancelled = false
        let running = false
        let timer = null

        async function refresh() {
            if (running) return
            running = true
            try {
                const [roomResult, logResult, summaryResult] = await Promise.allSettled([
                    fetchRooms(buildingId),
                    fetchActivityLogs(buildingId),
                    fetchSummary(buildingId, month),
                ])
                if (cancelled) return
                if (roomResult.status === 'fulfilled') setRooms(roomResult.value)
                if (logResult.status === 'fulfilled') setLogs(logResult.value)
                if (summaryResult.status === 'fulfilled') {
                    setSummary(summaryResult.value)
                    setSummaryEmpty(false)
                } else if (summaryResult.reason?.status === 404) {
                    setSummary(null)
                    setSummaryEmpty(true)
                }
                if (roomResult.status === 'fulfilled' || logResult.status === 'fulfilled'
                    || summaryResult.status === 'fulfilled') {
                    setLastUpdated(new Date().toISOString())
                }
            } finally {
                running = false
            }
        }

        function stop() {
            if (timer != null) {
                clearInterval(timer)
                timer = null
            }
        }

        function start() {
            stop()
            timer = setInterval(refresh, REFRESH_INTERVAL_MS)
        }

        function onVisibilityChange() {
            if (document.visibilityState === 'visible') {
                refresh()
                start()
            } else {
                stop()
            }
        }

        if (document.visibilityState === 'visible') start()
        document.addEventListener('visibilitychange', onVisibilityChange)
        return () => {
            cancelled = true
            stop()
            document.removeEventListener('visibilitychange', onVisibilityChange)
        }
    }, [buildingId, month, syncedBuildingId])

    return (
        <div className="w-full max-w-[68rem]">
            <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                <div>
                    <h1 className="text-2xl font-semibold text-main sm:text-3xl">{title}</h1>
                    <p className="mt-1 text-sm text-text/70">입주 현황과 수납 상태를 한 화면에서 확인합니다.</p>
                    {lastUpdated && (
                        <p className="mt-1 text-xs text-text/40">
                            마지막 갱신 {formatTime(lastUpdated)}
                        </p>
                    )}
                </div>
                <FilterBar
                    buildings={buildings}
                    buildingId={buildingId}
                    month={month}
                    onBuildingChange={setBuildingId}
                    onMonthChange={setMonth}
                />
            </div>

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <div className="space-y-5">
                {summaryEmpty && (
                    <div
                        className="rounded-lg border border-dashed border-main/30 bg-white p-8 text-center text-sm text-text/50">
                        선택한 월의 집계 데이터가 없습니다.
                    </div>
                )}

                {summary && <SummaryCards cards={summary.cards}/>}

                <div className="grid gap-5 lg:grid-cols-2">
                    <RoomStatusPanel rooms={rooms}/>
                    <ActivityLogPanel logs={logs} onMore={() => setLogModalOpen(true)}/>
                </div>

                {summary && <FinanceCards finance={summary.finance}/>}

                {summary && (
                    <div className="grid gap-5 lg:grid-cols-2">
                        <OccupancyTrendChart trend={summary.trend}/>
                        <CollectionChart trend={summary.trend}/>
                    </div>
                )}
            </div>

            {logModalOpen && buildingId != null && (
                <ActivityLogModal
                    buildingId={buildingId}
                    buildingName={buildings.find((building) => building.id === buildingId)?.name}
                    onClose={() => setLogModalOpen(false)}
                />
            )}
        </div>
    )
}

export default DashboardPage