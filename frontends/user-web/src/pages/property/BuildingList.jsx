import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { searchBuildings } from './propertyApi'
import { errorMessage, formatNumber, formatWon, roomTypeLabel } from './format'
import { BuildingImage, PageShell, StatusMessage } from './PropertyUi'
const PAGE_SIZE = 12
// 값(한국어)은 서버로 보내는 검색 값이고, 화면 표시 이름만 언어별로 바뀝니다.
const ROOM_TYPES = ['원룸', '투룸', '쉐어룸', '도미토리']
const EMPTY_FILTER = {
  keyword: '',
  region: '',
  minMonthlyFee: '',
  maxMonthlyFee: '',
  maxDeposit: '',
  roomType: '',
}
const toNumber = (v) => (v.trim() === '' ? void 0 : Number(v) * 1e4)
function BuildingList() {
  const { t, i18n } = useTranslation('property')
  const locale = i18n.resolvedLanguage
  const [form, setForm] = useState(EMPTY_FILTER)
  const [applied, setApplied] = useState({})
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    searchBuildings({ ...applied, page, size: PAGE_SIZE })
      .then((res) => !cancelled && setData(res))
      .catch((e) => !cancelled && setError(e))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [applied, page])
  const onSubmit = (e) => {
    e.preventDefault()
    setPage(0)
    setApplied({
      keyword: form.keyword.trim() || void 0,
      region: form.region.trim() || void 0,
      minMonthlyFee: toNumber(form.minMonthlyFee),
      maxMonthlyFee: toNumber(form.maxMonthlyFee),
      maxDeposit: toNumber(form.maxDeposit),
      roomType: form.roomType || void 0,
    })
  }
  const onReset = () => {
    setForm(EMPTY_FILTER)
    setApplied({})
    setPage(0)
  }
  const set = (key) => (value) => setForm((f) => ({ ...f, [key]: value }))
  const input = 'w-full rounded-lg border border-text/15 bg-white px-3 py-2 text-sm outline-none focus:border-main'
  return (
    <PageShell>
      <h1 className="text-2xl font-bold">{t('list.title')}</h1>

      <form onSubmit={onSubmit} className="mt-5 grid gap-3 rounded-xl bg-white p-5 shadow-sm md:grid-cols-4">
        <input
          className={`${input} md:col-span-2`}
          placeholder={t('list.keywordPlaceholder')}
          value={form.keyword}
          onChange={(e) => set('keyword')(e.target.value)}
        />
        <input
          className={input}
          placeholder={t('list.regionPlaceholder')}
          value={form.region}
          onChange={(e) => set('region')(e.target.value)}
        />
        <select className={input} value={form.roomType} onChange={(e) => set('roomType')(e.target.value)}>
          <option value="">{t('list.roomTypeAll')}</option>
          {ROOM_TYPES.map((type) => (
            <option key={type} value={type}>
              {roomTypeLabel(type, t)}
            </option>
          ))}
        </select>
        <input
          className={input}
          type="number"
          min={0}
          placeholder={t('list.minMonthly')}
          value={form.minMonthlyFee}
          onChange={(e) => set('minMonthlyFee')(e.target.value)}
        />
        <input
          className={input}
          type="number"
          min={0}
          placeholder={t('list.maxMonthly')}
          value={form.maxMonthlyFee}
          onChange={(e) => set('maxMonthlyFee')(e.target.value)}
        />
        <input
          className={input}
          type="number"
          min={0}
          placeholder={t('list.maxDeposit')}
          value={form.maxDeposit}
          onChange={(e) => set('maxDeposit')(e.target.value)}
        />
        <div className="flex gap-2">
          <button type="submit" className="flex-1 rounded-lg bg-main px-4 py-2 text-sm font-semibold text-white hover:opacity-90">
            {t('list.search')}
          </button>
          <button type="button" onClick={onReset} className="rounded-lg border border-main px-4 py-2 text-sm text-main">
            {t('list.reset')}
          </button>
        </div>
      </form>

      {loading && <StatusMessage>{t('common.loading')}</StatusMessage>}
      {error && <StatusMessage>{errorMessage(error, t)}</StatusMessage>}
      {!loading && !error && data && data.content.length === 0 && <StatusMessage>{t('list.empty')}</StatusMessage>}

      {!loading && !error && data && data.content.length > 0 && (
        <>
          <p className="mt-6 text-sm text-text/60">{t('list.total', { n: formatNumber(data.totalElements, locale) })}</p>
          <ul className="mt-2 grid gap-4 md:grid-cols-2">
            {data.content.map((b) => (
              <li key={b.id}>
                <Link
                  to={`/property/buildings/${b.id}`}
                  className="block overflow-hidden rounded-xl bg-white shadow-sm transition hover:shadow-md"
                >
                  <BuildingImage url={b.imageUrl} alt={b.name} className="aspect-video w-full" />
                  <div className="p-5">
                    <h2 className="text-lg font-bold">{b.name}</h2>
                    <p className="mt-1 text-sm text-text/60">{b.address}</p>
                    <div className="mt-4 flex items-end justify-between">
                      <div>
                        <p className="text-xs text-text/60">{t('list.lowestLabel')}</p>
                        <p className="font-semibold text-main">
                          {formatWon(b.lowestMonthlyFee, locale)} / {formatWon(b.lowestDeposit, locale)}
                        </p>
                      </div>
                      {b.availableRoomCount !== null && (
                        <span className="rounded-full bg-accent px-2.5 py-0.5 text-xs text-white">
                          {t('list.availableRooms', { n: b.availableRoomCount })}
                        </span>
                      )}
                    </div>
                  </div>
                </Link>
              </li>
            ))}
          </ul>

          <nav className="mt-8 flex items-center justify-center gap-3 text-sm">
            <button
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
              className="rounded-lg border border-main px-3 py-1.5 text-main disabled:opacity-40"
            >
              {t('list.prev')}
            </button>
            <span>
              {page + 1} / {Math.max(data.totalPages, 1)}
            </span>
            <button
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-lg border border-main px-3 py-1.5 text-main disabled:opacity-40"
            >
              {t('list.next')}
            </button>
          </nav>
        </>
      )}
    </PageShell>
  )
}
export { BuildingList as default }