
import { useEffect, useMemo, useState } from 'react'
import { getAdminBuildings } from '../property/propertyApi'

const API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

function OverdueManage() {
    // ==========================================
    // 1. 상태 관리
    // ==========================================

    const [buildings, setBuildings] = useState([])
    const [selectedBuildingId, setSelectedBuildingId] = useState('')

    const [resources, setResources] = useState([])
    const [reservations, setReservations] = useState([])

    const [buildingLoading, setBuildingLoading] = useState(true)
    const [loading, setLoading] = useState(false)

    const [buildingError, setBuildingError] = useState('')
    const [error, setError] = useState('')

    const [search, setSearch] = useState('')
    const [statusFilter, setStatusFilter] = useState('ALL')

    const [refreshKey, setRefreshKey] = useState(0)

    // ==========================================
    // 2. 관리자 건물 목록 조회
    // ==========================================

    useEffect(() => {
        let cancelled = false

        async function loadBuildings() {
            try {
                setBuildingLoading(true)
                setBuildingError('')

                const firstPage = await getAdminBuildings(0, 20)
                const allBuildings = [...(firstPage.content ?? [])]

                for (
                    let page = 1;
                    page < (firstPage.totalPages ?? 1);
                    page++
                ) {
                    const result = await getAdminBuildings(page, 20)
                    allBuildings.push(...(result.content ?? []))
                }

                if (!cancelled) {
                    setBuildings(allBuildings)
                }
            } catch (err) {
                if (!cancelled) {
                    setBuildingError(
                        err.message || '건물 목록 조회에 실패했습니다.'
                    )
                }
            } finally {
                if (!cancelled) {
                    setBuildingLoading(false)
                }
            }
        }

        loadBuildings()

        return () => {
            cancelled = true
        }
    }, [])

    // ==========================================
    // 3. 건물별 연체 이력 조회
    // ==========================================

    useEffect(() => {
        if (!selectedBuildingId) {
            setResources([])
            setReservations([])
            setError('')
            setLoading(false)
            return
        }

        let cancelled = false

        async function loadOverdueReservations() {
            try {
                setLoading(true)
                setError('')
                setResources([])
                setReservations([])

                // 선택한 건물의 공용 자원과
                // 관리자 연체 이력 API를 동시에 호출
                const [resourceResponse, overdueResponse] =
                    await Promise.all([
                        fetch(
                            `${API_BASE_URL}/reservation-service/resources?buildingId=${encodeURIComponent(selectedBuildingId)}`,
                            {
                                credentials: 'include',
                                headers: {
                                    Accept: 'application/json',
                                },
                            }
                        ),

                        fetch(
                            `${API_BASE_URL}/reservation-service/reservations/admin/overdue`,
                            {
                                credentials: 'include',
                                headers: {
                                    Accept: 'application/json',
                                },
                            }
                        ),
                    ])

                if (!resourceResponse.ok) {
                    throw new Error(
                        `공용 자원 조회 실패 (${resourceResponse.status})`
                    )
                }

                if (!overdueResponse.ok) {
                    throw new Error(
                        `연체 이력 조회 실패 (${overdueResponse.status})`
                    )
                }

                const resourceData = await resourceResponse.json()
                const overdueData = await overdueResponse.json()

                if (!Array.isArray(resourceData)) {
                    throw new Error(
                        '공용 자원 응답 형식이 올바르지 않습니다.'
                    )
                }

                if (!Array.isArray(overdueData)) {
                    throw new Error(
                        '연체 이력 응답 형식이 올바르지 않습니다.'
                    )
                }

                // 선택한 건물의 공용 자원 ID 목록
                const resourceIds = new Set(
                    resourceData.map((resource) => String(resource.id))
                )

                // 해당 건물에 속하면서 연체 이력이 있는 예약
                const buildingOverdueReservations = overdueData.filter(
                    (reservation) =>
                        resourceIds.has(String(reservation.resourceId)) &&
                        reservation.isOverdue === true
                )

                if (!cancelled) {
                    setResources(resourceData)
                    setReservations(buildingOverdueReservations)
                }
            } catch (err) {
                if (!cancelled) {
                    setError(
                        err.message || '연체 이력 조회에 실패했습니다.'
                    )
                }
            } finally {
                if (!cancelled) {
                    setLoading(false)
                }
            }
        }

        loadOverdueReservations()

        return () => {
            cancelled = true
        }
    }, [selectedBuildingId, refreshKey])

    // ==========================================
    // 4. 공용 자원 ID와 이름 연결
    // ==========================================

    const resourceMap = useMemo(() => {
        return new Map(
            resources.map((resource) => [
                String(resource.id),
                resource,
            ])
        )
    }, [resources])

    // ==========================================
    // 5. 검색 및 반납 상태 필터
    // ==========================================

    const filteredReservations = useMemo(() => {
        const keyword = search.trim().toLowerCase()

        return reservations.filter((reservation) => {
            const resource = resourceMap.get(
                String(reservation.resourceId)
            )

            const matchesSearch =
                !keyword ||
                String(reservation.reservationNumber ?? '')
                    .toLowerCase()
                    .includes(keyword) ||
                String(reservation.userId ?? '')
                    .toLowerCase()
                    .includes(keyword) ||
                String(resource?.resourceName ?? '')
                    .toLowerCase()
                    .includes(keyword)

            const matchesStatus =
                statusFilter === 'ALL' ||
                (statusFilter === 'UNRETURNED' &&
                    !reservation.returnedAt) ||
                (statusFilter === 'RETURNED' &&
                    Boolean(reservation.returnedAt))

            return matchesSearch && matchesStatus
        })
    }, [reservations, resourceMap, search, statusFilter])

    // ==========================================
    // 6. 연체 현황 집계
    // ==========================================

    const totalCount = reservations.length

    const unreturnedCount = reservations.filter(
        (reservation) => !reservation.returnedAt
    ).length

    const returnedCount = reservations.filter(
        (reservation) => Boolean(reservation.returnedAt)
    ).length

    // ==========================================
    // 7. 이벤트 처리
    // ==========================================

    function handleBuildingChange(event) {
        setSelectedBuildingId(event.target.value)
        setSearch('')
        setStatusFilter('ALL')
    }

    function handleRefresh() {
        setRefreshKey((prev) => prev + 1)
    }

    // ==========================================
    // 8. 날짜 및 시간 표시
    // ==========================================

    function formatTime(time) {
        return time ? String(time).slice(0, 5) : '-'
    }

    function formatDateTime(dateTime) {
        return dateTime
            ? String(dateTime).replace('T', ' ').slice(0, 16)
            : '-'
    }

    // ==========================================
    // 9. 화면
    // ==========================================

    return (
        <div className="min-h-screen bg-[#F3F5F1] p-6 text-[#26332E]">

            {/* 제목 */}
            <div className="mb-6">
                <h1 className="text-2xl font-bold">
                    공용 자원 연체 관리
                </h1>

                <p className="mt-2 text-sm text-gray-600">
                    건물별 물품 연체 이력과 반납 현황을 확인합니다.
                </p>
            </div>

            {/* 건물 선택 */}
            <div className="mb-6 rounded-xl bg-white p-6 shadow-sm">
                <h2 className="mb-4 text-lg font-semibold">
                    건물 선택
                </h2>

                <select
                    value={selectedBuildingId}
                    onChange={handleBuildingChange}
                    disabled={buildingLoading}
                    className="w-full rounded-lg border bg-white p-3"
                >
                    <option value="">건물을 선택하세요</option>

                    {buildings.map((building) => (
                        <option
                            key={building.id}
                            value={String(building.id)}
                        >
                            {building.name}
                        </option>
                    ))}
                </select>

                {buildingLoading && (
                    <p className="mt-3 text-sm text-gray-500">
                        건물 목록을 불러오는 중입니다...
                    </p>
                )}

                {buildingError && (
                    <p className="mt-3 text-sm text-red-600">
                        {buildingError}
                    </p>
                )}
            </div>

            {/* 건물 미선택 */}
            {!selectedBuildingId && (
                <div className="rounded-xl bg-white p-10 text-center text-gray-500 shadow-sm">
                    연체 현황을 조회할 건물을 선택해 주세요.
                </div>
            )}

            {/* 연체 관리 */}
            {selectedBuildingId && (
                <>
                    {/* 요약 카드 */}
                    <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
                        {[
                            {
                                label: '전체 연체 이력',
                                count: totalCount,
                            },
                            {
                                label: '미반납 연체',
                                count: unreturnedCount,
                            },
                            {
                                label: '반납 완료',
                                count: returnedCount,
                            },
                        ].map((item) => (
                            <div
                                key={item.label}
                                className="rounded-xl bg-white p-5 shadow-sm"
                            >
                                <p className="text-sm text-gray-500">
                                    {item.label}
                                </p>

                                <p className="mt-2 text-3xl font-bold">
                                    {loading || error ? '-' : item.count}
                                </p>
                            </div>
                        ))}
                    </div>

                    {/* 검색 */}
                    <div className="mb-6 rounded-xl bg-white p-6 shadow-sm">
                        <div className="flex flex-col gap-4 md:flex-row">
                            <input
                                type="text"
                                placeholder="예약번호, 이용자 ID, 자원명 검색"
                                value={search}
                                onChange={(event) =>
                                    setSearch(event.target.value)
                                }
                                className="flex-1 rounded-lg border p-3"
                            />

                            <select
                                value={statusFilter}
                                onChange={(event) =>
                                    setStatusFilter(event.target.value)
                                }
                                className="rounded-lg border bg-white p-3"
                            >
                                <option value="ALL">전체 연체 이력</option>
                                <option value="UNRETURNED">미반납</option>
                                <option value="RETURNED">반납 완료</option>
                            </select>

                            <button
                                type="button"
                                onClick={handleRefresh}
                                disabled={loading}
                                className="rounded-lg bg-[#52796F] px-5 py-3 font-medium text-white disabled:opacity-50"
                            >
                                새로고침
                            </button>
                        </div>
                    </div>

                    {/* 연체 목록 */}
                    <div className="overflow-hidden rounded-xl bg-white shadow-sm">
                        <div className="border-b p-5">
                            <h2 className="text-lg font-semibold">
                                연체 목록 (
                                {loading || error
                                    ? '-'
                                    : filteredReservations.length}
                                건)
                            </h2>
                        </div>

                        {loading && (
                            <p className="p-6 text-gray-500">
                                연체 정보를 불러오는 중입니다...
                            </p>
                        )}

                        {error && (
                            <p role="alert" className="p-6 text-red-600">
                                {error}
                            </p>
                        )}

                        {!loading &&
                            !error &&
                            filteredReservations.length === 0 && (
                                <p className="p-6 text-gray-500">
                                    조회된 연체 이력이 없습니다.
                                </p>
                            )}

                        {!loading &&
                            !error &&
                            filteredReservations.length > 0 && (
                                <div className="overflow-x-auto">
                                    <table className="w-full min-w-[950px] text-left text-sm">
                                        <thead className="bg-[#F3F5F1]">
                                        <tr>
                                            <th className="p-4">예약번호</th>
                                            <th className="p-4">공용 자원</th>
                                            <th className="p-4">이용자 ID</th>
                                            <th className="p-4">예약 날짜</th>
                                            <th className="p-4">예약 종료</th>
                                            <th className="p-4">반납 상태</th>
                                            <th className="p-4">반납 시간</th>
                                        </tr>
                                        </thead>

                                        <tbody>
                                        {filteredReservations.map((reservation) => {
                                            const resource = resourceMap.get(
                                                String(reservation.resourceId)
                                            )

                                            const isReturned = Boolean(
                                                reservation.returnedAt
                                            )

                                            return (
                                                <tr
                                                    key={reservation.id}
                                                    className="border-t hover:bg-gray-50"
                                                >
                                                    <td className="p-4 font-medium">
                                                        {reservation.reservationNumber ?? '-'}
                                                    </td>

                                                    <td className="p-4">
                                                        {resource?.resourceName ??
                                                            `자원 ${reservation.resourceId}`}
                                                    </td>

                                                    <td className="p-4">
                                                        {reservation.userId}
                                                    </td>

                                                    <td className="p-4">
                                                        {reservation.reservationDate}
                                                    </td>

                                                    <td className="p-4">
                                                        {formatTime(reservation.endTime)}
                                                    </td>

                                                    <td className="p-4">
                              <span
                                  className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${
                                      isReturned
                                          ? 'bg-blue-100 text-blue-800'
                                          : 'bg-red-100 text-red-700'
                                  }`}
                              >
                                {isReturned
                                    ? '반납 완료'
                                    : '미반납'}
                              </span>
                                                    </td>

                                                    <td className="p-4">
                                                        {formatDateTime(
                                                            reservation.returnedAt
                                                        )}
                                                    </td>
                                                </tr>
                                            )
                                        })}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                    </div>
                </>
            )}
        </div>
    )
}

export default OverdueManage
