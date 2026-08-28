import { getJson, requestJson } from './http-client'

export type MemberRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'SALES' | 'VIEWER'
export type MemberStatus = 'INVITED' | 'ACTIVE' | 'SUSPENDED' | 'LEFT'

export interface AdminMember {
  id: string
  userId: string
  email: string
  displayName: string
  role: MemberRole
  status: MemberStatus
  joinedAt: string | null
  lastLoginAt: string | null
  createdAt: string
}

export interface AdminMemberPage {
  content: AdminMember[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface MemberQuery {
  keyword: string
  role: string
  status: string
  page: number
  size: number
}

export interface CreateMemberInput {
  displayName: string
  email: string
  role: MemberRole
  initialPassword: string
}

export interface UpdateMemberInput {
  displayName: string
  role: MemberRole
  status: MemberStatus
}

export interface AdminTeam {
  id: string
  slug: string
  name: string
  timezone: string
  locale: string
  planCode: string
  status: string
  totalMembers: number
  activeMembers: number
  adminMembers: number
  createdAt: string
  updatedAt: string
}

export interface UpdateTeamInput {
  name: string
  timezone: string
  locale: string
}

export function getMembers(query: MemberQuery) {
  const params = new URLSearchParams({
    keyword: query.keyword,
    role: query.role,
    status: query.status,
    page: String(query.page),
    size: String(query.size),
  })
  return getJson<AdminMemberPage>(`/api/v1/admin/members?${params.toString()}`)
}

export function createMember(input: CreateMemberInput) {
  return requestJson<AdminMember>('/api/v1/admin/members', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateMember(memberId: string, input: UpdateMemberInput) {
  return requestJson<AdminMember>(`/api/v1/admin/members/${memberId}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function getTeam() {
  return getJson<AdminTeam>('/api/v1/admin/team')
}

export function updateTeam(input: UpdateTeamInput) {
  return requestJson<AdminTeam>('/api/v1/admin/team', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
