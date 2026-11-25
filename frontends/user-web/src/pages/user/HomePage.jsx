/**
 * @author 조휘재
 * 작성일: 2026-10-27
 */

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { searchBuildings } from '../property/propertyApi'
import { errorMessage, formatWon } from '../property/format'

const SPACE_IMAGES = ['/images/home-room.jpg', '/images/home-lounge.jpg', '/images/study-room.png', '/images/meeting-room-a.png']
const CARD_IMAGES = ['/images/home-building.jpg', '/images/home-room.jpg', '/images/community-room.png']

/** 홈 소개와 지점 카드를 보여주고 매물 목록을 불러온다. */
function HomePage() {
  const { t, i18n } = useTranslation('home')
  const { t: propertyT } = useTranslation('property')
  const locale = i18n.language?.startsWith('en') ? 'en' : 'ko'
  const steps = t('steps', { returnObjects: true })
  const spaces = t('spaces', { returnObjects: true })
  const [buildings, setBuildings] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    document.title = t('title')
  }, [t])

  useEffect(() => {
    const controller = new AbortController()
    setError(null)
    searchBuildings({ page: 0, size: 3 })
      .then((page) => {
        if (!controller.signal.aborted) setBuildings(page.content ?? [])
      })
      .catch((err) => {
        if (!controller.signal.aborted) setError(err)
      })
    return () => controller.abort()
  }, [])

  return (
    <div>
      <section className="grid lg:grid-cols-2">
        <div className="flex flex-col justify-center px-4 py-12 sm:px-8 sm:py-16 md:px-12 lg:py-20">
          <p className="text-sm font-medium text-main">{t('eyebrow')}</p>
          <h1 className="mt-3 max-w-xl text-3xl font-semibold text-main sm:text-5xl">{t('headline')}</h1>
          <p className="mt-4 max-w-xl text-sm leading-6 text-text/80 sm:text-base">{t('lead')}</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              to="/property/buildings"
              className="rounded-lg bg-main px-4 py-2.5 text-sm font-semibold text-background hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
              {t('findHome')}
            </Link>
            <Link
              to="/signup"
              className="rounded-lg border border-main px-4 py-2.5 text-sm font-semibold text-main hover:bg-main/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
              {t('signUp')}
            </Link>
          </div>
        </div>
        <img src="/images/home-lounge.jpg" alt={spaces[1]?.alt ?? ''} className="h-64 w-full object-cover sm:h-80 lg:h-full lg:min-h-[28rem]" />
      </section>

      <section className="px-4 py-12 sm:px-8 md:px-12">
        <h2 className="text-2xl font-semibold text-main">{t('stepsTitle')}</h2>
        <ol className="mt-6 grid gap-4 md:grid-cols-3">
          {steps.map((step, index) => (
            <li key={step.title} className="rounded-xl bg-white p-5">
              <p className="text-sm font-semibold text-accent">{index + 1}</p>
              <h3 className="mt-2 text-lg font-semibold">{step.title}</h3>
              <p className="mt-2 text-sm leading-6 text-text/80">{step.body}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="px-4 pb-12 sm:px-8 md:px-12">
        <h2 className="text-2xl font-semibold text-main">{t('spacesTitle')}</h2>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-text/80">{t('spacesLead')}</p>
        <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {spaces.map((space, index) => (
            <li key={space.title} className="overflow-hidden rounded-xl bg-white">
              <img src={SPACE_IMAGES[index]} alt={space.alt} className="h-44 w-full object-cover" />
              <div className="p-4">
                <h3 className="font-semibold">{space.title}</h3>
                <p className="mt-1 text-sm leading-6 text-text/80">{space.body}</p>
              </div>
            </li>
          ))}
        </ul>
      </section>

      <section className="px-4 pb-12 sm:px-8 md:px-12">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-2xl font-semibold text-main">{t('homesTitle')}</h2>
            <p className="mt-2 text-sm leading-6 text-text/80">{t('homesLead')}</p>
          </div>
          <Link to="/property/buildings" className="text-sm font-semibold text-main underline-offset-2 hover:underline">
            {t('seeAll')}
          </Link>
        </div>
        {error && <p className="mt-6 text-sm text-text/70">{errorMessage(error, propertyT)}</p>}
        {!error && buildings === null && <p className="mt-6 text-sm text-text/70">{propertyT('common.loading')}</p>}
        {!error && buildings?.length === 0 && <p className="mt-6 text-sm text-text/70">{t('homesEmpty')}</p>}
        {!error && buildings?.length > 0 && (
          <ul className="mt-6 grid gap-4 md:grid-cols-3">
            {buildings.map((building, index) => (
              <li key={building.id}>
                <Link
                  to={`/property/buildings/${building.id}`}
                  className="block overflow-hidden rounded-xl bg-white shadow-sm transition hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                >
                  <img
                    src={CARD_IMAGES[index % CARD_IMAGES.length]}
                    alt={t('samplePhoto')}
                    className="h-44 w-full object-cover"
                  />
                  <div className="p-5">
                    <h3 className="text-lg font-semibold">{building.name}</h3>
                    <p className="mt-1 text-sm text-text/70">{building.address}</p>
                    <p className="mt-4 text-sm font-semibold text-main">
                      {t('monthlyDeposit', {
                        monthly: formatWon(building.lowestMonthlyFee, locale),
                        deposit: formatWon(building.lowestDeposit, locale),
                      })}
                    </p>
                    {building.availableRoomCount != null && (
                      <p className="mt-2 text-xs text-text/70">{propertyT('list.availableRooms', { n: building.availableRoomCount })}</p>
                    )}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="px-4 pb-16 sm:px-8 md:px-12">
        <h2 className="text-2xl font-semibold text-main">{t('pathsTitle')}</h2>
        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <Link to="/property/buildings" className="rounded-xl bg-main px-5 py-6 text-background hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent">
            <span className="text-lg font-semibold">{t('findPath')}</span>
          </Link>
          <Link to="/facilities" className="rounded-xl border border-main bg-white px-5 py-6 text-main hover:bg-main/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent">
            <span className="text-lg font-semibold">{t('facilityPath')}</span>
          </Link>
        </div>
      </section>
    </div>
  )
}

export default HomePage
