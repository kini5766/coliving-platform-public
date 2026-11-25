
import { useEffect, useMemo, useState } from 'react'
import { getAdminBuildings } from '../property/propertyApi'

const API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

const STATUS_LABELS = {
  PENDING: '대기',
  APPROVED: '예약 승인',
  CANCELED: '예약 취소',
  COMPLETED: '이용 완료',
  EXPIRED: '이용 만료',
  OVERDUE: '연체',
}

const STATUS_COLORS = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-green-100 text-green-800',
  CANCELED: 'bg-gray-100 text-gray-700',
  COMPLETED: 'bg-blue-100 text-blue-800',
  EXPIRED: 'bg-gray-100 text-gray-700',
  OVERDUE: 'bg-red-100 text-red-700',
}

function ReservationManage() {
  // 건물 목록
  const [buildings, setBuildings] = useState([])
  const [selectedBuildingId, setSelectedBuildingId] = useState('')
  const [buildingLoading, setBuildingLoading] = useState(true)
  const [buildingError, setBuildingError] = useState('')

  // 공용 자원 및 예약 목록
  const [resources, setResources] = useState([])
  const [reservations, setReservations] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // 검색 및 필터
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')

  // 새로고침
  const [refreshKey, setRefreshKey] = useState(0)

  // 1. 관리자 건물 목록 조회
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
          setBuildingError(err.message)
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

  // 2. 선택한 건물의 공용 자원 및 예약 조회
  useEffect(() => {
    if (!selectedBuildingId) {
      setResources([])
      setReservations([])
      setError('')
      return
    }

    let cancelled = false

    async function loadReservations() {
      try {
        setLoading(true)
        setError('')
        setResources([])
        setReservations([])

        // 선택한 건물에 등록된 공용 자원 조회
        const resourceResponse = await fetch(
          `${API_BASE_URL}/reservation-service/resources?buildingId=${selectedBuildingId}`,
          {
            credentials: 'include',
            headers: {
              Accept: 'application/json',
            },
          }
        )

        if (!resourceResponse.ok) {
          throw new Error(
            `공용 자원 조회 실패 (${resourceResponse.status})`
          )
        }

        const resourceData = await resourceResponse.json()

        // 관리자 전체 예약 조회
        const reservationResponse = await fetch(
          `${API_BASE_URL}/reservation-service/reservations/admin`,
          {
            credentials: 'include',
            headers: {
              Accept: 'application/json',
            },
          }
        )

        if (!reservationResponse.ok) {
          throw new Error(
            `예약 조회 실패 (${reservationResponse.status})`
          )
        }

        const reservationData = await reservationResponse.json()

        if (!Array.isArray(resourceData)) {
          throw new Error('공용 자원 응답 형식이 올바르지 않습니다.')
        }

        if (!Array.isArray(reservationData)) {
          throw new Error('예약 응답 형식이 올바르지 않습니다.')
        }

        // 선택한 건물의 공용 자원 ID만 추출
        const resourceIds = new Set(
          resourceData.map((resource) => String(resource.id))
        )

        // 해당 건물의 예약만 추출
        const buildingReservations = reservationData.filter(
          (reservation) =>
            resourceIds.has(String(reservation.resourceId))
        )

        if (!cancelled) {
          setResources(resourceData)
          setReservations(buildingReservations)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err.message)
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    loadReservations()

    return () => {
      cancelled = true
    }
  }, [selectedBuildingId, refreshKey])

  // 3. 공용 자원 ID → 공용 자원 정보 연결
  const resourceMap = useMemo(() => {
    return new Map(
      resources.map((resource) => [
        String(resource.id),
        resource,
      ])
    )
  }, [resources])

  // 4. 예약번호 / 이용자 검색 및 상태 필터
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
        (statusFilter === 'OVERDUE'
          ? reservation.status === 'OVERDUE' ||
            reservation.overdueStatus === 'OVERDUE'
          : reservation.status === statusFilter)

      return matchesSearch && matchesStatus
    })
  }, [reservations, resourceMap, search, statusFilter])

  // 5. 예약 현황 집계
  const totalCount = reservations.length

  const approvedCount = reservations.filter(
    (reservation) => reservation.status === 'APPROVED'
  ).length

  const completedCount = reservations.filter(
    (reservation) => reservation.status === 'COMPLETED'
  ).length

  const overdueCount = reservations.filter(
    (reservation) =>
      reservation.status === 'OVERDUE' ||
      reservation.overdueStatus === 'OVERDUE'
  ).length

  // 6. 건물 변경
  function handleBuildingChange(e) {
    setSelectedBuildingId(e.target.value)
    setSearch('')
    setStatusFilter('ALL')
  }

  // 7. 새로고침
  function handleRefresh() {
    setRefreshKey((prev) => prev + 1)
  }

  // 8. 날짜 및 시간 표시
  function formatTime(time) {
    return time ? String(time).slice(0, 5) : '-'
  }

  return (
    <div className="min-h-screen bg-[#F3F5F1] p-6 text-[#26332E]">
      {/* 페이지 제목 */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold">
          공용 자원 예약 관리
        </h1>

        <p className="mt-2 text-sm text-gray-600">
          건물별 공용 자원 예약 및 연체 현황을 확인합니다.
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

      {/* 건물 미선택 안내 */}
      {!selectedBuildingId && (
        <div className="rounded-xl bg-white p-10 text-center text-gray-500 shadow-sm">
          예약 현황을 조회할 건물을 선택해 주세요.
        </div>
      )}

      {/* 건물 선택 후 예약 관리 화면 */}
      {selectedBuildingId && (
        <>
          {/* 예약 현황 요약 */}
          <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[
              { label: '전체 예약', count: totalCount },
              { label: '승인 예약', count: approvedCount },
              { label: '이용 완료', count: completedCount },
              { label: '연체 예약', count: overdueCount },
            ].map((item) => (
              <div
                key={item.label}
                className="rounded-xl bg-white p-5 shadow-sm"
              >
                <p className="text-sm text-gray-500">
                  {item.label}
                </p>

                <p className="mt-2 text-3xl font-bold">
                  {loading ? '-' : item.count}
                </p>
              </div>
            ))}
          </div>

          {/* 검색 및 필터 */}
          <div className="mb-6 rounded-xl bg-white p-6 shadow-sm">
            <div className="flex flex-col gap-4 md:flex-row">
              <input
                type="text"
                placeholder="예약번호, 이용자 ID, 시설명 검색"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="flex-1 rounded-lg border p-3"
              />

              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="rounded-lg border bg-white p-3"
              >
                <option value="ALL">전체 상태</option>
                <option value="PENDING">대기</option>
                <option value="APPROVED">예약 승인</option>
                <option value="COMPLETED">이용 완료</option>
                <option value="CANCELED">예약 취소</option>
                <option value="EXPIRED">이용 만료</option>
                <option value="OVERDUE">연체</option>
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

          {/* 예약 목록 */}
          <div className="overflow-hidden rounded-xl bg-white shadow-sm">
            <div className="border-b p-5">
              <h2 className="text-lg font-semibold">
                예약 목록 ({loading ? '-' : filteredReservations.length}건)
              </h2>
            </div>

            {loading && (
              <p className="p-6 text-gray-500">
                예약 정보를 불러오는 중입니다...
              </p>
            )}

            {error && (
              <p
                role="alert"
                className="p-6 text-red-600"
              >
                {error}
              </p>
            )}

            {!loading &&
              !error &&
              filteredReservations.length === 0 && (
                <p className="p-6 text-gray-500">
                  조회된 예약이 없습니다.
                </p>
              )}

            {!loading &&
              !error &&
              filteredReservations.length > 0 && (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[900px] text-left text-sm">
                    <thead className="bg-[#F3F5F1]">
                      <tr>
                        <th className="p-4">예약번호</th>
                        <th className="p-4">시설명</th>
                        <th className="p-4">이용자 ID</th>
                        <th className="p-4">예약 날짜</th>
                        <th className="p-4">예약 시간</th>
                        <th className="p-4">예약 상태</th>
                        <th className="p-4">반납 시간</th>
                      </tr>
                    </thead>

                    <tbody>
                      {filteredReservations.map((reservation) => {
                        const resource = resourceMap.get(
                          String(reservation.resourceId)
                        )

                        const isOverdue =
                          reservation.status === 'OVERDUE' ||
                          reservation.overdueStatus === 'OVERDUE'

                        const displayStatus = isOverdue
                          ? 'OVERDUE'
                          : reservation.status

                        return (
                          <tr
                            key={reservation.id}
                            className="border-t hover:bg-gray-50"
                          >
                            <td className="p-4 font-medium">
                              {reservation.reservationNumber}
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
                              {formatTime(reservation.startTime)}
                              {' ~ '}
                              {formatTime(reservation.endTime)}
                            </td>

                            <td className="p-4">
                              <span
                                className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${
                                  STATUS_COLORS[displayStatus] ??
                                  'bg-gray-100 text-gray-700'
                                }`}
                              >
                                {STATUS_LABELS[displayStatus] ??
                                  displayStatus ??
                                  '-'}
                              </span>
                            </td>

                            <td className="p-4">
                              {reservation.returnedAt
                                ? String(reservation.returnedAt)
                                    .replace('T', ' ')
                                    .slice(0, 16)
                                : '-'}
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

export default ReservationManage
