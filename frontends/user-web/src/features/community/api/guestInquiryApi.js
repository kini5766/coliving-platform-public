// 비회원 문의 API — 연락처·문의 내용·개인정보 동의 제출
import { request, USE_MOCK } from './communityClient'

// 비회원 문의 → POST /community-service/guest-inquiries → GuestInquiryController.create
export async function createGuestInquiry(inquiryRequest) {
  // USE_MOCK — 백엔드 없이 개발할 때 가짜 데이터, false이면 실제 API
  if (!USE_MOCK) return request('/guest-inquiries', { method: 'POST', body: inquiryRequest })
  return { inquiryId: 15, createdAt: new Date().toISOString().slice(0, 19) }
}
