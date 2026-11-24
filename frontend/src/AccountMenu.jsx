import { useEffect, useRef, useState } from 'react'
import { Avatar, Icon } from './components'

export default function AccountMenu({ profile, session, logout }) {
  const [open, setOpen] = useState(false)
  const container = useRef(null)
  const trigger = useRef(null)
  useEffect(() => {
    if (!open) return
    const outside = event => { if (!container.current?.contains(event.target)) setOpen(false) }
    const keyboard = event => { if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() } }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('keydown', keyboard)
    container.current?.querySelector('[role="menuitem"]')?.focus()
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', keyboard) }
  }, [open])
  return <div className="account-menu" ref={container} onBlur={event => { if (event.relatedTarget && !event.currentTarget.contains(event.relatedTarget)) setOpen(false) }}>
    <button className="topbar-account" type="button" ref={trigger} aria-haspopup="menu" aria-expanded={open} aria-label="Меню аккаунта" onClick={() => setOpen(value => !value)} onKeyDown={event => { if (event.key === 'ArrowDown') { event.preventDefault(); setOpen(true) } }}>
      <span>{profile?.name || `Аккаунт #${session.userId}`}</span><Avatar small name={profile?.name}/><Icon name="chevron" size={14}/>
    </button>
    {open && <div className="account-dropdown" role="menu" aria-label="Действия аккаунта" onKeyDown={event => {
      const items = [...event.currentTarget.querySelectorAll('[role="menuitem"]')]
      const index = items.indexOf(document.activeElement)
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); items[(index + (event.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length]?.focus() }
    }}><div className="account-dropdown-heading"><strong>{profile?.name || 'Мой аккаунт'}</strong><small>ID {session.userId}</small></div><a role="menuitem" href="#/settings" onClick={() => setOpen(false)}><Icon name="settings" size={18}/>Настройки профиля</a><button role="menuitem" type="button" onClick={() => { setOpen(false); logout() }}><Icon name="logout" size={18}/>Выйти из аккаунта</button></div>}
  </div>
}
