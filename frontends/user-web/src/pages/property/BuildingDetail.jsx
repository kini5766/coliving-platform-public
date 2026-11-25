import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { getBuilding } from './propertyApi'
import { errorMessage, formatWon, formatYesNo, roomTypeLabel } from './format'
import { AvailabilityBadge, BuildingImage, FacilityList, InfoRow, PageShell, Section, StatusMessage } from './PropertyUi'
function BuildingDetail() {
  const { t, i18n } = useTranslation('property')
  const locale = i18n.resolvedLanguage
  const { buildingId } = useParams()
  const [building, setBuilding] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => {
    let cancelled = false
    setBuilding(null)
    setError(null)
    getBuilding(Number(buildingId))
      .then((res) => !cancelled && setBuilding(res))
      .catch((e) => !cancelled && setError(e))
    return () => {
      cancelled = true
    }
  }, [buildingId])
  if (error)
    return (
      <PageShell>
        <StatusMessage>{errorMessage(error, t)}</StatusMessage>
      </PageShell>
    )
  if (!building)
    return (
      <PageShell>
        <StatusMessage>{t('common.loading')}</StatusMessage>
      </PageShell>
    )
  return (
    <PageShell>
      <Link to="/property/buildings" className="text-sm text-main">
        {t('building.back')}
      </Link>
      <BuildingImage url={building.imageUrl} alt={building.name} className="mt-3 aspect-video w-full rounded-xl md:aspect-[21/9]" />
      <h1 className="mt-4 text-2xl font-bold">{building.name}</h1>
      <p className="mt-1 text-sm text-text/60">{building.address}</p>

      <Section title={t('building.info')}>
        <dl>
          <InfoRow label={t('building.region')} value={building.region} />
          <InfoRow label={t('building.type')} value={building.buildingType} />
          <InfoRow label={t('building.totalFloors')} value={t('building.floorsValue', { n: building.totalFloors })} />
          <InfoRow label={t('building.parking')} value={formatYesNo(building.parkingAvailable, t)} />
          <InfoRow label={t('building.elevator')} value={formatYesNo(building.elevatorAvailable, t)} />
          <InfoRow label={t('building.contact')} value={building.contactPhone ?? '-'} />
        </dl>
      </Section>

      {building.introduction && (
        <Section title={t('building.introduction')}>
          <p className="whitespace-pre-line text-sm leading-relaxed">{building.introduction}</p>
        </Section>
      )}

      <Section title={t('building.facilities')}>
        <FacilityList facilities={building.facilities} />
      </Section>

      <Section title={t('building.rooms', { n: building.rooms.length })}>
        {building.rooms.length === 0 ? (
          <p className="text-sm text-text/60">{t('building.noRooms')}</p>
        ) : (
          <ul className="divide-y divide-background">
            {building.rooms.map((r) => (
              <li key={r.id} className="flex items-center justify-between gap-3 py-3">
                <Link to={`/property/rooms/${r.id}`} className="min-w-0 flex-1 hover:opacity-80">
                  <p className="font-semibold">
                    {r.roomName} <AvailabilityBadge available={r.available} />
                  </p>
                  <p className="text-xs text-text/60">
                    {[
                      roomTypeLabel(r.roomType, t),
                      t('building.floorValue', { n: r.floor }),
                      `${r.areaM2}㎡`,
                      t('building.capacityValue', { n: r.capacity }),
                    ].join(' · ')}
                  </p>
                </Link>
                <div className="text-right text-sm">
                  <p className="font-semibold text-main">{t('building.monthly', { fee: formatWon(r.monthlyFee, locale) })}</p>
                  <p className="text-xs text-text/60">{t('building.deposit', { fee: formatWon(r.deposit, locale) })}</p>
                </div>
                <Link
                  to={`/apply/room?roomId=${r.id}`}
                  className="shrink-0 rounded-lg bg-main px-3 py-1.5 text-xs font-semibold text-white hover:opacity-90"
                >
                  {t('building.apply')}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Section>
    </PageShell>
  )
}
export { BuildingDetail as default }