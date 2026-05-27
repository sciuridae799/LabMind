<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { authApi } from '../shared/api/auth'
import { setAuthSession } from '../shared/auth/authSession'

const route = useRoute()
const router = useRouter()
const account = ref('')
const password = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)

function resolveRedirectPath(): string {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/chat'
}

async function handleSubmit(): Promise<void> {
  if (isSubmitting.value) {
    return
  }

  errorMessage.value = ''
  isSubmitting.value = true
  try {
    const session = await authApi.login({
      account: account.value.trim(),
      password: password.value
    })
    if (!session) {
      throw new Error('登录响应缺少账号信息')
    }
    setAuthSession(session)
    await router.replace(resolveRedirectPath())
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    isSubmitting.value = false
  }
}

async function handleGuestLogin(): Promise<void> {
  if (isSubmitting.value) {
    return
  }

  errorMessage.value = ''
  isSubmitting.value = true
  try {
    const session = await authApi.loginGuest()
    if (!session) {
      throw new Error('访客登录响应缺少账号信息')
    }
    setAuthSession(session)
    await router.replace('/chat')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '访客登录失败'
  } finally {
    isSubmitting.value = false
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
  <main class="login-view">
    <section class="login-card">
      <div class="brand-block">
        <span class="brand-mark">LAB</span>
        <div>
          <p class="eyebrow">Workspace Access</p>
          <h1>实验室文档工作台</h1>
        </div>
      </div>

      <form
        class="login-form"
        @submit.prevent="handleSubmit"
      >
        <label class="field">
          <span>账号</span>
          <input
            v-model.trim="account"
            type="text"
            placeholder="请输入管理员分配的账号"
            autocomplete="username"
            required
            @input="clearErrorMessage"
          >
        </label>
        <label class="field">
          <span>密码</span>
          <input
            v-model="password"
            type="password"
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
          :disabled="isSubmitting"
        >
          {{ isSubmitting ? '登录中' : '登录工作台' }}
        </button>
      </form>

      <div class="guest-panel">
        <div>
          <strong>访客体验</strong>
          <p>访问默认资料库，可体验问答和只读后台。</p>
        </div>
        <button
          type="button"
          class="guest-button"
          @click="handleGuestLogin"
        >
          访客进入
        </button>
      </div>

      <p class="register-note">
        暂不开放自助注册。需要账号或工作组权限，请联系超级管理员开通。
      </p>
    </section>
  </main>
</template>

<style scoped>
.login-view {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    linear-gradient(rgba(21, 94, 99, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(21, 94, 99, 0.035) 1px, transparent 1px),
    linear-gradient(135deg, rgba(230, 246, 245, 0.92) 0%, rgba(243, 248, 251, 0.98) 52%, #edf5f7 100%);
  background-size: 28px 28px, 28px 28px, auto;
}

.login-card {
  width: min(460px, 100%);
  padding: 28px;
  border: 1px solid var(--admin-color-border);
  border-radius: 8px;
  background: var(--admin-color-card);
  box-shadow: var(--admin-shadow-panel);
}

.brand-block {
  display: flex;
  gap: 14px;
  align-items: center;
}

.brand-mark {
  display: inline-flex;
  width: 56px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--admin-color-accent);
  color: #ffffff;
  font-size: 20px;
  font-weight: 800;
}

.eyebrow {
  margin: 0;
  color: var(--admin-color-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h1 {
  margin: 6px 0 0;
  color: var(--admin-color-title);
  font-size: 26px;
  line-height: 1.2;
}

.login-form {
  display: grid;
  gap: 14px;
  margin-top: 24px;
}

.field {
  display: grid;
  gap: 6px;
  color: var(--admin-color-subtle);
  font-size: 13px;
  font-weight: 700;
}

.field input {
  height: var(--admin-control-height);
  padding: 0 12px;
  border: 1px solid var(--admin-color-field-border);
  border-radius: var(--admin-radius-control);
  outline: none;
  background: #ffffff;
  color: var(--admin-color-text);
  font-size: var(--admin-control-font-size);
}

.field input:focus {
  border-color: #5cc3b8;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.14);
}

.error-message {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--admin-color-danger-border);
  border-radius: var(--admin-radius-control);
  background: var(--admin-color-danger-soft);
  color: var(--admin-color-danger);
  font-size: 13px;
  line-height: 1.6;
}

.submit-button,
.guest-button {
  height: var(--admin-control-height);
  border-radius: var(--admin-radius-control);
  font-weight: 700;
  cursor: pointer;
}

.submit-button {
  border: 1px solid var(--admin-color-accent);
  background: var(--admin-color-accent);
  color: #ffffff;
}

.submit-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.guest-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 18px;
  padding: 14px;
  border: 1px dashed #b8d8d4;
  border-radius: 8px;
  background: rgba(238, 248, 246, 0.66);
}

.guest-panel strong {
  color: var(--admin-color-title);
  font-size: 14px;
}

.guest-panel p,
.register-note {
  margin: 4px 0 0;
  color: var(--admin-color-muted);
  font-size: 13px;
  line-height: 1.6;
}

.guest-button {
  flex: 0 0 auto;
  padding: 0 14px;
  border: 1px solid var(--admin-color-field-border);
  background: #ffffff;
  color: var(--admin-color-text);
}

.register-note {
  margin-top: 14px;
}

@media (max-width: 520px) {
  .login-card {
    padding: 22px;
  }

  .guest-panel {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
