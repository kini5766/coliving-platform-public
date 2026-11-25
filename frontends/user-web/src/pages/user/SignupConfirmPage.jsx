/**
 * @author 조휘재
 * 작성일: 2026-11-11
 */

import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import LocaleSwitch from '../../components/LocaleSwitch'

const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

/** 메일 링크로 가입을 확인한다. 버튼을 눌러야 토큰을 한 번 쓴다. */
function SignupConfirmPage() {
  const { t, i18n } = useTranslation('signup')
  const { t: tCommon } = useTranslation('common')
  const [params] = useSearchParams()
  const token = params.get('token')?.trim() ?? ''
  const [status, setStatus] = useState(token ? 'ready' : 'invalid')

  useEffect(() => {
    document.title = t('confirmDocumentTitle')
  }, [t, i18n.language])

  async function confirm() {
    setStatus('pending')
    try {
      const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/user/signup/email-verification`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ token }),
      })
      setStatus(response.status === 204 ? 'done' : 'invalid')
    } catch {
      setStatus('invalid')
    }
  }

  const title = status === 'done'
    ? t('confirmDoneTitle')
    : status === 'invalid'
      ? t('confirmInvalidTitle')
      : status === 'pending'
        ? t('confirmTitle')
        : t('confirmSubmit')
  const body = status === 'done' ? t('confirmDoneBody') : status === 'invalid' ? t('confirmInvalidBody') : ''

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
          {body !== '' && <p className="text-sm text-text sm:text-base">{body}</p>}
          {(status === 'ready' || status === 'pending') && (
            <button
              type="button"
              disabled={status === 'pending'}
              onClick={confirm}
              className="rounded-lg bg-main px-4 py-3 text-sm font-semibold text-background hover:bg-main/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-60 sm:text-base"
            >
              {status === 'pending' ? t('confirmTitle') : t('confirmSubmit')}
            </button>
          )}
          {status === 'done' && (
            <Link
              to="/login"
              className="rounded-lg bg-main px-4 py-3 text-center text-sm font-semibold text-background no-underline hover:bg-main/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:text-base"
            >
              {tCommon('login')}
            </Link>
          )}
          {status === 'invalid' && (
            <Link
              to="/signup"
              className="rounded-lg bg-main px-4 py-3 text-center text-sm font-semibold text-background no-underline hover:bg-main/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:text-base"
            >
              {t('title')}
            </Link>
          )}
        </div>
      </section>
    </main>
  )
}

export default SignupConfirmPage
