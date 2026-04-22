<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

import { useAdminSession } from '../shared/auth/adminAuth'

const route = useRoute()
const adminSession = useAdminSession()
const showTopBar = computed(() => route.meta.layout !== 'fullscreen')
const isAdminView = computed(() => route.path.startsWith('/admin'))
const topBarBrandIcon = computed(() => (isAdminView.value ? 'A' : 'S'))
const topBarBrandText = computed(() => (isAdminView.value ? '后台管理' : '超级智能'))
const topBarActionText = computed(() => (isAdminView.value ? '回到对话' : '后台管理'))
const topBarActionTo = computed(() => (isAdminView.value ? '/chat' : '/admin/login'))
const adminDisplayName = computed(() => adminSession.value?.account ?? '')
const adminDisplayInitial = computed(() => adminDisplayName.value.slice(0, 1).toUpperCase())
</script>

<template>
  <div class="app-shell">
    <header
      v-if="showTopBar"
      class="top-bar"
    >
      <div class="logo">
        <span class="logo-icon">{{ topBarBrandIcon }}</span>
        <span class="logo-text">{{ topBarBrandText }}</span>
      </div>
      <div class="top-bar-actions">
        <div
          v-if="isAdminView && adminSession"
          class="top-bar-user"
        >
          <span class="top-bar-user-avatar">{{ adminDisplayInitial }}</span>
          <span class="top-bar-user-copy">
            <span class="top-bar-user-name">{{ adminDisplayName }}</span>
            <span class="top-bar-user-role">管理员</span>
          </span>
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
      </div>
    </header>

    <RouterView />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #f8fafc;
}

.top-bar {
  height: 56px;
  background-color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #e5e7eb;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 16px;
  color: #111827;
}

.logo-icon {
  background-color: #2563eb;
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
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 0 12px 0 10px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #f8fafc;
  color: #111827;
}

.top-bar-user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: #e0ecff;
  color: #1d4ed8;
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

.top-bar-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid #dbe3f0;
  border-radius: 999px;
  background-color: #ffffff;
  color: #374151;
  cursor: pointer;
  text-decoration: none;
  transition:
    border-color 160ms ease,
    background-color 160ms ease,
    box-shadow 160ms ease,
    color 160ms ease;
}

.top-bar-button:hover {
  border-color: #bfdbfe;
  background-color: #eff6ff;
  color: #1d4ed8;
}

.top-bar-button:focus-visible {
  outline: none;
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(147, 197, 253, 0.35);
  color: #1d4ed8;
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

@media (max-width: 720px) {
  .top-bar {
    height: auto;
    align-items: flex-start;
    gap: 12px;
    padding: 12px 16px;
  }

  .top-bar-actions {
    margin-left: auto;
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}
</style>
