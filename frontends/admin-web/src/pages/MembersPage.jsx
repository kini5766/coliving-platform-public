/**
 * @author 조휘재
 * 작성일: 2026-11-11
 */

import { useEffect, useState } from 'react'
import { Button } from '../features/community/components'

const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

const ROLE_LABELS = {
  USER: '회원',
  CANDIDATE: '후보자',
  RESIDENT: '입주자',
  ADMIN: '관리자',
}

const PROVIDER_LABELS = {
  GOOGLE: '구글',
  NAVER: '네이버',
}

/** 테스트 계정 폼의 필수 입력 칸. */
function TextField({ id, label, type, value, onChange, autoComplete }) {
  return (
    <label htmlFor={id} className="grid gap-1.5 text-sm font-medium text-text sm:text-base">
      {label}
      <input
        id={id}
        name={id}
        type={type}
        value={value}
        autoComplete={autoComplete}
        required
        onChange={onChange}
        className="rounded-lg border border-main/25 bg-white px-3 py-2.5 text-base font-normal outline-none focus:border-main focus:ring-2 focus:ring-accent sm:px-4 sm:py-3"
      />
    </label>
  )
}

/** 응답 JSON의 code. 본문이 없거나 형식이 아니면 빈 문자열이다. */
async function readCode(response) {
  const text = await response.text()
  if (!text) return ''
  try {
    const body = JSON.parse(text)
    return typeof body.code === 'string' ? body.code : ''
  } catch {
    return ''
  }
}

/** 가입 시각. 없거나 해석할 수 없으면 - 이다. */
function formatCreatedAt(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

/** 이메일이 있으면 이메일, 없으면 소셜 제공자 이름. */
function accountLabel(member) {
  if (member.email) return member.email
  const providers = (member.providers ?? []).map((provider) => PROVIDER_LABELS[provider] ?? provider)
  return providers.length > 0 ? providers.join(', ') : '-'
}

/** 회원 목록과 개발 테스트 계정 추가. 제목을 문서 제목으로 쓴다. */
function MembersPage({ title }) {
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [formResult, setFormResult] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    document.title = title
  }, [title])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setLoadError('')
      try {
        const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/admin/members`, {
          credentials: 'include',
        })
        if (!response.ok) {
          if (!cancelled) setLoadError('회원 목록을 불러오지 못했습니다.')
          return
        }
        const body = await response.json()
        if (!cancelled) setMembers(Array.isArray(body) ? body : [])
      } catch {
        if (!cancelled) setLoadError('회원 목록을 불러오지 못했습니다.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [refreshKey])

  function closeForm() {
    setFormOpen(false)
    setEmail('')
    setPassword('')
    setPasswordConfirm('')
    setFormResult('')
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormResult('')
    if (password !== passwordConfirm) {
      setFormResult('비밀번호가 일치하지 않습니다.')
      return
    }
    setSubmitting(true)
    try {
      const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/admin/test-accounts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
      })
      if (response.status === 201) {
        closeForm()
        setRefreshKey((key) => key + 1)
        return
      }
      const code = await readCode(response)
      if (response.status === 409 || code === 'EMAIL_TAKEN') {
        setFormResult('이미 가입된 이메일입니다.')
        return
      }
      if (response.status === 401 || response.status === 403) {
        setFormResult('관리자 권한이 없습니다.')
        return
      }
      setFormResult('테스트 계정을 만들지 못했습니다.')
    } catch {
      setFormResult('테스트 계정을 만들지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="w-full max-w-[68rem]">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-main sm:text-3xl">{title}</h1>
          <p className="mt-1 text-sm text-text/70">이메일 가입과 소셜 가입 회원을 함께 봅니다.</p>
        </div>
        <Button onClick={() => setFormOpen(true)}>개발 테스트 계정 추가</Button>
      </div>

      {loading && <p className="mt-6 text-sm text-text/70">불러오는 중</p>}
      {loadError !== '' && <p className="mt-6 text-sm text-text">{loadError}</p>}
      {!loading && loadError === '' && members.length === 0 && (
        <p className="mt-6 text-sm text-text/70">등록된 회원이 없습니다.</p>
      )}
      {!loading && members.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded-lg border border-main/20 bg-white">
          <table className="w-full min-w-[40rem] text-left text-sm">
            <thead className="border-b border-main/15 text-text/70">
              <tr>
                <th className="px-4 py-3 font-medium">계정</th>
                <th className="px-4 py-3 font-medium">이름</th>
                <th className="px-4 py-3 font-medium">역할</th>
                <th className="px-4 py-3 font-medium">상태</th>
                <th className="px-4 py-3 font-medium">가입</th>
              </tr>
            </thead>
            <tbody>
              {members.map((member) => (
                <tr key={member.id} className="border-b border-main/10 last:border-b-0">
                  <td className="px-4 py-3 break-all">{accountLabel(member)}</td>
                  <td className="px-4 py-3">{member.nickname || '-'}</td>
                  <td className="px-4 py-3">{(member.roles ?? []).map((role) => ROLE_LABELS[role] ?? role).join(', ') || '-'}</td>
                  <td className="px-4 py-3">{member.enabled ? '활성' : '비활성'}</td>
                  <td className="px-4 py-3 whitespace-nowrap">{formatCreatedAt(member.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {formOpen && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-main/40 px-4">
          <form onSubmit={handleSubmit} className="grid w-full max-w-md gap-4 rounded-lg bg-background p-5 sm:p-6">
            <h2 className="text-xl font-semibold text-main">개발 테스트 계정 추가</h2>
            <p className="text-sm text-text/70">확인 메일을 보내지 않고, 바로 로그인할 수 있는 계정을 만듭니다.</p>
            <TextField
              id="dev-test-email"
              label="이메일"
              type="email"
              autoComplete="off"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <TextField
              id="dev-test-password"
              label="비밀번호"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <TextField
              id="dev-test-password-confirm"
              label="비밀번호 확인"
              type="password"
              autoComplete="new-password"
              value={passwordConfirm}
              onChange={(event) => setPasswordConfirm(event.target.value)}
            />
            {formResult !== '' && <p className="text-sm text-text">{formResult}</p>}
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={closeForm}
                className="rounded-lg border border-main/25 px-4 py-2.5 text-sm font-semibold text-main"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="rounded-lg bg-main px-4 py-2.5 text-sm font-semibold text-background hover:bg-main/90 disabled:opacity-60"
              >
                {submitting ? '만드는 중' : '추가'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}

export default MembersPage
