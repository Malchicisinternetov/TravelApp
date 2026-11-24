const SESSION_KEY = 'travelapp.session'

export function readSession() {
  try {
    const session = JSON.parse(sessionStorage.getItem(SESSION_KEY))
    if (session?.accessToken && session?.userId && Number.isFinite(session.expiresAt)) return session
  } catch { /* An unavailable or invalid session starts at the login screen. */ }
  sessionStorage.removeItem(SESSION_KEY)
  return null
}

export function saveSession(response) {
  const session = { ...response, expiresAt: Date.now() + response.expiresIn * 1000 }
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  return session
}

export function clearSession() { sessionStorage.removeItem(SESSION_KEY) }

export class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status }
}

const messages = {
  'Invalid username or password': 'Неверный логин или пароль.',
  'Username is already taken': 'Этот логин уже занят.',
  'User is already a trip participant': 'Этот пользователь уже участвует в поездке.',
  'A trip must have at least one owner': 'В поездке должен остаться хотя бы один владелец.',
  'Arrival must be after departure': 'Прибытие должно быть позже отправления с учётом часовых поясов.',
  'Route legs must not overlap and must be ordered': 'Участки маршрута должны идти по порядку и не пересекаться по времени.',
  'Shares must add up to the expense amount': 'Сумма долей должна совпадать с общей суммой расхода.',
  'Amount has too many decimal places for this currency': 'Для этой валюты указано слишком много знаков после запятой.',
  'Payer must be a trip participant': 'Плательщик должен быть участником поездки.',
  'User is not a trip participant': 'У вас больше нет доступа к этой поездке.',
  'Only owners can manage participants or delete trips': 'Это действие доступно только владельцу поездки.',
  'Read-only participants cannot edit trips': 'Ваша роль разрешает только просмотр поездки.',
  'Trip service is unavailable': 'Сервис поездок временно недоступен. Попробуйте позже.',
  'Unsupported currency': 'Эта валюта не поддерживается.',
  'Invalid request body': 'Проверьте формат полей, валюту и часовой пояс.',
  'Data conflicts with an existing record': 'Такая запись уже существует. Проверьте данные.',
}

export function createApi(initialSession, onExpired = () => {}, onSessionUpdated = () => {}) {
  let session = initialSession
  let refreshPromise = null
  let generation = 0
  let active = true

  function acceptSession(response) {
    generation++
    session = response.expiresAt ? response : saveSession(response)
    onSessionUpdated(session)
    return session
  }

  async function renewSession(force = false) {
    if (!active) throw new ApiError('Вы вышли из аккаунта.', 401)
    if (!force && session?.expiresAt > Date.now() + 60000) return session
    if (!refreshPromise) {
      const startedAt = generation
      refreshPromise = request('users', '/auth/refresh', { method: 'POST', publicRequest: true })
        .then(response => {
          if (!active || startedAt !== generation) throw new ApiError('Сессия уже изменилась.', 401)
          if (session && response.userId !== session.userId) {
            onExpired()
            throw new ApiError('В другой вкладке выполнен вход в другой аккаунт. Войдите здесь, чтобы продолжить.', 401)
          }
          return acceptSession(response)
        }).catch(error => { if (active && startedAt === generation && error.status === 401) onExpired(); throw error })
        .finally(() => { refreshPromise = null })
    }
    return refreshPromise
  }

  async function request(service, path, { method = 'GET', body, signal, publicRequest = false, retried = false } = {}) {
    if (!publicRequest) await renewSession()
    let response
    try {
      response = await fetch(`/api/${service}${path}`, {
        method, signal, credentials: 'same-origin',
        headers: {
          Accept: 'application/json',
          ...(service === 'users' && path.startsWith('/auth/') ? { 'X-TravelApp-Client': 'web' } : {}),
          ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
          ...(!publicRequest ? { Authorization: `Bearer ${session.accessToken}` } : {}),
        },
        ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      })
    } catch (error) {
      if (error.name === 'AbortError') throw error
      throw new ApiError('Не удалось связаться с сервером. Проверьте соединение и повторите попытку.', 0)
    }
    const text = await response.text()
    let data
    try { data = text ? JSON.parse(text) : null } catch { data = null }
    if (!response.ok) {
      if (response.status === 401 && !publicRequest && !retried) {
        await renewSession(true)
        return request(service, path, { method, body, signal, retried: true })
      }
      if (response.status === 401 && !publicRequest) onExpired()
      const fallback = {
        400: 'Проверьте заполненные поля.', 401: publicRequest ? 'Неверный логин или пароль.' : 'Сессия истекла. Войдите снова.',
        403: 'Недостаточно прав для этого действия.', 404: 'Запись не найдена.', 409: 'Данные конфликтуют с существующей записью.',
        503: 'Сервис временно недоступен. Попробуйте позже.',
      }[response.status] || 'Не удалось выполнить запрос. Попробуйте ещё раз.'
      const message = data?.message || data?.detail
      throw new ApiError(messages[message] || message || fallback, response.status)
    }
    if (text && data === null) throw new ApiError('Сервер вернул неожиданный ответ. Проверьте настройку API.', 502)
    return data
  }
  return {
    acceptSession,
    renewSession,
    dispose: () => { active = false; generation++ },
    logout: async () => {
      const previous = session
      active = false; generation++
      try {
        await request('users', '/auth/logout', { method: 'POST', publicRequest: true })
        session = null; clearSession()
      } catch (error) { active = true; session = previous; throw error }
    },
    login: body => request('users', '/auth/login', { method: 'POST', body, publicRequest: true }),
    register: body => request('users', '/auth/register', { method: 'POST', body, publicRequest: true }),
    profile: () => request('users', `/users/${session.userId}`),
    updateProfile: body => request('users', `/users/${session.userId}`, { method: 'PUT', body }),
    userSummaries: async userIds => {
      const ids = [...new Set(userIds)]
      const result = []
      for (let start = 0; start < ids.length; start += 500) {
        result.push(...await request('users', '/users/directory', { method: 'POST', body: { userIds: ids.slice(start, start + 500) } }))
      }
      return result
    },
    settings: () => request('users', `/users/${session.userId}/settings`),
    updateSettings: body => request('users', `/users/${session.userId}/settings`, { method: 'PUT', body }),
    telegramCode: () => request('users', '/auth/telegram/code', { method: 'POST' }),
    unlinkTelegram: () => request('users', '/auth/telegram', { method: 'DELETE' }),
    weatherLocations: city => request('weather', `/weather/locations?${new URLSearchParams({ city })}`),
    weather: query => request('weather', `/weather?${new URLSearchParams(query)}`),
    currencies: () => request('currency', '/currencies'),
    rates: base => request('currency', `/currencies/rates?${new URLSearchParams({ base })}`),
    convert: query => request('currency', `/currencies/convert?${new URLSearchParams(query)}`),
    trips: () => request('trips', '/trips'),
    trip: id => request('trips', `/trips/${id}`),
    createTrip: body => request('trips', '/trips', { method: 'POST', body }),
    updateTrip: (id, body) => request('trips', `/trips/${id}`, { method: 'PUT', body }),
    deleteTrip: id => request('trips', `/trips/${id}`, { method: 'DELETE' }),
    addMember: (id, body) => request('trips', `/trips/${id}/participants`, { method: 'POST', body }),
    changeRole: (id, userId, role) => request('trips', `/trips/${id}/participants/${userId}/role`, { method: 'PUT', body: { role } }),
    removeMember: (id, userId) => request('trips', `/trips/${id}/participants/${userId}`, { method: 'DELETE' }),
    expenses: (id, mine = false) => request('expenses', `/trips/${id}/expenses${mine ? '/me' : ''}`),
    summary: (id, mine = false) => request('expenses', `/trips/${id}/expenses/summary${mine ? '/me' : ''}`),
    addExpense: (id, body) => request('expenses', `/trips/${id}/expenses`, { method: 'POST', body }),
    deleteExpense: (id, expenseId) => request('expenses', `/trips/${id}/expenses/${expenseId}`, { method: 'DELETE' }),
  }
}
