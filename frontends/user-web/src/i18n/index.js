/**
 * @author 조휘재
 * 작성일: 2026-10-18
 */

import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import enCommon from './locales/en/common.json'
import enLogin from './locales/en/login.json'
import enSignup from './locales/en/signup.json'
import enProperty from './locales/en/property.json'
import enCommunity from './locales/en/community.json'
import enSettings from './locales/en/settings.json'
import enProfile from './locales/en/profile.json'
import enHome from './locales/en/home.json'
import enPasswordReset from './locales/en/passwordReset.json'
import koCommon from './locales/ko/common.json'
import koLogin from './locales/ko/login.json'
import koSignup from './locales/ko/signup.json'
import koProperty from './locales/ko/property.json'
import koCommunity from './locales/ko/community.json'
import koSettings from './locales/ko/settings.json'
import koProfile from './locales/ko/profile.json'
import koHome from './locales/ko/home.json'
import koPasswordReset from './locales/ko/passwordReset.json'

export const LOCALES = ['ko', 'en']

const STORAGE_KEY = 'locale'
const BOUND = Symbol.for('coliving.localeBound')

/** 저장한 언어, 없으면 브라우저 언어, 둘 다 아니면 한국어를 고른다. */
export function detectLocale() {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (LOCALES.includes(stored)) return stored

  const browser = navigator.language?.toLowerCase() ?? ''
  return LOCALES.find((locale) => browser.startsWith(locale)) ?? 'ko'
}

/** document 언어를 ko 또는 en 으로 맞춘다. */
function applyDocumentLocale(locale) {
  document.documentElement.lang = LOCALES.includes(locale) ? locale : 'ko'
}

/** 고른 언어를 저장하고 document 언어에 반영한다. 모르는 값은 한국어로 둔다. */
function persistLocale(locale) {
  const next = LOCALES.includes(locale) ? locale : 'ko'
  localStorage.setItem(STORAGE_KEY, next)
  applyDocumentLocale(next)
}

/** 번역을 한 번만 붙인다. 이미 켜져 있으면 언어만 맞추고, 언어가 바뀌면 저장한다. */
export async function initI18n() {
  const locale = detectLocale()
  applyDocumentLocale(locale)

  if (!i18n[BOUND]) {
    i18n[BOUND] = true
    i18n.on('languageChanged', persistLocale)
  }

  if (i18n.isInitialized) {
    if (i18n.resolvedLanguage !== locale) await i18n.changeLanguage(locale)
    return i18n
  }

  await i18n.use(initReactI18next).init({
    resources: {
      ko: { common: koCommon, login: koLogin, signup: koSignup, community: koCommunity, settings: koSettings, profile: koProfile, property: koProperty, home: koHome, passwordReset: koPasswordReset },
      en: { common: enCommon, login: enLogin, signup: enSignup, community: enCommunity, settings: enSettings, profile: enProfile, property: enProperty, home: enHome, passwordReset: enPasswordReset },
    },
    lng: locale,
    fallbackLng: 'ko',
    supportedLngs: LOCALES,
    ns: ['common', 'login', 'signup', 'community', 'settings', 'profile', 'property', 'home', 'passwordReset'],
    defaultNS: 'common',
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
  })

  return i18n
}

export default i18n
