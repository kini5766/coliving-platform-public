/**
 * @author 조휘재
 * 작성일: 2026-10-30
 */

import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import LocaleSwitch from '../../components/LocaleSwitch'
import { readSignupEmail, storeSignupEmail } from './signupEmailStorage'

const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

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

/** location state 의 이메일이 문자열일 때만 앞뒤 공백을 뺀다. */
function emailFromState(state) {
  const value = state?.email
  return typeof value === 'string' ? value.trim() : ''
}

/** 가입 메일 안내. 주소가 없으면 가입 화면으로 되돌린다. */
function SignupCheckEmailPage() {
  const { t, i18n } = useTranslation('signup')
  const { t: tCommon } = useTranslation('common')
  const location = useLocation()
  const navigate = useNavigate()
  const fromState = emailFromState(location.state)
  const [email] = useState(() => fromState || readSignupEmail())
  const [notice, setNotice] = useState('')
  const [resending, setResending] = useState(false)

  async function resend() {
    setNotice('')
    setResending(true)
    try {
      const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/user/signup/email-verification/resend`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email }),
      })
      const code = await readCode(response)
      if (response.status === 204) {
        setNotice(t('resent'))
        return
      }
      if (code === 'TOO_MANY_REQUESTS') {
        setNotice(t('tooManyRequests'))
        return
      }
      if (code === 'MAIL_DISABLED') {
        setNotice(t('mailDisabled'))
        return
      }
      if (code === 'MAIL_SEND_FAILED') {
        setNotice(t('mailSendFailed'))
        return
      }
      setNotice(t('failed'))
    } catch {
      setNotice(t('failed'))
    } finally {
      setResending(false)
    }
  }

  useEffect(() => {
    if (fromState) storeSignupEmail(fromState)
  }, [fromState])

  useEffect(() => {
    if (!email) navigate('/signup', { replace: true })
  }, [email, navigate])

  useEffect(() => {
    document.title = t('checkEmailDocumentTitle')
  }, [t, i18n.language])

  if (!email) return null

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
            <h2 className="text-xl font-semibold text-main sm:text-2xl">{t('checkEmailTitle')}</h2>
            <LocaleSwitch />
          </div>
          <p className="min-w-0 text-sm break-all text-text sm:text-base">{t('checkEmailBody', { email })}</p>
          <p className="text-sm text-text/70">{t('checkEmailHint')}</p>
          <button
            type="button"
            onClick={resend}
            disabled={resending}
            className="rounded-lg border border-main/25 bg-white px-4 py-3 text-sm font-semibold text-main hover:bg-main/5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-60 sm:text-base"
          >
            {resending ? t('resending') : t('resend')}
          </button>
          {notice ? <p className="text-sm text-text/80">{notice}</p> : null}
          <Link
            to="/login"
            className="rounded-lg bg-main px-4 py-3 text-center text-sm font-semibold text-background no-underline hover:bg-main/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:text-base"
          >
            {tCommon('login')}
          </Link>
        </div>
      </section>
    </main>
  )
}

export default SignupCheckEmailPage
