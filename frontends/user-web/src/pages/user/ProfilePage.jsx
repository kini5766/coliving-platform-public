/**
 * @author 조휘재
 * 작성일: 2026-11-02
 */

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { Avatar } from '../../features/community/components'
import { isResident } from '../../resident'

const BACKEND_API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL

/** 문자열만 앞뒤 공백을 빼고, 아니면 빈 문자열. */
function text(value) {
  return typeof value === 'string' ? value.trim() : ''
}

/** 내 프로필 또는 해당 회원의 프로필을 조회한다. userId 가 없으면 내 프로필이다. */
function ProfilePage({ userId }) {
  const { t, i18n } = useTranslation('profile')
  const mine = userId == null
  const [status, setStatus] = useState('loading')
  const [profile, setProfile] = useState({ nickname: '', buildingName: '', roomNumber: '' })

  useEffect(() => {
    document.title = t(mine ? 'documentTitle' : 'otherDocumentTitle')
  }, [t, i18n.language, mine])

  useEffect(() => {
    const controller = new AbortController()

    async function load() {
      setStatus('loading')
      try {
        const path = mine ? '/user-service/user/profile' : `/user-service/user/profile/${userId}`
        const response = await fetch(`${BACKEND_API_BASE_URL}${path}`, {
          credentials: 'include',
          signal: controller.signal,
        })
        if (response.status === 401) {
          setStatus('unauthorized')
          return
        }
        if (response.status === 403) {
          setStatus('forbidden')
          return
        }
        if (response.status === 404) {
          setStatus('missing')
          return
        }
        if (!response.ok) {
          setStatus('error')
          return
        }
        const body = await response.json()
        const residence = body && typeof body.residence === 'object' && body.residence ? body.residence : {}
        setProfile({
          nickname: text(body?.nickname),
          buildingName: text(residence.buildingName),
          roomNumber: text(residence.roomNumber),
        })
        setStatus('ready')
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setStatus('error')
      }
    }

    load()
    return () => controller.abort()
  }, [mine, userId])

  return (
    <div className="mx-auto grid w-full max-w-2xl gap-4 px-4 py-6 sm:gap-5 sm:px-8 sm:py-8 md:px-12 md:py-10">
      <h1 className="text-2xl font-semibold text-main sm:text-3xl">{t(mine ? 'documentTitle' : 'otherDocumentTitle')}</h1>
      {status === 'ready' && (
        <section className="rounded-lg border border-main/15 bg-white p-5 sm:p-6">
          <Avatar name={profile.nickname || t('anonymous')} size="lg" />
          <dl className="mt-4 grid gap-4">
            <div className="grid gap-1">
              <dt className="text-sm font-medium text-text/70">{t('nickname')}</dt>
              <dd className="break-words text-base text-text">{profile.nickname || t('anonymous')}</dd>
            </div>
            <div className="grid gap-1">
              <dt className="text-sm font-medium text-text/70">{t('building')}</dt>
              <dd className="break-words text-base text-text">{profile.buildingName}</dd>
            </div>
            <div className="grid gap-1">
              <dt className="text-sm font-medium text-text/70">{t('room')}</dt>
              <dd className="break-words text-base text-text">{profile.roomNumber}</dd>
            </div>
          </dl>
        </section>
      )}
      {status === 'unauthorized' && <p className="text-sm text-text sm:text-base">{t('loginRequired')}</p>}
      {status === 'forbidden' && <p className="text-sm text-text sm:text-base">{t('residentsOnly')}</p>}
      {status === 'missing' && <p className="text-sm text-text sm:text-base">{t('notFound')}</p>}
      {status === 'error' && <p className="text-sm text-text sm:text-base">{t('loadFailed')}</p>}
    </div>
  )
}

/** 입주자만 숫자 userId 의 프로필을 연다. */
export function ResidentProfileRoute() {
  const { userId } = useParams()
  const { t } = useTranslation('profile')
  const numericId = typeof userId === 'string' && /^\d+$/.test(userId) ? userId : null

  if (!isResident()) {
    return (
      <div className="mx-auto grid w-full max-w-2xl gap-4 px-4 py-6 sm:px-8 sm:py-8 md:px-12 md:py-10">
        <h1 className="text-2xl font-semibold text-main sm:text-3xl">{t('otherDocumentTitle')}</h1>
        <p className="text-sm text-text sm:text-base">{t('residentsOnly')}</p>
      </div>
    )
  }

  if (numericId == null) {
    return (
      <div className="mx-auto grid w-full max-w-2xl gap-4 px-4 py-6 sm:px-8 sm:py-8 md:px-12 md:py-10">
        <h1 className="text-2xl font-semibold text-main sm:text-3xl">{t('otherDocumentTitle')}</h1>
        <p className="text-sm text-text sm:text-base">{t('notFound')}</p>
      </div>
    )
  }

  return <ProfilePage userId={numericId} />
}

export default ProfilePage
