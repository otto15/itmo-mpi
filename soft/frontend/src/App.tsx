import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, ApiError } from './api'
import type { Allocation, Crew, DemoState, Loot, Role, Session } from './types'

type Tab = 'overview' | 'crew' | 'shipyard' | 'results' | 'audit'
type Notice = { kind: 'ok' | 'error'; text: string } | null

const roleNames: Record<Role, string> = {
  JARL: 'Ярл',
  WARRIOR: 'Воин',
  SHIPBUILDER: 'Кораблестроитель',
  PRIEST: 'Жрец'
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
      const next = await api<Session>('/api/auth/login', undefined, {
        username,
        password
      })
      localStorage.setItem('drakkar-session', JSON.stringify(next))
      setSession(next)
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

  if (!session) {
    return <LoginScreen busy={busy} onLogin={login} notice={notice} />
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">D</div>
          <strong>Drakkar ERP</strong>
        </div>
        <nav>
          <NavItem active={tab === 'overview'} icon="⌂" label="Обзор" onClick={() => setTab('overview')} />
          <NavItem active={tab === 'crew'} icon="●" label="Команда" onClick={() => setTab('crew')} />
          <NavItem active={tab === 'shipyard'} icon="◇" label="Верфь" onClick={() => setTab('shipyard')} />
          <NavItem active={tab === 'results'} icon="○" label="Итоги похода" onClick={() => setTab('results')} />
          <NavItem active={tab === 'audit'} icon="≡" label="История" onClick={() => setTab('audit')} />
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
        {!state ? <Loading /> : (
          <div className="content">
            {tab === 'overview' && <Overview state={state} session={session} busy={busy} perform={perform} />}
            {tab === 'crew' && <CrewModule state={state} session={session} busy={busy} perform={perform} setNotice={setNotice} loadState={loadState} />}
            {tab === 'shipyard' && <ShipyardModule state={state} session={session} busy={busy} perform={perform} />}
            {tab === 'results' && <ResultsModule state={state} session={session} busy={busy} perform={perform} />}
            {tab === 'audit' && <AuditModule state={state} />}
          </div>
        )}
      </main>
    </div>
  )
}

function LoginScreen({ busy, onLogin, notice }: { busy: boolean; onLogin: (username: string, password: string) => Promise<void>; notice: Notice }) {
  const [username, setUsername] = useState('ragnar')
  const [password, setPassword] = useState('raven-2026')

  return (
    <div className="login-page">
      <section className="login-panel">
        <div className="login-brand">
          <div className="brand-mark">D</div>
          <strong>Drakkar ERP</strong>
        </div>
        <h1>Вход в систему</h1>
        <p>Введите данные своей учётной записи.</p>
        <form className="login-form" onSubmit={event => { event.preventDefault(); void onLogin(username, password) }}>
          <label><span>Логин</span><input autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} /></label>
          <label><span>Пароль</span><input autoComplete="current-password" type="password" value={password} onChange={event => setPassword(event.target.value)} /></label>
          <button className="primary" type="submit" disabled={busy || !username.trim() || !password}>{busy ? 'Вход…' : 'Войти'}</button>
        </form>
        <small className="demo-hint">Демо-доступ: ragnar / raven-2026</small>
        {notice && <div className={`notice ${notice.kind}`}>{notice.text}</div>}
      </section>
    </div>
  )
}

function Overview({ state, session, busy, perform }: ModuleProps) {
  const preparation = state.expeditions.find(item => item.status === 'PREPARATION')
  const sailing = state.expeditions.find(item => item.status === 'SAILING')
  const prepCrew = state.crew.filter(item => item.expeditionId === preparation?.id)
  const ready = prepCrew.length ? Math.round(prepCrew.filter(item => item.participationStatus === 'CONFIRMED').length / prepCrew.length * 100) : 0
  const coreStock = state.stock.filter(item => ['WOOD', 'CLOTH', 'RESIN'].includes(item.resource)).reduce((sum, item) => sum + item.quantity, 0)
  return (
    <>
      <section className="metric-grid">
        <Metric label="Команда готова" value={`${ready}%`} detail={`${prepCrew.filter(x => x.participationStatus === 'CONFIRMED').length} из ${prepCrew.length} подтвердили`} tone="amber" />
        <Metric label="Корабль" value={`${state.ship?.progress ?? 0}%`} detail={state.ship?.stageName ?? 'не добавлен'} tone="blue" />
        <Metric label="Ресурсы верфи" value={String(coreStock)} detail="единиц на складе" tone="green" />
        <Metric label="Активный поход" value={sailing ? '1' : '0'} detail={sailing?.name ?? 'нет'} tone="red" />
      </section>
      <section className="two-column">
        <div className="panel expedition-card">
          <PanelHead overline="Сейчас в пути" title={sailing?.name ?? 'Нет активного похода'} />
          {sailing && <>
            <div className="route-line"><span>{state.activeSettlementName}</span><i /><b>{sailing.target}</b></div>
            <dl className="facts"><div><dt>Корабль</dt><dd>{sailing.shipName}</dd></div><div><dt>Выход</dt><dd>{dateOf(sailing.plannedDeparture)}</dd></div><div><dt>Статус</dt><dd><Status value={sailing.status} /></dd></div></dl>
          </>}
        </div>
        <div className="panel expedition-card">
          <PanelHead overline="Готовится" title={preparation?.name ?? 'Нет запланированного похода'} />
          {preparation && <>
            <div className="route-line"><span>{preparation.shipName}</span><i /><b>{preparation.target}</b></div>
            <dl className="facts"><div><dt>Выход</dt><dd>{dateOf(preparation.plannedDeparture)}</dd></div><div><dt>Команда</dt><dd>{prepCrew.length} участников</dd></div><div><dt>Готовность</dt><dd>{ready}%</dd></div></dl>
          </>}
        </div>
      </section>
      {session.role === 'JARL' && state.demoResetAvailable && <button className="text-button" disabled={busy} onClick={() => void perform(() => api('/api/demo/reset', session.token, {}), 'Демо-данные восстановлены')}>↻ Восстановить исходные данные</button>}
    </>
  )
}

function CrewModule({ state, session, busy, perform, setNotice, loadState }: ModuleProps & { setNotice: (n: Notice) => void; loadState: () => Promise<void> }) {
  const expedition = state.expeditions.find(item => item.status === 'PREPARATION')
  const members = state.crew.filter(item => item.expeditionId === expedition?.id)
  const [userId, setUserId] = useState(state.availableUsers[0]?.id ?? '')
  const [expeditionRole, setExpeditionRole] = useState('разведчик')
  const pendingMine = members.find(item => item.userId === session.userId && item.participationStatus === 'PENDING')

  async function race(member: Crew) {
    setNotice(null)
    const body = { decision: 'CONFIRMED', expectedVersion: member.version }
    const attempts = await Promise.allSettled([
      api(`/api/crew/${member.id}/decision`, session.token, body),
      api(`/api/crew/${member.id}/decision`, session.token, body)
    ])
    await loadState()
    const rejected = attempts.find(item => item.status === 'rejected') as PromiseRejectedResult | undefined
    setNotice({ kind: rejected ? 'ok' : 'error', text: rejected ? `Один запрос принят, второй отклонён: ${messageOf(rejected.reason)}` : 'Конфликт не воспроизведён' })
  }

  return <section className="module-grid">
    <div className="panel wide">
      <PanelHead overline="Состав похода" title={expedition?.name ?? 'Поход не найден'} />
      <div className="table">
        <div className="tr th"><span>Участник</span><span>Роль</span><span>Статус</span></div>
        {members.map(member => <div className="tr" key={member.id}><span><b>{member.userName}</b></span><span>{member.expeditionRole}</span><span><Status value={member.participationStatus} /></span></div>)}
      </div>
      {session.role === 'JARL' && <div className="inline-form">
        <select value={userId} onChange={event => setUserId(event.target.value)}>{state.availableUsers.map(user => <option key={user.id} value={user.id}>{user.displayName}</option>)}</select>
        <input value={expeditionRole} onChange={event => setExpeditionRole(event.target.value)} />
        <button className="primary" disabled={busy || !userId || !expedition} onClick={() => expedition && void perform(() => api(`/api/expeditions/${expedition.id}/crew`, session.token, { userId, expeditionRole }), 'Участник добавлен')}>Добавить</button>
      </div>}
    </div>
    <div className="panel side-panel">
      <PanelHead overline="Участие в походе" title="Ответ воина" />
      {session.role !== 'WARRIOR' ? <RolePrompt role="WARRIOR" /> : pendingMine ? <>
        <div className="assignment"><span>Назначение</span><b>{pendingMine.expeditionRole}</b></div>
        <div className="button-stack">
          <button className="primary" disabled={busy} onClick={() => void perform(() => api(`/api/crew/${pendingMine.id}/decision`, session.token, { decision: 'CONFIRMED', expectedVersion: pendingMine.version }), 'Участие подтверждено')}>Подтвердить</button>
          <button className="secondary danger" disabled={busy} onClick={() => void perform(() => api(`/api/crew/${pendingMine.id}/decision`, session.token, { decision: 'DECLINED', expectedVersion: pendingMine.version }), 'Отказ зафиксирован')}>Отказаться</button>
          <button className="race-button" disabled={busy} onClick={() => void race(pendingMine)}>⚡ Отправить два ответа одновременно</button>
        </div>
      </> : <div className="empty-state"><b>Нет ожидающих назначений</b><span>Восстановите демо из обзора</span></div>}
    </div>
  </section>
}

function ShipyardModule({ state, session, busy, perform }: ModuleProps) {
  const ship = state.ship
  if (!ship) return session.role === 'JARL'
    ? <section className="panel access-denied"><span>◇</span><h2>Кораблей пока нет</h2><p>В этом поселении ещё не добавлен корабль.</p></section>
    : <AccessDenied module="Верфь" />
  return <section className="module-grid">
    <div className="panel wide ship-panel">
      <PanelHead overline="Строительство" title={ship.name} />
      <div className="progress-head"><span>Текущий этап</span><b>{ship.stageName}</b><em>{ship.progress}%</em></div>
      <div className="progress"><i style={{ width: `${ship.progress}%` }} /></div>
      <div className="stage-track">{['Лес', 'Каркас', 'Обшивка', 'Оснастка', 'Готов'].map((name, index) => <div key={name} className={index < ship.stage ? 'done' : index === ship.stage ? 'current' : ''}><span>{index < ship.stage ? '✓' : index + 1}</span><small>{name}</small></div>)}</div>
      <h3>Необходимо для закрытия</h3>
      <div className="requirement-grid">{ship.requirements.length ? ship.requirements.map(req => <div key={req.resource} className={req.available >= req.quantity ? 'requirement enough' : 'requirement shortage'}><span>{resourceNames[req.resource]}</span><b>{req.quantity}</b><small>на складе {req.available}</small></div>) : <div className="empty-state"><b>Ресурсы не требуются</b></div>}</div>
      {session.role === 'SHIPBUILDER' ? <button className="primary large" disabled={busy || ship.stage === 4} onClick={() => void perform(() => api(`/api/ships/${ship.id}/complete-stage`, session.token, { expectedVersion: ship.version }), 'Этап завершён, склад обновлён')}>Завершить этап →</button> : <RolePrompt role="SHIPBUILDER" />}
    </div>
    <div className="panel side-panel blessing-card">
      <PanelHead overline="Перед спуском на воду" title="Благословение" />
      <div className={ship.blessed ? 'seal blessed' : 'seal'}>ᛉ</div>
      <b>{ship.blessed ? 'Блот проведён' : 'Ожидается Жрец'}</b>
      <p>Без этой отметки финальный этап будет отклонён сервером.</p>
      {session.role === 'PRIEST' ? <button className="primary" disabled={busy || ship.blessed || ship.stage !== 3} onClick={() => void perform(() => api(`/api/ships/${ship.id}/bless`, session.token, {}), 'Благословение подтверждено')}>Подтвердить Блот</button> : <RolePrompt role="PRIEST" />}
    </div>
  </section>
}

function ResultsModule({ state, session, busy, perform }: ModuleProps) {
  const expedition = state.expeditions.find(item => item.status === 'SAILING' || item.status === 'COMPLETED')
  const crew = state.crew.filter(item => item.expeditionId === expedition?.id)
  const [loot, setLoot] = useState<Loot>({ gold: 100, provisions: 50, thralls: 10 })
  const [fallen, setFallen] = useState<string[]>([])
  const [preview, setPreview] = useState<Allocation[]>([])
  const payload = { loot, fallenAssignmentIds: fallen, expectedVersion: expedition?.version ?? 0 }
  function setResource(key: keyof Loot, value: string) { setLoot(old => ({ ...old, [key]: Math.max(0, Number(value) || 0) })) }
  function toggleFallen(id: string) { setFallen(old => old.includes(id) ? old.filter(item => item !== id) : [...old, id]) }
  if (!expedition) return <div className="empty-state"><b>Поход не найден</b></div>
  return <section className="module-grid results-grid">
    <div className="panel wide">
      <PanelHead overline="Завершение похода" title={expedition.name} />
      {expedition.immutable && <div className="immutable-banner"><span>✓</span><div><b>Итоги утверждены</b><small>Эти данные больше нельзя изменить</small></div></div>}
      <h3>Фактическая добыча</h3>
      <div className="loot-grid">{(['gold', 'provisions', 'thralls'] as (keyof Loot)[]).map(key => <label key={key}><span>{resourceNames[key.toUpperCase()]}</span><input type="number" min="0" value={expedition.loot?.[key] ?? loot[key]} disabled={expedition.immutable} onChange={event => setResource(key, event.target.value)} /></label>)}</div>
      <h3>Состав и потери</h3>
      <div className="casualty-list">{crew.map(member => <label key={member.id} className={fallen.includes(member.id) || !member.alive ? 'fallen' : ''}><input type="checkbox" disabled={expedition.immutable} checked={fallen.includes(member.id) || !member.alive} onChange={() => toggleFallen(member.id)} /><span>{member.userName}<small>{member.expeditionRole}</small></span><b>{fallen.includes(member.id) || !member.alive ? 'Погиб' : 'Выжил'}</b></label>)}</div>
      {!expedition.immutable && (session.role === 'JARL' ? <div className="button-row">
        <button className="secondary" disabled={busy} onClick={() => void perform(() => api<Allocation[]>(`/api/expeditions/${expedition.id}/finalization-preview`, session.token, payload), 'Предварительный расчёт готов', setPreview)}>Рассчитать Вергельд</button>
        <button className="primary" disabled={busy || !preview.length} onClick={() => void perform(() => api<Allocation[]>(`/api/expeditions/${expedition.id}/finalize`, session.token, payload), 'Итоги утверждены', setPreview)}>Утвердить итоги</button>
      </div> : <RolePrompt role="JARL" />)}
    </div>
    <div className="panel side-panel allocation-panel">
      <PanelHead overline="Правило 20 / 80" title="Распределение" />
      <p>Доля ярла — 20%. Остаток делится поровну между выжившими и семьями погибших.</p>
      <div className="allocations">{(preview.length ? preview : state.allocations).map((item, index) => <div key={`${item.recipient}-${index}`}><span><b>{item.recipient}</b><small>{item.category}</small></span><code>{item.loot.gold} зл · {item.loot.provisions} пр · {item.loot.thralls} пл</code></div>)}</div>
      {!preview.length && !state.allocations.length && <div className="empty-state"><b>Ещё не рассчитано</b><span>Введите добычу и потери</span></div>}
    </div>
  </section>
}

function AuditModule({ state }: { state: DemoState }) {
  return <section className="panel audit-panel"><PanelHead overline="История изменений" title="Последние события" /><div className="audit-list">{state.audit.map(event => <div key={event.id}><time>{new Date(event.happenedAt).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</time><span className="audit-dot" /><div><b>{eventLabel(event.eventType)}</b><small>{roleNames[event.actorRole]}</small></div></div>)}</div>{!state.audit.length && <div className="empty-state"><b>История пока пуста</b><span>Выполните любое действие</span></div>}</section>
}

type ModuleProps = { state: DemoState; session: Session; busy: boolean; perform: <T>(work: () => Promise<T>, success: string, after?: (result: T) => void) => Promise<void> }
function NavItem({ active, icon, label, onClick }: { active: boolean; icon: string; label: string; onClick: () => void }) { return <button className={active ? 'nav-item active' : 'nav-item'} onClick={onClick}><span>{icon}</span>{label}</button> }
function Metric({ label, value, detail, tone }: { label: string; value: string; detail: string; tone: string }) { return <div className={`metric ${tone}`}><span>{label}</span><strong>{value}</strong><small>{detail}</small><i /></div> }
function PanelHead({ overline, title }: { overline: string; title: string }) { return <div className="panel-head"><span>{overline}</span><h2>{title}</h2></div> }
function RolePrompt({ role }: { role: Role }) { return <div className="role-prompt"><span>⚿</span><div><b>Нужна роль «{roleNames[role]}»</b><small>Войдите под учётной записью с этой ролью</small></div></div> }
function AccessDenied({ module }: { module: string }) { return <section className="panel access-denied"><span>⚿</span><h2>{module}</h2><p>Данные модуля не возвращаются сервером для текущей роли.</p></section> }
function Loading() { return <div className="loading"><i /><span>Поднимаем паруса…</span></div> }
function Status({ value }: { value: string }) { const labels: Record<string, string> = { PREPARATION: 'Подготовка', SAILING: 'В плавании', COMPLETED: 'Завершён', PENDING: 'Ожидает ответа', CONFIRMED: 'Подтверждено', DECLINED: 'Отказ' }; return <span className={`status ${value.toLowerCase()}`}>{labels[value] ?? value}</span> }
function messageOf(error: unknown) { return error instanceof Error ? error.message : 'Неизвестная ошибка' }
function dateOf(value: string) { return new Date(`${value}T12:00:00`).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' }) }
function tabTitle(tab: Tab) { return ({ overview: 'Обзор', crew: 'Команда', shipyard: 'Верфь', results: 'Итоги похода', audit: 'История' } as const)[tab] }
function eventLabel(value: string) {
  const labels: Record<string, string> = {
    CREW_MEMBER_ASSIGNED: 'Участник добавлен в команду',
    PARTICIPATION_CONFIRMED: 'Участие подтверждено',
    PARTICIPATION_DECLINED: 'Участник отказался от похода',
    SHIP_STAGE_COMPLETED: 'Этап строительства завершён',
    SHIP_BLESSED: 'Корабль благословлён',
    EXPEDITION_FINALIZED: 'Итоги похода утверждены',
    SETTLEMENT_CREATED: 'Поселение добавлено'
  }
  return labels[value] ?? 'Изменение сохранено'
}

export default App
