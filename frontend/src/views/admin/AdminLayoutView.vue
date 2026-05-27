<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { authApi } from '../../shared/api/auth'
import { logoutAuthSession, useAuthSession, type AuthRole } from '../../shared/auth/authSession'

const route = useRoute()
const router = useRouter()
const authSession = useAuthSession()

interface NavigationItem {
  to: string
  label: string
  icon: string
  roles?: AuthRole[]
}

const navigationItems: NavigationItem[] = [
  {
    to: '/admin/dashboard',
    label: '运营总览',
    icon: 'M4.75 4.75h4.5v4.5h-4.5zM10.75 4.75h4.5v4.5h-4.5zM4.75 10.75h4.5v4.5h-4.5zM10.75 10.75h4.5v4.5h-4.5z'
  },
  {
    to: '/admin/documents',
    label: '文档接入',
    icon: 'M7.25 3.75h4.8l3.2 3.2v7.8a1.25 1.25 0 0 1-1.25 1.25h-6.5a1.25 1.25 0 0 1-1.25-1.25V5A1.25 1.25 0 0 1 7.25 3.75Zm4 0V7h3.25'
  },
  {
    to: '/admin/knowledge-route',
    label: '知识路由',
    icon: 'M5.5 5.25a1.75 1.75 0 1 1 0 3.5a1.75 1.75 0 0 1 0-3.5Zm9 0a1.75 1.75 0 1 1 0 3.5a1.75 1.75 0 0 1 0-3.5ZM10 11.25a1.75 1.75 0 1 1 0 3.5a1.75 1.75 0 0 1 0-3.5Zm-2.75-4.25h5.5M7 8l2.1 3M13 8l-2.1 3'
  },
  {
    to: '/admin/knowledge-route/traces',
    label: '路由追踪',
    icon: 'M5 14.75a1.75 1.75 0 1 1 0-3.5a1.75 1.75 0 0 1 0 3.5Zm4-6a1.75 1.75 0 1 1 0-3.5a1.75 1.75 0 0 1 0 3.5Zm6 6a1.75 1.75 0 1 1 0-3.5a1.75 1.75 0 0 1 0 3.5ZM6.35 11.9l1.3-1.05c.55-.45.95-.7 1.35-1.25l.85-1.15M10.35 8.45l1.05 1.45c.4.55.8.8 1.35 1.25l.9.75'
  },
  {
    to: '/admin/observability',
    label: '对话观测',
    icon: 'M2.75 10s2.55-4.25 7.25-4.25S17.25 10 17.25 10s-2.55 4.25-7.25 4.25S2.75 10 2.75 10Zm7.25-2.25a2.25 2.25 0 1 1 0 4.5a2.25 2.25 0 0 1 0-4.5Z'
  },
  {
    to: '/admin/users',
    label: '账号管理',
    roles: ['super_admin'],
    icon: 'M6.5 9.25a2.75 2.75 0 1 1 0-5.5 2.75 2.75 0 0 1 0 5.5Zm-4 6.25c.45-2.5 2.05-4 4-4s3.55 1.5 4 4M13.5 6.75h3M15 5.25v3M12.75 12.25h4.5M12.75 15.25h4.5'
  },
  {
    to: '/admin/workspaces',
    label: '工作组管理',
    roles: ['super_admin'],
    icon: 'M4.75 5.25h4.5v4.5h-4.5zM10.75 5.25h4.5v4.5h-4.5zM4.75 11.25h4.5v4.5h-4.5zM10.75 11.25h4.5v4.5h-4.5z'
  }
]

const visibleNavigationItems = computed(() => {
  const role = authSession.value?.role
  return navigationItems.filter((item) => !item.roles || (role && item.roles.includes(role)))
})

async function handleLogout(): Promise<void> {
  try {
    await authApi.logout()
  } finally {
    logoutAuthSession()
    await router.replace('/login')
  }
}
</script>

<template>
  <main class="admin-layout-view">
    <aside class="admin-sidebar">
      <nav class="admin-nav">
        <RouterLink
          v-for="item in visibleNavigationItems"
          :key="item.to"
          :to="item.to"
          class="admin-nav-link"
          :class="{ 'is-active': route.path === item.to }"
        >
          <span
            class="admin-nav-icon"
            aria-hidden="true"
          >
            <svg viewBox="0 0 20 20">
              <path
                :d="item.icon"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </span>
          <span class="admin-nav-label">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="admin-sidebar-footer">
        <button
          type="button"
          class="admin-logout-button"
          @click="handleLogout"
        >
          <span
            class="admin-logout-icon"
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
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <section class="admin-content">
      <RouterView />
    </section>
  </main>
</template>

<style scoped>
* {
  box-sizing: border-box;
}

.admin-layout-view {
  height: calc(100vh - 56px);
  min-height: calc(100vh - 56px);
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  background:
    linear-gradient(rgba(21, 94, 99, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(21, 94, 99, 0.035) 1px, transparent 1px),
    linear-gradient(135deg, rgba(230, 246, 245, 0.88) 0%, rgba(243, 248, 251, 0.96) 45%, #edf5f7 100%);
  background-size: 28px 28px, 28px 28px, auto;
  overflow: hidden;
}

.admin-sidebar {
  display: flex;
  flex-direction: column;
  width: 260px;
  height: 100%;
  flex-shrink: 0;
  padding: 16px 12px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.74) 0%, rgba(245, 250, 249, 0.94) 100%),
    repeating-linear-gradient(0deg, rgba(15, 118, 110, 0.035) 0, rgba(15, 118, 110, 0.035) 1px, transparent 1px, transparent 24px);
  border-right: 1px solid #d8e6e8;
  overflow: hidden;
}

.admin-nav {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 4px;
  overflow-y: auto;
}

.admin-nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 6px;
  background: transparent;
  color: #284247;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition:
    background-color 160ms ease,
    color 160ms ease;
}

.admin-nav-icon {
  display: inline-flex;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
}

.admin-nav-icon svg {
  width: 18px;
  height: 18px;
}

.admin-nav-label {
  flex: 1;
}

.admin-sidebar-footer {
  display: grid;
  margin-top: auto;
  padding-top: 20px;
}

.admin-nav-link:hover {
  background: #e8f3f2;
  color: #0f766e;
}

.admin-nav-link.is-active {
  background: #eef8f6;
  color: #0f766e;
  box-shadow: inset 0 0 0 1px #cae4e0;
}

.admin-logout-button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #b91c1c;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 160ms ease,
    color 160ms ease;
}

.admin-logout-button:hover {
  background: #fef2f2;
  color: #991b1b;
}

.admin-logout-icon {
  display: inline-flex;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
}

.admin-logout-icon svg {
  width: 18px;
  height: 18px;
}

.admin-content {
  min-width: 0;
  height: 100%;
  padding: 24px;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.66) 0 1px, transparent 1px 100%),
    linear-gradient(0deg, rgba(255, 255, 255, 0.58) 0 1px, transparent 1px 100%),
    linear-gradient(180deg, rgba(245, 251, 251, 0.48) 0%, rgba(237, 245, 247, 0.72) 100%);
  background-size: 56px 56px, 56px 56px, auto;
  overflow-y: auto;
}

.admin-content :deep(.panel),
.admin-content :deep(.filter-panel),
.admin-content :deep(.table-panel),
.admin-content :deep(.summary-panel),
.admin-content :deep(.exchange-panel),
.admin-content :deep(.state-panel),
.admin-content :deep(.error-panel) {
  border-color: var(--admin-color-border);
  background: var(--admin-color-card);
  box-shadow: var(--admin-shadow-panel);
}

.admin-content :deep(.overview-page),
.admin-content :deep(.document-page),
.admin-content :deep(.route-page),
.admin-content :deep(.trace-page),
.admin-content :deep(.observability-list-page),
.admin-content :deep(.session-trace-page),
.admin-content :deep(.exchange-detail-page),
.admin-content :deep(.admin-content-page) {
  width: min(100%, 1120px);
  margin: 0 auto;
}

.admin-content :deep(button:not(.text-action):not(.danger):not(.delete-popover-cancel):not(.delete-popover-confirm)),
.admin-content :deep(.ghost-button) {
  border-color: var(--admin-color-field-border);
}

.admin-content :deep(button:not(:disabled):not(.danger):hover),
.admin-content :deep(.ghost-button:not(:disabled):hover) {
  border-color: var(--admin-color-border-strong);
  background: #e8f7f5;
  color: var(--admin-color-accent);
}

.admin-content :deep(input:focus),
.admin-content :deep(select:focus),
.admin-content :deep(textarea:focus) {
  border-color: #5cc3b8;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.14);
}

.admin-content :deep(.status-message),
.admin-content :deep(.error-panel) {
  overflow-wrap: anywhere;
}

@supports (height: 100dvh) {
  .admin-layout-view {
    height: calc(100dvh - 56px);
    min-height: calc(100dvh - 56px);
  }
}

@media (max-width: 900px) {
  .admin-layout-view {
    width: 100%;
    max-width: 100vw;
    height: auto;
    min-height: auto;
    grid-template-columns: 1fr;
    align-content: start;
    overflow-x: hidden;
    overflow-y: visible;
  }

  .admin-sidebar {
    width: 100%;
    max-width: 100vw;
    height: auto;
    flex: none;
    padding: 10px 12px 8px;
    border-right: 0;
    border-bottom: 1px solid #d8e6e8;
    overflow: hidden;
  }

  .admin-nav {
    width: 100%;
    max-width: 100%;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    grid-auto-rows: 40px;
    flex: none;
    align-content: start;
    gap: 6px;
    overflow: visible;
    padding-bottom: 0;
    scrollbar-width: none;
  }

  .admin-nav::-webkit-scrollbar {
    display: none;
  }

  .admin-nav-link {
    min-height: 38px;
    justify-content: center;
    gap: 6px;
    padding: 7px 8px;
    border: 1px solid #cfe1e4;
    background: rgba(255, 255, 255, 0.86);
    font-size: 12px;
    white-space: nowrap;
  }

  .admin-nav-label {
    flex: 0 1 auto;
  }

  .admin-nav-link.is-active {
    border-color: #95cfc8;
    background: #eef8f6;
  }

  .admin-sidebar-footer {
    margin-top: 6px;
    padding-top: 6px;
    border-top: 1px solid #d8e6e8;
  }

  .admin-logout-button {
    min-height: 34px;
    padding: 7px 10px;
    font-size: 13px;
  }

  .admin-content {
    width: 100%;
    max-width: 100vw;
    min-width: 0;
    height: auto;
    padding: 12px;
    overflow-x: hidden;
    overflow-y: visible;
  }

  .admin-content :deep(*) {
    min-width: 0;
  }
}
</style>
