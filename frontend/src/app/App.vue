<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { authApi } from '../shared/api/auth'
import { logoutAuthSession, setAuthSession, useAuthSession } from '../shared/auth/authSession'

const route = useRoute()
const router = useRouter()
const authSession = useAuthSession()
const showTopBar = computed(() => route.meta.layout !== 'fullscreen')
const isAdminView = computed(() => route.path.startsWith('/admin'))
const showProductNavigation = computed(() => showTopBar.value && !isAdminView.value)
const topBarBrandIcon = computed(() => (isAdminView.value ? 'A' : 'LAB'))
const topBarBrandText = computed(() => (isAdminView.value ? '后台管理' : 'LabMind'))
const topBarActionText = computed(() => (isAdminView.value ? '回到对话' : '后台管理'))
const topBarActionTo = computed(() => (isAdminView.value ? '/chat' : '/admin/dashboard'))
const userDisplayName = computed(() => authSession.value?.displayName ?? authSession.value?.account ?? '')
const userDisplayInitial = computed(() => userDisplayName.value.slice(0, 1).toUpperCase())
const roleLabel = computed(() => {
  if (authSession.value?.role === 'super_admin') {
    return '超级管理员'
  }
  if (authSession.value?.role === 'user') {
    return '成员'
  }
  if (authSession.value?.role === 'guest') {
    return '访客'
  }
  return ''
})
const workspaceLabel = computed(() => authSession.value?.workspaceName ?? '')
const routeSessionKey = computed(() => {
  const session = authSession.value
  if (!session) {
    return 'anonymous'
  }
  if (session.role === 'guest') {
    return `guest:${session.token}`
  }
  return `${session.userId}:${session.workspaceId}`
})
const routeWorkspaceKey = computed(() => {
  return `${route.fullPath}:${routeSessionKey.value}`
})
const availableWorkspaces = computed(() => authSession.value?.accessibleWorkspaces ?? [])
const canSwitchWorkspace = computed(() => availableWorkspaces.value.length > 1)
const isWorkspaceMenuOpen = ref(false)
const isWorkspaceSwitching = ref(false)
const workspaceStatusMessage = ref('')
const topBarUserElement = ref<HTMLElement | null>(null)
let authRequestGeneration = 0

interface AuthRequestContext {
  generation: number
  token: string
}

function beginAuthRequest(): AuthRequestContext | null {
  const token = authSession.value?.token
  if (!token) {
    return null
  }

  authRequestGeneration += 1
  return {
    generation: authRequestGeneration,
    token
  }
}

function isAuthRequestCurrent(request: AuthRequestContext): boolean {
  return request.generation === authRequestGeneration &&
    authSession.value?.token === request.token
}

function canApplyAuthResponse(request: AuthRequestContext, responseToken?: string): boolean {
  return isAuthRequestCurrent(request) && responseToken === request.token
}

function closeWorkspaceMenu(): void {
  isWorkspaceMenuOpen.value = false
  workspaceStatusMessage.value = ''
}

function handleDocumentClick(event: MouseEvent): void {
  if (!isWorkspaceMenuOpen.value) {
    return
  }
  const target = event.target
  if (target instanceof Node && topBarUserElement.value?.contains(target)) {
    return
  }
  closeWorkspaceMenu()
}

async function toggleWorkspaceMenu(): Promise<void> {
  if (!authSession.value) {
    return
  }
  if (isWorkspaceMenuOpen.value) {
    closeWorkspaceMenu()
    return
  }
  if (isWorkspaceSwitching.value) {
    return
  }
  isWorkspaceMenuOpen.value = true
  workspaceStatusMessage.value = ''
  const request = beginAuthRequest()
  if (!request) {
    return
  }
  try {
    const session = await authApi.queryCurrentSession()
    if (session && canApplyAuthResponse(request, session.token)) {
      setAuthSession(session)
    }
  } catch (error) {
    if (isAuthRequestCurrent(request)) {
      workspaceStatusMessage.value = error instanceof Error ? error.message : '工作组列表加载失败'
    }
  }
}

async function switchWorkspace(workspaceId: string): Promise<void> {
  const currentWorkspaceId = authSession.value?.workspaceId
  if (!workspaceId || workspaceId === currentWorkspaceId || isWorkspaceSwitching.value) {
    return
  }

  const request = beginAuthRequest()
  if (!request) {
    return
  }
  isWorkspaceSwitching.value = true
  workspaceStatusMessage.value = ''
  try {
    const session = await authApi.switchWorkspace(workspaceId)
    if (session && canApplyAuthResponse(request, session.token)) {
      setAuthSession(session)
      closeWorkspaceMenu()
    }
  } catch (error) {
    if (isAuthRequestCurrent(request)) {
      workspaceStatusMessage.value = error instanceof Error ? error.message : '工作组切换失败'
    }
  } finally {
    if (request.generation === authRequestGeneration) {
      isWorkspaceSwitching.value = false
    }
  }
}

async function handleLogout(): Promise<void> {
  authRequestGeneration += 1
  closeWorkspaceMenu()
  isWorkspaceSwitching.value = false
  try {
    await authApi.logout()
  } finally {
    authRequestGeneration += 1
    logoutAuthSession()
    await router.replace('/login')
  }
}

document.addEventListener('click', handleDocumentClick)

onBeforeUnmount(() => {
  authRequestGeneration += 1
  document.removeEventListener('click', handleDocumentClick)
})
</script>

<template>
  <div class="app-shell">
    <header
      v-if="showTopBar"
      class="top-bar"
    >
      <div class="top-bar-leading">
        <div class="logo">
          <span class="logo-icon">{{ topBarBrandIcon }}</span>
          <span class="logo-text">{{ topBarBrandText }}</span>
        </div>
        <nav v-if="showProductNavigation" class="product-navigation" aria-label="产品入口">
          <RouterLink to="/chat" class="product-navigation-link">文档助手</RouterLink>
          <RouterLink to="/paper-graphs" class="product-navigation-link">论文知识图谱</RouterLink>
        </nav>
      </div>
      <div class="top-bar-actions">
        <div
          v-if="authSession"
          class="top-bar-user"
          ref="topBarUserElement"
        >
          <button
            type="button"
            class="top-bar-user-trigger"
            :class="{ 'top-bar-user-trigger-open': isWorkspaceMenuOpen }"
            :aria-expanded="isWorkspaceMenuOpen"
            @click.stop="toggleWorkspaceMenu"
          >
            <span class="top-bar-user-avatar">{{ userDisplayInitial }}</span>
            <span class="top-bar-user-copy">
              <span class="top-bar-user-name">{{ userDisplayName }}</span>
              <span class="top-bar-user-role">{{ roleLabel }} · {{ workspaceLabel }}</span>
            </span>
            <span
              class="top-bar-user-caret"
              aria-hidden="true"
            >
              <svg viewBox="0 0 20 20">
                <path
                  d="m6 8 4 4 4-4"
                  fill="none"
                  stroke="currentColor"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="1.7"
                />
              </svg>
            </span>
          </button>
          <div
            v-if="isWorkspaceMenuOpen"
            class="workspace-menu"
          >
            <div class="workspace-menu-header">
              <span>当前工作组</span>
              <strong>{{ workspaceLabel }}</strong>
            </div>
            <div class="workspace-menu-list">
              <button
                v-for="workspace in availableWorkspaces"
                :key="workspace.workspaceId"
                type="button"
                class="workspace-menu-item"
                :class="{ active: workspace.workspaceId === authSession.workspaceId }"
                :disabled="workspace.workspaceId === authSession.workspaceId || isWorkspaceSwitching"
                @click="switchWorkspace(workspace.workspaceId)"
              >
                <span>{{ workspace.workspaceName }}</span>
                <small>{{ workspace.workspaceId }}</small>
              </button>
            </div>
            <p
              v-if="!canSwitchWorkspace && !workspaceStatusMessage"
              class="workspace-menu-status"
            >
              当前账号只有一个可访问工作组
            </p>
            <p
              v-if="workspaceStatusMessage"
              class="workspace-menu-status workspace-menu-error"
            >
              {{ workspaceStatusMessage }}
            </p>
          </div>
        </div>
        <RouterLink
          :to="topBarActionTo"
          class="top-bar-button"
        >
          <span
            class="top-bar-button-icon"
            aria-hidden="true"
          >
            <svg viewBox="0 0 20 20">
              <path
                d="M4.167 5.833A1.667 1.667 0 0 1 5.833 4.167h2.5A1.667 1.667 0 0 1 10 5.833v2.5A1.667 1.667 0 0 1 8.333 10h-2.5a1.667 1.667 0 0 1-1.666-1.667v-2.5ZM10 5.833a1.667 1.667 0 0 1 1.667-1.666h2.5a1.667 1.667 0 0 1 1.666 1.666v2.5A1.667 1.667 0 0 1 14.167 10h-2.5A1.667 1.667 0 0 1 10 8.333v-2.5ZM4.167 11.667A1.667 1.667 0 0 1 5.833 10h2.5A1.667 1.667 0 0 1 10 11.667v2.5a1.667 1.667 0 0 1-1.667 1.666h-2.5a1.667 1.667 0 0 1-1.666-1.666v-2.5ZM10 11.667A1.667 1.667 0 0 1 11.667 10h2.5a1.667 1.667 0 0 1 1.666 1.667v2.5a1.667 1.667 0 0 1-1.666 1.666h-2.5A1.667 1.667 0 0 1 10 14.167v-2.5Z"
                fill="none"
                stroke="currentColor"
                stroke-width="1.4"
              />
            </svg>
          </span>
          <span class="top-bar-button-text">{{ topBarActionText }}</span>
        </RouterLink>
        <button
          v-if="authSession"
          type="button"
          class="top-bar-button top-bar-logout-button"
          @click="handleLogout"
        >
          <span
            class="top-bar-button-icon"
            aria-hidden="true"
          >
            <svg viewBox="0 0 20 20">
              <path
                d="M8 4.75H6.75A1.75 1.75 0 0 0 5 6.5v7a1.75 1.75 0 0 0 1.75 1.75H8M11 6.75l3.25 3.25L11 13.25M14 10H8"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </span>
          <span class="top-bar-button-text">退出</span>
        </button>
      </div>
    </header>

    <RouterView :key="routeWorkspaceKey" />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #eef5f7;
}

.top-bar {
  height: 56px;
  background-color: rgba(248, 252, 252, 0.96);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #d8e5e7;
  box-shadow: 0 1px 0 rgba(17, 78, 84, 0.03);
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-weight: 600;
  font-size: 16px;
  color: #12282c;
}

.top-bar-leading {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 28px;
}

.product-navigation {
  display: flex;
  align-items: center;
  gap: 4px;
}

.product-navigation-link {
  padding: 7px 11px;
  border-radius: 7px;
  color: #587176;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.product-navigation-link:hover {
  background: #edf7f6;
  color: #0f766e;
}

.product-navigation-link.router-link-active {
  background: #e5f5f2;
  color: #0f766e;
}

.logo-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logo-icon {
  background-color: #0f766e;
  color: #ffffff;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 14px;
}

.top-bar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.top-bar-user {
  position: relative;
}

.top-bar-user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 0 12px 0 10px;
  border: 1px solid #d7e5e7;
  border-radius: 999px;
  background: #f3faf9;
  color: #12282c;
  cursor: pointer;
  font: inherit;
  transition:
    border-color 160ms ease,
    background-color 160ms ease,
    box-shadow 160ms ease,
    color 160ms ease;
}

.top-bar-user-trigger:hover,
.top-bar-user-trigger-open {
  border-color: #8fd8cf;
  background: #eefbf8;
  color: #0f766e;
}

.top-bar-user-trigger:focus-visible {
  outline: none;
  border-color: #5cc3b8;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.16);
}

.top-bar-user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: #d9f3ef;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.top-bar-user-copy {
  display: grid;
  line-height: 1.1;
}

.top-bar-user-name {
  font-size: 13px;
  font-weight: 600;
}

.top-bar-user-role {
  color: #6b7280;
  font-size: 11px;
}

.top-bar-user-caret {
  display: inline-flex;
  width: 16px;
  height: 16px;
  color: #6d858a;
}

.top-bar-user-caret svg {
  width: 16px;
  height: 16px;
}

.workspace-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 20;
  width: min(320px, calc(100vw - 32px));
  padding: 10px;
  border: 1px solid #cfe0e4;
  border-radius: 10px;
  background: rgba(250, 253, 253, 0.98);
  box-shadow: 0 18px 40px rgba(31, 66, 72, 0.16);
}

.workspace-menu-header {
  display: grid;
  gap: 3px;
  padding: 4px 4px 10px;
  border-bottom: 1px solid #e1ecee;
}

.workspace-menu-header span {
  color: #6d858a;
  font-size: 12px;
  font-weight: 700;
}

.workspace-menu-header strong {
  min-width: 0;
  overflow: hidden;
  color: #132c31;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-menu-list {
  display: grid;
  gap: 6px;
  max-height: 260px;
  overflow: auto;
  padding-top: 8px;
}

.workspace-menu-item {
  display: grid;
  gap: 2px;
  width: 100%;
  min-height: 54px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #294448;
  cursor: pointer;
  text-align: left;
}

.workspace-menu-item:hover:not(:disabled) {
  border-color: #b8dedb;
  background: #eef8f7;
}

.workspace-menu-item.active {
  border-color: #88d2ca;
  background: #e6f6f3;
  color: #0f766e;
}

.workspace-menu-item span {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-menu-item small {
  min-width: 0;
  overflow: hidden;
  color: #6d858a;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-menu-status {
  margin: 8px 2px 0;
  color: #6d858a;
  font-size: 12px;
  line-height: 1.5;
}

.workspace-menu-error {
  color: #b91c1c;
}

.top-bar-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid #cfe0e4;
  border-radius: 999px;
  background-color: #ffffff;
  color: #2f4a4f;
  cursor: pointer;
  text-decoration: none;
  font: inherit;
  transition:
    border-color 160ms ease,
    background-color 160ms ease,
    box-shadow 160ms ease,
    color 160ms ease;
}

.top-bar-logout-button {
  color: #b91c1c;
}

.top-bar-logout-button:hover {
  border-color: #fecaca;
  background-color: #fef2f2;
  color: #991b1b;
}

.top-bar-button:hover {
  border-color: #8fd8cf;
  background-color: #eefbf8;
  color: #0f766e;
}

.top-bar-button:focus-visible {
  outline: none;
  border-color: #5cc3b8;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.16);
  color: #0f766e;
}

.top-bar-button-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
}

.top-bar-button-icon svg {
  width: 18px;
  height: 18px;
}

.top-bar-button-text {
  font-size: 14px;
  font-weight: 500;
}

@media (max-width: 900px) {
  .top-bar {
    padding: 0 14px;
  }

  .top-bar-leading {
    gap: 8px;
  }

  .logo-text,
  .top-bar-user-copy,
  .top-bar-user-caret {
    display: none;
  }

  .product-navigation-link {
    padding: 6px 8px;
    font-size: 12px;
  }
}

@media (max-width: 720px) {
  .top-bar {
    height: 64px;
    align-items: center;
    gap: 8px;
    padding: 0 20px;
  }

  .top-bar-actions {
    margin-left: auto;
    flex: 0 0 auto;
    flex-wrap: nowrap;
    justify-content: flex-end;
    gap: 8px;
  }

  .logo {
    flex: 1 1 auto;
    font-size: 15px;
  }

  .top-bar-leading {
    flex: 1 1 auto;
    gap: 10px;
  }

  .product-navigation-link {
    padding: 6px 7px;
    font-size: 11px;
  }

  .logo-icon {
    flex: 0 0 auto;
    font-size: 13px;
  }

  .top-bar-user-trigger {
    min-height: 34px;
    padding: 0 8px;
  }

  .top-bar-user-copy {
    display: none;
  }

  .top-bar-user-caret {
    display: none;
  }

  .top-bar-button {
    height: 34px;
    padding: 0 12px;
  }

  .top-bar-button-text {
    display: none;
  }
}
</style>
