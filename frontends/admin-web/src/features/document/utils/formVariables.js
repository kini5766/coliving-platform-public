// 양식 본문에 넣을 수 있는 빈칸 목록. key는 계약 서비스가 값을 채울 때 쓰는 이름과 같아야 한다.
// ⚠ 계약 서비스 ContractContentRenderer.KEYS, 문서 서비스 FormPlaceholders.KEYS와 똑같이 유지할 것 (20개)
export const FORM_VARIABLES = [
    { key: 'applicationNo', label: '신청 번호' },
    { key: 'contractDate', label: '계약일 (결제 완료일, 결제 전에는 빈칸)' },
    { key: 'applicantName', label: '신청자 이름' },
    { key: 'applicantEmail', label: '이메일' },
    { key: 'applicantPhone', label: '휴대폰 번호' },
    { key: 'applicantAddress', label: '주소' },
    { key: 'buildingName', label: '건물명' },
    { key: 'buildingAddress', label: '건물 주소' },
    { key: 'roomName', label: '호실' },
    { key: 'roomType', label: '호실 유형' },
    { key: 'roomStructure', label: '호실 구조' },
    { key: 'floorNumber', label: '층' },
    { key: 'area', label: '면적(㎡)' },
    { key: 'moveInDate', label: '입주일' },
    { key: 'moveOutDate', label: '퇴실 예정일' },
    { key: 'contractMonths', label: '계약 기간(개월)' },
    { key: 'monthlyFee', label: '월 이용료' },
    { key: 'deposit', label: '보증금' },
    { key: 'managementFee', label: '관리비(월)' },
    { key: 'firstPayment', label: '첫 결제 금액' },
]

const KEYS = new Set(FORM_VARIABLES.map((v) => v.key))
const PLACEHOLDER = /\{\{\s*([A-Za-z0-9_]+)\s*}}/g

// 저장 전에 빈칸 검사 (문서 서비스 FormPlaceholders와 같은 규칙). 문제 문구 목록, 없으면 빈 배열
//   - 모르는 이름: 모든 신청서 제출이 실패하게 된다
//   - 모양이 깨진 빈칸: 빈칸 일부에만 서식(굵게 등)이 들어가 {{ }}가 태그로 나뉜 경우 → 계약서에 중괄호가 그대로 남는다
export function placeholderProblems(content) {
    const unknown = new Set()
    const rest = (content ?? '').replace(PLACEHOLDER, (_, key) => {
        if (!KEYS.has(key)) unknown.add(`{{${key}}}`)
        return ''
    })
    const problems = []
    if (unknown.size > 0) {
        problems.push(`모르는 빈칸이 있어요: ${[...unknown].sort().join(', ')}. 오른쪽 빈칸 목록의 이름만 쓸 수 있어요.`)
    }
    if (rest.includes('{{') || rest.includes('}}')) {
        problems.push('모양이 깨진 빈칸이 있어요. {{ }} 안에는 서식(굵게 등) 없이 이름만 있어야 해요. 빈칸을 지우고 빈칸 목록에서 다시 넣어 주세요.')
    }
    return problems
}
