import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Field, Icon, Loading } from './components'
import { useApp, useRemote } from './state'
import { decimal, localDate, money } from './utils'

const displayNames = new Intl.DisplayNames(['ru'], { type: 'currency' })
const currencyName = code => displayNames.of(code) || code
const rateNumber = value => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 8 }).format(value)
const favorites = ['RUB', 'USD', 'EUR', 'GBP', 'TRY', 'AED', 'CNY', 'JPY', 'KZT', 'GEL', 'THB']

export default function Currency() {
  const { api, profile } = useApp()
  const catalogue = useRemote(api.currencies, [api])
  const [from, setFrom] = useState('RUB')
  const [to, setTo] = useState('USD')
  const [amount, setAmount] = useState('1000')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [filter, setFilter] = useState('')
  const request = useRef(0)
  useEffect(() => () => { request.current++ }, [])
  useEffect(() => {
    if (!catalogue.data?.length) return
    const preferred = catalogue.data.some(c => c.code === profile?.currency) ? profile.currency : catalogue.data.some(c => c.code === 'RUB') ? 'RUB' : catalogue.data[0].code
    setFrom(preferred)
    setTo(catalogue.data.some(c => c.code === 'USD') ? 'USD' : catalogue.data[0].code)
  }, [catalogue.data, profile?.currency])
  const rates = useRemote(() => catalogue.data ? api.rates(from) : Promise.resolve(null), [api, from, catalogue.data])
  const currencies = [...(catalogue.data || [])].sort((a, b) => {
    const aRank = favorites.includes(a.code) ? favorites.indexOf(a.code) : 100
    const bRank = favorites.includes(b.code) ? favorites.indexOf(b.code) : 100
    return aRank - bRank || a.code.localeCompare(b.code)
  })
  const change = setter => event => { setter(event.target.value); setResult(null); setError('') }
  const choices = currencies.map(c => <option value={c.code} key={c.code}>{c.code} · {currencyName(c.code)}</option>)
  return <div className="travel-tool currency-page">
    <div className="page-heading"><div><span className="eyebrow teal">БЮДЖЕТ БЕЗ ЛИШНИХ ВЫЧИСЛЕНИЙ</span><h1>Валюты<span className="heading-dot">.</span></h1><p>Прикиньте стоимость в привычной валюте перед новой поездкой.</p></div><span className="tool-heading-icon"><Icon name="exchange" size={32}/></span></div>
    {catalogue.loading ? <Loading label="Загружаем доступные валюты…"/> : catalogue.error ? <div className="panel"><Alert>{catalogue.error}</Alert><Button variant="secondary" onClick={catalogue.reload}>Повторить</Button></div> : <>
      <div className="converter-layout"><section className="panel converter-form"><div className="section-heading"><div><h2>Конвертер валют</h2><p>Выберите пару и введите сумму.</p></div><span className="stat-icon teal-bg"><Icon name="wallet"/></span></div>
        <form onSubmit={async event => {
          event.preventDefault()
          if (busy) return
          const id = ++request.current
          setBusy(true); setError(''); setResult(null)
          try { const data = await api.convert({ from, to, amount: decimal(amount) }); if (id === request.current) setResult(data) }
          catch (err) { if (id === request.current) setError(err.message) }
          finally { if (id === request.current) setBusy(false) }
        }}><fieldset disabled={busy} className="form-fields"><Field label="Сумма" value={amount} onChange={change(setAmount)} inputMode="decimal" pattern="[0-9]{1,12}([.,][0-9]{1,6})?" maxLength={19} required placeholder="1000" hint="До 6 знаков после запятой"/>
          <div className="currency-pair"><Field label="Из валюты"><select value={from} onChange={change(setFrom)}>{choices}</select></Field><button type="button" className="currency-swap" title="Поменять валюты местами" aria-label="Поменять валюты местами" onClick={() => { setFrom(to); setTo(from); setResult(null); setError('') }}><Icon name="exchange"/></button><Field label="В валюту"><select value={to} onChange={change(setTo)}>{choices}</select></Field></div>
        </fieldset><Alert>{error}</Alert><Button type="submit" icon="exchange" disabled={busy}>{busy ? 'Считаем…' : 'Конвертировать'}</Button></form>
      </section><section className={`conversion-result ${result ? 'has-result' : ''}`} aria-live="polite"><span className="conversion-decoration" aria-hidden="true"><Icon name="exchange" size={180}/></span><span className="eyebrow">{result ? 'РЕЗУЛЬТАТ КОНВЕРТАЦИИ' : 'ПУСТЬ ВСЁ СХОДИТСЯ'}</span>{result ? <><p>{money(result.amount, result.from)} =</p><strong className="conversion-total">{money(result.convertedAmount, result.to)}</strong><div className="conversion-rate">1 {result.from} = {rateNumber(result.rate)} {result.to}</div><span className="conversion-date"><Icon name="calendar" size={16}/>{result.date ? `Курс на ${localDate(result.date)}` : 'Одинаковые валюты · курс 1:1'}</span></> : <><h2>Один расчёт.<br/>Больше ясности.</h2><p>Здесь появится сумма после пересчёта и курс, по которому она рассчитана.</p><span className="conversion-badge"><Icon name="shield" size={16}/>Курс и дата всегда под рукой</span></>}</section></div>
      <section className="panel exchange-rates"><div className="section-heading"><div><h2>Курсы к {from}</h2><p>Сколько иностранной валюты можно получить за 1 {from}</p></div><div className="search-field"><Icon name="search" size={17}/><input aria-label="Поиск валюты" placeholder="Код или название…" value={filter} onChange={event => setFilter(event.target.value)}/></div></div>
        {rates.loading ? <Loading label="Загружаем курсы…"/> : rates.error ? <><Alert>{rates.error}</Alert><Button variant="secondary" onClick={rates.reload}>Повторить</Button></> : <><div className="rates-table-wrap"><table className="rates-table"><thead><tr><th>Валюта</th><th>За 1 {from}</th><th>Дата курса</th><th><span className="muted">Быстрый выбор</span></th></tr></thead><tbody>{(rates.data?.rates || []).filter(r => `${r.quote} ${currencyName(r.quote)}`.toLowerCase().includes(filter.toLowerCase())).map(r => <tr key={r.quote}><td><span className="currency-code">{r.quote}</span><span className="currency-full-name">{currencyName(r.quote)}</span></td><td className="rate-value">{rateNumber(r.rate)}</td><td>{localDate(r.date)}</td><td><button className="text-link" onClick={() => { setTo(r.quote); setResult(null); setError(''); window.scrollTo({ top: 0, behavior: 'smooth' }) }}>В конвертер</button></td></tr>)}</tbody></table>{rates.data && !rates.data.rates.some(r => `${r.quote} ${currencyName(r.quote)}`.toLowerCase().includes(filter.toLowerCase())) && <p className="rates-empty">Для этого поиска курсов нет.</p>}</div><p className="rates-updated">Получено {new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(rates.data?.fetchedAt || Date.now()))}</p></>}
      </section>
    </>}
    <p className="tool-source">Источник: <a href="https://frankfurter.dev/" target="_blank" rel="noreferrer">Frankfurter</a>. Справочные курсы; курс банка и комиссии могут отличаться. Долги по поездкам считаются отдельно в каждой валюте.</p>
  </div>
}
