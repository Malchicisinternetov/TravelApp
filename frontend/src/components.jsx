import { cloneElement, useEffect, useId, useRef, useState } from 'react'
import { useApp } from './state'

export function Icon({ name = 'compass', size = 20, ...props }) {
  const paths = {
    compass: <><circle cx="12" cy="12" r="9"/><path d="m16 8-2.5 5.5L8 16l2.5-5.5Z"/></>,
    plane: <><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></>,
    plus: <path d="M12 5v14M5 12h14"/>, arrow: <path d="M4 12h16m-6-6 6 6-6 6"/>,
    back: <path d="M20 12H4m6-6-6 6 6 6"/>, close: <path d="m6 6 12 12M6 18 18 6"/>,
    users: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87"/><circle cx="9" cy="7" r="4"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></>,
    wallet: <><path d="M20 8V5a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h15v12H5a3 3 0 0 1-3-3V6"/><path d="M20 12h-5v5h5"/></>,
    settings: <><path d="m9 3-1 3-3 1 1 3-2 2 2 2-1 3 3 1 1 3h6l1-3 3-1-1-3 2-2-2-2 1-3-3-1-1-3Z"/><circle cx="12" cy="12" r="3"/></>,
    calendar: <><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M16 3v4M8 3v4M3 11h18"/></>,
    pin: <><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></>,
    logout: <><path d="M9 21H4V3h5m5 5 4 4-4 4m-5-4h13"/></>,
    check: <path d="m5 12 4 4L19 6"/>, trash: <><path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/></>,
    edit: <><path d="m16 3 5 5-12 12-6 1 1-6Z"/><path d="m13 6 5 5"/></>,
    search: <><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></>,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></>,
    shield: <><path d="m12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6Z"/><path d="m8 12 3 3 5-6"/></>,
    copy: <><rect x="8" y="8" width="13" height="13" rx="2"/><path d="M16 8V3H3v13h5"/></>,
    sun: <><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1 1m12 12 1 1M5 19l1-1M18 6l1-1"/></>,
    cloud: <path d="M6 18a4 4 0 1 1 1-7.87A6 6 0 1 1 19 18Z"/>,
    rain: <><path d="M6 15a4 4 0 1 1 1-7.87A6 6 0 1 1 19 15M8 18l-1 3m6-3-1 3m6-3-1 3"/></>,
    snow: <><path d="M6 15a4 4 0 1 1 1-7.87A6 6 0 1 1 19 15M8 19v2m-1-1h2m8-1v2m-1-1h2"/></>,
    moon: <path d="M20.9 13a9 9 0 0 1-9.9-9.9A9 9 0 1 0 20.9 13Z"/>,
    wind: <path d="M3 8h12a3 3 0 1 0-3-3M2 12h17a3 3 0 1 1-3 3M4 16h5a3 3 0 1 1-3 3"/>,
    drop: <path d="M12 2S5 10 5 15a7 7 0 0 0 14 0c0-5-7-13-7-13Z"/>,
    exchange: <path d="M4 7h16m-4-4 4 4-4 4M20 17H4m4-4-4 4 4 4"/>,
    menu: <path d="M4 6h16M4 12h16M4 18h16"/>, chevron: <path d="m6 9 6 6 6-6"/>,
  }
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>{paths[name] || paths.compass}</svg>
}

export function Button({ children, icon, variant = 'primary', className = '', ...props }) {
  return <button type="button" className={`button ${variant} ${className}`} {...props}>{icon && <Icon name={icon}/>}<span>{children}</span></button>
}
export function Field({ label, hint, children, ...props }) {
  const id = useId()
  return <label className="field" htmlFor={id}><span>{label}</span>{children ? cloneElement(children, { id }) : <input id={id} {...props}/>}{hint && <small>{hint}</small>}</label>
}
export function Alert({ children, kind = 'error' }) {
  return children && <div className={`alert ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>
}
export function Loading({ label = 'Загружаем данные…' }) { return <div className="loading" role="status"><span className="spinner"/>{label}</div> }
export function Empty({ icon = 'compass', title, children, action }) {
  return <div className="empty"><div className="empty-icon"><Icon name={icon} size={30}/></div><h3>{title}</h3><p>{children}</p>{action}</div>
}
export function Form({ onSubmit, children, submit = 'Сохранить', onCancel, submitVariant = 'primary' }) {
  const app = useApp()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function send(event) {
    event.preventDefault()
    if (busy) return
    setError(''); setBusy(true)
    try { await onSubmit(new FormData(event.currentTarget)) }
    catch (err) { setError(err.message) }
    finally { setBusy(false) }
  }
  return <form onSubmit={send}><fieldset disabled={busy} className="form-fields">{children}</fieldset><Alert>{error}</Alert>{app?.sessionExpired && <Button variant="secondary" onClick={app.requestLogin}>Продолжить работу без потери формы</Button>}<div className="form-actions">{onCancel && <Button variant="secondary" onClick={onCancel} disabled={busy}>Отмена</Button>}<Button variant={submitVariant} type="submit" disabled={busy}>{busy ? 'Сохраняем…' : submit}</Button></div></form>
}
export function Modal({ title, subtitle, children, onClose }) {
  const dialog = useRef(null)
  const heading = useId()
  useEffect(() => {
    const element = dialog.current
    element.showModal()
    element.querySelector('input, select, textarea')?.focus()
    const old = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { element.close(); document.body.style.overflow = old }
  }, [])
  return <dialog ref={dialog} className="modal" aria-labelledby={heading} onCancel={event => { event.preventDefault(); onClose() }} onClick={event => { if (event.target === dialog.current) { const r = dialog.current.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) onClose() } }}><div className="modal-heading"><div><h2 id={heading}>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><button type="button" className="icon-button" aria-label="Закрыть окно" onClick={onClose}><Icon name="close"/></button></div>{children}</dialog>
}
export function Confirm({ title, children, onConfirm, onClose, label = 'Удалить' }) {
  return <Modal title={title} onClose={onClose}><Form onSubmit={onConfirm} onCancel={onClose} submit={label} submitVariant="danger"><div className="confirm-copy">{children}</div><div className="danger-note"><Icon name="shield"/>Проверьте детали перед подтверждением действия.</div></Form></Modal>
}
export function Avatar({ name = '?', small = false }) { return <span className={`avatar ${small ? 'small' : ''}`}>{String(name || '?').slice(0, 1).toUpperCase()}</span> }
export function CurrencyInput({ id, value, onChange, name = 'currency', defaultValue }) {
  return <><input id={id} name={name} list="currency-list" value={value} onChange={onChange} defaultValue={defaultValue} pattern="[A-Z]{3}" maxLength={3} required placeholder="RUB" autoCapitalize="characters"/><datalist id="currency-list">{['RUB','USD','EUR','GBP','TRY','AED','CNY','JPY','KZT','GEL','THB','KWD'].map(c => <option key={c} value={c}/>)}</datalist></>
}
