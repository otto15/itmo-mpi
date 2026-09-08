import { useState } from 'react'
import { api } from './api'
import type { Expedition, Preparation, Session, Supply } from './types'

const names: Record<string, string> = { PROVISIONS: 'Провизия', WOOD: 'Дерево', CLOTH: 'Ткань', RESIN: 'Смола', GOLD: 'Золото', THRALLS: 'Пленные' }
type Perform = <T>(work: () => Promise<T>, success: string, after?: (result: T) => void) => Promise<void>
const quantities = (items: Supply[]) => items.map(item => `${names[item.resource]}: ${item.quantity}`).join(' · ')
const dateTime = (value: string) => new Date(value).toLocaleString('ru-RU')

export function PreparationPanel({ expedition, session, busy, perform }: {
  expedition: Expedition; session: Session; busy: boolean; perform: Perform
}) {
  const preparation = expedition.preparation!
  const [editing, setEditing] = useState(false)
  const active = preparation.reservations.find(item => item.status === 'ACTIVE')
  const last = preparation.reservations[0]
  return <div className="preparation-section">
    <h3>Маршрут и припасы</h3>
    <Route points={preparation.route} />
    <p>{quantities(preparation.requirements) || 'Припасы не указаны'}</p>
    <div className="reservation-summary">
      <b>{active ? 'Припасы зарезервированы' : last?.status === 'EXPIRED' || last?.status === 'RELEASED' ? 'Резерв освобождён' : 'Резерв ещё не создан'}</b>
      {active && <small>До {dateTime(active.expiresAt)}</small>}
    </div>
    <div className="button-row">
      <button className="secondary" disabled={busy} onClick={() => setEditing(!editing)}>{editing ? 'Закрыть' : 'Изменить маршрут и припасы'}</button>
      <button className="secondary" disabled={busy || !preparation.requirements.length} onClick={() => void perform(
        () => api(`/api/expeditions/${expedition.id}/reservations`, session.token, { expectedVersion: expedition.version }),
        'Припасы зарезервированы')}>{active ? 'Продлить резерв' : 'Зарезервировать припасы'}</button>
    </div>
    {editing && <PreparationEditor expedition={expedition} session={session} busy={busy} perform={perform} close={() => setEditing(false)} />}
  </div>
}

function PreparationEditor({ expedition, session, busy, perform, close }: {
  expedition: Expedition; session: Session; busy: boolean; perform: Perform; close: () => void
}) {
  const [route, setRoute] = useState(expedition.preparation!.route)
  const [resources, setResources] = useState<Record<string, number>>(() => Object.fromEntries(expedition.preparation!.requirements.map(r => [r.resource, r.quantity])))
  const [version, setVersion] = useState(expedition.version)
  function reload() {
    setRoute(expedition.preparation!.route)
    setResources(Object.fromEntries(expedition.preparation!.requirements.map(r => [r.resource, r.quantity])))
    setVersion(expedition.version)
  }
  const supplies = Object.entries(resources).filter(([, quantity]) => quantity > 0).map(([resource, quantity]) => ({ resource, quantity }))
  const valid = route.length >= 2 && route.every((p, i) => p.name.trim() && (i === 0 ? p.distanceKm === 0 : p.distanceKm > 0)) && supplies.length > 0
  return <form className="preparation-editor" onSubmit={event => {
    event.preventDefault()
    void perform(() => api(`/api/expeditions/${expedition.id}/preparation`, session.token, { route, resources: supplies, expectedVersion: version }), 'Маршрут и припасы сохранены', close)
  }}>
    <fieldset disabled={busy}>
      <small>Расстояние в километрах от предыдущей точки. При изменении плана текущий резерв освобождается.</small>
      {route.map((point, index) => <div className="route-editor-row" key={index}>
        <input aria-label={`Точка ${index + 1}`} value={point.name} maxLength={160} onChange={e => setRoute(old => old.map((p, i) => i === index ? { ...p, name: e.target.value } : p))} />
        <input aria-label={`Расстояние до точки ${index + 1}`} type="number" min={index === 0 ? 0 : 1} disabled={index === 0} value={point.distanceKm} onChange={e => setRoute(old => old.map((p, i) => i === index ? { ...p, distanceKm: Math.max(0, Math.floor(Number(e.target.value))) } : p))} />
        <button type="button" className="text-button" aria-label={`Убрать точку ${index + 1}`} disabled={index === 0} onClick={() => setRoute(old => old.filter((_, i) => i !== index))}>×</button>
      </div>)}
      <button type="button" className="text-button" disabled={route.length >= 30} onClick={() => setRoute(old => [...old, { name: '', distanceKm: 1 }])}>+ Добавить точку</button>
      <div className="loot-grid">{Object.entries(names).map(([key, label]) => <label key={key}><span>{label}</span><input type="number" min="0" value={resources[key] ?? 0} onChange={event => setResources(old => ({ ...old, [key]: Math.max(0, Math.floor(Number(event.target.value))) }))} /></label>)}</div>
      {version !== expedition.version && <p>Поход изменён в другом окне. <button type="button" className="text-button" onClick={reload}>Загрузить актуальный план</button></p>}
      <button className="primary" disabled={!valid || version !== expedition.version}>Сохранить</button>
    </fieldset>
  </form>
}

function Route({ points }: { points: Preparation['route'] }) {
  return <ol className="route-list">{points.map((point, index) => <li key={index}><b>{point.name}</b>{index > 0 && <small>{point.distanceKm} км</small>}</li>)}</ol>
}

export function DeparturePanel({ preparation }: { preparation: Preparation }) {
  const snapshot = preparation.snapshot
  if (!snapshot) return null
  return <details className="departure-panel"><summary>На момент выхода{preparation.startedAt ? ` · ${dateTime(preparation.startedAt)}` : ''}</summary>
    <h3>Маршрут</h3><Route points={snapshot.route} />
    <h3>Команда · {snapshot.crew.length}</h3>
    <div className="fleet-chips">{snapshot.crew.map(member => <span key={member.id}>{member.name}<small>{member.role}</small></span>)}</div>
    <h3>Флот · {snapshot.fleet.length}</h3>
    <div className="fleet-chips">{snapshot.fleet.map(ship => <span key={ship.id}>{ship.name}<small>{ship.type} · {ship.capacity} мест</small></span>)}</div>
    <h3>Припасы</h3><p>{quantities(snapshot.resources) || 'Нет сведений о списании'}</p>
  </details>
}
