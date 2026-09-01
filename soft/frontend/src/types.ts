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
  shipName: string
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
export type Ship = {
  id: string
  name: string
  stage: number
  stageName: string
  progress: number
  blessed: boolean
  version: number
  requirements: Requirement[]
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
  ship?: Ship | null
  stock: Stock[]
  allocations: Allocation[]
  audit: Audit[]
  activeSettlementName: string
  demoResetAvailable: boolean
}
