
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { searchBuildings } from '../property/propertyApi'

const API_BASE_URL =
    import.meta.env.VITE_BACKEND_API_BASE_URL || 'http://localhost:8081'

function ResourceListPage() {
  const navigate = useNavigate()

  // 건물 검색어
  const [buildingKeyword, setBuildingKeyword] = useState('')

  // 건물 검색 결과
  const [buildings, setBuildings] = useState([])
  const [buildingLoading, setBuildingLoading] = useState(false)
  const [buildingError, setBuildingError] = useState('')

  // 선택한 건물
  const [buildingId, setBuildingId] = useState('')
  const [selectedBuildingName, setSelectedBuildingName] =
      useState('선택된 건물 없음')

  // 공용 자원 목록
  const [resources, setResources] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // 공용 자원별 이미지
  const resourceImages = {
    '회의실 A': '/images/meeting-room-a.png',
    '회의실 B': '/images/meeting-room-b.png',
    '회의실 C': '/images/meeting-room-c.png',
    '스터디룸': '/images/study-room.png',
    '커뮤니티룸': '/images/community-room.png',
    '공용 청소기': '/images/vacuum-cleaner.png',
    '빔프로젝터': '/images/projector.png'
  }

  // 공용 자원 표시 순서
  const resourceOrder = {
    '회의실 A': 1,
    '회의실 B': 2,
    '회의실 C': 3,
    '스터디룸': 4,
    '커뮤니티룸': 5,
    '공용 청소기': 6,
    '빔프로젝터': 7
  }

  // 건물명 검색
  const handleBuildingSearch = async (e) => {
    e.preventDefault()

    setBuildingLoading(true)
    setBuildingError('')
    setBuildings([])

    try {
      const data = await searchBuildings({
        keyword: buildingKeyword.trim(),
        page: 0,
        size: 50
      })

      setBuildings(data.content ?? [])
    } catch (err) {
      console.error('건물 검색 오류:', err)
      setBuildingError('건물 목록을 불러오지 못했습니다.')
    } finally {
      setBuildingLoading(false)
    }
  }

  // 검색 결과에서 건물 선택
  const handleBuildingSelect = (building) => {
    setBuildingId(String(building.id))
    setSelectedBuildingName(building.name)
    setBuildings([])
    setBuildingError('')
  }

  // 건물 ID가 변경되면 해당 건물의 공용 자원 조회
  useEffect(() => {
    if (!buildingId) {
      setResources([])
      setError('')
      setLoading(false)
      return
    }

    const controller = new AbortController()

    async function loadResources() {
      setLoading(true)
      setError('')
      setResources([])

      try {
        const response = await fetch(
            `${API_BASE_URL}/reservation-service/resources?buildingId=${buildingId}`,
            {
              signal: controller.signal,
              credentials: 'include',
              headers: {
                Accept: 'application/json'
              }
            }
        )

        if (!response.ok) {
          throw new Error(
              `공용 자원 조회 실패 (${response.status})`
          )
        }

        const data = await response.json()

        if (!controller.signal.aborted) {
          setResources(data)
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          console.error('공용 자원 조회 오류:', err)
          setError(err.message || '공용 자원 목록을 불러오지 못했습니다.')
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false)
        }
      }
    }

    loadResources()

    return () => controller.abort()
  }, [buildingId])

  // 공용 자원 정렬
  const sortedResources = [...resources].sort((a, b) => {
    const orderA = resourceOrder[a.resourceName] ?? 999
    const orderB = resourceOrder[b.resourceName] ?? 999

    return orderA - orderB
  })

  return (
      <div
          style={{
            backgroundColor: '#F3F5F1',
            minHeight: '100vh',
            padding: '40px 20px',
            color: '#26332E',
            boxSizing: 'border-box',
            overflowY: 'auto'
          }}
      >
        <div
            style={{
              maxWidth: '1000px',
              margin: '0 auto'
            }}
        >
          {/* 페이지 제목 */}
          <div
              style={{
                textAlign: 'center',
                marginBottom: '35px'
              }}
          >
            <h1
                style={{
                  color: '#26332E',
                  marginBottom: '12px'
                }}
            >
              공용 시설 및 물품 예약
            </h1>

            <p
                style={{
                  color: '#52796F',
                  fontSize: '16px'
                }}
            >
              이용할 건물을 선택하고 공용 자원을 예약하세요.
            </p>
          </div>

          {/* 건물 검색 영역 */}
          <div
              style={{
                backgroundColor: 'white',
                padding: '24px',
                borderRadius: '12px',
                marginBottom: '25px'
              }}
          >
            <label
                htmlFor="buildingKeyword"
                style={{
                  display: 'block',
                  fontWeight: 'bold',
                  marginBottom: '12px'
                }}
            >
              이용 건물 검색
            </label>

            <form
                onSubmit={handleBuildingSearch}
                style={{
                  display: 'flex',
                  gap: '10px',
                  flexWrap: 'wrap'
                }}
            >
              <input
                  id="buildingKeyword"
                  type="text"
                  value={buildingKeyword}
                  onChange={(e) =>
                      setBuildingKeyword(e.target.value)
                  }
                  placeholder="건물명 또는 지역을 입력하세요"
                  style={{
                    flex: '1',
                    minWidth: '180px',
                    padding: '14px',
                    border: '1px solid #D5DDD7',
                    borderRadius: '8px',
                    fontSize: '16px',
                    boxSizing: 'border-box'
                  }}
              />

              <button
                  type="submit"
                  disabled={buildingLoading}
                  style={{
                    backgroundColor: '#52796F',
                    color: 'white',
                    padding: '14px 24px',
                    border: 'none',
                    borderRadius: '8px',
                    cursor: buildingLoading
                        ? 'not-allowed'
                        : 'pointer'
                  }}
              >
                {buildingLoading ? '검색 중...' : '검색'}
              </button>
            </form>

            {/* 현재 선택한 건물 */}
            <p
                style={{
                  fontSize: '14px',
                  color: '#52796F',
                  marginTop: '16px'
                }}
            >
              현재 선택한 건물:{' '}
              <strong>{selectedBuildingName}</strong>
            </p>

            {/* 건물 검색 오류 */}
            {buildingError && (
                <p style={{ color: '#B91C1C' }}>
                  {buildingError}
                </p>
            )}

            {/* 건물 검색 결과 */}
            {buildings.map((building) => (
                <button
                    key={building.id}
                    type="button"
                    onClick={() =>
                        handleBuildingSelect(building)
                    }
                    style={{
                      display: 'block',
                      width: '100%',
                      textAlign: 'left',
                      backgroundColor: '#F3F5F1',
                      border: '1px solid #D5DDD7',
                      borderRadius: '8px',
                      padding: '14px',
                      marginTop: '10px',
                      cursor: 'pointer',
                      color: '#26332E'
                    }}
                >
                  <strong>{building.name}</strong>

                  <span
                      style={{
                        display: 'block',
                        fontSize: '13px',
                        marginTop: '5px',
                        color: '#66756D'
                      }}
                  >
                {building.address}
              </span>
                </button>
            ))}

            <p
                style={{
                  fontSize: '13px',
                  color: '#66756D',
                  marginBottom: 0
                }}
            >
              건물 검색 결과에서 이용할 건물을 선택해 주세요.
            </p>
          </div>

          {/* 건물 선택 전 안내 */}
          {!buildingId && (
              <p
                  style={{
                    textAlign: 'center',
                    padding: '30px',
                    color: '#66756D'
                  }}
              >
                공용 자원을 조회하려면 먼저 건물을 선택해 주세요.
              </p>
          )}

          {/* 건물 선택 후 공용 자원 목록 */}
          {buildingId && (
              <>
                {/* 조회 중 */}
                {loading && (
                    <p style={{ textAlign: 'center' }}>
                      공용 자원을 불러오는 중입니다.
                    </p>
                )}

                {/* 조회 오류 */}
                {error && (
                    <p
                        style={{
                          color: '#B91C1C',
                          textAlign: 'center'
                        }}
                    >
                      {error}
                    </p>
                )}

                {/* 조회 결과 없음 */}
                {!loading &&
                    !error &&
                    sortedResources.length === 0 && (
                        <p
                            style={{
                              textAlign: 'center',
                              padding: '30px'
                            }}
                        >
                          해당 건물에 등록된 공용 자원이 없습니다.
                        </p>
                    )}

                {/* 공용 자원 목록 */}
                {!loading &&
                    !error &&
                    sortedResources.map((resource) => (
                        <div
                            key={resource.id}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              flexWrap: 'wrap',
                              gap: '24px',
                              backgroundColor: 'white',
                              padding: '20px',
                              borderRadius: '12px',
                              marginBottom: '20px'
                            }}
                        >
                          {/* 왼쪽: 공용 자원 이미지 */}
                          <div
                              style={{
                                width: '200px',
                                height: '140px',
                                flexShrink: 0
                              }}
                          >
                            {resource.imageUrl ||
                            resourceImages[resource.resourceName] ? (
                                <img
                                    src={
                                        resource.imageUrl ||
                                        resourceImages[resource.resourceName]
                                    }
                                    alt={resource.resourceName}
                                    style={{
                                      width: '100%',
                                      height: '100%',
                                      objectFit: 'cover',
                                      borderRadius: '8px'
                                    }}
                                />
                            ) : (
                                <div
                                    style={{
                                      width: '100%',
                                      height: '100%',
                                      backgroundColor: '#F3F5F1',
                                      borderRadius: '8px',
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'center',
                                      color: '#52796F'
                                    }}
                                >
                                  이미지 준비 중
                                </div>
                            )}
                          </div>

                          {/* 가운데: 공용 자원 정보 */}
                          <div
                              style={{
                                flex: '1',
                                minWidth: '180px'
                              }}
                          >
                            <h2 style={{ marginTop: 0 }}>
                              {resource.resourceName}
                            </h2>

                            {resource.description && (
                                <p>{resource.description}</p>
                            )}

                            <p>
                              유형:{' '}
                              {resource.resourceType === 'SPACE'
                                  ? '공간'
                                  : '물품'}
                            </p>

                            <p
                                style={{
                                  color:
                                      resource.resourceStatus ===
                                      'AVAILABLE'
                                          ? '#52796F'
                                          : '#999999',
                                  fontWeight: 'bold'
                                }}
                            >
                              {resource.resourceStatus ===
                              'AVAILABLE'
                                  ? '예약 가능'
                                  : '사용 불가'}
                            </p>
                          </div>

                          {/* 오른쪽: 예약하기 버튼 */}
                          <button
                              type="button"
                              onClick={() =>
                                  navigate(
                                      `/facilities/reservation/${resource.id}`
                                  )
                              }
                              disabled={
                                  resource.resourceStatus !== 'AVAILABLE'
                              }
                              style={{
                                backgroundColor:
                                    resource.resourceStatus ===
                                    'AVAILABLE'
                                        ? '#52796F'
                                        : '#999999',
                                color: 'white',
                                padding: '14px 24px',
                                border: 'none',
                                borderRadius: '8px',
                                cursor:
                                    resource.resourceStatus ===
                                    'AVAILABLE'
                                        ? 'pointer'
                                        : 'not-allowed',
                                whiteSpace: 'nowrap'
                              }}
                          >
                            예약하기
                          </button>
                        </div>
                    ))}
              </>
          )}
        </div>
      </div>
  )
}

export default ResourceListPage
