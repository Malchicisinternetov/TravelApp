import { useEffect, useState } from 'react'
import { Alert, Button, Confirm, CurrencyInput, Field, Form, Icon } from './components'
import { useApp } from './state'
import { decimal, ZONES } from './utils'

function Telegram() {
  const { api, settings, refreshAccount, notify } = useApp()
  const [code, setCode] = useState(null)
  const [now, setNow] = useState(Date.now())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [confirm, setConfirm] = useState(false)
  useEffect(() => { if (!code) return; const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer) }, [code])
  const remaining = code ? Math.max(0, Math.ceil((code.expiresAt - now) / 1000)) : 0
  const botName = (import.meta.env.VITE_TELEGRAM_BOT_USERNAME || '').replace(/^@/, '')
  const botUrl = /^[a-zA-Z0-9_]+$/.test(botName) ? `https://t.me/${botName}${code && remaining ? `?start=${encodeURIComponent(code.code)}` : ''}` : null
  const connected = settings.telegramVerified === true && Boolean(settings.telegramId)
  async function generate() {
    setBusy(true); setError('')
    try { const result = await api.telegramCode(); setCode({ ...result, expiresAt: Date.now() + result.expiresIn * 1000 }); setNow(Date.now()) }
    catch (err) { setError(err.message) }
    finally { setBusy(false) }
  }
  return <section className="panel settings-section telegram-section" id="telegram">
    <div className="panel-heading"><div className="settings-title"><span className="stat-icon telegram-bg"><Icon name="plane"/></span><div><h2>Telegram рядом</h2><p>Те же поездки и расходы — прямо в чате.</p></div></div><span className={`pill ${connected ? 'role-owner' : ''}`}>{connected ? 'Подключён' : 'Не подключён'}</span></div>
    <p className="section-copy">Бот поможет управлять поездками и напомнит о каждом вылете за 3 часа. Привязка объединяет сайт и Telegram одним аккаунтом.</p><Alert>{error}</Alert>
    {connected ? <div className="connected-box"><span className="stat-icon teal-bg"><Icon name="check"/></span><div><strong>Telegram привязан к вашему аккаунту</strong><p>ID в Telegram: {settings.telegramId}</p></div><Button variant="ghost" onClick={() => setConfirm(true)}>Отключить</Button></div> : <>
      <ol className="steps-list"><li><span>1</span><div><strong>Получите одноразовый код</strong><p>Он действует 5 минут и предназначен только для вас.</p></div></li><li><span>2</span><div><strong>Отправьте команду боту</strong><p>В личном чате выполните <code>/link ваш_код</code> или откройте бота по кнопке.</p></div></li><li><span>3</span><div><strong>Проверьте подключение</strong><p>Вернитесь сюда и обновите статус привязки.</p></div></li></ol>
      {settings.telegramId && !connected && <Alert kind="info">В настройках есть Telegram ID, но привязка не подтверждена. Подключите бота с помощью кода.</Alert>}
      {code && <div className="link-code"><div className="split-heading"><strong>Команда для бота</strong><span className={remaining ? 'teal' : 'negative'}>{remaining ? `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2,'0')}` : 'Код истёк'}</span></div><code className="copy-code">/link {code.code}</code><Button variant="secondary" icon="copy" disabled={!remaining} onClick={async () => { try { await navigator.clipboard.writeText(`/link ${code.code}`); notify('Команда скопирована') } catch { setError('Скопируйте команду вручную: буфер обмена недоступен.') } }}>Скопировать команду</Button></div>}
      <div className="inline-actions"><Button icon="plus" disabled={busy} onClick={generate}>{busy ? 'Получаем код…' : code ? 'Получить новый код' : 'Получить код привязки'}</Button>{botUrl && <a className="button secondary" href={botUrl} target="_blank" rel="noreferrer">Открыть бота<Icon name="arrow" size={16}/></a>}<Button variant="ghost" onClick={refreshAccount}>Проверить подключение</Button></div><p className="form-hint">Если Telegram уже привязан к другому аккаунту, сначала отключите его там. Код никому не передавайте.</p>
    </>}
    {connected && botUrl && <a className="text-link" href={botUrl} target="_blank" rel="noreferrer">Открыть Telegram-бота<Icon name="arrow" size={16}/></a>}
    {confirm && <Confirm title="Отключить Telegram?" label="Отключить" onClose={() => setConfirm(false)} onConfirm={async () => { await api.unlinkTelegram(); setConfirm(false); refreshAccount(); notify('Telegram отключён') }}><p>Бот потеряет доступ к этому аккаунту, напоминания перестанут приходить. На сайт можно будет входить по логину и паролю.</p></Confirm>}
  </section>
}

export default function Settings() {
  const { api, profile, settings, session, refreshAccount, notify, setTheme } = useApp()

  return <>
    <div className="page-heading"><div><span className="eyebrow teal">ВАШЕ ПРОСТРАНСТВО</span><h1>Всё по-вашему<span className="heading-dot">.</span></h1><p>Профиль, предпочтения и связь с Telegram — в одном месте.</p></div><span className="id-card"><Icon name="users" size={18}/>Мой ID: <b>{session.userId}</b></span></div>
    <div className="settings-layout"><div className="settings-main">
      <section className="panel settings-section"><div className="settings-title"><span className="stat-icon teal-bg"><Icon name="users"/></span><div><h2>Личные данные</h2><p>Так вас узнают в вашем пространстве.</p></div></div>
        <Form onSubmit={async data => { await api.updateProfile({ name: data.get('name').trim(), timezone: data.get('timezone') || null, currency: data.get('currency') || null, amount: data.get('amount') ? decimal(data.get('amount')) : null }); refreshAccount(); notify('Профиль сохранён') }}>
          <Field label="Имя" name="name" defaultValue={profile.name || ''} maxLength={255} required/>
          <div className="form-grid"><Field label="Часовой пояс"><input name="timezone" list="profile-timezones" defaultValue={profile.timezone || 'Europe/Moscow'} required/></Field><Field label="Валюта профиля"><CurrencyInput defaultValue={profile.currency || 'RUB'}/></Field></div>
          <datalist id="profile-timezones">{ZONES.map(zone => <option key={zone} value={zone}/>)}</datalist>
          <Field label="Личный бюджет" hint="Поле профиля. Не меняет расходы и долги поездок, не конвертирует валюты." name="amount" inputMode="decimal" pattern="[0-9]+([.,][0-9]{1,3})?" defaultValue={profile.amount ?? ''} placeholder="Не указан"/>
        </Form>
      </section><Telegram/>
      <section className="panel settings-section"><div className="settings-title"><span className="stat-icon lavender-bg"><Icon name="settings"/></span><div><h2>Предпочтения</h2><p>Оформление и уведомления вашего аккаунта.</p></div></div>
        <Form onSubmit={async data => { const values = Object.fromEntries(data); await api.updateSettings(values); setTheme(values.theme); refreshAccount(); notify('Настройки сохранены') }}>
          <div className="form-grid"><Field label="Тема интерфейса"><select name="theme" defaultValue={['DARK','BLACK'].includes(settings.theme) ? 'DARK' : 'WHITE'}><option value="WHITE">Светлая</option><option value="DARK">Тёмная</option></select></Field><Field label="Язык профиля" hint="Сохраняется в аккаунте. Этот интерфейс пока доступен на русском."><select name="language" defaultValue={settings.language || 'ru'}><option value="ru">Русский</option><option value="en">English</option></select></Field></div>
          <Field label="Напоминания о вылетах"><select name="notificationMethod" defaultValue={settings.notificationMethod || 'TELEGRAM'}><option value="TELEGRAM">Получать в Telegram</option><option value="NONE">Отключить уведомления</option>{settings.notificationMethod && !['TELEGRAM','NONE'].includes(settings.notificationMethod) && <option value={settings.notificationMethod}>{settings.notificationMethod} (сохранённое значение)</option>}</select></Field>
          <Alert kind="info">Для напоминаний нужен подключённый Telegram. Сообщения отправляются за 3 часа до каждого перелёта по данным маршрута.</Alert>
        </Form>
      </section>
    </div><aside className="settings-aside"><div className="info-card"><Icon name="compass" size={28}/><h3>Один аккаунт.<br/>Два способа быть на связи.</h3><p>Создавайте большие планы на сайте, а в пути добавляйте расходы и проверяйте маршрут в боте.</p><a className="text-link" href="#telegram" onClick={event => { event.preventDefault(); document.getElementById('telegram')?.scrollIntoView({ behavior: 'smooth' }) }}>Перейти к привязке<Icon name="arrow" size={16}/></a></div><div className="info-card subtle"><Icon name="users"/><h4>Пригласили в поездку?</h4><p>Сообщите владельцу ваш ID: <b>{session.userId}</b>. После добавления поездка появится в вашем списке.</p></div></aside></div>
  </>
}
