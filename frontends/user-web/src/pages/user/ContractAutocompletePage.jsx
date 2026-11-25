/**
 * @author 조휘재
 * 작성일: 2026-11-01
 */

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL
const CONTRACT_PATH = `${BACKEND_API_BASE_URL}/user-service/user/contract-autocomplete`
const EMPTY = { email: '', name: '', phone: '', address: '', contractLanguage: '' }

/** 문자열이 아니면 빈 문자열로 맞춘다. */
function text(value) {
  return typeof value === 'string' ? value : ''
}

/** 비어 있으면 통과하고, 아니면 @ 와 그 뒤 점만 확인한다. */
function isEmail(value) {
  if (!value) return true
  const at = value.indexOf('@')
  return at > 0 && value.indexOf('.', at + 1) > at + 1 && !value.includes(' ')
}

/** 계약 자동완성 입력. multiline 이면 여러 줄 칸을 쓴다. */
function Field({ id, label, value, onChange, autoComplete, maxLength, multiline }) {
  const className =
    'w-full rounded-lg border border-main/25 bg-white px-3 py-2.5 text-base font-normal outline-none focus:border-main focus:ring-2 focus:ring-accent sm:px-4 sm:py-3'
  return (
    <label htmlFor={id} className="grid gap-1.5 text-sm font-medium text-text sm:text-base">
      {label}
      {multiline ? (
        <textarea
          id={id}
          name={id}
          rows={3}
          value={value}
          autoComplete={autoComplete}
          onChange={onChange}
          className={className}
        />
      ) : (
        <input
          id={id}
          name={id}
          type="text"
          value={value}
          autoComplete={autoComplete}
          maxLength={maxLength}
          onChange={onChange}
          className={className}
        />
      )}
    </label>
  )
}

/** 계약서에 넣을 연락처를 조회하고 저장한다. */
function ContractAutocompletePage() {
  const { t, i18n } = useTranslation('settings')
  const [fields, setFields] = useState(EMPTY)
  const [status, setStatus] = useState('loading')
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')

  useEffect(() => {
    document.title = t('contractDocumentTitle')
  }, [t, i18n.language])

  useEffect(() => {
    const controller = new AbortController()

    async function load() {
      try {
        const response = await fetch(CONTRACT_PATH, {
          credentials: 'include',
          signal: controller.signal,
        })
        if (response.status === 401) {
          setStatus('unauthorized')
          return
        }
        if (!response.ok) {
          setStatus('error')
          return
        }
        const body = await response.json()
        setFields({
          email: text(body.email),
          name: text(body.name),
          phone: text(body.phone),
          address: text(body.address),
          contractLanguage: text(body.contractLanguage),
        })
        setStatus('ready')
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setStatus('error')
      }
    }

    load()
    return () => controller.abort()
  }, [])

  function change(key) {
    return (event) => {
      setMessage('')
      setFields((current) => ({ ...current, [key]: event.target.value }))
    }
  }

  function clear() {
    setFields(EMPTY)
    setMessage('')
  }

  async function save(event) {
    event.preventDefault()
    setMessage('')
    if (!isEmail(fields.email.trim())) {
      setMessage(t('invalidEmail'))
      return
    }
    if (fields.contractLanguage.trim().length > 10) {
      setMessage(t('languageTooLong'))
      return
    }
    setSaving(true)
    try {
      const response = await fetch(CONTRACT_PATH, {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(fields),
      })
      if (response.status === 401) {
        setStatus('unauthorized')
        return
      }
      if (!response.ok) {
        setMessage(t('saveFailed'))
        return
      }
      const body = await response.json()
      setFields({
        email: text(body.email),
        name: text(body.name),
        phone: text(body.phone),
        address: text(body.address),
        contractLanguage: text(body.contractLanguage),
      })
      setMessage(t('saved'))
    } catch {
      setMessage(t('saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="px-4 py-6 sm:px-8 sm:py-8 md:px-12 md:py-10">
      <h1 className="text-2xl font-semibold text-main sm:text-3xl">{t('contractDocumentTitle')}</h1>
      <Link
        to="/my/settings"
        className="mt-3 inline-block text-sm font-semibold text-main focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:text-base"
      >
        {t('documentTitle')}
      </Link>
      {status === 'unauthorized' && <p className="mt-6 text-sm text-text sm:text-base">{t('loginRequired')}</p>}
      {status === 'error' && <p className="mt-6 text-sm text-text sm:text-base">{t('loadFailed')}</p>}
      {status === 'ready' && (
        <form onSubmit={save} className="mt-6 grid w-full max-w-md gap-4">
          <Field id="email" label={t('email')} autoComplete="email" value={fields.email} onChange={change('email')} />
          <Field id="name" label={t('name')} autoComplete="name" value={fields.name} onChange={change('name')} />
          <Field id="phone" label={t('phone')} autoComplete="tel" value={fields.phone} onChange={change('phone')} />
          <Field
            id="address"
            label={t('address')}
            autoComplete="street-address"
            value={fields.address}
            onChange={change('address')}
            multiline
          />
          <Field
            id="contractLanguage"
            label={t('contractLanguage')}
            autoComplete="off"
            maxLength={10}
            value={fields.contractLanguage}
            onChange={change('contractLanguage')}
          />
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={clear}
              className="rounded-lg border border-main/30 bg-white px-4 py-2.5 text-sm font-semibold text-text hover:bg-main/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:text-base"
            >
              {t('clear')}
            </button>
            <button
              type="submit"
              disabled={saving}
              className="rounded-lg bg-main px-4 py-2.5 text-sm font-semibold text-background hover:bg-main/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-60 sm:text-base"
            >
              {saving ? t('saving') : t('save')}
            </button>
          </div>
          {message && <p className="text-sm text-text">{message}</p>}
        </form>
      )}
    </div>
  )
}

export default ContractAutocompletePage
