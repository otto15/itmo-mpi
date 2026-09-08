export type Role = 'JARL' | 'WARRIOR' | 'SHIPBUILDER' | 'PRIEST'

export type Session = {
  token: string
  expiresAt: string
  userId: number
  displayName: string
  role: Role
}

export type Loot = { gold: number; provisions: number; thralls: number }

export type Expedition = {
  id: number
  name: string
  target: string
  status: 'PREPARATION' | 'SAILING' | 'COMPLETED' | 'CANCELLED'
  plannedDeparture: string
  crewSize: number
  readyCapacity: number
  plannedCapacity: number
  fleet: FleetShip[]
  audit: Audit[]
  version: number
  immutable: boolean
  loot?: Loot
  preparation?: Preparation
}

export type RoutePoint = { name: string; distanceKm: number }
export type Supply = { resource: string; quantity: number }
export type Departure = {
  crew: { id: number; userId: number; name: string; role: string }[]
  fleet: { id: number; name: string; type: string; capacity: number }[]
  route: RoutePoint[]
  resources: Supply[]
}
export type Preparation = {
  route: RoutePoint[]
  requirements: Supply[]
  reservations: { id: number; status: string; expiresAt: string; resources: Supply[] }[]
  readiness: { ready: boolean; blockers: { code: string; message: string }[] }
  startedAt?: string
  snapshot?: Departure
}

export type Crew = {
  id: number
  expeditionId: number
  userId: number
  userName: string
  expeditionRole: string
  participationStatus: 'PENDING' | 'CONFIRMED' | 'DECLINED'
  alive: boolean
  version: number
}

export type User = { id: number; displayName: string; systemRole: string }
export type Requirement = { resource: string; quantity: number; available: number }
export type FleetShip = {
  id: number
  name: string
  typeName: string
  capacity: number
  stage: number
  ready: boolean
  requestStatus: string
}
export type Ship = {
  id: number
  name: string
  typeCode: string
  typeName: string
  capacity: number
  stage: number
  stageName: string
  progress: number
  blessed: boolean
  version: number
  available: boolean
  expeditionId?: number | null
  expeditionName?: string | null
  requestStatus?: string | null
  requirements: Requirement[]
}
export type ShipType = {
  code: string
  name: string
  capacity: number
  recipe: { resource: string; quantity: number }[]
}
export type Stock = { resource: string; quantity: number; version: number; reserved: number; available: number }
export type Allocation = { expeditionId: number; recipient: string; category: string; loot: Loot }
export type Audit = {
  id: number
  happenedAt: string
  actorRole: Role | 'SYSTEM'
  actorName?: string
  eventType: string
  aggregateType: string
  aggregateId: number
  details: string
}

export type DemoState = {
  expeditions: Expedition[]
  crew: Crew[]
  availableUsers: User[]
  ships: Ship[]
  shipTypes: ShipType[]
  stock: Stock[]
  allocations: Allocation[]
  activeSettlementName: string
  demoResetAvailable: boolean
}
