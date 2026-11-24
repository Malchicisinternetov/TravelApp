import { useEffect, useState } from 'react'
import { Alert, Avatar, Button, Field, Icon, Modal } from './components'
import { useApp } from './state'
import { isPositiveId, ROLES } from './utils'

export default function AddMember({ trip, onAdded, onClose }) {
  const { api, rememberUsers, notify, sessionExpired, requestLogin } = useApp()
  const [id, setId] = useState('')
  const [role, setRole] = useState('MEMBER')
  const [found, setFound] = useState(null)
  const [checking, setChecking] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    setFound(null); setError('')
    if (sessionExpired) { setChecking(false); return }
    if (!isPositiveId(id)) { setChecking(false); return }
    if (trip.participants.some(p => p.userId === Number(id))) { setError('Этот человек уже участвует в поездке.'); setChecking(false); return }
    setChecking(true)
    const timer = setTimeout(async () => {
      try {
        const users = await api.userSummaries([Number(id)])
        if (!active) return
        if (!users.length) setError('Пользователь с таким ID не найден. Проверьте номер.')
        else { setFound(users[0]); rememberUsers(users) }
      } catch (err) { if (active) setError(err.message) }
      finally { if (active) setChecking(false) }
    }, 350)
    return () => { active = false; clearTimeout(timer) }
  }, [api, id, trip.participants, rememberUsers, sessionExpired])
  const verified = found?.userId === Number(id) && !checking
  return <Modal title="Пригласить попутчика" subtitle="Введите ID и убедитесь, что это нужный человек." onClose={onClose}>
    <form onSubmit={async event => {
      event.preventDefault()
      if (!verified || busy) return
      setBusy(true); setError('')
      try { onAdded(await api.addMember(trip.id, { userId: found.userId, role })); notify('Участник добавлен') }
      catch (err) { setError(err.message) }
      finally { setBusy(false) }
    }}><fieldset className="form-fields" disabled={busy}>
      <Field label="ID пользователя" value={id} onChange={event => { setId(event.target.value); setFound(null) }} inputMode="numeric" pattern="[1-9][0-9]*" placeholder="Например, 12" required/>
      {checking && <p className="lookup-status" role="status"><span className="spinner"/>Проверяем пользователя…</p>}
      {verified && <div className="found-user" role="status"><Avatar name={found.name}/><div><strong>{found.name || 'Имя не указано'}</strong><small>ID {found.userId}</small></div><Icon name="check"/></div>}
      <Alert>{error}</Alert>
      {sessionExpired && <Button variant="secondary" onClick={requestLogin}>Продолжить работу без потери формы</Button>}
      <Field label="Права в поездке"><select value={role} onChange={event => setRole(event.target.value)}>{Object.entries(ROLES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></Field>
      <p className="form-hint">Имя найдено в профиле пользователя. Сверьте его перед добавлением. ID можно узнать в профиле сайта или командой /id в боте.</p>
    </fieldset><div className="form-actions"><Button variant="secondary" onClick={onClose} disabled={busy}>Отмена</Button><Button type="submit" disabled={!verified || busy}>{busy ? 'Добавляем…' : 'Добавить участника'}</Button></div></form>
  </Modal>
}
