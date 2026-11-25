
import { useCallback, useEffect, useState } from 'react'

const API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

const INQUIRY_API_URL =
  `${API_BASE_URL}/reservation-service/admin/inquiries`

// 문의 유형 한글 표시
const INQUIRY_TYPE_LABELS = {
  EXTENSION: '예약 연장 문의',
  BREAKDOWN: '고장 및 이용 불가 문의',
  OTHER: '기타 문의',
}

// 날짜 표시
function formatDateTime(value) {
  if (!value) return '-'

  return String(value).replace('T', ' ').slice(0, 16)
}

// 서버 오류 메시지 처리
async function getErrorMessage(response) {
  try {
    const data = await response.json()

    return (
      data.message ||
      data.detail ||
      `요청 처리에 실패했습니다. (${response.status})`
    )
  } catch {
    return `요청 처리에 실패했습니다. (${response.status})`
  }
}

function InquiryManage() {
  // ==========================================
  // 1. 상태 관리
  // ==========================================

  const [inquiries, setInquiries] = useState([])
  const [replyInputs, setReplyInputs] = useState({})

  const [loading, setLoading] = useState(false)
  const [savingId, setSavingId] = useState(null)

  const [filter, setFilter] = useState('ALL')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  // ==========================================
  // 2. 전체 문의 목록 조회
  // ==========================================

  const loadInquiries = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const response = await fetch(INQUIRY_API_URL, {
        method: 'GET',
        credentials: 'include',
      })

      if (!response.ok) {
        throw new Error(await getErrorMessage(response))
      }

      const data = await response.json()

      setInquiries(Array.isArray(data) ? data : [])
    } catch (err) {
      setError(err.message || '문의 목록 조회에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  // ==========================================
  // 3. 화면 최초 실행 시 문의 조회
  // ==========================================

  useEffect(() => {
    loadInquiries()
  }, [loadInquiries])

  // ==========================================
  // 4. 답변 입력값 변경
  // ==========================================

  function handleReplyChange(inquiryId, value) {
    setReplyInputs((prev) => ({
      ...prev,
      [inquiryId]: value,
    }))
  }

  // ==========================================
  // 5. 관리자 답변 등록
  // ==========================================

  async function handleSubmitReply(inquiryId) {
    const reply = (replyInputs[inquiryId] || '').trim()

    if (!reply) {
      alert('답변 내용을 입력해 주세요.')
      return
    }

    const confirmed = window.confirm(
      '작성한 답변을 등록하시겠습니까?'
    )

    if (!confirmed) return

    setSavingId(inquiryId)
    setError('')
    setMessage('')

    try {
      const response = await fetch(
        `${INQUIRY_API_URL}/${inquiryId}/reply`,
        {
          method: 'PATCH',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            reply: reply,
          }),
        }
      )

      if (!response.ok) {
        throw new Error(await getErrorMessage(response))
      }

      const updatedInquiry = await response.json()

      // 답변 완료된 문의 정보를 화면에 즉시 반영
      setInquiries((prev) =>
        prev.map((inquiry) =>
          inquiry.id === inquiryId
            ? updatedInquiry
            : inquiry
        )
      )

      // 답변 입력창 초기화
      setReplyInputs((prev) => ({
        ...prev,
        [inquiryId]: '',
      }))

      setMessage(`문의 번호 ${inquiryId}번 답변이 등록되었습니다.`)
    } catch (err) {
      setError(err.message || '답변 등록에 실패했습니다.')
    } finally {
      setSavingId(null)
    }
  }

  // ==========================================
  // 6. 문의 상태별 필터
  // ==========================================

  const pendingCount = inquiries.filter(
    (inquiry) => inquiry.status !== 'ANSWERED'
  ).length

  const answeredCount = inquiries.filter(
    (inquiry) => inquiry.status === 'ANSWERED'
  ).length

  const filteredInquiries = inquiries
    .filter((inquiry) => {
      if (filter === 'PENDING') {
        return inquiry.status !== 'ANSWERED'
      }

      if (filter === 'ANSWERED') {
        return inquiry.status === 'ANSWERED'
      }

      return true
    })
    .sort((a, b) => b.id - a.id)

  // ==========================================
  // 7. 화면 출력
  // ==========================================

  return (
    <div className="min-h-screen bg-[#F3F5F1] p-6">

      {/* 페이지 제목 */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#26332E]">
            공용 자원 문의 관리
          </h1>

          <p className="mt-2 text-sm text-gray-600">
            사용자의 문의를 확인하고 관리자 답변을 등록합니다.
          </p>
        </div>

        <button
          type="button"
          onClick={loadInquiries}
          disabled={loading}
          className="rounded-lg bg-[#52796F] px-5 py-2 text-white disabled:opacity-50"
        >
          {loading ? '조회 중...' : '새로고침'}
        </button>
      </div>

      {/* 문의 현황 */}
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">

        <div className="rounded-xl bg-white p-5 shadow-sm">
          <p className="text-sm text-gray-500">
            전체 문의
          </p>

          <p className="mt-2 text-2xl font-bold text-[#26332E]">
            {inquiries.length}건
          </p>
        </div>

        <div className="rounded-xl bg-white p-5 shadow-sm">
          <p className="text-sm text-gray-500">
            답변 대기
          </p>

          <p className="mt-2 text-2xl font-bold text-amber-700">
            {pendingCount}건
          </p>
        </div>

        <div className="rounded-xl bg-white p-5 shadow-sm">
          <p className="text-sm text-gray-500">
            답변 완료
          </p>

          <p className="mt-2 text-2xl font-bold text-[#52796F]">
            {answeredCount}건
          </p>
        </div>
      </div>

      {/* 상태별 조회 버튼 */}
      <div className="mb-5 flex flex-wrap gap-2">
        {[
          { value: 'ALL', label: '전체 문의' },
          { value: 'PENDING', label: '답변 대기' },
          { value: 'ANSWERED', label: '답변 완료' },
        ].map((item) => (
          <button
            key={item.value}
            type="button"
            onClick={() => setFilter(item.value)}
            className={`rounded-lg px-4 py-2 text-sm font-medium ${
              filter === item.value
                ? 'bg-[#52796F] text-white'
                : 'border border-gray-200 bg-white text-gray-700'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {/* 오류 메시지 */}
      {error && (
        <div className="mb-5 rounded-lg border border-red-200 bg-red-50 p-4 text-red-700">
          {error}
        </div>
      )}

      {/* 성공 메시지 */}
      {message && (
        <div className="mb-5 rounded-lg border border-green-200 bg-green-50 p-4 text-green-800">
          {message}
        </div>
      )}

      {/* 문의 목록 */}
      {loading ? (
        <div className="rounded-xl bg-white p-10 text-center text-gray-500">
          문의 목록을 불러오는 중입니다.
        </div>
      ) : filteredInquiries.length === 0 ? (
        <div className="rounded-xl bg-white p-10 text-center text-gray-500">
          조회된 문의가 없습니다.
        </div>
      ) : (
        <div className="space-y-5">

          {filteredInquiries.map((inquiry) => {
            const isAnswered =
              inquiry.status === 'ANSWERED'

            return (
              <div
                key={inquiry.id}
                className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm"
              >

                {/* 문의 상단 정보 */}
                <div className="mb-4 flex flex-wrap items-center justify-between gap-3">

                  <div className="flex flex-wrap items-center gap-3">
                    <span className="text-lg font-bold text-[#26332E]">
                      문의 #{inquiry.id}
                    </span>

                    <span
                      className={`rounded-full px-3 py-1 text-xs font-semibold ${
                        isAnswered
                          ? 'bg-green-100 text-green-800'
                          : 'bg-yellow-100 text-yellow-800'
                      }`}
                    >
                      {isAnswered ? '답변 완료' : '답변 대기'}
                    </span>
                  </div>

                  <span className="text-sm text-gray-500">
                    {formatDateTime(inquiry.createdAt)}
                  </span>
                </div>

                {/* 문의 상세 정보 */}
                <div className="mb-5 grid grid-cols-1 gap-3 text-sm sm:grid-cols-3">

                  <div>
                    <span className="text-gray-500">
                      사용자 ID
                    </span>

                    <p className="mt-1 font-medium text-[#26332E]">
                      {inquiry.userId}
                    </p>
                  </div>

                  <div>
                    <span className="text-gray-500">
                      예약 ID
                    </span>

                    <p className="mt-1 font-medium text-[#26332E]">
                      {inquiry.reservationId}
                    </p>
                  </div>

                  <div>
                    <span className="text-gray-500">
                      문의 유형
                    </span>

                    <p className="mt-1 font-medium text-[#26332E]">
                      {INQUIRY_TYPE_LABELS[inquiry.inquiryType] ||
                        inquiry.inquiryType}
                    </p>
                  </div>
                </div>

                {/* 사용자 문의 내용 */}
                <div className="mb-5 rounded-lg bg-[#F3F5F1] p-4">
                  <h3 className="mb-2 font-semibold text-[#26332E]">
                    사용자 문의 내용
                  </h3>

                  <p className="whitespace-pre-wrap text-sm leading-6 text-gray-700">
                    {inquiry.content}
                  </p>
                </div>

                {/* 답변 완료된 문의 */}
                {isAnswered ? (
                  <div className="rounded-lg border border-green-200 bg-green-50 p-4">

                    <h3 className="mb-2 font-semibold text-[#52796F]">
                      관리자 답변
                    </h3>

                    <p className="whitespace-pre-wrap text-sm leading-6 text-gray-700">
                      {inquiry.adminReply || '답변 내용이 없습니다.'}
                    </p>

                    <p className="mt-3 text-xs text-gray-500">
                      답변 등록일: {formatDateTime(inquiry.repliedAt)}
                    </p>
                  </div>
                ) : (

                  /* 답변 대기 중인 문의 */
                  <div className="border-t border-gray-200 pt-5">

                    <h3 className="mb-3 font-semibold text-[#26332E]">
                      관리자 답변 작성
                    </h3>

                    <textarea
                      rows={5}
                      value={replyInputs[inquiry.id] || ''}
                      onChange={(e) =>
                        handleReplyChange(
                          inquiry.id,
                          e.target.value
                        )
                      }
                      placeholder="사용자에게 전달할 답변을 입력해 주세요."
                      className="w-full resize-y rounded-lg border border-gray-300 p-4 text-sm outline-none focus:border-[#52796F]"
                    />

                    <div className="mt-3 flex justify-end">
                      <button
                        type="button"
                        onClick={() =>
                          handleSubmitReply(inquiry.id)
                        }
                        disabled={savingId !== null}
                        className="rounded-lg bg-[#52796F] px-6 py-3 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {savingId === inquiry.id
                          ? '답변 저장 중...'
                          : '답변 등록'}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default InquiryManage
