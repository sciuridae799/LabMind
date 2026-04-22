<script setup lang="ts">
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { logoutAdmin } from '../../shared/auth/adminAuth'

const route = useRoute()
const router = useRouter()

const navigationItems = [
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
  }
]

function handleLogout(): void {
  logoutAdmin()
  void router.replace('/admin/login')
}
</script>

<template>
  <main class="admin-layout-view">
    <aside class="admin-sidebar">
      <nav class="admin-nav">
        <RouterLink
          v-for="item in navigationItems"
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
  background: #ffffff;
  overflow: hidden;
}

.admin-sidebar {
  display: flex;
  flex-direction: column;
  width: 260px;
  height: 100%;
  flex-shrink: 0;
  padding: 16px 12px;
  background: #ffffff;
  border-right: 1px solid #eaeaea;
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
  color: #444444;
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
  background: #f1f3f5;
  color: #222222;
}

.admin-nav-link.is-active {
  background: #f3f5f7;
  color: #111111;
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
  background: #ffffff;
  overflow-y: auto;
}

@supports (height: 100dvh) {
  .admin-layout-view {
    height: calc(100dvh - 56px);
    min-height: calc(100dvh - 56px);
  }
}

@media (max-width: 900px) {
  .admin-layout-view {
    height: auto;
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .admin-sidebar {
    width: 100%;
    height: auto;
    padding: 12px;
    border-right: 0;
    border-bottom: 1px solid #eaeaea;
    overflow: visible;
  }

  .admin-nav {
    overflow: visible;
  }

  .admin-content {
    height: auto;
    padding: 16px;
    overflow: visible;
  }
}
</style>
