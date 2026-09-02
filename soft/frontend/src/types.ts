export type Role = 'JARL' | 'WARRIOR' | 'SHIPBUILDER' | 'PRIEST'

export type Session = {
  token: string
  expiresAt: string
  userId: string
  displayName: string
  role: Role
}

export type Loot = { gold: number; provisions: number; thralls: number }

export type Expedition = {
  id: string
  name: string
  target: string
  status: 'PREPARATION' | 'SAILING' | 'COMPLETED' | 'CANCELLED'
  plannedDeparture: string
  requiredCapacity: number
  readyCapacity: number
  plannedCapacity: number
  fleet: FleetShip[]
  audit: Audit[]
  version: number
  immutable: boolean
  loot?: Loot
}

export type Crew = {
  id: string
  expeditionId: string
  userId: string
  userName: string
  expeditionRole: string
  participationStatus: 'PENDING' | 'CONFIRMED' | 'DECLINED'
  alive: boolean
  version: number
}

export type User = { id: string; displayName: string; systemRole: string }
export type Requirement = { resource: string; quantity: number; available: number }
export type FleetShip = {
  id: string
  name: string
  typeName: string
  capacity: number
  stage: number
  ready: boolean
  requestStatus: string
}
export type Ship = {
  id: string
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
  expeditionId?: string | null
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
export type Stock = { resource: string; quantity: number; version: number }
export type Allocation = { recipient: string; category: string; loot: Loot }
export type Audit = {
  id: number
  happenedAt: string
  actorRole: Role
  eventType: string
  aggregateType: string
  aggregateId: string
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
