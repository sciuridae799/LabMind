import { requestApiEnvelope } from './http'
import type { AuthSession, WorkspaceSummary } from '../auth/authSession'
import type { ManageObject } from './types'

export interface LoginPayload extends ManageObject {
  account: string
  password: string
}

export interface CreateUserPayload extends ManageObject {
  account: string
  displayName: string
  password: string
  role: 'user' | 'super_admin'
  workspaceIds: string[]
}

export interface UpdateUserPayload extends ManageObject {
  userId: string
  displayName: string
  role: 'user' | 'super_admin'
  enabled: boolean
  workspaceIds: string[]
}

export interface DeleteUserPayload extends ManageObject {
  userId: string
}

export interface CreateWorkspacePayload extends ManageObject {
  workspaceName: string
  workspaceCode?: string
}

export interface UpdateWorkspacePayload extends ManageObject {
  workspaceId: string
  workspaceName: string
}

export interface DeleteWorkspacePayload extends ManageObject {
  workspaceId: string
}

export interface UserAccountRow {
  userId: string
  account: string
  displayName: string
  role: string
  workspaceId: string
  workspaceName: string
  workspaces: WorkspaceSummary[]
  enabled: boolean
  createTime?: string
}

export const authApi = {
  login(payload: LoginPayload): Promise<AuthSession | null> {
    return requestApiEnvelope<AuthSession, LoginPayload>('/auth/login', {
      body: payload
    })
  },

  loginGuest(): Promise<AuthSession | null> {
    return requestApiEnvelope<AuthSession>('/auth/guest-login')
  },

  queryCurrentSession(): Promise<AuthSession | null> {
    return requestApiEnvelope<AuthSession>('/auth/me')
  },

  switchWorkspace(workspaceId: string): Promise<AuthSession | null> {
    return requestApiEnvelope<AuthSession, ManageObject>('/auth/switch-workspace', {
      body: {
        workspaceId
      }
    })
  },

  logout(): Promise<unknown> {
    return requestApiEnvelope('/auth/logout')
  },

  listWorkspaces(): Promise<WorkspaceSummary[] | null> {
    return requestApiEnvelope<WorkspaceSummary[]>('/admin/workspaces')
  },

  createWorkspace(payload: CreateWorkspacePayload): Promise<WorkspaceSummary | null> {
    return requestApiEnvelope<WorkspaceSummary, CreateWorkspacePayload>('/admin/workspaces/create', {
      body: payload
    })
  },

  updateWorkspace(payload: UpdateWorkspacePayload): Promise<WorkspaceSummary | null> {
    return requestApiEnvelope<WorkspaceSummary, UpdateWorkspacePayload>('/admin/workspaces/update', {
      body: payload
    })
  },

  deleteWorkspace(payload: DeleteWorkspacePayload): Promise<unknown> {
    return requestApiEnvelope<unknown, DeleteWorkspacePayload>('/admin/workspaces/delete', {
      body: payload
    })
  },

  listUsers(): Promise<UserAccountRow[] | null> {
    return requestApiEnvelope<UserAccountRow[]>('/admin/users')
  },

  createUser(payload: CreateUserPayload): Promise<UserAccountRow | null> {
    return requestApiEnvelope<UserAccountRow, CreateUserPayload>('/admin/users/create', {
      body: payload
    })
  },

  updateUser(payload: UpdateUserPayload): Promise<UserAccountRow | null> {
    return requestApiEnvelope<UserAccountRow, UpdateUserPayload>('/admin/users/update', {
      body: payload
    })
  },

  deleteUser(payload: DeleteUserPayload): Promise<unknown> {
    return requestApiEnvelope<unknown, DeleteUserPayload>('/admin/users/delete', {
      body: payload
    })
  }
}
