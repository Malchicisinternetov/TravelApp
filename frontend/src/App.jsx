import { useCallback, useEffect, useMemo, useState } from 'react'
import { clearSession, createApi, readSession } from './api'
import { AppContext, useRemote } from './state'
import { Alert, Avatar, Button, Field, Form, Icon, Loading, Modal } from './components'
import { routeFromHash } from './utils'
import { PASSWORD_HINT, passwordError } from './password'
import AccountMenu from './AccountMenu'
import Trips from './Trips'
import TripDetail from './TripDetail'
import Settings from './Settings'
import Weather from './Weather'
import Currency from './Currency'
import './App.css'
import './travel-tools.css'

function Auth({ onLogin, notice }) {
  const [mode, setMode] = useState('login')
  const [password, setPassword] = useState('')
  const api = useMemo(() => createApi(null), [])
  return <div className="auth-layout"><section className="auth-story"><a className="brand" href="#/"><span className="brand-symbol"><Icon size={26}/></span>попутно<span className="brand-dot">.</span></a><div className="auth-story-content"><span className="eyebrow">ВАШ ЛИЧНЫЙ ТРЕВЕЛ-АССИСТЕНТ</span><h1>Больше впечатлений.<br/><em>Меньше забот.</em></h1><p>От первого билета до последнего общего счёта — всё путешествие в одном месте.</p><div className="ticket-art"><div className="ticket-top"><span>ВАШ СЛЕДУЮЩИЙ МАРШРУТ</span><Icon name="plane"/></div><div className="ticket-route"><strong>Отсюда</strong><span className="flight-line"><Icon name="plane"/></span><strong>К мечте</strong></div><div className="ticket-dashed"/><div className="ticket-bottom"><span>МАРШРУТ<br/><b>Только ваш</b></span><span>ПОПУТЧИКИ<br/><b>Свои люди</b></span><span className="ticket-barcode"/></div></div><div className="story-features"><span><Icon name="pin"/>Поездки и маршруты</span><span><Icon name="wallet"/>Общие расходы</span><span><Icon name="bell"/>Напоминания в Telegram</span></div></div><div className="auth-footer">Планы становятся путешествиями.</div></section><main className="auth-main"><div className="auth-form"><div className="auth-mobile-brand"><Icon/> попутно.</div><span className="eyebrow teal">НАЧНЁМ ПУТЕШЕСТВИЕ</span><h2>{mode === 'login' ? 'С возвращением!' : 'Впереди целый мир'}</h2><p>{mode === 'login' ? 'Войдите, чтобы продолжить строить планы.' : 'Создайте аккаунт и соберите свою первую поездку.'}</p><div className="segmented auth-tabs"><button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => { setMode('login'); setPassword('') }}>Вход</button><button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => { setMode('register'); setPassword('') }}>Регистрация</button></div><Alert kind="info">{notice}</Alert><Form key={mode} submit={mode === 'login' ? 'Войти в аккаунт →' : 'Создать аккаунт →'} onSubmit={async data => {
    const body = Object.fromEntries(data)
    if (mode === 'register' && passwordError(body.password)) throw new Error(passwordError(body.password))
    onLogin(await (mode === 'login' ? api.login(body) : api.register(body)))
  }}>
    {mode === 'register' && <Field label="Как вас зовут" name="name" placeholder="Введите имя" maxLength={255} autoComplete="name" required/>}
    <Field label="Логин" name="username" placeholder="Ваш логин" autoComplete="username" pattern="[a-zA-Z0-9_.\-]{3,50}" minLength={3} maxLength={50} hint={mode === 'register' ? '3–50 символов: латинские буквы, цифры, точка, дефис или _' : null} required/>
    <Field label="Пароль" name="password" type="password" placeholder={mode === 'register' ? 'Придумайте надёжный пароль' : 'Введите пароль'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={mode === 'register' ? 10 : undefined} maxLength={72} value={password} onChange={event => setPassword(event.target.value)} hint={mode === 'register' ? PASSWORD_HINT : null} required/>
    {mode === 'register' && password && <p className={`password-status ${passwordError(password) ? 'negative' : 'positive'}`} role="status">{passwordError(password) || 'Пароль подходит'}</p>}
    </Form><div className="auth-note"><Icon name="shield" size={18}/><span>Один аккаунт для сайта и Telegram-бота.<br/>Привязать Telegram можно после входа.</span></div></div></main></div>
}

export default function App() {
  const [session, setSession] = useState(readSession)
  const [booting, setBooting] = useState(!session)
  const [expired, setExpired] = useState(false)
  const [reauth, setReauth] = useState(false)
  const [route, setRoute] = useState(routeFromHash)
  const [menu, setMenu] = useState(false)
  const [toast, setToast] = useState('')
  const [theme, setTheme] = useState('WHITE')
  const onExpired = useCallback(() => setExpired(true), [])
  const onUpdated = useCallback(value => { setSession(value); setExpired(false) }, [])
  // The client keeps its current token internally. Refreshing it must not remount forms.
  const api = useMemo(() => createApi(session, onExpired, onUpdated), [session?.userId, onExpired, onUpdated])
  useEffect(() => {
    if (session) return
    let active = true
    api.renewSession(true).catch(() => {}).finally(() => { if (active) setBooting(false) })
    return () => { active = false }
  }, [])
  useEffect(() => { const changed = () => { setRoute(routeFromHash()); setMenu(false); window.scrollTo(0, 0) }; window.addEventListener('hashchange', changed); return () => window.removeEventListener('hashchange', changed) }, [])
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => setToast(''), 4500); return () => clearTimeout(timer) }, [toast])
  useEffect(() => { document.documentElement.dataset.theme = theme === 'DARK' || theme === 'BLACK' ? 'dark' : 'light' }, [theme])
  useEffect(() => {
    if (!session) return
    const refresh = () => { if (document.visibilityState === 'visible') api.renewSession().catch(() => {}) }
    const timer = setTimeout(refresh, Math.max(1000, session.expiresAt - Date.now() - 60000))
    window.addEventListener('focus', refresh)
    document.addEventListener('visibilitychange', refresh)
    return () => { clearTimeout(timer); window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh) }
  }, [api, session])
  const loggedIn = response => { api.acceptSession(response); setReauth(false); setExpired(false); setBooting(false) }
  const logout = async () => {
    try { await api.logout() }
    catch { setToast('Не удалось завершить сессию на сервере. Проверьте соединение и повторите выход.'); return }
    clearSession(); setSession(null); setTheme('WHITE'); setExpired(false); setReauth(false); window.location.hash = '#/trips'
  }
  if (booting && !session) return <Loading label="Открываем ваше пространство…"/>
  if (!session) return <Auth onLogin={loggedIn}/>
  return <><Workspace key={session.userId} {...{ session, api, route, menu, setMenu, logout, toast, setToast, setTheme, expired }} onReauth={() => setReauth(true)}/>
    {reauth && <Modal title="Продолжить работу" subtitle="Открытая страница и заполненные формы сохранятся." onClose={() => setReauth(false)}><Form submit="Войти" onCancel={() => setReauth(false)} onSubmit={async data => { loggedIn(await api.login(Object.fromEntries(data))) }}><Field label="Логин" name="username" autoComplete="username" required/><Field label="Пароль" name="password" type="password" autoComplete="current-password" required/></Form></Modal>}
  </>
}

function Workspace({ session, api, route, menu, setMenu, logout, toast, setToast, setTheme, expired, onReauth }) {
  const account = useRemote(async () => { const [profile, settings] = await Promise.all([api.profile(), api.settings()]); return { profile, settings } }, [api])
  const [names, setNames] = useState({})
  const rememberUsers = useCallback(users => setNames(previous => ({ ...previous, ...Object.fromEntries(users.map(user => [user.userId, user.name])) })), [])
  useEffect(() => { if (account.data?.settings?.theme) setTheme(account.data.settings.theme) }, [account.data?.settings?.theme, setTheme])
  const profile = account.data?.profile ? { ...account.data.profile, participantNames: names } : null
  const connected = account.data?.settings?.telegramVerified && account.data?.settings?.telegramId
  const value = { api, session, profile, settings: account.data?.settings, refreshAccount: account.reload, rememberUsers, sessionExpired: expired, requestLogin: onReauth, notify: setToast, logout, setTheme }
  return <AppContext.Provider value={value}><a className="skip-link" href="#main-content" onClick={event => { event.preventDefault(); document.getElementById('main-content')?.focus() }}>К содержимому</a><div className="app-shell">
    {menu && <button className="sidebar-overlay" aria-label="Закрыть меню" onClick={() => setMenu(false)}/>}
    <aside className={`sidebar ${menu ? 'open' : ''}`}><a href="#/trips" className="brand"><span className="brand-symbol"><Icon size={25}/></span>попутно<span className="brand-dot">.</span></a><div className="nav-label">ВАШЕ ПРОСТРАНСТВО</div><nav aria-label="Основная навигация"><a href="#/trips" className={['trips', 'trip'].includes(route.page) ? 'nav-link active' : 'nav-link'}><Icon name="compass"/>Мои поездки<Icon name="arrow" size={16}/></a><a href="#/weather" className={route.page === 'weather' ? 'nav-link active' : 'nav-link'}><Icon name="sun"/>Погода</a><a href="#/currency" className={route.page === 'currency' ? 'nav-link active' : 'nav-link'}><Icon name="exchange"/>Курсы валют</a><a href="#/settings" className={route.page === 'settings' ? 'nav-link active' : 'nav-link'}><Icon name="settings"/>Профиль и настройки</a></nav>{!connected && <div className="sidebar-tip"><span className="tip-icon"><Icon name="plane" size={23}/></span><h4>Планы всегда под рукой</h4><p>Подключите Telegram — и напоминание о вылете придёт прямо в чат.</p><a href="#/settings">Подключить бота <Icon name="arrow" size={15}/></a></div>}<div className={`sidebar-bottom ${connected ? 'without-tip' : ''}`}><a href="#/settings" className="sidebar-user"><Avatar name={profile?.name || '?'}/><span><strong>{profile?.name || 'Мой аккаунт'}</strong><small>ID {session.userId}</small></span></a><button className="icon-button" title="Выйти из аккаунта" aria-label="Выйти из аккаунта" onClick={logout}><Icon name="logout"/></button></div></aside>
    <div className="workspace"><header className="topbar"><div className="topbar-left"><button className="icon-button mobile-menu" aria-label="Открыть меню" onClick={() => setMenu(true)}><Icon name="menu"/></button><span>Личный кабинет</span><span className="breadcrumb-separator">/</span><strong>{route.page === 'weather' ? 'Погода' : route.page === 'currency' ? 'Курсы валют' : route.page === 'settings' ? 'Профиль и настройки' : route.page === 'trip' ? 'Моя поездка' : 'Мои поездки'}</strong></div><AccountMenu {...{ profile, session, logout }}/></header><main id="main-content" className="main-content" tabIndex={-1}>
      {expired && <div className="session-notice" role="status"><span>Не удалось продлить вход. Ваши данные и формы остаются на странице.</span><Button variant="secondary" onClick={onReauth}>Продолжить работу</Button></div>}
      {account.loading ? <Loading label="Открываем ваше пространство…"/> : account.error ? <div className="panel"><Alert>{account.error}</Alert><Button variant="secondary" onClick={account.reload}>Повторить загрузку</Button>{expired && <Button variant="ghost" onClick={onReauth}>Войти</Button>}</div> : route.page === 'weather' ? <Weather/> : route.page === 'currency' ? <Currency/> : route.page === 'settings' ? <Settings/> : route.page === 'trip' ? <TripDetail key={route.id} id={route.id} tab={route.tab}/> : <Trips/>}
    </main><footer className="workspace-footer"><span>попутно. — всё складывается в путешествие</span><span>Планируйте. Делитесь. Отправляйтесь.</span></footer></div>
    {toast && <div className="toast" role="status"><Icon name="check"/>{toast}<button className="icon-button" aria-label="Закрыть уведомление" onClick={() => setToast('')}><Icon name="close" size={16}/></button></div>}
  </div></AppContext.Provider>
}
