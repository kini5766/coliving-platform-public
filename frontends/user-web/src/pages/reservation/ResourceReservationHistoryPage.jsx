
import { useEffect, useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { useSession } from '../../session'
import './ResourceReservationHistoryPage.css'

const API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

function ResourceReservationHistoryPage() {
  const { authenticated, status } = useSession()

  const [reservations, setReservations] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState('ALL')

  // QR코드 팝업
  const [qrReservation, setQrReservation] = useState(null)
  const [qrLoadingId, setQrLoadingId] = useState(null)

  // 내 관리자 문의 내역
  const [inquiries, setInquiries] = useState([])
  const [inquiryListLoading, setInquiryListLoading] = useState(false)
  const [inquiryListError, setInquiryListError] = useState('')
  const [deletingInquiryId, setDeletingInquiryId] = useState(null)
  const [inquiryRefreshKey, setInquiryRefreshKey] = useState(0)

  // 예약 기능 처리 상태
  const [cancelingId, setCancelingId] = useState(null)
  const [returningId, setReturningId] = useState(null)
  const [extendingId, setExtendingId] = useState(null)

  // 관리자 문의 등록 팝업
  const [inquiryReservation, setInquiryReservation] = useState(null)
  const [inquiryType, setInquiryType] = useState('EXTENSION')
  const [inquiryContent, setInquiryContent] = useState('')
  const [inquirySubmitting, setInquirySubmitting] = useState(false)
  const [inquiryError, setInquiryError] = useState('')

  // 1. 내 공용 자원 예약 내역 조회
  useEffect(() => {
    if (status !== 'ready') return

    if (!authenticated) {
      setReservations([])
      setError('로그인이 필요합니다.')
      return
    }

    const controller = new AbortController()

    async function loadReservations() {
      setLoading(true)
      setError('')

      try {
        const response = await fetch(
            `${API_BASE_URL}/reservation-service/reservations/my`,
            {
              credentials: 'include',
              signal: controller.signal,
            }
        )

        if (!response.ok) {
          throw new Error('예약 내역 조회에 실패했습니다.')
        }

        const data = await response.json()
        setReservations(data)
      } catch (error) {
        if (error.name === 'AbortError') return
        setError(error.message)
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false)
        }
      }
    }

    loadReservations()

    return () => controller.abort()
  }, [authenticated, status])

  // 2. 내 관리자 문의 내역 조회
  // 문의 내역 탭을 열 때 최신 답변을 다시 가져옴
  useEffect(() => {
    if (status !== 'ready') return

    if (!authenticated) {
      setInquiries([])
      setInquiryListError('로그인이 필요합니다.')
      return
    }

    if (activeTab !== 'INQUIRIES') return

    const controller = new AbortController()

    async function loadInquiries() {
      setInquiryListLoading(true)
      setInquiryListError('')

      try {
        const response = await fetch(
            `${API_BASE_URL}/reservation-service/inquiries/my`,
            {
              credentials: 'include',
              signal: controller.signal,
              cache: 'no-store',
            }
        )

        if (!response.ok) {
          throw new Error('문의 내역 조회에 실패했습니다.')
        }

        const data = await response.json()

        if (!controller.signal.aborted) {
          setInquiries(data)
        }
      } catch (error) {
        if (error.name === 'AbortError') return
        setInquiryListError(error.message)
      } finally {
        if (!controller.signal.aborted) {
          setInquiryListLoading(false)
        }
      }
    }

    loadInquiries()

    return () => controller.abort()
  }, [
    authenticated,
    status,
    activeTab,
    inquiryRefreshKey,
  ])

  // 3. 예약 시작 일시 계산
  function getStartDateTime(reservation) {
    return new Date(
        `${reservation.reservationDate}T${reservation.startTime}`
    )
  }

  // 4. 예약 종료 일시 계산
  function getEndDateTime(reservation) {
    return new Date(
        `${reservation.reservationDate}T${reservation.endTime}`
    )
  }

  // 5. 예약 취소 가능 여부
  function canCancel(reservation) {
    return (
        reservation.status === 'APPROVED' &&
        new Date() < getStartDateTime(reservation)
    )
  }

  // 6. 이용 종료 / 반납 가능 여부
  function canReturn(reservation) {
    return (
        reservation.status === 'APPROVED' &&
        !reservation.returnedAt &&
        new Date() >= getStartDateTime(reservation)
    )
  }

  // 7. 예약 연장 가능 여부
  function canExtend(reservation) {
    return (
        reservation.status === 'APPROVED' &&
        !reservation.returnedAt &&
        new Date() < getEndDateTime(reservation)
    )
  }

  // 8. QR코드 표시 가능 여부
  function canShowQr(reservation) {
    if (reservation.status !== 'APPROVED') return false
    if (reservation.checkedInAt) return false

    const now = new Date()
    const startDateTime = getStartDateTime(reservation)
    const endDateTime = getEndDateTime(reservation)

    const checkInDeadline = new Date(
        startDateTime.getTime() + 30 * 60 * 1000
    )

    return now < checkInDeadline && now < endDateTime
  }

  // 9. QR코드 팝업 열기
  async function openQrCode(reservation) {
    if (!canShowQr(reservation) || qrLoadingId !== null) return

    setQrLoadingId(reservation.id)

    try {
      const response = await fetch(
          `${API_BASE_URL}/reservation-service/reservations/${reservation.id}/qr-token`,
          {
            method: 'GET',
            credentials: 'include',
          }
      )

      if (!response.ok) {
        throw new Error(
            'QR코드를 불러오지 못했습니다. 예약 상태를 확인해 주세요.'
        )
      }

      const data = await response.json()

      if (!data.qrToken) {
        throw new Error('발급된 QR코드가 없습니다.')
      }

      setQrReservation({
        ...reservation,
        qrToken: data.qrToken,
      })
    } catch (error) {
      window.alert(error.message)
    } finally {
      setQrLoadingId(null)
    }
  }

  // 10. QR코드 팝업 닫기
  function closeQrCode() {
    setQrReservation(null)
  }

  // 11. 예약 종료 시간에 1시간 추가
  function getExtendedEndTime(reservation) {
    const endDateTime = getEndDateTime(reservation)

    endDateTime.setHours(endDateTime.getHours() + 1)

    const hours = String(endDateTime.getHours()).padStart(2, '0')
    const minutes = String(endDateTime.getMinutes()).padStart(2, '0')
    const seconds = String(endDateTime.getSeconds()).padStart(2, '0')

    return `${hours}:${minutes}:${seconds}`
  }

  // 12. 공용 자원 예약 연장
  async function handleExtend(reservation) {
    const confirmed = window.confirm(
        '예약 종료 시간을 1시간 연장하시겠습니까?\n' +
        '예약당 연장은 최대 1회이며, 다음 예약이 있으면 연장할 수 없습니다.'
    )

    if (!confirmed) return

    setExtendingId(reservation.id)

    try {
      const response = await fetch(
          `${API_BASE_URL}/reservation-service/reservation-extensions`,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            credentials: 'include',
            body: JSON.stringify({
              reservationId: reservation.id,
              requestedEndTime: getExtendedEndTime(reservation),
            }),
          }
      )

      if (!response.ok) {
        const message = await response.text()
        throw new Error(message || '예약 연장에 실패했습니다.')
      }

      const updatedResponse = await fetch(
          `${API_BASE_URL}/reservation-service/reservations/my`,
          {
            credentials: 'include',
          }
      )

      if (!updatedResponse.ok) {
        throw new Error(
            '연장 요청은 처리되었지만 예약 내역을 다시 불러오지 못했습니다. 화면을 새로고침해 주세요.'
        )
      }

      const updatedReservations = await updatedResponse.json()
      setReservations(updatedReservations)

      window.alert('예약이 1시간 연장되었습니다.')
    } catch (error) {
      window.alert(error.message)
    } finally {
      setExtendingId(null)
    }
  }

  // 13. 공용 자원 예약 취소
  async function handleCancel(reservationId) {
    const confirmed = window.confirm(
        '정말 예약을 취소하시겠습니까?'
    )

    if (!confirmed) return

    setCancelingId(reservationId)

    try {
      const response = await fetch(
          `${API_BASE_URL}/reservation-service/reservations/${reservationId}/cancel`,
          {
            method: 'PATCH',
            credentials: 'include',
          }
      )

      if (!response.ok) {
        throw new Error('예약 취소에 실패했습니다.')
      }

      const updatedReservation = await response.json()

      setReservations((prev) =>
          prev.map((reservation) =>
              reservation.id === reservationId
                  ? updatedReservation
                  : reservation
          )
      )

      window.alert('예약이 취소되었습니다.')
    } catch (error) {
      window.alert(error.message)
    } finally {
      setCancelingId(null)
    }
  }

  // 14. 공용 자원 이용 종료 / 반납
  async function handleReturn(reservationId) {
    const confirmed = window.confirm(
        '이용을 종료하고 반납하시겠습니까?'
    )

    if (!confirmed) return

    setReturningId(reservationId)

    try {
      const response = await fetch(
          `${API_BASE_URL}/reservation-service/reservations/${reservationId}/return`,
          {
            method: 'PATCH',
            credentials: 'include',
          }
      )

      if (!response.ok) {
        throw new Error('이용 종료 / 반납 처리에 실패했습니다.')
      }

      const updatedReservation = await response.json()

      setReservations((prev) =>
          prev.map((reservation) =>
              reservation.id === reservationId
                  ? updatedReservation
                  : reservation
          )
      )

      window.alert('이용 종료 / 반납이 완료되었습니다.')
    } catch (error) {
      window.alert(error.message)
    } finally {
      setReturningId(null)
    }
  }

  // 15. 관리자 문의 창 열기
  function openInquiry(reservation) {
    setInquiryReservation(reservation)
    setInquiryType('EXTENSION')
    setInquiryContent('')
    setInquiryError('')
  }

  // 16. 관리자 문의 창 닫기
  function closeInquiry() {
    if (inquirySubmitting) return

    setInquiryReservation(null)
    setInquiryType('EXTENSION')
    setInquiryContent('')
    setInquiryError('')
  }

  // 17. 관리자 문의 등록
  async function handleInquirySubmit(event) {
    event.preventDefault()

    if (!inquiryReservation || inquirySubmitting) return

    if (!inquiryContent.trim()) {
      setInquiryError('문의 내용을 입력해 주세요.')
      return
    }

    setInquirySubmitting(true)
    setInquiryError('')

    try {
      const response = await fetch(
          `${API_BASE_URL}/reservation-service/inquiries`,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            credentials: 'include',
            body: JSON.stringify({
              reservationId: inquiryReservation.id,
              inquiryType: inquiryType,
              content: inquiryContent.trim(),
            }),
          }
      )

      if (!response.ok) {
        throw new Error('관리자 문의 등록에 실패했습니다.')
      }

      const updatedResponse = await fetch(
          `${API_BASE_URL}/reservation-service/inquiries/my`,
          {
            credentials: 'include',
            cache: 'no-store',
          }
      )

      if (updatedResponse.ok) {
        const updatedInquiries = await updatedResponse.json()
        setInquiries(updatedInquiries)
        setInquiryListError('')
      } else {
        setInquiryListError(
            '문의가 등록되었지만 목록을 새로 불러오지 못했습니다.'
        )
      }

      window.alert('관리자 문의가 정상적으로 등록되었습니다.')

      setInquiryReservation(null)
      setInquiryType('EXTENSION')
      setInquiryContent('')
      setInquiryError('')
    } catch (error) {
      setInquiryError(error.message)
    } finally {
      setInquirySubmitting(false)
    }
  }

  // 18. 사용자 문의 삭제
  async function handleDeleteInquiry(inquiryId) {
    if (deletingInquiryId !== null) return

    const confirmed = window.confirm(
        '정말 이 문의를 삭제하시겠습니까?\n삭제한 문의는 복구할 수 없습니다.'
    )

    if (!confirmed) return

    setDeletingInquiryId(inquiryId)

    try {
      const response = await fetch(
          `${API_BASE_URL}/reservation-service/inquiries/${inquiryId}`,
          {
            method: 'DELETE',
            credentials: 'include',
          }
      )

      if (!response.ok) {
        throw new Error('문의 삭제에 실패했습니다.')
      }

      setInquiries((prev) =>
          prev.filter((inquiry) => inquiry.id !== inquiryId)
      )

      window.alert('문의가 삭제되었습니다.')
    } catch (error) {
      window.alert(error.message)
    } finally {
      setDeletingInquiryId(null)
    }
  }

  // 19. 예약 상태 한글 표시
  function getStatusText(reservationStatus) {
    switch (reservationStatus) {
      case 'APPROVED':
        return '예약 확정'
      case 'CANCELED':
        return '예약 취소'
      case 'COMPLETED':
        return '이용 완료'
      case 'OVERDUE':
        return '연체'
      case 'EXPIRED':
        return '이용 만료'
      case 'REQUESTED':
        return '예약 요청'
      default:
        return reservationStatus || '상태 확인 중'
    }
  }

  // 20. 문의 유형 한글 표시
  function getInquiryTypeText(type) {
    switch (type) {
      case 'EXTENSION':
        return '추가 연장 문의'
      case 'BREAKDOWN':
        return '고장 문의'
      case 'OTHER':
        return '기타 문의'
      default:
        return type || '문의'
    }
  }

  // 21. 날짜 표시 형식
  function formatDateTime(value) {
    if (!value) return ''
    return String(value).replace('T', ' ')
  }

  // 22. 선택한 탭에 따라 예약 내역 필터링
  const filteredReservations = reservations.filter((reservation) => {
    if (activeTab === 'INQUIRIES') return false
    if (activeTab === 'ALL') return true

    if (activeTab === 'UPCOMING') {
      return reservation.status === 'APPROVED'
    }

    if (activeTab === 'COMPLETED') {
      return (
          reservation.status === 'COMPLETED' ||
          reservation.status === 'EXPIRED'
      )
    }

    if (activeTab === 'CANCELED') {
      return reservation.status === 'CANCELED'
    }

    return true
  })

  // 23. 기능 처리 중 여부
  const processing =
      cancelingId !== null ||
      returningId !== null ||
      extendingId !== null ||
      inquirySubmitting

  return (
      <div className="reservation-history-page">
        <div className="reservation-history-container">

          {/* 페이지 제목 */}
          <h1 className="reservation-history-title">
            내 공용 자원 예약
          </h1>

          <p className="reservation-history-subtitle">
            예약 현황 및 이용 내역
          </p>

          {/* 예약 및 문의 내역 탭 */}
          <div className="reservation-history-tabs">

            <button
                type="button"
                className={`reservation-history-tab ${
                    activeTab === 'ALL' ? 'active' : ''
                }`}
                onClick={() => setActiveTab('ALL')}
            >
              전체 예약
            </button>

            <button
                type="button"
                className={`reservation-history-tab ${
                    activeTab === 'UPCOMING' ? 'active' : ''
                }`}
                onClick={() => setActiveTab('UPCOMING')}
            >
              예약 확정
            </button>

            <button
                type="button"
                className={`reservation-history-tab ${
                    activeTab === 'COMPLETED' ? 'active' : ''
                }`}
                onClick={() => setActiveTab('COMPLETED')}
            >
              이용 완료
            </button>

            <button
                type="button"
                className={`reservation-history-tab ${
                    activeTab === 'CANCELED' ? 'active' : ''
                }`}
                onClick={() => setActiveTab('CANCELED')}
            >
              취소 내역
            </button>

            <button
                type="button"
                className={`reservation-history-tab ${
                    activeTab === 'INQUIRIES' ? 'active' : ''
                }`}
                onClick={() => setActiveTab('INQUIRIES')}
            >
              문의 내역
            </button>

          </div>

          {/* 로그인 상태 */}
          {status === 'loading' && (
              <p className="reservation-history-message">
                로그인 정보 확인 중...
              </p>
          )}

          {/* 예약 내역 화면 */}
          {activeTab !== 'INQUIRIES' && (
              <>
                {loading && (
                    <p className="reservation-history-message">
                      예약 내역을 불러오는 중...
                    </p>
                )}

                {error && (
                    <p className="reservation-history-message">
                      {error}
                    </p>
                )}

                {!loading &&
                    !error &&
                    status === 'ready' &&
                    filteredReservations.length === 0 && (
                        <p className="reservation-history-message">
                          해당하는 예약 내역이 없습니다.
                        </p>
                    )}

                {/* 예약 내역 카드 */}
                {filteredReservations.map((reservation) => (
                    <div
                        key={reservation.id}
                        className="reservation-history-card"
                    >
                      <div className="reservation-history-card-header">
                  <span className="reservation-history-resource-name">
                    공용 자원 #{reservation.resourceId}
                  </span>

                        <span
                            className={`reservation-history-status ${
                                reservation.status === 'CANCELED'
                                    ? 'canceled'
                                    : reservation.status === 'COMPLETED'
                                        ? 'completed'
                                        : reservation.status === 'OVERDUE'
                                            ? 'overdue'
                                            : ''
                            }`}
                        >
                    {getStatusText(reservation.status)}
                  </span>
                      </div>

                      <p className="reservation-history-number">
                        예약 번호 {reservation.reservationNumber}
                      </p>

                      <div className="reservation-history-details">
                        <p className="reservation-history-detail">
                          📅 {reservation.reservationDate}
                        </p>

                        <p className="reservation-history-detail">
                          🕒 {reservation.startTime} ~ {reservation.endTime}
                        </p>

                        {reservation.returnedAt && (
                            <p className="reservation-history-detail">
                              반납 시간: {reservation.returnedAt}
                            </p>
                        )}

                        {reservation.isOverdue && (
                            <p className="reservation-history-detail">
                              연체 이력이 있는 예약입니다.
                            </p>
                        )}

                        {/* QR 체크인 상태 */}
                        {reservation.checkedInAt && (
                            <p
                                className="reservation-history-detail"
                                style={{
                                  color: '#52796F',
                                  fontWeight: 600,
                                }}
                            >
                              ✓ QR 체크인 완료:{' '}
                              {formatDateTime(reservation.checkedInAt)}
                            </p>
                        )}

                        {reservation.status === 'APPROVED' &&
                            !reservation.checkedInAt && (
                                <p className="reservation-history-detail">
                                  예약 시작 후 30분 이내 QR 체크인이 필요합니다.
                                </p>
                            )}
                      </div>

                      {/* QR코드 보기 */}
                      {canShowQr(reservation) && (
                          <div
                              style={{
                                marginTop: '12px',
                                marginBottom: '12px',
                              }}
                          >
                            <button
                                type="button"
                                className="reservation-history-button primary"
                                onClick={() => openQrCode(reservation)}
                                disabled={qrLoadingId !== null}
                            >
                              {qrLoadingId === reservation.id
                                  ? 'QR코드 불러오는 중...'
                                  : 'QR코드 보기'}
                            </button>
                          </div>
                      )}

                      {/* 예약 기능 버튼 */}
                      <div className="reservation-history-actions">

                        <button
                            type="button"
                            className="reservation-history-button"
                            disabled={!canExtend(reservation) || processing}
                            onClick={() => handleExtend(reservation)}
                        >
                          {extendingId === reservation.id
                              ? '연장 처리 중...'
                              : '예약 연장'}
                        </button>

                        <button
                            type="button"
                            className="reservation-history-button"
                            disabled={!canCancel(reservation) || processing}
                            onClick={() => handleCancel(reservation.id)}
                        >
                          {cancelingId === reservation.id
                              ? '취소 중...'
                              : '예약 취소'}
                        </button>

                        <button
                            type="button"
                            className="reservation-history-button primary"
                            disabled={!canReturn(reservation) || processing}
                            onClick={() => handleReturn(reservation.id)}
                        >
                          {returningId === reservation.id
                              ? '반납 처리 중...'
                              : '이용 종료 / 반납'}
                        </button>

                        <button
                            type="button"
                            className="reservation-history-button inquiry"
                            disabled={processing}
                            onClick={() => openInquiry(reservation)}
                        >
                          관리자 문의
                        </button>

                      </div>
                    </div>
                ))}
              </>
          )}

          {/* 내 문의 내역 화면 */}
          {activeTab === 'INQUIRIES' && (
              <>
                {status === 'ready' && !authenticated && (
                    <p className="reservation-history-message">
                      로그인이 필요합니다.
                    </p>
                )}

                {status === 'ready' && authenticated && (
                    <>
                      <div
                          style={{
                            display: 'flex',
                            justifyContent: 'flex-end',
                            marginBottom: '16px',
                          }}
                      >
                        <button
                            type="button"
                            className="reservation-history-button"
                            disabled={inquiryListLoading}
                            onClick={() =>
                                setInquiryRefreshKey((prev) => prev + 1)
                            }
                        >
                          {inquiryListLoading
                              ? '새로고침 중...'
                              : '문의 내역 새로고침'}
                        </button>
                      </div>

                      {inquiryListLoading && (
                          <p className="reservation-history-message">
                            문의 내역을 불러오는 중...
                          </p>
                      )}

                      {inquiryListError && (
                          <p className="reservation-history-message">
                            {inquiryListError}
                          </p>
                      )}

                      {!inquiryListLoading &&
                          !inquiryListError &&
                          inquiries.length === 0 && (
                              <p className="reservation-history-message">
                                등록된 문의 내역이 없습니다.
                              </p>
                          )}

                      {!inquiryListLoading &&
                          !inquiryListError &&
                          inquiries.map((inquiry) => {
                            const hasAdminReply = Boolean(
                                inquiry.adminReply?.trim()
                            )

                            return (
                                <div
                                    key={inquiry.id}
                                    className="reservation-inquiry-card"
                                >
                                  <div className="reservation-inquiry-header">
                                    <h2 className="reservation-inquiry-title">
                                      {getInquiryTypeText(inquiry.inquiryType)}
                                    </h2>

                                    <span className="reservation-inquiry-type">
                            {getInquiryTypeText(inquiry.inquiryType)}
                          </span>
                                  </div>

                                  <p className="reservation-inquiry-info">
                                    예약 ID: {inquiry.reservationId}
                                  </p>

                                  {inquiry.createdAt && (
                                      <p className="reservation-inquiry-info">
                                        문의 등록일:{' '}
                                        {formatDateTime(inquiry.createdAt)}
                                      </p>
                                  )}

                                  {/* 사용자 문의 내용 */}
                                  <div className="reservation-inquiry-content">
                                    {inquiry.content}
                                  </div>

                                  {/* 관리자 답변 상태 */}
                                  <div
                                      style={{
                                        marginTop: '16px',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '8px',
                                      }}
                                  >
                          <span
                              style={{
                                display: 'inline-block',
                                padding: '5px 10px',
                                borderRadius: '20px',
                                fontSize: '13px',
                                fontWeight: 600,
                                backgroundColor: hasAdminReply
                                    ? '#E3F1E8'
                                    : '#F3F5F1',
                                color: hasAdminReply
                                    ? '#52796F'
                                    : '#66756D',
                              }}
                          >
                            {hasAdminReply
                                ? '답변 완료'
                                : '답변 대기'}
                          </span>
                                  </div>

                                  {/* 관리자 답변 표시 */}
                                  <div
                                      style={{
                                        marginTop: '12px',
                                        padding: '16px',
                                        backgroundColor: '#F3F5F1',
                                        border: '1px solid #DCE5DE',
                                        borderRadius: '10px',
                                      }}
                                  >
                                    <div
                                        style={{
                                          fontWeight: 700,
                                          fontSize: '15px',
                                          color: '#52796F',
                                          marginBottom: '10px',
                                        }}
                                    >
                                      관리자 답변
                                    </div>

                                    {hasAdminReply ? (
                                        <>
                                          <p
                                              style={{
                                                margin: 0,
                                                fontSize: '14px',
                                                lineHeight: 1.7,
                                                color: '#26332E',
                                                whiteSpace: 'pre-wrap',
                                                overflowWrap: 'anywhere',
                                              }}
                                          >
                                            {inquiry.adminReply}
                                          </p>

                                          {inquiry.repliedAt && (
                                              <p
                                                  style={{
                                                    marginTop: '12px',
                                                    marginBottom: 0,
                                                    fontSize: '12px',
                                                    color: '#66756D',
                                                  }}
                                              >
                                                답변 등록일:{' '}
                                                {formatDateTime(inquiry.repliedAt)}
                                              </p>
                                          )}
                                        </>
                                    ) : (
                                        <p
                                            style={{
                                              margin: 0,
                                              fontSize: '14px',
                                              color: '#66756D',
                                            }}
                                        >
                                          아직 관리자 답변이 등록되지 않았습니다.
                                        </p>
                                    )}
                                  </div>

                                  {/* 문의 삭제 버튼 */}
                                  <div style={{ marginTop: '16px' }}>
                                    <button
                                        type="button"
                                        className="reservation-history-button"
                                        disabled={deletingInquiryId !== null}
                                        onClick={() =>
                                            handleDeleteInquiry(inquiry.id)
                                        }
                                        style={{
                                          borderColor: '#C94A4A',
                                          color: '#C94A4A',
                                        }}
                                    >
                                      {deletingInquiryId === inquiry.id
                                          ? '삭제 중...'
                                          : '문의 삭제'}
                                    </button>
                                  </div>
                                </div>
                            )
                          })}
                    </>
                )}
              </>
          )}

          {/* QR코드 팝업 */}
          {qrReservation && (
              <div
                  role="presentation"
                  onClick={closeQrCode}
                  style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundColor: 'rgba(0, 0, 0, 0.45)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '20px',
                    zIndex: 1000,
                  }}
              >
                <div
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="qr-dialog-title"
                    style={{
                      width: '100%',
                      maxWidth: '380px',
                      backgroundColor: '#FFFFFF',
                      borderRadius: '12px',
                      padding: '28px',
                      boxSizing: 'border-box',
                      textAlign: 'center',
                      color: '#26332E',
                    }}
                    onClick={(event) => event.stopPropagation()}
                >
                  <h2 id="qr-dialog-title">
                    공용 자원 QR 체크인
                  </h2>

                  <p style={{ marginBottom: '20px' }}>
                    예약 번호: {qrReservation.reservationNumber}
                  </p>

                  <div
                      style={{
                        display: 'flex',
                        justifyContent: 'center',
                        padding: '16px',
                        backgroundColor: '#FFFFFF',
                      }}
                  >
                    <QRCodeSVG
                        value={qrReservation.qrToken}
                        size={220}
                        level="M"
                        marginSize={2}
                    />
                  </div>

                  <p style={{ marginTop: '16px' }}>
                    예약 시작 후 30분 이내에
                    QR코드로 체크인해 주세요.
                  </p>

                  <p
                      style={{
                        fontSize: '13px',
                        color: '#66756D',
                      }}
                  >
                    QR코드는 다른 사람에게 공유하지 마세요.
                  </p>

                  <button
                      type="button"
                      className="reservation-history-button primary"
                      onClick={closeQrCode}
                      style={{ marginTop: '16px' }}
                  >
                    닫기
                  </button>
                </div>
              </div>
          )}

          {/* 관리자 문의 등록 팝업 */}
          {inquiryReservation && (
              <div
                  role="presentation"
                  onClick={closeInquiry}
                  style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundColor: 'rgba(0, 0, 0, 0.45)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '20px',
                    zIndex: 1000,
                  }}
              >
                <div
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="inquiry-dialog-title"
                    style={{
                      width: '100%',
                      maxWidth: '480px',
                      backgroundColor: '#FFFFFF',
                      borderRadius: '12px',
                      padding: '24px',
                      boxSizing: 'border-box',
                      color: '#26332E',
                    }}
                    onClick={(event) => event.stopPropagation()}
                >
                  <h2 id="inquiry-dialog-title">
                    관리자 문의
                  </h2>

                  <p>
                    예약 번호: {inquiryReservation.reservationNumber}
                  </p>

                  <form onSubmit={handleInquirySubmit}>

                    <label htmlFor="inquiry-type">
                      문의 유형
                    </label>

                    <select
                        id="inquiry-type"
                        value={inquiryType}
                        onChange={(event) =>
                            setInquiryType(event.target.value)
                        }
                        disabled={inquirySubmitting}
                        style={{
                          display: 'block',
                          width: '100%',
                          boxSizing: 'border-box',
                          margin: '8px 0 16px',
                          padding: '10px',
                          border: '1px solid #D0D5D2',
                          borderRadius: '6px',
                        }}
                    >
                      <option value="EXTENSION">
                        추가 연장 문의
                      </option>

                      <option value="BREAKDOWN">
                        고장 문의
                      </option>

                      <option value="OTHER">
                        기타 문의
                      </option>
                    </select>

                    <label htmlFor="inquiry-content">
                      문의 내용
                    </label>

                    <textarea
                        id="inquiry-content"
                        value={inquiryContent}
                        onChange={(event) =>
                            setInquiryContent(event.target.value)
                        }
                        disabled={inquirySubmitting}
                        placeholder="관리자에게 전달할 내용을 입력해 주세요."
                        rows={5}
                        maxLength={2000}
                        required
                        style={{
                          display: 'block',
                          width: '100%',
                          boxSizing: 'border-box',
                          margin: '8px 0 16px',
                          padding: '10px',
                          border: '1px solid #D0D5D2',
                          borderRadius: '6px',
                          resize: 'vertical',
                        }}
                    />

                    {inquiryError && (
                        <p
                            role="alert"
                            style={{ color: '#B42318' }}
                        >
                          {inquiryError}
                        </p>
                    )}

                    <div
                        style={{
                          display: 'flex',
                          gap: '8px',
                          justifyContent: 'flex-end',
                        }}
                    >
                      <button
                          type="button"
                          className="reservation-history-button"
                          onClick={closeInquiry}
                          disabled={inquirySubmitting}
                      >
                        닫기
                      </button>

                      <button
                          type="submit"
                          className="reservation-history-button primary"
                          disabled={inquirySubmitting}
                      >
                        {inquirySubmitting
                            ? '등록 중...'
                            : '문의 등록'}
                      </button>
                    </div>

                  </form>
                </div>
              </div>
          )}

        </div>
      </div>
  )
}

export default ResourceReservationHistoryPage
