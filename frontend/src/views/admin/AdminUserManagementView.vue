<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { authApi, type UserAccountRow } from '../../shared/api/auth'
import type { WorkspaceSummary } from '../../shared/auth/authSession'

const userRows = ref<UserAccountRow[]>([])
const workspaceRows = ref<WorkspaceSummary[]>([])
const editingUserId = ref('')
const formState = ref({
  account: '',
  displayName: '',
  password: '',
  role: 'user' as 'user' | 'super_admin',
  enabled: true,
  workspaceIds: [] as string[]
})
const statusMessage = ref('')
const isLoading = ref(false)
const isSaving = ref(false)
const isEditing = computed(() => editingUserId.value.length > 0)
const canSubmit = computed(() => {
  if (!formState.value.displayName.trim() || formState.value.workspaceIds.length === 0) {
    return false
  }
  if (isEditing.value) {
    return true
  }
  return Boolean(formState.value.account.trim() && formState.value.password)
})

function normalizeError(error: unknown): string {
  return error instanceof Error ? error.message : '账号请求失败'
}

async function loadData(): Promise<void> {
  isLoading.value = true
  statusMessage.value = ''
  try {
    const [users, workspaces] = await Promise.all([
      authApi.listUsers(),
      authApi.listWorkspaces()
    ])
    userRows.value = users ?? []
    workspaceRows.value = workspaces ?? []
    if (formState.value.workspaceIds.length === 0) {
      formState.value.workspaceIds = workspaceRows.value[0] ? [workspaceRows.value[0].workspaceId] : []
    }
  } catch (error) {
    userRows.value = []
    workspaceRows.value = []
    statusMessage.value = normalizeError(error)
  } finally {
    isLoading.value = false
  }
}

function resetForm(): void {
  editingUserId.value = ''
  formState.value = {
    account: '',
    displayName: '',
    password: '',
    role: 'user',
    enabled: true,
    workspaceIds: workspaceRows.value[0] ? [workspaceRows.value[0].workspaceId] : []
  }
}

function startEdit(user: UserAccountRow): void {
  editingUserId.value = user.userId
  formState.value = {
    account: user.account,
    displayName: user.displayName,
    password: '',
    role: user.role === 'super_admin' ? 'super_admin' : 'user',
    enabled: user.enabled,
    workspaceIds: user.workspaces.map((workspace) => workspace.workspaceId)
  }
  statusMessage.value = ''
}

async function submitUser(): Promise<void> {
  if (!canSubmit.value || isSaving.value) {
    statusMessage.value = isEditing.value ? '请完整填写姓名和工作组' : '请完整填写账号、姓名、密码和工作组'
    return
  }

  isSaving.value = true
  statusMessage.value = ''
  try {
    if (isEditing.value) {
      await authApi.updateUser({
        userId: editingUserId.value,
        displayName: formState.value.displayName.trim(),
        role: formState.value.role,
        enabled: formState.value.enabled,
        workspaceIds: formState.value.workspaceIds
      })
      statusMessage.value = '账号已更新'
    } else {
      await authApi.createUser({
        account: formState.value.account.trim(),
        displayName: formState.value.displayName.trim(),
        password: formState.value.password,
        role: formState.value.role,
        workspaceIds: formState.value.workspaceIds
      })
      statusMessage.value = '账号已创建'
    }
    resetForm()
    await loadData()
  } catch (error) {
    statusMessage.value = normalizeError(error)
  } finally {
    isSaving.value = false
  }
}

async function deleteUser(user: UserAccountRow): Promise<void> {
  if (isSaving.value) {
    return
  }
  isSaving.value = true
  statusMessage.value = ''
  try {
    await authApi.deleteUser({
      userId: user.userId
    })
    if (editingUserId.value === user.userId) {
      resetForm()
    }
    statusMessage.value = '账号已删除'
    await loadData()
  } catch (error) {
    statusMessage.value = normalizeError(error)
  } finally {
    isSaving.value = false
  }
}

function roleText(role: string): string {
  if (role === 'super_admin') {
    return '超级管理员'
  }
  if (role === 'guest') {
    return '访客'
  }
  return '成员'
}

function workspaceText(workspaces: WorkspaceSummary[]): string {
  return workspaces.map((workspace) => workspace.workspaceName).join('、')
}

onMounted(() => {
  void loadData()
})
</script>

<template>
  <section class="admin-content-page">
    <article class="admin-content-panel">
      <h1 class="admin-content-title">账号管理</h1>
      <p class="admin-content-copy">创建账号并分配角色和工作组。注册不开放给用户自助完成。</p>

      <form
        class="admin-form"
        @submit.prevent="submitUser"
      >
        <label class="admin-field">
          <span>账号</span>
          <input
            v-model="formState.account"
            :disabled="isEditing"
            placeholder="例如 zhangsan"
          >
        </label>
        <label class="admin-field">
          <span>显示名称</span>
          <input
            v-model="formState.displayName"
            placeholder="例如 张三"
          >
        </label>
        <label class="admin-field">
          <span>初始密码</span>
          <input
            v-model="formState.password"
            type="password"
            :disabled="isEditing"
            :placeholder="isEditing ? '编辑时不修改密码' : '由管理员分配'"
          >
        </label>
        <label class="admin-field">
          <span>角色</span>
          <select v-model="formState.role">
            <option value="user">成员</option>
            <option value="super_admin">超级管理员</option>
          </select>
        </label>
        <label class="admin-field">
          <span>状态</span>
          <select v-model="formState.enabled">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <div class="admin-field admin-field-wide">
          <span>可访问工作组</span>
          <div class="workspace-choice-list">
            <label
              v-for="workspace in workspaceRows"
              :key="workspace.workspaceId"
              class="workspace-choice"
              :class="{ 'workspace-choice-selected': formState.workspaceIds.includes(workspace.workspaceId) }"
            >
              <input
                v-model="formState.workspaceIds"
                type="checkbox"
                :value="workspace.workspaceId"
              >
              <span class="workspace-choice-mark"></span>
              <span class="workspace-choice-name">{{ workspace.workspaceName }}</span>
            </label>
          </div>
        </div>
        <button
          class="admin-primary-button"
          type="submit"
          :disabled="isSaving || !canSubmit"
        >
          {{ isSaving ? '保存中' : isEditing ? '保存账号' : '创建账号' }}
        </button>
        <button
          v-if="isEditing"
          class="admin-secondary-button"
          type="button"
          :disabled="isSaving"
          @click="resetForm"
        >
          取消
        </button>
      </form>

      <p
        v-if="statusMessage"
        class="admin-status"
      >
        {{ statusMessage }}
      </p>
    </article>

    <article class="admin-content-panel">
      <h2 class="admin-section-title">账号列表</h2>
      <div
        v-if="userRows.length === 0"
        class="admin-empty"
      >
        {{ isLoading ? '正在加载账号' : '暂无账号' }}
      </div>
      <div
        v-else
        class="user-list"
      >
        <article
          v-for="user in userRows"
          :key="user.userId"
          class="user-row"
        >
          <div>
            <strong>{{ user.displayName }}</strong>
            <span>{{ user.account }}</span>
          </div>
          <div>
            <strong>{{ roleText(user.role) }}</strong>
            <span>{{ workspaceText(user.workspaces) || user.workspaceName }}</span>
          </div>
          <div>
            <strong>{{ user.enabled ? '启用' : '停用' }}</strong>
            <span>{{ user.userId }}</span>
          </div>
          <div class="row-actions">
            <button
              type="button"
              @click="startEdit(user)"
            >
              编辑
            </button>
            <button
              type="button"
              @click="deleteUser(user)"
            >
              删除
            </button>
          </div>
        </article>
      </div>
    </article>
  </section>
</template>

<style scoped>
.admin-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: end;
  margin-top: 18px;
}

.admin-field {
  display: grid;
  gap: 6px;
  color: var(--admin-color-subtle);
  font-size: 13px;
  font-weight: 700;
}

.admin-field input,
.admin-field select,
.admin-primary-button,
.admin-secondary-button {
  height: var(--admin-control-height);
  border-radius: var(--admin-radius-control);
}

.admin-field-wide {
  grid-column: span 2;
}

.admin-field input,
.admin-field select {
  padding: 0 12px;
  border: 1px solid var(--admin-color-field-border);
  outline: none;
  color: var(--admin-color-text);
  font-size: var(--admin-control-font-size);
}

.admin-field input:disabled {
  color: var(--admin-color-muted);
  cursor: not-allowed;
}

.workspace-choice-list {
  display: grid;
  gap: 8px;
  min-height: 104px;
  padding: 8px;
  border: 1px solid var(--admin-color-field-border);
  border-radius: var(--admin-radius-control);
  background: #ffffff;
}

.workspace-choice {
  position: relative;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-height: 36px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--admin-color-text);
  cursor: pointer;
  font-size: var(--admin-control-font-size);
  font-weight: 700;
}

.workspace-choice input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.workspace-choice-mark {
  position: relative;
  width: 18px;
  height: 18px;
  border: 1px solid var(--admin-color-field-border);
  border-radius: 5px;
  background: #ffffff;
}

.workspace-choice-mark::after {
  position: absolute;
  top: 3px;
  left: 6px;
  width: 4px;
  height: 8px;
  border: solid #ffffff;
  border-width: 0 2px 2px 0;
  content: '';
  opacity: 0;
  transform: rotate(45deg);
}

.workspace-choice-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-choice-selected {
  border-color: var(--admin-color-border-strong);
  background: var(--admin-color-accent-soft);
}

.workspace-choice-selected .workspace-choice-mark {
  border-color: var(--admin-color-accent);
  background: var(--admin-color-accent);
}

.workspace-choice-selected .workspace-choice-mark::after {
  opacity: 1;
}

.workspace-choice:focus-within {
  border-color: var(--admin-color-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--admin-color-accent) 18%, transparent);
}

.admin-primary-button,
.admin-secondary-button {
  padding: 0 16px;
  font-weight: 700;
}

.admin-primary-button {
  border: 1px solid var(--admin-color-accent);
  background: var(--admin-color-accent);
  color: #ffffff;
}

.admin-secondary-button {
  border: 1px solid var(--admin-color-field-border);
  background: #ffffff;
  color: var(--admin-color-text);
}

.admin-primary-button:disabled,
.admin-secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.admin-section-title {
  margin: 0;
  color: var(--admin-color-title);
  font-size: 20px;
}

.admin-status,
.admin-empty {
  margin: 14px 0 0;
  color: var(--admin-color-muted);
  font-size: 13px;
  line-height: 1.6;
}

.user-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.user-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 0.75fr) minmax(120px, 0.4fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px;
  border: 1px solid #d8e6e8;
  border-radius: var(--admin-radius-panel);
  background: var(--admin-color-card-soft);
}

.user-row div {
  display: grid;
  gap: 4px;
}

.user-row strong {
  color: var(--admin-color-text);
}

.user-row span {
  min-width: 0;
  overflow: hidden;
  color: var(--admin-color-muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-actions {
  display: flex;
  gap: 8px;
}

.row-actions button {
  min-width: 56px;
  height: 32px;
  border: 1px solid var(--admin-color-field-border);
  border-radius: 6px;
  background: #ffffff;
  color: var(--admin-color-text);
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .admin-form,
  .user-row {
    grid-template-columns: 1fr;
  }

  .admin-field-wide {
    grid-column: auto;
  }
}
</style>
