import { readonly, ref } from 'vue'

export type AdminSession = {
  account: string
}

const ADMIN_SESSION_STORAGE_KEY = 'super-agent.admin-session'
const LEGACY_ADMIN_AUTH_STORAGE_KEY = 'super-agent.admin-authenticated'
const ADMIN_ACCOUNT = 'admin'
const ADMIN_PASSWORD = '123456'

function clearLegacyAdminAuth(): void {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.removeItem(LEGACY_ADMIN_AUTH_STORAGE_KEY)
}

function loadAdminSession(): AdminSession | null {
  if (typeof window === 'undefined') {
    return null
  }

  const rawSession = window.localStorage.getItem(ADMIN_SESSION_STORAGE_KEY)
  if (!rawSession) {
    clearLegacyAdminAuth()
    return null
  }

  try {
    const parsedSession = JSON.parse(rawSession) as Partial<AdminSession>
    const account = typeof parsedSession.account === 'string' ? parsedSession.account.trim() : ''

    if (account !== ADMIN_ACCOUNT) {
      window.localStorage.removeItem(ADMIN_SESSION_STORAGE_KEY)
      clearLegacyAdminAuth()
      return null
    }

    return { account }
  } catch {
    window.localStorage.removeItem(ADMIN_SESSION_STORAGE_KEY)
    clearLegacyAdminAuth()
    return null
  }
}

function syncAdminSession(session: AdminSession | null): void {
  if (typeof window === 'undefined') {
    return
  }

  if (!session) {
    window.localStorage.removeItem(ADMIN_SESSION_STORAGE_KEY)
    window.localStorage.removeItem(LEGACY_ADMIN_AUTH_STORAGE_KEY)
    return
  }

  window.localStorage.setItem(ADMIN_SESSION_STORAGE_KEY, JSON.stringify(session))
  clearLegacyAdminAuth()
}

const adminSession = ref<AdminSession | null>(loadAdminSession())

export function useAdminSession() {
  return readonly(adminSession)
}

export function loginAdmin(account: string, password: string): void {
  const normalizedAccount = account.trim()
  const normalizedPassword = password.trim()

  if (normalizedAccount !== ADMIN_ACCOUNT || normalizedPassword !== ADMIN_PASSWORD) {
    throw new Error('账号或密码错误')
  }

  const session = { account: ADMIN_ACCOUNT }
  adminSession.value = session
  syncAdminSession(session)
}

export function logoutAdmin(): void {
  adminSession.value = null
  syncAdminSession(null)
}

export function isAdminAuthenticated(): boolean {
  return adminSession.value !== null
}
