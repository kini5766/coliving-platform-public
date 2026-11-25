/**
 * @author 조휘재
 * 작성일: 2026-11-11
 */

import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import LocaleSwitch from '../../components/LocaleSwitch'

const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

/** 비밀번호 재설정 폼의 필수 입력 칸. */
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

/** 재설정 화면의 소개 영역과 폼 틀. */
function AuthShell({ title, children }) {
  const { t } = useTranslation('passwordReset')
  const { t: tCommon } = useTranslation('common')

  return (
    <main className="grid min-h-dvh bg-background text-text lg:grid-cols-2">
      <header className="bg-main px-4 py-8 text-background sm:px-8 sm:py-12 md:px-12 lg:flex lg:flex-col lg:justify-end lg:px-16 lg:py-20">
        <Link to="/" className="block max-w-md text-inherit no-underline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent">
          <p className="text-xs font-semibold text-accent sm:text-sm">{tCommon('serviceName')}</p>
          <p className="mt-0.5 text-xs text-background/80">{tCommon('serviceNameEn')}</p>
          <h1 className="mt-3 text-2xl font-semibold sm:text-3xl md:text-4xl lg:text-5xl">{t('headline')}</h1>
          <p className="mt-2 text-sm text-background/85 sm:text-base md:mt-4">{t('description')}</p>
        </Link>
      </header>
      <section className="flex min-w-0 items-center px-4 py-8 sm:px-8 sm:py-12 md:px-12 lg:px-16">
        <div className="grid w-full min-w-0 max-w-md gap-4 sm:gap-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-xl font-semibold text-main sm:text-2xl">{title}</h2>
            <LocaleSwitch />
          </div>
          {children}
        </div>
      </section>
    </main>
  )
}

/** 응답 본문의 code. 본문이 없거나 JSON 이 아니면 빈 문자열. */
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

/** 메일 비활성·발송 실패·형식 오류 코드를 안내 문구로 바꾼다. */
function requestError(code, t) {
  if (code === 'MAIL_DISABLED') return t('mailDisabled')
  if (code === 'MAIL_SEND_FAILED') return t('mailSendFailed')
  if (code === 'INVALID_REQUEST') return t('invalidEmail')
  return t('failed')
}

/** 재설정 메일을 요청하고, token 이 있으면 새 비밀번호를 보낸다. */
function PasswordResetPage() {
  const { t, i18n } = useTranslation('passwordReset')
  const { t: tCommon } = useTranslation('common')
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')?.trim() ?? ''
  const [email, setEmail] = useState('')
  const [sentTo, setSentTo] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [done, setDone] = useState(() => location.state?.done === true)
  const [result, setResult] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    document.title = t('documentTitle')
  }, [t, i18n.language])

  async function requestReset(event) {
    event.preventDefault()
    setResult('')
    setSubmitting(true)
    try {
      const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/user/password-reset`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email: email.trim() }),
      })
      if (response.status === 204) {
        setSentTo(email.trim())
        return
      }
      setResult(requestError(await readCode(response), t))
    } catch {
      setResult(t('failed'))
    } finally {
      setSubmitting(false)
    }
  }

  async function confirmReset(event) {
    event.preventDefault()
    setResult('')
    if (password !== passwordConfirm) {
      setResult(t('passwordMismatch'))
      return
    }
    setSubmitting(true)
    try {
      const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/user/password-reset/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ token, password }),
      })
      if (response.status === 204) {
        setDone(true)
        navigate('/password-reset', { replace: true, state: { done: true } })
        return
      }
      const code = await readCode(response)
      setResult(code === 'INVALID_TOKEN' ? t('invalidToken') : t('failed'))
    } catch {
      setResult(t('failed'))
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return (
      <AuthShell title={t('doneTitle')}>
        <p className="text-sm text-text sm:text-base">{t('doneBody')}</p>
        <Link
          to="/login"
          className="rounded-lg bg-main px-4 py-3 text-center text-sm font-semibold text-background no-underline hover:bg-main/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:text-base"
        >
          {tCommon('login')}
        </Link>
      </AuthShell>
    )
  }

  if (sentTo) {
    return (
      <AuthShell title={t('sentTitle')}>
        <p className="min-w-0 text-sm break-all text-text sm:text-base">{t('sentBody', { email: sentTo })}</p>
        <p className="text-sm text-text/70">{t('sentHint')}</p>
        <Link
          to="/login"
          className="rounded-lg bg-main px-4 py-3 text-center text-sm font-semibold text-background no-underline hover:bg-main/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:text-base"
        >
          {t('backToLogin')}
        </Link>
      </AuthShell>
    )
  }

  if (token) {
    return (
      <AuthShell title={t('confirmTitle')}>
        <form onSubmit={confirmReset} className="grid gap-4 sm:gap-5">
          <TextField
            id="password"
            label={t('password')}
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <TextField
            id="passwordConfirm"
            label={t('passwordConfirm')}
            type="password"
            autoComplete="new-password"
            value={passwordConfirm}
            onChange={(event) => setPasswordConfirm(event.target.value)}
          />
          <button
            type="submit"
            disabled={submitting}
            className="rounded-lg bg-main px-4 py-3 text-sm font-semibold text-background hover:bg-main/90 disabled:opacity-60 sm:text-base"
          >
            {submitting ? t('confirmSubmitting') : t('confirmSubmit')}
          </button>
          <p className="text-center text-sm text-text/70">
            <Link
              to="/login"
              className="font-semibold text-main focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
              {t('backToLogin')}
            </Link>
          </p>
          {result !== '' && (
            <p className="min-w-0 rounded-lg border border-main/20 bg-white p-3 text-sm">{result}</p>
          )}
        </form>
      </AuthShell>
    )
  }

  return (
    <AuthShell title={t('requestTitle')}>
      <form onSubmit={requestReset} className="grid gap-4 sm:gap-5">
        <TextField
          id="email"
          label={t('email')}
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <button
          type="submit"
          disabled={submitting}
          className="rounded-lg bg-main px-4 py-3 text-sm font-semibold text-background hover:bg-main/90 disabled:opacity-60 sm:text-base"
        >
          {submitting ? t('requestSubmitting') : t('requestSubmit')}
        </button>
        <p className="text-center text-sm text-text/70">
          <Link
            to="/login"
            className="font-semibold text-main focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          >
            {t('backToLogin')}
          </Link>
        </p>
        {result !== '' && (
          <p className="min-w-0 rounded-lg border border-main/20 bg-white p-3 text-sm">{result}</p>
        )}
      </form>
    </AuthShell>
  )
}

export default PasswordResetPage
