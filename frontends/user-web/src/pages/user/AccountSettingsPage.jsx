/**
 * @author 조휘재
 * 작성일: 2026-11-01
 */

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { refreshSession } from '../../session'

const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL
const NICKNAME_MAX = 100
const fieldClassName =
  'w-full rounded-lg border border-main/25 bg-white px-3 py-2.5 text-base font-normal outline-none focus:border-main focus:ring-2 focus:ring-accent sm:px-4 sm:py-3'

/** 닉네임을 고치고 회원 탈퇴를 요청한다. 탈퇴가 되면 로그인 화면으로 보낸다. */
function AccountSettingsPage() {
  const { t, i18n } = useTranslation('settings')
  const navigate = useNavigate()
  const [nickname, setNickname] = useState('')
  const [savedNickname, setSavedNickname] = useState('')
  const [editingNickname, setEditingNickname] = useState(false)
  const [nicknameStatus, setNicknameStatus] = useState('loading')
  const [savingNickname, setSavingNickname] = useState(false)
  const [nicknameMessage, setNicknameMessage] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [withdrawing, setWithdrawing] = useState(false)
  const [message, setMessage] = useState('')

  useEffect(() => {
    document.title = t('documentTitle')
  }, [t, i18n.language])

  useEffect(() => {
    const controller = new AbortController()

    async function load() {
      try {
        const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/user/session`, {
          credentials: 'include',
          signal: controller.signal,
        })
        if (response.status === 401) {
          setNicknameStatus('unauthorized')
          return
        }
        if (!response.ok) {
          setNicknameStatus('error')
          return
        }
        const body = await response.json()
        const value = typeof body.nickname === 'string' ? body.nickname : ''
        setNickname(value)
        setSavedNickname(value)
        setNicknameStatus('ready')
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setNicknameStatus('error')
      }
    }

    load()
    return () => controller.abort()
  }, [])

  async function saveNickname(event) {
    event.preventDefault()
    setNicknameMessage('')
    const next = nickname.trim()
    if (next.length > NICKNAME_MAX) {
      setNicknameMessage(t('nicknameTooLong'))
      return
    }
    setSavingNickname(true)
    try {
      const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/user/account/nickname`, {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nickname: next }),
      })
      if (response.status === 401) {
        setNicknameStatus('unauthorized')
        return
      }
      if (!response.ok) {
        setNicknameMessage(t('saveFailed'))
        return
      }
      const body = await response.json()
      const value = typeof body.nickname === 'string' ? body.nickname : ''
      setNickname(value)
      setSavedNickname(value)
      setEditingNickname(false)
      setNicknameMessage(t('saved'))
      refreshSession()
    } catch {
      setNicknameMessage(t('saveFailed'))
    } finally {
      setSavingNickname(false)
    }
  }

  async function withdraw() {
    setWithdrawing(true)
    setMessage('')
    try {
      const response = await fetch(`${BACKEND_API_BASE_URL}/user-service/user/account/withdraw`, {
        method: 'POST',
        credentials: 'include',
      })
      if (response.status === 401) {
        setMessage(t('loginRequired'))
        return
      }
      if (!response.ok) {
        setMessage(t('withdrawFailed'))
        return
      }
      navigate('/login', { replace: true })
    } catch {
      setMessage(t('withdrawFailed'))
    } finally {
      setWithdrawing(false)
    }
  }

  const quietButtonClassName =
    'rounded-lg border border-main/30 bg-white px-4 py-2.5 text-sm font-semibold text-text hover:bg-main/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-60 sm:text-base'

  return (
    <div className="mx-auto grid w-full max-w-2xl gap-4 px-4 py-6 sm:gap-5 sm:px-8 sm:py-8 md:px-12 md:py-10">
      <h1 className="text-2xl font-semibold text-main sm:text-3xl">{t('documentTitle')}</h1>
      <section className="rounded-lg border border-main/15 bg-white p-5 sm:p-6">
        {nicknameStatus === 'ready' && !editingNickname && (
          <div className="grid gap-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="grid min-w-0 gap-1">
                <h2 className="text-base font-semibold text-text sm:text-lg">{t('nickname')}</h2>
                <p className="break-words text-base text-text">{savedNickname.trim() || t('anonymous')}</p>
              </div>
              <button
                type="button"
                onClick={() => {
                  setNickname(savedNickname)
                  setNicknameMessage('')
                  setEditingNickname(true)
                }}
                className={quietButtonClassName}
              >
                {t('edit')}
              </button>
            </div>
            {nicknameMessage && <p className="text-sm text-text">{nicknameMessage}</p>}
          </div>
        )}
        {nicknameStatus === 'ready' && editingNickname && (
          <form onSubmit={saveNickname} className="grid gap-3">
            <div className="flex flex-wrap items-end gap-3">
              <label htmlFor="nickname" className="grid min-w-48 flex-1 gap-1.5 text-sm font-medium text-text sm:text-base">
                <span className="text-base font-semibold sm:text-lg">{t('nickname')}</span>
                <input
                  id="nickname"
                  name="nickname"
                  type="text"
                  value={nickname}
                  autoComplete="nickname"
                  maxLength={NICKNAME_MAX}
                  onChange={(event) => {
                    setNicknameMessage('')
                    setNickname(event.target.value)
                  }}
                  className={fieldClassName}
                />
              </label>
              <button
                type="button"
                onClick={() => {
                  setNickname(savedNickname)
                  setNicknameMessage('')
                  setEditingNickname(false)
                }}
                className={quietButtonClassName}
              >
                {t('cancel')}
              </button>
              <button
                type="submit"
                disabled={savingNickname}
                className="rounded-lg bg-main px-4 py-2.5 text-sm font-semibold text-background hover:bg-main/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-60 sm:py-3 sm:text-base"
              >
                {savingNickname ? t('saving') : t('save')}
              </button>
            </div>
            {nicknameMessage && <p className="text-sm text-text">{nicknameMessage}</p>}
          </form>
        )}
        {nicknameStatus !== 'ready' && (
          <div className="grid gap-3">
            <h2 className="text-base font-semibold text-text sm:text-lg">{t('nickname')}</h2>
            {nicknameStatus === 'unauthorized' && <p className="text-sm text-text sm:text-base">{t('loginRequired')}</p>}
            {nicknameStatus === 'error' && <p className="text-sm text-text sm:text-base">{t('loadFailed')}</p>}
          </div>
        )}
      </section>
      <Link
        to="/my/settings/contract"
        className="flex items-center justify-between gap-4 rounded-lg border border-main/15 bg-white p-5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent hover:bg-main/5 sm:p-6"
      >
        <span className="grid min-w-0 gap-1">
          <span className="text-base font-semibold text-main sm:text-lg">{t('editContractAutocomplete')}</span>
          <span className="text-sm text-text/70 sm:text-base">{t('contractDescription')}</span>
        </span>
        <svg viewBox="0 0 20 20" aria-hidden="true" className="h-5 w-5 shrink-0 text-main">
          <path
            d="M7.5 4.5 13 10l-5.5 5.5"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </Link>
      <section className="rounded-lg border border-main/15 bg-white p-5 sm:p-6">
        {confirming ? (
          <div className="grid gap-3">
            <p className="text-sm text-text sm:text-base">{t('withdrawConfirm')}</p>
            <div className="flex flex-wrap gap-3">
              <button
                type="button"
                onClick={() => {
                  setConfirming(false)
                  setMessage('')
                }}
                className={quietButtonClassName}
              >
                {t('cancel')}
              </button>
              <button type="button" disabled={withdrawing} onClick={withdraw} className={quietButtonClassName}>
                {t('withdraw')}
              </button>
            </div>
          </div>
        ) : (
          <button type="button" onClick={() => setConfirming(true)} className={quietButtonClassName}>
            {t('withdraw')}
          </button>
        )}
        {message && <p className="mt-3 text-sm text-text">{message}</p>}
      </section>
    </div>
  )
}

export default AccountSettingsPage
