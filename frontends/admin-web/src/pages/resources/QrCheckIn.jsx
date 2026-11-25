
import { useEffect, useRef, useState } from 'react'
import { Html5Qrcode } from 'html5-qrcode'

const API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

function QrCheckIn() {
    const scannerRef = useRef(null)
    const scannerElementId = 'resource-qr-reader'

    const [scanning, setScanning] = useState(false)
    const [processing, setProcessing] = useState(false)
    const [message, setMessage] = useState('')
    const [error, setError] = useState('')
    const [checkedInReservation, setCheckedInReservation] = useState(null)

    const processingRef = useRef(false)

    // 화면을 벗어나면 카메라 정리
    useEffect(() => {
        return () => {
            const scanner = scannerRef.current
            scannerRef.current = null

            if (scanner) {
                scanner
                    .stop()
                    .catch(() => {})
                    .finally(() => {
                        scanner.clear().catch(() => {})
                    })
            }
        }
    }, [])

    // QR 체크인 API 호출
    async function handleCheckIn(qrToken) {
        if (processingRef.current) return

        processingRef.current = true
        setProcessing(true)
        setMessage('')
        setError('')

        try {
            const response = await fetch(
                `${API_BASE_URL}/reservation-service/reservations/check-in`,
                {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    credentials: 'include',
                    body: JSON.stringify({
                        qrToken,
                    }),
                }
            )

            if (!response.ok) {
                const responseText = await response.text()
                throw new Error(
                    responseText || 'QR 체크인에 실패했습니다.'
                )
            }

            const reservation = await response.json()

            setCheckedInReservation(reservation)
            setMessage('QR 체크인이 완료되었습니다.')

            await stopScanner()
        } catch (err) {
            setError(err.message || 'QR 체크인 중 오류가 발생했습니다.')
        } finally {
            processingRef.current = false
            setProcessing(false)
        }
    }

    // 카메라 시작
    async function startScanner() {
        if (scannerRef.current) return

        setError('')
        setMessage('')
        setCheckedInReservation(null)

        const scanner = new Html5Qrcode(scannerElementId)
        scannerRef.current = scanner

        try {
            await scanner.start(
                { facingMode: 'environment' },
                {
                    fps: 10,
                    qrbox: {
                        width: 250,
                        height: 250,
                    },
                },
                (decodedText) => {
                    handleCheckIn(decodedText)
                },
                () => {}
            )

            setScanning(true)
        } catch (err) {
            scannerRef.current = null

            try {
                await scanner.clear()
            } catch {
                // 카메라 초기화 실패 시 정리
            }

            setError(
                '카메라를 실행할 수 없습니다. 브라우저 카메라 권한을 확인해 주세요.'
            )
        }
    }

    // 카메라 중지
    async function stopScanner() {
        const scanner = scannerRef.current

        if (!scanner) {
            setScanning(false)
            return
        }

        try {
            if (scanner.isScanning) {
                await scanner.stop()
            }

            await scanner.clear()
        } catch (err) {
            console.error('QR 스캐너 종료 오류:', err)
        } finally {
            scannerRef.current = null
            setScanning(false)
        }
    }

    return (
        <div
            style={{
                padding: '32px',
                maxWidth: '900px',
                margin: '0 auto',
                color: '#26332E',
            }}
        >
            <h1 style={{ marginBottom: '8px' }}>
                QR 체크인 관리
            </h1>

            <p style={{ marginBottom: '24px' }}>
                입주자의 공용 자원 예약 QR코드를 스캔하여
                체크인을 처리합니다.
            </p>

            <div
                style={{
                    padding: '24px',
                    borderRadius: '12px',
                    backgroundColor: '#F3F5F1',
                    marginBottom: '24px',
                }}
            >
                <h2 style={{ marginBottom: '16px' }}>
                    QR코드 스캔
                </h2>

                <div
                    id={scannerElementId}
                    style={{
                        width: '100%',
                        maxWidth: '420px',
                        margin: '0 auto 20px',
                        backgroundColor: '#FFFFFF',
                        borderRadius: '8px',
                        overflow: 'hidden',
                    }}
                />

                <div
                    style={{
                        display: 'flex',
                        justifyContent: 'center',
                        gap: '12px',
                    }}
                >
                    <button
                        type="button"
                        onClick={startScanner}
                        disabled={scanning || processing}
                        style={{
                            padding: '12px 24px',
                            backgroundColor: '#52796F',
                            color: '#FFFFFF',
                            border: 'none',
                            borderRadius: '8px',
                            cursor: 'pointer',
                        }}
                    >
                        카메라 시작
                    </button>

                    <button
                        type="button"
                        onClick={stopScanner}
                        disabled={!scanning || processing}
                        style={{
                            padding: '12px 24px',
                            backgroundColor: '#FFFFFF',
                            color: '#26332E',
                            border: '1px solid #52796F',
                            borderRadius: '8px',
                            cursor: 'pointer',
                        }}
                    >
                        카메라 중지
                    </button>
                </div>
            </div>

            {processing && (
                <p style={{ textAlign: 'center' }}>
                    QR 체크인을 처리하고 있습니다...
                </p>
            )}

            {error && (
                <p
                    role="alert"
                    style={{
                        padding: '16px',
                        color: '#B42318',
                        backgroundColor: '#FFF1F0',
                        borderRadius: '8px',
                    }}
                >
                    {error}
                </p>
            )}

            {message && (
                <p
                    role="status"
                    style={{
                        padding: '16px',
                        color: '#52796F',
                        backgroundColor: '#E7F0EB',
                        borderRadius: '8px',
                    }}
                >
                    {message}
                </p>
            )}

            {checkedInReservation && (
                <div
                    style={{
                        marginTop: '20px',
                        padding: '24px',
                        backgroundColor: '#FFFFFF',
                        border: '1px solid #D0D5D2',
                        borderRadius: '12px',
                    }}
                >
                    <h2>체크인 완료 정보</h2>

                    <p>
                        예약 번호: {checkedInReservation.reservationNumber}
                    </p>

                    <p>
                        공용 자원 ID: {checkedInReservation.resourceId}
                    </p>

                    <p>
                        예약 날짜: {checkedInReservation.reservationDate}
                    </p>

                    <p>
                        이용 시간: {checkedInReservation.startTime}
                        {' ~ '}
                        {checkedInReservation.endTime}
                    </p>

                    <p>
                        체크인 시간: {checkedInReservation.checkedInAt}
                    </p>
                </div>
            )}
        </div>
    )
}

export default QrCheckIn
