
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'

const API_BASE_URL = 'http://localhost:8081'

function ResourceReservationPage() {
    const { resourceId } = useParams()
    const navigate = useNavigate()

    const [reservationDate, setReservationDate] = useState('')
    const [startTime, setStartTime] = useState('')
    const [endTime, setEndTime] = useState('')
    const [message, setMessage] = useState('')
    const [loading, setLoading] = useState(false)
    const [reservationCompleted, setReservationCompleted] = useState(false)

    async function handleReservation() {
        setReservationCompleted(false)

        if (!reservationDate || !startTime || !endTime) {
            setMessage('예약 날짜와 이용 시간을 모두 선택해 주세요.')
            return
        }

        if (startTime >= endTime) {
            setMessage('종료 시간은 시작 시간보다 늦어야 합니다.')
            return
        }

        setLoading(true)
        setMessage('')

        try {
            const response = await fetch(
                `${API_BASE_URL}/reservation-service/reservations`,
                {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    credentials: 'include',
                    body: JSON.stringify({
                        resourceId: Number(resourceId),
                        reservationDate,
                        startTime,
                        endTime,
                    }),
                }
            )

            if (!response.ok) {
                const errorText = await response.text()
                throw new Error(errorText || '예약 신청에 실패했습니다.')
            }

            const result = await response.json()

            setMessage(
                `예약이 완료되었습니다. 예약 번호: ${result.reservationNumber}`
            )
            setReservationCompleted(true)
        } catch (error) {
            console.error('예약 신청 오류:', error)
            setMessage(`예약 신청 실패: ${error.message}`)
        } finally {
            setLoading(false)
        }
    }

    return (
        <div
            style={{
                backgroundColor: '#F3F5F1',
                minHeight: '100vh',
                padding: '40px 20px',
                color: '#26332E',
            }}
        >
            <div
                style={{
                    maxWidth: '600px',
                    margin: '0 auto',
                    backgroundColor: 'white',
                    padding: '30px',
                    borderRadius: '12px',
                }}
            >
                <h1>공용 자원 예약 신청</h1>
                <p>예약할 날짜와 이용 시간을 선택해 주세요.</p>
                <p>자원 번호: {resourceId}</p>

                <div style={{ marginTop: '20px' }}>
                    <label>예약 날짜</label>
                    <input
                        type="date"
                        value={reservationDate}
                        onChange={(e) => {
                            setReservationDate(e.target.value)
                            setReservationCompleted(false)
                        }}
                        style={{
                            display: 'block',
                            width: '100%',
                            padding: '12px',
                            marginTop: '8px',
                            boxSizing: 'border-box',
                        }}
                    />
                </div>

                <div style={{ marginTop: '20px' }}>
                    <label>시작 시간</label>
                    <input
                        type="time"
                        value={startTime}
                        onChange={(e) => {
                            setStartTime(e.target.value)
                            setReservationCompleted(false)
                        }}
                        style={{
                            display: 'block',
                            width: '100%',
                            padding: '12px',
                            marginTop: '8px',
                            boxSizing: 'border-box',
                        }}
                    />
                </div>

                <div style={{ marginTop: '20px' }}>
                    <label>종료 시간</label>
                    <input
                        type="time"
                        value={endTime}
                        onChange={(e) => {
                            setEndTime(e.target.value)
                            setReservationCompleted(false)
                        }}
                        style={{
                            display: 'block',
                            width: '100%',
                            padding: '12px',
                            marginTop: '8px',
                            boxSizing: 'border-box',
                        }}
                    />
                </div>

                <button
                    type="button"
                    onClick={handleReservation}
                    disabled={loading}
                    style={{
                        backgroundColor: '#52796F',
                        color: 'white',
                        padding: '14px 24px',
                        border: 'none',
                        borderRadius: '8px',
                        marginTop: '30px',
                        width: '100%',
                        cursor: loading ? 'not-allowed' : 'pointer',
                    }}
                >
                    {loading ? '예약 처리 중...' : '예약 신청'}
                </button>

                {message && (
                    <p style={{ marginTop: '20px' }}>
                        {message}
                    </p>
                )}

                {/* 예약 완료 후 예약 내역 이동 버튼 */}
                {reservationCompleted && (
                    <button
                        type="button"
                        onClick={() => navigate('/facilities/reservations')}
                        style={{
                            backgroundColor: 'white',
                            color: '#52796F',
                            border: '1px solid #52796F',
                            borderRadius: '8px',
                            padding: '14px 24px',
                            marginTop: '12px',
                            width: '100%',
                            fontWeight: '600',
                            cursor: 'pointer',
                        }}
                    >
                        내 예약 내역 보기
                    </button>
                )}
            </div>
        </div>
    )
}

export default ResourceReservationPage
