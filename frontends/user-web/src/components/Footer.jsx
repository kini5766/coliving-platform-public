/**
 * @author 조휘재
 * 작성일: 2026-10-27
 */

import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

/** 로고, 서비스 설명, 개인정보 처리 방침, 올해 저작권 표시. */
function Footer() {
  const { t, i18n } = useTranslation('common')
  const year = new Intl.DateTimeFormat(i18n.language, { year: 'numeric' })
    .formatToParts(new Date())
    .find((part) => part.type === 'year').value

  return (
    <footer className="border-t border-main/15 px-4 py-4 sm:px-8 md:px-12">
      <Link to="/" className="inline-block focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent">
        <img src="/roomlink-logo.svg" alt={t('serviceName')} className="block h-8 w-auto" />
      </Link>
      <p className="mt-1 text-xs font-medium text-main/80">{t('serviceNameEn')}</p>
      <p className="mt-1 text-xs text-text/70 sm:text-sm">{t('serviceDescription')}</p>
      <p className="mt-3">
        <Link
          to="/privacy"
          className="text-xs text-text/70 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        >
          {t('privacy')}
        </Link>
      </p>
      <p className="mt-3 text-xs text-text/60">{t('copyright', { year })}</p>
    </footer>
  )
}

export default Footer
