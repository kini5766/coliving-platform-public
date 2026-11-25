import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { getRoom } from './propertyApi'
import { errorMessage, formatWon, formatYesNo, leaseTypeLabel, roomTypeLabel } from './format'
import { AvailabilityBadge, BuildingImage, FacilityList, InfoRow, PageShell, Section, StatusMessage } from './PropertyUi'
function RoomDetail() {
  const { t, i18n } = useTranslation('property')
  const locale = i18n.resolvedLanguage
  const { roomId } = useParams()
  const [room, setRoom] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => {
    let cancelled = false
    setRoom(null)
    setError(null)
    getRoom(Number(roomId))
      .then((res) => !cancelled && setRoom(res))
      .catch((e) => !cancelled && setError(e))
    return () => {
      cancelled = true
    }
  }, [roomId])
  if (error)
    return (
      <PageShell>
        <StatusMessage>{errorMessage(error, t)}</StatusMessage>
      </PageShell>
    )
  if (!room)
    return (
      <PageShell>
        <StatusMessage>{t('common.loading')}</StatusMessage>
      </PageShell>
    )
  return (
    <PageShell>
      <Link to={`/property/buildings/${room.buildingId}`} className="text-sm text-main">
        ← {room.buildingName}
      </Link>
      <BuildingImage url={room.buildingImageUrl} alt={room.buildingName} className="mt-3 aspect-video w-full rounded-xl md:aspect-[21/9]" />
      <h1 className="mt-4 text-2xl font-bold">
        {room.buildingName} {room.roomName} <AvailabilityBadge available={room.available} />
      </h1>

      <Section title={t('room.conditions')}>
        <dl>
          <InfoRow label={t('room.leaseType')} value={leaseTypeLabel(room.leaseType, t)} />
          <InfoRow label={t('room.monthlyFee')} value={formatWon(room.monthlyFee, locale)} />
          <InfoRow label={t('room.deposit')} value={formatWon(room.deposit, locale)} />
          <InfoRow label={t('room.managementFee')} value={formatWon(room.managementFee, locale)} />
        </dl>
      </Section>

      <Section title={t('room.roomInfo')}>
        <dl>
          <InfoRow label={t('room.roomType')} value={roomTypeLabel(room.roomType, t)} />
          <InfoRow label={t('room.roomStructure')} value={room.roomStructure ?? '-'} />
          <InfoRow label={t('room.floor')} value={t('building.floorValue', { n: room.floor })} />
          <InfoRow label={t('room.area')} value={`${room.areaM2}㎡`} />
          <InfoRow label={t('room.capacity')} value={t('building.capacityValue', { n: room.capacity })} />
        </dl>
      </Section>

      <Section title={t('room.buildingInfo')}>
        <dl>
          <InfoRow label={t('building.type')} value={room.buildingType} />
          <InfoRow label={t('building.parking')} value={formatYesNo(room.parkingAvailable, t)} />
          <InfoRow label={t('building.elevator')} value={formatYesNo(room.elevatorAvailable, t)} />
          <InfoRow label={t('building.contact')} value={room.contactPhone ?? '-'} />
        </dl>
      </Section>

      <Section title={t('building.facilities')}>
        <FacilityList facilities={room.facilities} />
      </Section>
    </PageShell>
  )
}
export { RoomDetail as default }