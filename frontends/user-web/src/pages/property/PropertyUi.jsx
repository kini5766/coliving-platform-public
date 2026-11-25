import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import LocaleSwitch from '../../components/LocaleSwitch'
import { groupByCategory } from './format'
import { imageSrc } from './propertyApi'
function PageShell({ children }) {
  return (
    <div className="min-h-screen bg-background text-text">
      <div className="mx-auto max-w-5xl px-4 py-8">
        <div className="mb-4 flex justify-end">
          <LocaleSwitch />
        </div>
        {children}
      </div>
    </div>
  )
}
function Section({ title, children }) {
  return (
    <section className="mt-6 rounded-xl bg-white p-5 shadow-sm">
      <h2 className="mb-3 text-lg font-bold">{title}</h2>
      {children}
    </section>
  )
}
function InfoRow({ label, value }) {
  return (
    <div className="flex border-b border-background py-2 text-sm last:border-0">
      <dt className="w-32 shrink-0 text-text/60">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  )
}
function StatusMessage({ children }) {
  return <p className="py-16 text-center text-sm text-text/60">{children}</p>
}
function AvailabilityBadge({ available }) {
  const { t } = useTranslation('property')
  if (available === null) return null
  return available ? (
    <span className="rounded-full bg-main px-2 py-0.5 text-xs text-white">{t('common.available')}</span>
  ) : (
    <span className="rounded-full bg-accent px-2 py-0.5 text-xs text-white">{t('common.unavailable')}</span>
  )
}
function FacilityList({ facilities }) {
  const { t } = useTranslation('property')
  if (facilities.length === 0) return <p className="text-sm text-text/60">{t('common.noFacilities')}</p>
  return (
    <div className="space-y-3">
      {[...groupByCategory(facilities)].map(([category, items]) => (
        <div key={category} className="flex gap-3 text-sm">
          <span className="w-24 shrink-0 font-semibold text-main">{category}</span>
          <span className="flex flex-wrap gap-1.5">
            {items.map((f) => (
              <span key={`${category}-${f.name}`} className="rounded-full bg-background px-2.5 py-0.5">
                {f.name}
              </span>
            ))}
          </span>
        </div>
      ))}
    </div>
  )
}
// 건물 대표 이미지. 사진이 없거나 불러오지 못하면 회색 칸에 안내 문구를 보여 준다.
function BuildingImage({ url, alt, className = '' }) {
  const { t } = useTranslation('property')
  const src = imageSrc(url)
  const [failedSrc, setFailedSrc] = useState(null)
  const showImage = src && failedSrc !== src
  return (
    <div className={`flex items-center justify-center overflow-hidden bg-background ${className}`}>
      {showImage ? (
        <img src={src} alt={alt} loading="lazy" className="h-full w-full object-cover" onError={() => setFailedSrc(src)} />
      ) : (
        <span className="text-xs text-text/50">{t('common.noImage')}</span>
      )}
    </div>
  )
}
export { AvailabilityBadge, BuildingImage, FacilityList, InfoRow, PageShell, Section, StatusMessage }