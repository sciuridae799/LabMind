<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { loginAdmin } from '../shared/auth/adminAuth'

const router = useRouter()
const account = ref('')
const password = ref('')
const errorMessage = ref('')

function handleSubmit(): void {
  errorMessage.value = ''

  try {
    loginAdmin(account.value, password.value)
    void router.replace('/admin/dashboard')
  } catch (error) {
    if (error instanceof Error) {
      errorMessage.value = error.message
      return
    }

    throw error
  }
}

function clearErrorMessage(): void {
  if (!errorMessage.value) {
    return
  }

  errorMessage.value = ''
}
</script>

<template>
  <main class="admin-login-view">
    <section class="login-card">
      <div class="header-row">
        <p class="eyebrow">Admin Access</p>
        <RouterLink
          to="/chat"
          class="back-link"
        >
          返回对话
        </RouterLink>
      </div>
      <h1>管理后台登录</h1>

      <form
        class="login-form"
        @submit.prevent="handleSubmit"
      >
        <label class="field">
          <span>账号</span>
          <input
            type="text"
            v-model.trim="account"
            placeholder="请输入管理员账号"
            autocomplete="username"
            required
            @input="clearErrorMessage"
          >
        </label>
        <label class="field">
          <span>密码</span>
          <input
            type="password"
            v-model.trim="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            required
            @input="clearErrorMessage"
          >
        </label>
        <p
          v-if="errorMessage"
          class="error-message"
        >
          {{ errorMessage }}
        </p>
        <button
          type="submit"
          class="submit-button"
        >
          登录管理后台
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.admin-login-view {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(255, 222, 189, 0.82), transparent 32%),
    radial-gradient(circle at bottom right, rgba(191, 219, 254, 0.72), transparent 32%),
    linear-gradient(180deg, #fbf7ef 0%, #eef4fb 100%);
}

.login-card {
  width: min(460px, 100%);
  padding: 32px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.1);
  backdrop-filter: blur(16px);
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.eyebrow {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #b45309;
}

.back-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid rgba(148, 163, 184, 0.26);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.back-link:hover {
  border-color: rgba(37, 99, 235, 0.22);
  background: rgba(239, 246, 255, 0.92);
  color: #1d4ed8;
  transform: translateY(-1px);
}

.back-link:focus-visible {
  outline: 2px solid rgba(59, 130, 246, 0.45);
  outline-offset: 2px;
}

h1 {
  margin: 12px 0 0;
  font-size: 34px;
  line-height: 1.08;
  color: #0f172a;
}

.login-form {
  display: grid;
  gap: 14px;
  margin-top: 24px;
}

.field {
  display: grid;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.field input {
  height: 46px;
  padding: 0 14px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 14px;
  background: #ffffff;
}

.error-message {
  margin: -2px 0 0;
  color: #b91c1c;
  font-size: 13px;
  line-height: 1.5;
}

.submit-button {
  height: 46px;
  margin-top: 4px;
  border: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, #2563eb 0%, #0f62fe 100%);
  color: #ffffff;
  font-weight: 700;
  cursor: pointer;
}
</style>
