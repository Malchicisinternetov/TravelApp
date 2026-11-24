import test, { afterEach, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { createApi, readSession, saveSession, clearSession } from '../src/api.js'
import { localDate, tripPayload, routeFromHash, isPositiveId } from '../src/utils.js'
import { passwordError } from '../src/password.js'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })
const session = { accessToken: 'test-token', userId: 7, expiresAt: Date.now() + 3600000 }
test('weather and currency tabs have independent routes', () => {
  assert.deepEqual(routeFromHash('#/weather'), { page: 'weather' })
  assert.deepEqual(routeFromHash('#/currency'), { page: 'currency' })
})

test('travel tools use authenticated service proxies and encode city and decimal inputs', async () => {
  const calls = intercept()
  const api = createApi(session)
  await api.weatherLocations('Москва&count=100')
  await api.weather({ latitude: -33.86, longitude: 151.21 })
  await api.currencies()
  await api.rates('RUB')
  await api.convert({ from: 'RUB', to: 'USD', amount: '999999999999.123456' })
  const city = new URL(calls[0].url, 'http://test').searchParams
  assert.equal(city.get('city'), 'Москва&count=100')
  assert.equal(city.get('count'), null)
  assert.equal(calls[1].url, '/api/weather/weather?latitude=-33.86&longitude=151.21')
  assert.equal(calls[2].url, '/api/currency/currencies')
  assert.equal(calls[3].url, '/api/currency/currencies/rates?base=RUB')
  assert.equal(new URL(calls[4].url, 'http://test').searchParams.get('amount'), '999999999999.123456')
  calls.forEach(call => assert.equal(call.headers.Authorization, 'Bearer test-token'))
})
beforeEach(() => {
  const storage = new Map()
  globalThis.sessionStorage = { getItem: k => storage.get(k) || null, setItem: (k,v) => storage.set(k,v), removeItem: k => storage.delete(k) }
})
function intercept(result = {}) {
  const calls = []
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return new Response(JSON.stringify(result), { headers: { 'Content-Type': 'application/json' } }) }
  return calls
}

test('registration/login never send a prior token; authenticated requests send Bearer', async () => {
  const calls = intercept()
  const api = createApi(session)
  await api.login({ username: 'user', password: 'test-only-password' })
  await api.register({ username: 'user', password: 'test-only-password', name: 'Test' })
  await api.profile()
  assert.equal(calls[0].headers.Authorization, undefined)
  assert.equal(calls[1].headers.Authorization, undefined)
  assert.equal(calls[2].url, '/api/users/users/7')
  assert.equal(calls[2].headers.Authorization, 'Bearer test-token')
})

test('failed refresh requests notify the app instead of requesting protected data with an expired token', async () => {
  const calls = []
  globalThis.fetch = async url => { calls.push(url); return new Response('', { status: 401 }) }
  let expired = 0
  await assert.rejects(createApi({ ...session, expiresAt: 0 }, () => expired++).trips(), { status: 401 })
  assert.deepEqual(calls, ['/api/users/auth/refresh'])
  globalThis.fetch = async () => new Response('', { status: 401 })
  await assert.rejects(createApi(session, () => expired++).trips(), { status: 401 })
  assert.equal(expired, 2)
})

test('personal expense history and summary use their dedicated backend endpoints', async () => {
  const calls = intercept()
  const api = createApi(session)
  await api.expenses(12, true); await api.summary(12, true)
  assert.deepEqual(calls.map(c => c.url), ['/api/expenses/trips/12/expenses/me', '/api/expenses/trips/12/expenses/summary/me'])
})

test('expense requests preserve decimal strings and only the chosen split contract', async () => {
  const calls = intercept()
  const expense = { amount: '12345678901234.12', currency: 'RUB', description: 'Билеты', paidBy: 7, shares: [{ userId: 7, amount: '12345678901234.12' }] }
  await createApi(session).addExpense(12, expense)
  assert.deepEqual(JSON.parse(calls[0].body), expense)
  assert.equal(JSON.parse(calls[0].body).participantIds, undefined)
})

test('trip, participant and Telegram mutations use backend contracts without internal secrets', async () => {
  const calls = intercept()
  const api = createApi(session)
  await api.updateTrip(3, { name: 'Trip', legs: [] })
  await api.addMember(3, { userId: 8, role: 'MEMBER' })
  await api.changeRole(3, 8, 'READ_ONLY')
  await api.telegramCode(); await api.unlinkTelegram()
  assert.deepEqual(calls.map(c => [c.method, c.url]), [
    ['PUT','/api/trips/trips/3'], ['POST','/api/trips/trips/3/participants'],
    ['PUT','/api/trips/trips/3/participants/8/role'], ['POST','/api/users/auth/telegram/code'], ['DELETE','/api/users/auth/telegram'],
  ])
  assert.equal(JSON.parse(calls[2].body).role, 'READ_ONLY')
  assert.ok(calls.every(c => !('X-Bot-Secret' in c.headers) && !('X-Notification-Secret' in c.headers)))
})

test('errors retain status, translate business validation, and handle offline and invalid JSON', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ message: 'A trip must have at least one owner' }), { status: 409 })
  await assert.rejects(createApi(session).removeMember(1, 7), { status: 409, message: 'В поездке должен остаться хотя бы один владелец.' })
  globalThis.fetch = async () => { throw new TypeError('offline') }
  await assert.rejects(createApi(session).trips(), { status: 0 })
  globalThis.fetch = async () => new Response('<html>proxy error</html>')
  await assert.rejects(createApi(session).trips(), { status: 502 })
  globalThis.fetch = async () => new Response(null, { status: 204 })
  assert.equal(await createApi(session).deleteTrip(1), null)
})

test('tab storage keeps expired access tokens so the app can refresh without losing its page', () => {
  const storage = new Map()
  globalThis.sessionStorage = { getItem: k => storage.get(k) || null, setItem: (k,v) => storage.set(k,v), removeItem: k => storage.delete(k) }
  const result = saveSession({ accessToken: 'token', userId: 3, expiresIn: 60 })
  assert.equal(readSession().userId, 3)
  storage.set('travelapp.session', JSON.stringify({ ...result, expiresAt: 0 }))
  assert.equal(readSession().expiresAt, 0)
  storage.set('travelapp.session', 'invalid'); assert.equal(readSession(), null)
  clearSession(); assert.equal(readSession(), null)
})

test('parallel requests share one refresh and use the new token without clearing their session', async () => {
  const calls = []
  let updates = 0
  let expired = 0
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return new Response(JSON.stringify(url.endsWith('/refresh') ? { accessToken: 'fresh', userId: 7, expiresIn: 3600 } : []))
  }
  const api = createApi({ ...session, expiresAt: 0 }, () => expired++, () => updates++)
  await Promise.all([api.trips(), api.expenses(1), api.summary(1)])
  assert.equal(calls.filter(c => c.url.endsWith('/refresh')).length, 1)
  assert.ok(calls.slice(1).every(c => c.options.headers.Authorization === 'Bearer fresh'))
  assert.equal(calls[0].options.headers['X-TravelApp-Client'], 'web')
  assert.equal(calls[0].options.credentials, 'same-origin')
  assert.equal(updates, 1); assert.equal(expired, 0)
})

test('a server 401 refreshes once and retries the original request body', async () => {
  const calls = []
  let count = 0
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    if (url.endsWith('/refresh')) return new Response(JSON.stringify({ accessToken: 'fresh', userId: 7, expiresIn: 3600 }))
    return count++ === 0 ? new Response('', { status: 401 }) : new Response('{}')
  }
  await createApi(session).addMember(1, { userId: 2, role: 'MEMBER' })
  assert.equal(calls.length, 3)
  assert.equal(calls[0].options.body, calls[2].options.body)
  assert.equal(calls[2].options.headers.Authorization, 'Bearer fresh')
})

test('logout revokes the web credential and cannot be undone by a pending refresh', async () => {
  let finishRefresh
  let updates = 0
  globalThis.fetch = (url) => url.endsWith('/refresh') ? new Promise(resolve => { finishRefresh = resolve }) : Promise.resolve(new Response(null, { status: 204 }))
  const api = createApi({ ...session, expiresAt: 0 }, () => {}, () => updates++)
  const pending = api.renewSession()
  await api.logout()
  finishRefresh(new Response(JSON.stringify({ accessToken: 'late', userId: 7, expiresIn: 3600 })))
  await assert.rejects(pending, { status: 401 })
  assert.equal(updates, 0)
  assert.equal(readSession(), null)
})

test('directory uses the public-name contract and de-duplicates ids', async () => {
  const calls = intercept([{ userId: 2, name: 'Alice' }])
  const result = await createApi(session).userSummaries([2, 2])
  assert.equal(calls[0].url, '/api/users/users/directory')
  assert.deepEqual(JSON.parse(calls[0].body), { userIds: [2] })
  assert.equal(result[0].name, 'Alice')
})

test('registration rules reject weak and obvious passwords, including UTF-8 overflow', () => {
  for (const password of ['12345678', '1234567890', 'OnlyLetters!', 'Password123!', 'Qwerty123456!', `${'П'.repeat(35)}a1!`]) assert.ok(passwordError(password))
  assert.equal(passwordError('Compass!Trip42'), '')
  assert.equal(passwordError('НашПоход2026!'), '')
})

test('editing details preserves all route fields and local times never shift to browser timezone', () => {
  const trip = { name: 'Old', description: 'Desc', destinationPoint: 'Paris', legs: [{ departureTime: '2026-11-20T10:00:00', departureZone: 'Asia/Tokyo' }] }
  assert.deepEqual(tripPayload(trip, { name: 'New' }).legs, trip.legs)
  assert.equal(localDate('2026-11-20T10:00:00', true), '20.11.2026, 10:00')
})

test('hash routes support deep links and reject invalid or unsafe identifiers', () => {
  assert.deepEqual(routeFromHash('#/trips/12/expenses'), { page: 'trip', id: 12, tab: 'expenses' })
  assert.deepEqual(routeFromHash('#/settings'), { page: 'settings' })
  assert.deepEqual(routeFromHash('#/trips/-2'), { page: 'trips' })
  assert.equal(isPositiveId('9007199254740992'), false)
})
