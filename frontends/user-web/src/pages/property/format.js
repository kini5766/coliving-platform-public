// 금액·표시 형식 도우미. 언어(locale)에 따라 표기가 달라집니다.
function formatWon(value, locale) {
  if (value === null || value === void 0) return '-'
  if (locale === 'en') return `₩${value.toLocaleString('en-US')}`
  if (value === 0) return '0원'
  if (value % 1e4 === 0) return `${(value / 1e4).toLocaleString('ko-KR')}만원`
  return `${value.toLocaleString('ko-KR')}원`
}
function formatNumber(value, locale) {
  return value.toLocaleString(locale === 'en' ? 'en-US' : 'ko-KR')
}
function leaseTypeLabel(code, t) {
  return code ? t(`leaseTypes.${code}`, { defaultValue: code }) : '-'
}
function roomTypeLabel(type, t) {
  return type ? t(`roomTypes.${type}`, { defaultValue: type }) : '-'
}
function formatYesNo(value, t) {
  return value ? t('common.yes') : t('common.no')
}
function errorMessage(error, t) {
  return error && error.status ? t('errors.requestFailed', { status: error.status }) : t('errors.network')
}
function groupByCategory(items) {
  const map = /* @__PURE__ */ new Map()
  items.forEach((item) => {
    map.set(item.category, [...(map.get(item.category) ?? []), item])
  })
  return map
}
export { errorMessage, formatNumber, formatWon, formatYesNo, groupByCategory, leaseTypeLabel, roomTypeLabel }