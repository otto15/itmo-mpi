import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, apiDelete, ApiError } from './api'
import type { Allocation, Crew, DemoState, Expedition, Loot, Role, Session } from './types'

type Tab = 'overview' | 'expeditions' | 'crew' | 'shipyard' | 'resources' | 'results' | 'history'
type Notice = { kind: 'ok' | 'error'; text: string } | null
type Perform = <T>(work: () => Promise<T>, success: string, after?: (result: T) => void) => Promise<void>
type ModuleProps = { state: DemoState; session: Session; busy: boolean; perform: Perform }

const roleNames: Record<Role, string> = {
  JARL: 'Ярл', WARRIOR: 'Воин', SHIPBUILDER: 'Кораблестроитель', PRIEST: 'Жрец'
}
const resourceNames: Record<string, string> = {
  WOOD: 'Дерево', CLOTH: 'Ткань', RESIN: 'Смола',
  GOLD: 'Золото', PROVISIONS: 'Провизия', THRALLS: 'Пленные'
}

function App() {
  const [session, setSession] = useState<Session | null>(() => {
    const saved = localStorage.getItem('drakkar-session')
    return saved ? JSON.parse(saved) as Session : null
  })
  const [state, setState] = useState<DemoState | null>(null)
  const [tab, setTab] = useState<Tab>('overview')
  const [notice, setNotice] = useState<Notice>(null)
  const [busy, setBusy] = useState(false)

  const loadState = useCallback(async (activeSession = session) => {
    if (!activeSession) return
    try {
      setState(await api<DemoState>('/api/demo/state', activeSession.token))
    } catch (error) {
      if (error instanceof ApiError && (error.code === 'SESSION_INVALID' || error.code === 'AUTH_REQUIRED')) {
        localStorage.removeItem('drakkar-session')
        setSession(null)
      }
      setNotice({ kind: 'error', text: messageOf(error) })
    }
  }, [session])

  useEffect(() => { void loadState() }, [loadState])
  useEffect(() => {
    if (!notice) return
    const timer = window.setTimeout(() => setNotice(null), 5000)
    return () => window.clearTimeout(timer)
  }, [notice])

  async function login(username: string, password: string) {
    setBusy(true)
    try {
      const next = await api<Session>('/api/auth/login', undefined, { username, password })
      localStorage.setItem('drakkar-session', JSON.stringify(next))
      setSession(next)
      setTab('overview')
      await loadState(next)
      setNotice({ kind: 'ok', text: `Вход выполнен: ${next.displayName}` })
    } catch (error) {
      setNotice({ kind: 'error', text: messageOf(error) })
    } finally {
      setBusy(false)
    }
  }

  async function logout() {
    if (!session) return
    setBusy(true)
    try {
      await api<void>('/api/auth/logout', session.token, {})
    } finally {
      localStorage.removeItem('drakkar-session')
      setState(null)
      setSession(null)
      setBusy(false)
    }
  }

  async function perform<T>(work: () => Promise<T>, success: string, after?: (result: T) => void) {
    setBusy(true)
    try {
      const result = await work()
      after?.(result)
      await loadState()
      setNotice({ kind: 'ok', text: success })
    } catch (error) {
      setNotice({ kind: 'error', text: messageOf(error) })
    } finally {
      setBusy(false)
    }
  }

  if (!session) return <LoginScreen busy={busy} onLogin={login} notice={notice} />

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark">D</div><strong>Drakkar ERP</strong></div>
      <nav>
        <NavItem active={tab === 'overview'} icon="⌂" label="Обзор" onClick={() => setTab('overview')} />
        <NavItem active={tab === 'expeditions'} icon="↗" label="Походы" onClick={() => setTab('expeditions')} />
        <NavItem active={tab === 'crew'} icon="●" label="Команда" onClick={() => setTab('crew')} />
        <NavItem active={tab === 'shipyard'} icon="◇" label="Верфь" onClick={() => setTab('shipyard')} />
        <NavItem active={tab === 'resources'} icon="▦" label="Ресурсы" onClick={() => setTab('resources')} />
        <NavItem active={tab === 'results'} icon="○" label="Итоги" onClick={() => setTab('results')} />
        <NavItem active={tab === 'history'} icon="≡" label="История" onClick={() => setTab('history')} />
      </nav>
      <button className="logout-button" disabled={busy} onClick={() => void logout()}>Выйти</button>
    </aside>

    <main>
      <header className="topbar">
        <h1>{tabTitle(tab)}</h1>
        <div className="user-context">
          <div className="current-settlement"><small>Поселение</small><b>{state?.activeSettlementName ?? '—'}</b></div>
          <div className="current-user"><b>{session.displayName}</b><small>{roleNames[session.role]}</small></div>
        </div>
      </header>
      {notice && <div className={`notice ${notice.kind}`}><span>{notice.kind === 'ok' ? '✓' : '!'}</span>{notice.text}</div>}
      {!state ? <Loading /> : <div className="content">
        {tab === 'overview' && <Overview state={state} session={session} busy={busy} perform={perform} />}
        {tab === 'expeditions' && <ExpeditionsModule state={state} session={session} busy={busy} perform={perform} />}
        {tab === 'crew' && <CrewModule state={state} session={session} busy={busy} perform={perform} />}
        {tab === 'shipyard' && <ShipyardModule state={state} session={session} busy={busy} perform={perform} />}
        {tab === 'resources' && <ResourcesModule state={state} />}
        {tab === 'results' && <ResultsModule state={state} session={session} busy={busy} perform={perform} />}
        {tab === 'history' && <HistoryModule state={state} />}
      </div>}
    </main>
  </div>
}

function LoginScreen({ busy, onLogin, notice }: { busy: boolean; onLogin: (username: string, password: string) => Promise<void>; notice: Notice }) {
  const [username, setUsername] = useState('ragnar')
  const [password, setPassword] = useState('raven-2026')
  return <div className="login-page"><section className="login-panel">
    <div className="login-brand"><div className="brand-mark">D</div><strong>Drakkar ERP</strong></div>
    <h1>Вход в систему</h1><p>Введите данные своей учётной записи.</p>
    <form className="login-form" onSubmit={event => { event.preventDefault(); void onLogin(username, password) }}>
      <label><span>Логин</span><input autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} /></label>
      <label><span>Пароль</span><input autoComplete="current-password" type="password" value={password} onChange={event => setPassword(event.target.value)} /></label>
      <button className="primary" type="submit" disabled={busy || !username.trim() || !password}>{busy ? 'Вход…' : 'Войти'}</button>
    </form>
    <div className="demo-accounts"><span>Каттегат: ragnar / raven-2026</span><span>Бирка: erik / birka-2026</span></div>
    {notice && <div className={`notice ${notice.kind}`}>{notice.text}</div>}
  </section></div>
}

function Overview({ state, session, busy, perform }: ModuleProps) {
  const current = state.expeditions.filter(item => item.status !== 'COMPLETED')
  const completed = state.expeditions.filter(item => item.status === 'COMPLETED')
  const readyShips = state.ships.filter(item => item.stage === 4).length
  const stockTotal = state.stock.reduce((sum, item) => sum + item.quantity, 0)
  return <>
    <section className="metric-grid">
      <Metric label="Текущие походы" value={String(current.length)} detail="подготовка и плавание" tone="amber" />
      <Metric label="Готовые корабли" value={String(readyShips)} detail={`всего кораблей: ${state.ships.length}`} tone="blue" />
      <Metric label="Ресурсы" value={String(stockTotal)} detail="единиц на складе" tone="green" />
      <Metric label="Завершено" value={String(completed.length)} detail="походов в истории" tone="red" />
    </section>
    <section className="panel overview-list">
      <PanelHead overline="Ближайшие события" title="Текущие походы" />
      {current.length ? <ExpeditionList expeditions={current} /> : <Empty title="Нет текущих походов" />}
    </section>
    {session.role === 'JARL' && state.demoResetAvailable && <button className="text-button" disabled={busy} onClick={() => void perform(() => api('/api/demo/reset', session.token, {}), 'Демо-данные восстановлены')}>↻ Восстановить исходные данные</button>}
  </>
}

function ExpeditionsModule({ state, session, busy, perform }: ModuleProps) {
  const current = state.expeditions.filter(item => item.status !== 'COMPLETED' && item.status !== 'CANCELLED')
  const [selectedId, setSelectedId] = useState(current[0]?.id ?? '')
  const selected = current.find(item => item.id === selectedId) ?? current[0]
  useEffect(() => {
    if (selected && selected.id !== selectedId) setSelectedId(selected.id)
  }, [selected, selectedId])
  if (!current.length) return <section className="panel"><Empty title="Нет текущих походов" text="Завершённые походы находятся в истории" /></section>
  return <section className="list-detail">
    <div className="panel compact-list-panel">
      <PanelHead overline="В работе" title={`Походы · ${current.length}`} />
      <ExpeditionList expeditions={current} selectedId={selected?.id} onSelect={setSelectedId} />
    </div>
    <div className="panel expedition-detail-panel">
      {selected && <ExpeditionDetails expedition={selected} state={state} session={session} busy={busy} perform={perform} />}
    </div>
  </section>
}

function ExpeditionList({ expeditions, selectedId, onSelect }: { expeditions: Expedition[]; selectedId?: string; onSelect?: (id: string) => void }) {
  return <div className="expedition-list">
    {expeditions.map(expedition => <button key={expedition.id} className={selectedId === expedition.id ? 'expedition-list-item selected' : 'expedition-list-item'} onClick={() => onSelect?.(expedition.id)}>
      <span><b>{expedition.name}</b><small>{expedition.target}</small></span>
      <span><Status value={expedition.status} /><small>{dateOf(expedition.plannedDeparture)}</small></span>
    </button>)}
  </div>
}

function ExpeditionDetails({ expedition, state, session, busy, perform }: { expedition: Expedition } & ModuleProps) {
  const freeShips = state.ships.filter(ship => ship.available && ship.stage === 4)
  const [shipId, setShipId] = useState(freeShips[0]?.id ?? '')
  const [typeCode, setTypeCode] = useState(state.shipTypes[0]?.code ?? '')
  const [shipName, setShipName] = useState('Новый корабль')
  const selectedType = state.shipTypes.find(type => type.code === typeCode)
  const readyShortage = Math.max(0, expedition.requiredCapacity - expedition.readyCapacity)
  const plannedShortage = Math.max(0, expedition.requiredCapacity - expedition.plannedCapacity)
  const canManage = session.role === 'JARL' && expedition.status === 'PREPARATION'
  const expeditionCrew = state.crew.filter(member => member.expeditionId === expedition.id)
  const confirmedCrew = expeditionCrew.filter(member => member.participationStatus === 'CONFIRMED').length
  const pendingCrew = expeditionCrew.filter(member => member.participationStatus === 'PENDING').length
  const allShipsReady = expedition.fleet.length > 0 && expedition.fleet.every(ship => ship.ready)
  const canStart = readyShortage === 0 && allShipsReady && confirmedCrew > 0 && pendingCrew === 0
  return <>
    <div className="detail-heading">
      <div><span>Карточка похода</span><h2>{expedition.name}</h2><p>{expedition.target} · {dateOf(expedition.plannedDeparture)}</p></div>
      <Status value={expedition.status} />
    </div>
    <div className="capacity-block">
      <div><span>Готовая вместимость</span><b>{expedition.readyCapacity} / {expedition.requiredCapacity}</b></div>
      <div className="capacity-bar"><i style={{ width: `${Math.min(100, expedition.readyCapacity / expedition.requiredCapacity * 100)}%` }} /></div>
      <small>{readyShortage ? `Не хватает ${readyShortage} мест. С учётом строящихся: ${expedition.plannedCapacity}.` : 'Готовых мест достаточно для выхода.'}</small>
    </div>
    <h3>Флот похода</h3>
    <div className="fleet-list">
      {expedition.fleet.map(ship => <div key={ship.id}><span className={ship.ready ? 'ship-state ready' : 'ship-state building'}>{ship.ready ? '✓' : ship.stage}</span><span><b>{ship.name}</b><small>{ship.typeName} · {ship.capacity} мест</small></span><Status value={ship.ready ? 'READY' : 'IN_CONSTRUCTION'} />{canManage && <button className="remove-ship" aria-label={`Убрать корабль ${ship.name}`} title="Убрать из похода" disabled={busy} onClick={() => void perform(() => apiDelete(`/api/expeditions/${expedition.id}/ships/${ship.id}`, session.token), 'Корабль убран из похода')}>×</button>}</div>)}
      {!expedition.fleet.length && <Empty title="Корабли не назначены" />}
    </div>
    {canManage && <div className="launch-box">
      <div><b>Готовность к выходу</b><small>Поход начнётся, когда выполнены все условия</small></div>
      <ul>
        <li className={readyShortage === 0 ? 'ready' : ''}><span>{readyShortage === 0 ? '✓' : '·'}</span>Вместимость флота {expedition.readyCapacity} / {expedition.requiredCapacity}</li>
        <li className={allShipsReady ? 'ready' : ''}><span>{allShipsReady ? '✓' : '·'}</span>{allShipsReady ? 'Все корабли готовы' : 'Есть недостроенные корабли'}</li>
        <li className={confirmedCrew > 0 && pendingCrew === 0 ? 'ready' : ''}><span>{confirmedCrew > 0 && pendingCrew === 0 ? '✓' : '·'}</span>{confirmedCrew ? `Подтверждено участников: ${confirmedCrew}` : 'Нет подтверждённых участников'}{pendingCrew ? ` · ожидается ответ: ${pendingCrew}` : ''}</li>
      </ul>
      <button className="primary" disabled={busy || !canStart} onClick={() => void perform(() => api(`/api/expeditions/${expedition.id}/start`, session.token, { expectedVersion: expedition.version }), 'Поход начат')}>Начать поход</button>
    </div>}
    {canManage && <div className="fleet-actions">
      <div className="action-box">
        <b>Добавить готовый корабль</b>
        {freeShips.length ? <div className="inline-action"><select value={shipId} onChange={event => setShipId(event.target.value)}>{freeShips.map(ship => <option key={ship.id} value={ship.id}>{ship.name} · {ship.capacity} мест</option>)}</select><button className="secondary" disabled={busy || !shipId} onClick={() => void perform(() => api(`/api/expeditions/${expedition.id}/ships`, session.token, { shipId }), 'Корабль добавлен во флот')}>Добавить</button></div> : <small>Свободных готовых кораблей нет</small>}
      </div>
      <div className="action-box">
        <b>{plannedShortage ? `Запросить строительство · не хватает ${plannedShortage} мест` : 'Плановая вместимость набрана'}</b>
        <div className="request-fields"><input value={shipName} onChange={event => setShipName(event.target.value)} /><select value={typeCode} onChange={event => setTypeCode(event.target.value)}>{state.shipTypes.map(type => <option key={type.code} value={type.code}>{type.name} · {type.capacity} мест</option>)}</select></div>
        {selectedType && <div className="recipe-line">Нужно: {selectedType.recipe.map(item => `${resourceNames[item.resource]} ${item.quantity}`).join(' · ')}</div>}
        <button className="primary" disabled={busy || !shipName.trim() || !typeCode || plannedShortage === 0} onClick={() => void perform(() => api(`/api/expeditions/${expedition.id}/ship-requests`, session.token, { shipName, shipTypeCode: typeCode }), 'Заказ передан на верфь')}>{plannedShortage ? 'Передать заказ на верфь' : 'Новый корабль не требуется'}</button>
      </div>
    </div>}
    <AuditTimeline events={expedition.audit} />
  </>
}

function CrewModule({ state, session, busy, perform }: ModuleProps) {
  const candidates = state.expeditions.filter(item => item.status === 'PREPARATION')
  const ownActive = state.expeditions.find(item => state.crew.some(member => member.expeditionId === item.id && member.userId === session.userId && item.status !== 'COMPLETED'))
  const [expeditionId, setExpeditionId] = useState(ownActive?.id ?? candidates[0]?.id ?? state.expeditions[0]?.id ?? '')
  const expedition = state.expeditions.find(item => item.id === expeditionId) ?? ownActive ?? candidates[0]
  const members = state.crew.filter(item => item.expeditionId === expedition?.id)
  const [userId, setUserId] = useState(state.availableUsers[0]?.id ?? '')
  const [expeditionRole, setExpeditionRole] = useState('разведчик')
  const pendingMine = state.crew.find(item => item.userId === session.userId && item.participationStatus === 'PENDING')
  return <section className="module-grid">
    <div className="panel wide">
      <div className="module-title-row"><PanelHead overline="Состав" title={expedition?.name ?? 'Поход не найден'} />{candidates.length > 1 && <select value={expedition?.id} onChange={event => setExpeditionId(event.target.value)}>{candidates.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select>}</div>
      <div className="table"><div className="tr th"><span>Участник</span><span>Роль</span><span>Статус</span></div>{members.map(member => <div className="tr" key={member.id}><span><b>{member.userName}</b></span><span>{member.expeditionRole}</span><span><Status value={member.participationStatus} /></span></div>)}</div>
      {session.role === 'JARL' && expedition?.status === 'PREPARATION' && <div className="inline-form"><select value={userId} onChange={event => setUserId(event.target.value)}>{state.availableUsers.map(user => <option key={user.id} value={user.id}>{user.displayName}</option>)}</select><input value={expeditionRole} onChange={event => setExpeditionRole(event.target.value)} /><button className="primary" disabled={busy || !userId} onClick={() => void perform(() => api(`/api/expeditions/${expedition.id}/crew`, session.token, { userId, expeditionRole }), 'Участник добавлен')}>Добавить</button></div>}
    </div>
    <div className="panel side-panel"><PanelHead overline="Участие в походе" title="Ответ воина" />
      {session.role !== 'WARRIOR' ? <RolePrompt role="WARRIOR" /> : pendingMine ? <><div className="assignment"><span>Назначение</span><b>{pendingMine.expeditionRole}</b></div><div className="button-stack"><button className="primary" disabled={busy} onClick={() => void perform(() => api(`/api/crew/${pendingMine.id}/decision`, session.token, { decision: 'CONFIRMED', expectedVersion: pendingMine.version }), 'Участие подтверждено')}>Подтвердить</button><button className="secondary danger" disabled={busy} onClick={() => void perform(() => api(`/api/crew/${pendingMine.id}/decision`, session.token, { decision: 'DECLINED', expectedVersion: pendingMine.version }), 'Отказ зафиксирован')}>Отказаться</button></div></> : <Empty title="Нет ожидающих назначений" />}
    </div>
  </section>
}

function ShipyardModule({ state, session, busy, perform }: ModuleProps) {
  const [selectedId, setSelectedId] = useState(state.ships[0]?.id ?? '')
  const ship = state.ships.find(item => item.id === selectedId) ?? state.ships[0]
  if (!ship) return <section className="panel"><Empty title="На верфи нет заказов" text={session.role === 'PRIEST' ? 'Нет кораблей, ожидающих благословения' : undefined} /></section>
  return <section className="list-detail shipyard-layout">
    <div className="panel compact-list-panel"><PanelHead overline="Корабли" title={`Верфь · ${state.ships.length}`} /><div className="ship-order-list">{state.ships.map(item => <button key={item.id} className={ship.id === item.id ? 'selected' : ''} onClick={() => setSelectedId(item.id)}><span><b>{item.name}</b><small>{item.typeName} · {item.capacity} мест</small></span><span><Status value={item.stage === 4 ? 'READY' : 'IN_CONSTRUCTION'} /><small>{item.progress}%</small></span></button>)}</div></div>
    <div className="panel ship-panel">
      <div className="detail-heading"><div><span>{ship.requestStatus ? 'Заказ на строительство' : 'Корабль'}</span><h2>{ship.name}</h2><p>{ship.expeditionName ? `Для похода «${ship.expeditionName}»` : `${ship.typeName} · ${ship.capacity} мест`}</p></div><Status value={ship.stage === 4 ? 'READY' : 'IN_CONSTRUCTION'} /></div>
      <div className="progress-head"><span>Текущий этап</span><b>{ship.stageName}</b><em>{ship.progress}%</em></div><div className="progress"><i style={{ width: `${ship.progress}%` }} /></div>
      <div className="stage-track">{['Лес', 'Каркас', 'Обшивка', 'Оснастка', 'Готов'].map((name, index) => <div key={name} className={index < ship.stage ? 'done' : index === ship.stage ? 'current' : ''}><span>{index < ship.stage ? '✓' : index + 1}</span><small>{name}</small></div>)}</div>
      <h3>Для текущего этапа</h3><div className="requirement-grid">{ship.requirements.length ? ship.requirements.map(req => <div key={req.resource} className={req.available >= req.quantity ? 'requirement enough' : 'requirement shortage'}><span>{resourceNames[req.resource]}</span><b>{req.quantity}</b><small>на складе {req.available}</small></div>) : <Empty title="Ресурсы не требуются" />}</div>
      {session.role === 'SHIPBUILDER' && <button className="primary large" disabled={busy || ship.stage === 4} onClick={() => void perform(() => api(`/api/ships/${ship.id}/complete-stage`, session.token, { expectedVersion: ship.version }), 'Этап завершён, склад обновлён')}>Завершить этап →</button>}
      {ship.stage === 3 && <div className="blessing-inline"><div className={ship.blessed ? 'seal blessed' : 'seal'}>ᛉ</div><div><b>{ship.blessed ? 'Блот проведён' : 'Ожидается Жрец'}</b><small>Без подтверждения финальный этап не завершить</small></div>{session.role === 'PRIEST' && <button className="primary" disabled={busy || ship.blessed} onClick={() => void perform(() => api(`/api/ships/${ship.id}/bless`, session.token, {}), 'Благословение подтверждено')}>Подтвердить Блот</button>}</div>}
    </div>
  </section>
}

function ResourcesModule({ state }: { state: DemoState }) {
  if (!state.stock.length) return <section className="panel"><Empty title="Ресурсы недоступны для этой роли" /></section>
  return <section className="resource-layout">
    <div className="panel"><PanelHead overline="Текущие остатки" title="Склад" /><div className="stock-grid">{state.stock.map(item => <div key={item.resource}><span>{resourceNames[item.resource] ?? item.resource}</span><b>{item.quantity}</b><small>единиц</small></div>)}</div></div>
    <div className="panel"><PanelHead overline="Нормативы верфи" title="Рецепты кораблей" /><div className="recipe-cards">{state.shipTypes.map(type => <div key={type.code}><div><b>{type.name}</b><span>{type.capacity} мест</span></div><ul>{type.recipe.map(item => <li key={item.resource}><span>{resourceNames[item.resource]}</span><b>{item.quantity}</b></li>)}</ul></div>)}</div></div>
  </section>
}

function ResultsModule({ state, session, busy, perform }: ModuleProps) {
  const sailing = state.expeditions.filter(item => item.status === 'SAILING')
  const [selectedId, setSelectedId] = useState(sailing[0]?.id ?? '')
  const expedition = sailing.find(item => item.id === selectedId) ?? sailing[0]
  const crew = state.crew.filter(item => item.expeditionId === expedition?.id)
  const [loot, setLoot] = useState<Loot>({ gold: 100, provisions: 50, thralls: 10 })
  const [fallen, setFallen] = useState<string[]>([])
  const [preview, setPreview] = useState<Allocation[]>([])
  const payload = { loot, fallenAssignmentIds: fallen, expectedVersion: expedition?.version ?? 0 }
  function setResource(key: keyof Loot, value: string) { setLoot(old => ({ ...old, [key]: Math.max(0, Number(value) || 0) })) }
  function toggleFallen(id: string) { setFallen(old => old.includes(id) ? old.filter(item => item !== id) : [...old, id]) }
  useEffect(() => { setFallen([]); setPreview([]) }, [expedition?.id])
  if (!expedition) return <section className="panel"><Empty title="Нет похода в плавании" /></section>
  return <section className="module-grid results-grid"><div className="panel wide"><div className="module-title-row"><PanelHead overline="Завершение похода" title={expedition.name} />{sailing.length > 1 && <select value={expedition.id} onChange={event => setSelectedId(event.target.value)}>{sailing.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select>}</div>
    <h3>Фактическая добыча</h3><div className="loot-grid">{(['gold', 'provisions', 'thralls'] as (keyof Loot)[]).map(key => <label key={key}><span>{resourceNames[key.toUpperCase()]}</span><input type="number" min="0" value={loot[key]} onChange={event => setResource(key, event.target.value)} /></label>)}</div>
    <h3>Состав и потери</h3><div className="casualty-list">{crew.map(member => <label key={member.id} className={fallen.includes(member.id) ? 'fallen' : ''}><input type="checkbox" checked={fallen.includes(member.id)} onChange={() => toggleFallen(member.id)} /><span>{member.userName}<small>{member.expeditionRole}</small></span><b>{fallen.includes(member.id) ? 'Погиб' : 'Выжил'}</b></label>)}</div>
    {session.role === 'JARL' ? <div className="button-row"><button className="secondary" disabled={busy} onClick={() => void perform(() => api<Allocation[]>(`/api/expeditions/${expedition.id}/finalization-preview`, session.token, payload), 'Предварительный расчёт готов', setPreview)}>Рассчитать Вергельд</button><button className="primary" disabled={busy || !preview.length} onClick={() => void perform(() => api<Allocation[]>(`/api/expeditions/${expedition.id}/finalize`, session.token, payload), 'Итоги утверждены', setPreview)}>Утвердить итоги</button></div> : <RolePrompt role="JARL" />}
  </div><div className="panel side-panel allocation-panel"><PanelHead overline="Предварительный расчёт" title="Распределение" />{preview.length ? <div className="allocations">{preview.map((item, index) => <div key={`${item.recipient}-${index}`}><span><b>{item.recipient}</b><small>{item.category}</small></span><code>{item.loot.gold} · {item.loot.provisions} · {item.loot.thralls}</code></div>)}</div> : <Empty title="Расчёт ещё не выполнен" />}</div></section>
}

function HistoryModule({ state }: { state: DemoState }) {
  const completed = state.expeditions.filter(item => item.status === 'COMPLETED')
  const [selectedId, setSelectedId] = useState(completed[0]?.id ?? '')
  const selected = completed.find(item => item.id === selectedId) ?? completed[0]
  if (!completed.length) return <section className="panel"><Empty title="Завершённых походов пока нет" /></section>
  return <section className="list-detail">
    <div className="panel compact-list-panel"><PanelHead overline="Архив" title={`Завершённые · ${completed.length}`} /><ExpeditionList expeditions={completed} selectedId={selected?.id} onSelect={setSelectedId} /></div>
    <div className="panel history-card">{selected && <><div className="detail-heading"><div><span>Завершённый поход</span><h2>{selected.name}</h2><p>{selected.target} · {dateOf(selected.plannedDeparture)}</p></div><Status value={selected.status} /></div>
      <div className="history-summary"><div><span>Золото</span><b>{selected.loot?.gold ?? 0}</b></div><div><span>Провизия</span><b>{selected.loot?.provisions ?? 0}</b></div><div><span>Пленные</span><b>{selected.loot?.thralls ?? 0}</b></div><div><span>Корабли</span><b>{selected.fleet.length}</b></div></div>
      <h3>Флот</h3><div className="fleet-chips">{selected.fleet.map(ship => <span key={ship.id}>{ship.name}<small>{ship.typeName} · {ship.capacity} мест</small></span>)}</div>
      <AuditTimeline events={selected.audit} />
    </>}</div>
  </section>
}

function AuditTimeline({ events }: { events: Expedition['audit'] }) {
  return <div className="expedition-audit"><h3>История изменений</h3>{events.length ? <div className="audit-list">{events.map(event => <div key={event.id}><time>{dateTimeOf(event.happenedAt)}</time><i /><span><b>{eventLabel(event.eventType)}</b><small>{roleNames[event.actorRole]}</small></span></div>)}</div> : <Empty title="Изменений пока нет" />}</div>
}

function Metric({ label, value, detail, tone }: { label: string; value: string; detail: string; tone: string }) { return <div className={`metric ${tone}`}><span>{label}</span><strong>{value}</strong><small>{detail}</small></div> }
function PanelHead({ overline, title }: { overline: string; title: string }) { return <div className="panel-head"><span>{overline}</span><h2>{title}</h2></div> }
function NavItem({ active, icon, label, onClick }: { active: boolean; icon: string; label: string; onClick: () => void }) { return <button className={active ? 'nav-item active' : 'nav-item'} onClick={onClick}><span>{icon}</span>{label}</button> }
function RolePrompt({ role }: { role: Role }) { return <div className="role-prompt"><span>⚿</span><div><b>Действие выполняет «{roleNames[role]}»</b><small>Войдите под учётной записью с этой ролью</small></div></div> }
function Empty({ title, text }: { title: string; text?: string }) { return <div className="empty-state"><b>{title}</b>{text && <span>{text}</span>}</div> }
function Loading() { return <div className="loading"><i /><span>Загрузка…</span></div> }
function Status({ value }: { value: string }) { const labels: Record<string, string> = { PREPARATION: 'Подготовка', SAILING: 'В плавании', COMPLETED: 'Завершён', PENDING: 'Ожидает ответа', CONFIRMED: 'Подтверждено', DECLINED: 'Отказ', READY: 'Готов', IN_CONSTRUCTION: 'Строится' }; return <span className={`status ${value.toLowerCase()}`}>{labels[value] ?? value}</span> }
function messageOf(error: unknown) { return error instanceof Error ? error.message : 'Неизвестная ошибка' }
function dateOf(value: string) { return new Date(`${value}T12:00:00`).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' }) }
function dateTimeOf(value: string) { return new Date(value).toLocaleString('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) }
function tabTitle(tab: Tab) { return ({ overview: 'Обзор', expeditions: 'Походы', crew: 'Команда', shipyard: 'Верфь', resources: 'Ресурсы', results: 'Итоги похода', history: 'История походов' } as const)[tab] }
function eventLabel(value: string) { const labels: Record<string, string> = { CREW_MEMBER_ASSIGNED: 'Участник добавлен в команду', PARTICIPATION_CONFIRMED: 'Участие подтверждено', PARTICIPATION_DECLINED: 'Участник отказался от похода', SHIP_ASSIGNED: 'Корабль добавлен во флот', SHIP_REMOVED: 'Корабль убран из похода', SHIP_BUILD_REQUESTED: 'Запрошено строительство корабля', SHIP_STAGE_COMPLETED: 'Этап строительства завершён', SHIP_BLESSED: 'Корабль благословлён', EXPEDITION_STARTED: 'Поход начат', EXPEDITION_FINALIZED: 'Итоги похода утверждены', EXPEDITION_PLANNED: 'Поход запланирован' }; return labels[value] ?? 'Изменение сохранено' }

export default App
