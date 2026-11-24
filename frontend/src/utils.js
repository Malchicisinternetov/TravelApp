export const ROLES = { OWNER: 'Владелец', MEMBER: 'Участник', READ_ONLY: 'Наблюдатель' }
export const CURRENCIES = ['RUB', 'USD', 'EUR', 'GBP', 'TRY', 'AED', 'CNY', 'JPY', 'KZT', 'GEL', 'THB', 'KWD']
export const ZONES = [...new Set(['Europe/Moscow', 'Europe/Istanbul', 'Europe/London', 'Europe/Paris', 'Asia/Dubai', 'Asia/Tbilisi', 'Asia/Almaty', 'Asia/Bangkok', 'Asia/Tokyo', 'America/New_York', 'UTC', ...Intl.supportedValuesOf('timeZone')])]
export function userLabel(id, session, profile) {
  return Number(id) === Number(session.userId) ? `${profile?.name || 'Вы'} (вы)` : profile?.participantNames?.[id] || `Участник #${id}`
}
export function money(amount, currency = 'RUB') {
  try { return new Intl.NumberFormat('ru-RU', { style: 'currency', currency, maximumFractionDigits: 3 }).format(amount) }
  catch { return `${amount} ${currency}` }
}
export function localDate(value, withTime = false) {
  if (!value) return 'Дата пока не указана'
  // A leg contains local wall-clock time. Do not reinterpret it in the browser's zone.
  const [date, time] = value.split('T')
  const [year, month, day] = date.split('-')
  return `${day}.${month}.${year}${withTime && time ? `, ${time.slice(0, 5)}` : ''}`
}
export function tripPayload(trip, changes = {}) {
  return { name: trip.name, description: trip.description || '', destinationPoint: trip.destinationPoint, legs: trip.legs || [], ...changes }
}
export function decimal(value) { return String(value).trim().replace(',', '.') }
export function flightCount(count) {
  const form = new Intl.PluralRules('ru').select(count)
  return `${count} ${{ one: 'перелёт', few: 'перелёта', many: 'перелётов', other: 'перелёта' }[form]}`
}
export function isPositiveId(value) { return /^[1-9]\d*$/.test(String(value)) && Number.isSafeInteger(Number(value)) }
export function currencyStep(currency) {
  try { const digits = new Intl.NumberFormat('ru', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits; return digits ? `0.${'0'.repeat(digits - 1)}1` : '1' }
  catch { return '0.01' }
}
export function routeFromHash(hash = window.location.hash) {
  const match = hash.match(/^#\/trips\/([1-9]\d*)(?:\/(route|members|expenses|summary))?$/)
  if (match && isPositiveId(match[1])) return { page: 'trip', id: Number(match[1]), tab: match[2] || 'route' }
  if (hash === '#/settings') return { page: 'settings' }
  return { page: 'trips' }
}
