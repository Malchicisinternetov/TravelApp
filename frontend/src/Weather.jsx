import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Empty, Field, Icon, Loading } from './components'
import { useApp } from './state'
import { localDate } from './utils'

export function weatherCondition(code, isDay = true) {
  if (code == null) return ['Нет данных', 'cloud']
  if (code === 0) return [isDay ? 'Ясно' : 'Ясная ночь', isDay ? 'sun' : 'moon']
  if (code <= 2) return ['Переменная облачность', 'cloud']
  if (code === 3) return ['Пасмурно', 'cloud']
  if (code === 45 || code === 48) return ['Туман', 'cloud']
  if ([71, 73, 75, 77, 85, 86].includes(code)) return ['Снег', 'snow']
  if (code >= 95) return ['Гроза', 'rain']
  return ['Дождь', 'rain']
}
const temperature = value => value == null ? '—' : `${Math.round(value)}°`
const number = value => value == null ? '—' : new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value)

export default function Weather() {
  const { api } = useApp()
  const [mode, setMode] = useState('city')
  const [city, setCity] = useState('')
  const [locations, setLocations] = useState(null)
  const [weather, setWeather] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const request = useRef(0)
  useEffect(() => () => { request.current++ }, [])
  async function run(load) {
    const id = ++request.current
    setBusy(true); setError('')
    try { await load(id) }
    catch (err) { if (id === request.current) setError(err.message) }
    finally { if (id === request.current) setBusy(false) }
  }
  async function choose(location) {
    await run(async id => {
      const data = await api.weather({ latitude: location.latitude, longitude: location.longitude })
      if (id === request.current) { setWeather({ ...data, location }); setLocations(null) }
    })
  }
  const current = weather?.current
  const [condition, icon] = weatherCondition(current?.weatherCode, current?.isDay)
  const today = weather?.forecast?.[0]
  return <div className="travel-tool weather-page">
    <div className="page-heading"><div><span className="eyebrow teal">ПЛАНЫ С УЧЁТОМ ПРОГНОЗА</span><h1>Погода<span className="heading-dot">.</span></h1><p>Узнайте, что ждёт за окном в следующей точке маршрута.</p></div><span className="tool-heading-icon"><Icon name="sun" size={32}/></span></div>
    <section className="panel tool-search"><div className="section-heading"><div><h2>Куда смотрим?</h2><p>Выберите город или укажите точные координаты.</p></div><div className="segmented"><button disabled={busy} className={mode === 'city' ? 'active' : ''} onClick={() => { setMode('city'); setError(''); setLocations(null) }}>По городу</button><button disabled={busy} className={mode === 'coordinates' ? 'active' : ''} onClick={() => { setMode('coordinates'); setError(''); setLocations(null) }}>Координаты</button></div></div>
      <form className="weather-search-form" onSubmit={event => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        setWeather(null)
        run(async id => {
          if (mode === 'city') {
            const items = await api.weatherLocations(city.trim())
            if (id === request.current) setLocations(items)
          } else {
            const result = await api.weather({ latitude: data.get('latitude'), longitude: data.get('longitude') })
            if (id === request.current) setWeather(result)
          }
        })
      }}><fieldset disabled={busy} className={`weather-search-fields ${mode === 'coordinates' ? 'coordinates' : ''}`}>
        {mode === 'city' ? <Field label="Город" placeholder="Например, Стамбул или Париж, Франция" minLength={2} maxLength={150} value={city} onChange={event => { setCity(event.target.value); setLocations(null) }} required/> : <><Field label="Широта" name="latitude" type="number" min={-90} max={90} step="any" placeholder="55.7558" required/><Field label="Долгота" name="longitude" type="number" min={-180} max={180} step="any" placeholder="37.6173" required/></>}
        <Button type="submit" icon="search" disabled={busy}>{busy ? 'Загружаем…' : mode === 'city' ? 'Найти город' : 'Показать погоду'}</Button></fieldset></form>
      <Alert>{error}</Alert>
      {locations && <div className="location-results" aria-live="polite">{locations.length ? <><p>Выберите нужный город</p>{locations.map((location, index) => <button key={index} disabled={busy} onClick={() => choose(location)}><span className="location-pin"><Icon name="pin"/></span><span><strong>{location.name}</strong><small>{[location.admin1, location.country].filter(Boolean).join(', ')}</small></span><span className="location-coordinates">{location.latitude.toFixed(2)}, {location.longitude.toFixed(2)}</span></button>)}</> : <p>Город не найден. Попробуйте другое название или поиск по координатам.</p>}</div>}
    </section>
    {busy && <Loading label="Получаем данные о погоде…"/>}
    {weather && !busy ? <>
      <section className="weather-overview"><div className="weather-now"><div className="weather-location"><span className="weather-now-label">ПОГОДА СЕЙЧАС</span><h2>{weather.location.name || 'По вашим координатам'}</h2><p>{[weather.location.admin1, weather.location.country].filter(Boolean).join(', ') || `${weather.location.latitude}, ${weather.location.longitude}`}</p></div><div className="weather-temperature"><strong>{temperature(current.temperature)}<span>C</span></strong><span className="weather-art"><Icon name={icon} size={108}/></span></div><div className="weather-condition"><strong>{condition}</strong><span>Ощущается как {temperature(current.feelsLike)}</span></div><div className="weather-observation">Данные на {localDate(current.time, true)} · местное время</div></div>
        <div className="weather-details panel"><h2>Детали на сегодня</h2><div className="weather-metrics"><div><Icon name="wind"/><span>Ветер<strong>{number(current.windSpeed)} м/с</strong></span></div><div><Icon name="drop"/><span>Влажность<strong>{number(current.humidity)}%</strong></span></div><div><Icon name="rain"/><span>Вероятность осадков<strong>{number(today?.precipitationProbability)}%</strong></span></div><div><Icon name="sun"/><span>Восход / закат<strong>{today?.sunrise?.slice(11, 16) || '—'} / {today?.sunset?.slice(11, 16) || '—'}</strong></span></div></div><p className="weather-zone"><Icon name="pin" size={16}/>{weather.timezone}<br/>Температура в °C, время местное.</p></div></section>
      <section className="panel forecast-panel"><div className="section-heading"><div><h2>Неделя впереди</h2><p>Прогноз на {weather.forecast.length} дней</p></div><Icon name="calendar"/></div><div className="forecast-grid">{weather.forecast.map((day, index) => { const [label, dayIcon] = weatherCondition(day.weatherCode); return <article className={`forecast-day ${index === 0 ? 'today' : ''}`} key={day.date}><strong>{index === 0 ? 'Сегодня' : new Intl.DateTimeFormat('ru-RU', { weekday: 'short' }).format(new Date(`${day.date}T12:00:00`))}</strong><span>{localDate(day.date).slice(0, 5)}</span><Icon name={dayIcon} size={34}/><div className="forecast-temperatures"><b>{temperature(day.max)}</b><span>{temperature(day.min)}</span></div><small>{label}</small><span className="forecast-rain"><Icon name="drop" size={13}/>{number(day.precipitationProbability)}%</span></article>})}</div></section>
    </> : !busy && !locations && <div className="panel weather-placeholder"><Empty icon="sun" title="В любом направлении — со своим прогнозом">Найдите город, чтобы увидеть текущую погоду и выбрать подходящие дни для прогулок.</Empty><div className="weather-quick-cities">{['Москва', 'Стамбул', 'Тбилиси'].map(name => <button key={name} onClick={() => { setCity(name); setWeather(null); run(async id => { const items = await api.weatherLocations(name); if (id === request.current) setLocations(items) }) }}><Icon name="pin" size={15}/>{name}</button>)}</div></div>}
    <p className="tool-source">Погода: <a href="https://open-meteo.com/" target="_blank" rel="noreferrer">Open-Meteo</a> · <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a>. Поиск городов: <a href="https://www.geonames.org/" target="_blank" rel="noreferrer">GeoNames</a>. Прогноз может меняться.</p>
  </div>
}
