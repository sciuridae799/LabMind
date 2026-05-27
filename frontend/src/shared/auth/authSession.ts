import { readonly, ref } from 'vue'

export type AuthRole = 'guest' | 'user' | 'super_admin'

export interface WorkspaceSummary {
  workspaceId: string
  workspaceName: string
}

export interface AuthSession {
  token: string
  userId: string
  account: string
  displayName: string
  role: AuthRole
  workspaceId: string
  workspaceName: string
  accessibleWorkspaces: WorkspaceSummary[]
}

const AUTH_SESSION_STORAGE_KEY = 'super-agent.auth-session'
const LEGACY_ADMIN_SESSION_STORAGE_KEY = 'super-agent.admin-session'
const LEGACY_ADMIN_AUTH_STORAGE_KEY = 'super-agent.admin-authenticated'
function isAuthRole(value: unknown): value is AuthRole {
  return value === 'guest' || value === 'user' || value === 'super_admin'
}

function normalizeWorkspaces(rawWorkspaces: unknown): WorkspaceSummary[] {
  if (!Array.isArray(rawWorkspaces)) {
    return []
  }

  return rawWorkspaces
    .map((workspace) => {
      const rawWorkspace = workspace as Partial<WorkspaceSummary>
      const workspaceId = typeof rawWorkspace.workspaceId === 'string' ? rawWorkspace.workspaceId.trim() : ''
      const workspaceName = typeof rawWorkspace.workspaceName === 'string' ? rawWorkspace.workspaceName.trim() : ''
      return workspaceId && workspaceName ? { workspaceId, workspaceName } : null
    })
    .filter((workspace): workspace is WorkspaceSummary => workspace !== null)
}

function normalizeSession(rawSession: Partial<AuthSession>): AuthSession | null {
  const token = typeof rawSession.token === 'string' ? rawSession.token.trim() : ''
  const userId = typeof rawSession.userId === 'string' ? rawSession.userId.trim() : ''
  const account = typeof rawSession.account === 'string' ? rawSession.account.trim() : ''
  const displayName = typeof rawSession.displayName === 'string' ? rawSession.displayName.trim() : account
  const role = rawSession.role
  const workspaceId = typeof rawSession.workspaceId === 'string' ? rawSession.workspaceId.trim() : ''
  const workspaceName = typeof rawSession.workspaceName === 'string' ? rawSession.workspaceName.trim() : ''
  const accessibleWorkspaces = normalizeWorkspaces(rawSession.accessibleWorkspaces)

  if (
    !token ||
    !userId ||
    !account ||
    !isAuthRole(role) ||
    !workspaceId ||
    !workspaceName ||
    !accessibleWorkspaces.some((workspace) => workspace.workspaceId === workspaceId)
  ) {
    return null
  }

  return {
    token,
    userId,
    account,
    displayName,
    role,
    workspaceId,
    workspaceName,
    accessibleWorkspaces
  }
}

function clearLegacySession(): void {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.removeItem(LEGACY_ADMIN_SESSION_STORAGE_KEY)
  window.localStorage.removeItem(LEGACY_ADMIN_AUTH_STORAGE_KEY)
}

function loadAuthSession(): AuthSession | null {
  if (typeof window === 'undefined') {
    return null
  }

  const rawSession = window.localStorage.getItem(AUTH_SESSION_STORAGE_KEY)
  if (!rawSession) {
    clearLegacySession()
    return null
  }

  try {
    const parsedSession = JSON.parse(rawSession) as Partial<AuthSession>
    const session = normalizeSession(parsedSession)
    if (!session) {
      window.localStorage.removeItem(AUTH_SESSION_STORAGE_KEY)
      clearLegacySession()
      return null
    }
    clearLegacySession()
    return session
  } catch {
    window.localStorage.removeItem(AUTH_SESSION_STORAGE_KEY)
    clearLegacySession()
    return null
  }
}

function persistAuthSession(session: AuthSession | null): void {
  if (typeof window === 'undefined') {
    return
  }

  if (!session) {
    window.localStorage.removeItem(AUTH_SESSION_STORAGE_KEY)
    clearLegacySession()
    return
  }

  window.localStorage.setItem(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session))
  clearLegacySession()
}

const authSession = ref<AuthSession | null>(loadAuthSession())

export function useAuthSession() {
  return readonly(authSession)
}

export function readAuthSessionSnapshot(): AuthSession | null {
  return authSession.value
}

export function setAuthSession(session: AuthSession): void {
  const normalizedSession = normalizeSession(session)
  if (!normalizedSession) {
    throw new Error('登录响应缺少必要的身份或工作组信息')
  }
  authSession.value = normalizedSession
  persistAuthSession(normalizedSession)
}

export function switchAuthWorkspace(workspace: WorkspaceSummary): void {
  const currentSession = authSession.value
  const workspaceId = typeof workspace.workspaceId === 'string' ? workspace.workspaceId.trim() : ''
  const workspaceName = typeof workspace.workspaceName === 'string' ? workspace.workspaceName.trim() : ''
  if (!currentSession || !workspaceId || !workspaceName) {
    throw new Error('工作组信息不完整，无法切换')
  }

  const nextSession = {
    ...currentSession,
    workspaceId,
    workspaceName
  }
  authSession.value = nextSession
  persistAuthSession(nextSession)
}

export function logoutAuthSession(): void {
  authSession.value = null
  persistAuthSession(null)
}

export function isAuthenticated(): boolean {
  return authSession.value !== null
}

export function hasAnyRole(roles?: AuthRole[]): boolean {
  if (!roles || roles.length === 0) {
    return isAuthenticated()
  }

  const role = authSession.value?.role
  return role ? roles.includes(role) : false
}

export function canWriteDocuments(): boolean {
  const role = authSession.value?.role
  return role === 'user' || role === 'super_admin'
}

export function canManageAccounts(): boolean {
  return authSession.value?.role === 'super_admin'
}
