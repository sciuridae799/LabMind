<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { authApi } from '../../shared/api/auth'
import type { WorkspaceSummary } from '../../shared/auth/authSession'

const workspaceRows = ref<WorkspaceSummary[]>([])
const editingWorkspaceId = ref('')
const formState = ref({
  workspaceName: '',
  workspaceCode: ''
})
const statusMessage = ref('')
const isLoading = ref(false)
const isSaving = ref(false)
const isEditing = computed(() => editingWorkspaceId.value.length > 0)
const canSubmit = computed(() => {
  return Boolean(formState.value.workspaceName.trim() && !isSaving.value)
})

function normalizeError(error: unknown): string {
  return error instanceof Error ? error.message : '工作组请求失败'
}

async function loadWorkspaces(): Promise<void> {
  isLoading.value = true
  statusMessage.value = ''
  try {
    workspaceRows.value = await authApi.listWorkspaces() ?? []
  } catch (error) {
    workspaceRows.value = []
    statusMessage.value = normalizeError(error)
  } finally {
    isLoading.value = false
  }
}

function resetForm(): void {
  editingWorkspaceId.value = ''
  formState.value = {
    workspaceName: '',
    workspaceCode: ''
  }
}

function startEdit(workspace: WorkspaceSummary): void {
  editingWorkspaceId.value = workspace.workspaceId
  formState.value = {
    workspaceName: workspace.workspaceName,
    workspaceCode: workspace.workspaceId
  }
  statusMessage.value = ''
}

async function submitWorkspace(): Promise<void> {
  const workspaceName = formState.value.workspaceName.trim()
  if (!workspaceName || isSaving.value) {
    statusMessage.value = '请输入工作组名称'
    return
  }

  isSaving.value = true
  statusMessage.value = ''
  try {
    if (isEditing.value) {
      await authApi.updateWorkspace({
        workspaceId: editingWorkspaceId.value,
        workspaceName
      })
      statusMessage.value = '工作组已更新'
    } else {
      await authApi.createWorkspace({
        workspaceName,
        workspaceCode: formState.value.workspaceCode.trim()
      })
      statusMessage.value = '工作组已创建'
    }
    resetForm()
    await loadWorkspaces()
  } catch (error) {
    statusMessage.value = normalizeError(error)
  } finally {
    isSaving.value = false
  }
}

async function deleteWorkspace(workspace: WorkspaceSummary): Promise<void> {
  if (isSaving.value) {
    return
  }
  isSaving.value = true
  statusMessage.value = ''
  try {
    await authApi.deleteWorkspace({
      workspaceId: workspace.workspaceId
    })
    if (editingWorkspaceId.value === workspace.workspaceId) {
      resetForm()
    }
    statusMessage.value = '工作组已删除'
    await loadWorkspaces()
  } catch (error) {
    statusMessage.value = normalizeError(error)
  } finally {
    isSaving.value = false
  }
}

onMounted(() => {
  void loadWorkspaces()
})
</script>

<template>
  <section class="admin-content-page">
    <article class="admin-content-panel">
      <h1 class="admin-content-title">工作组管理</h1>
      <p class="admin-content-copy">创建实验室或课程组，后续账号与文档都归属到具体工作组。</p>

      <form
        class="admin-form"
        @submit.prevent="submitWorkspace"
      >
        <label class="admin-field">
          <span>工作组名称</span>
          <input
            v-model="formState.workspaceName"
            placeholder="例如 机器人实验室"
          >
        </label>
        <label class="admin-field">
          <span>工作组编码</span>
          <input
            v-model="formState.workspaceCode"
            :disabled="isEditing"
            placeholder="可空，例如 robotics-lab"
          >
        </label>
        <button
          class="admin-primary-button"
          type="submit"
          :disabled="!canSubmit"
        >
          {{ isSaving ? '保存中' : isEditing ? '保存工作组' : '创建工作组' }}
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
      <h2 class="admin-section-title">工作组列表</h2>
      <div
        v-if="workspaceRows.length === 0"
        class="admin-empty"
      >
        {{ isLoading ? '正在加载工作组' : '暂无工作组' }}
      </div>
      <div
        v-else
        class="workspace-list"
      >
        <article
          v-for="workspace in workspaceRows"
          :key="workspace.workspaceId"
          class="workspace-row"
        >
          <div>
            <strong>{{ workspace.workspaceName }}</strong>
            <span>{{ workspace.workspaceId }}</span>
          </div>
          <div class="row-actions">
            <button
              type="button"
              @click="startEdit(workspace)"
            >
              编辑
            </button>
            <button
              type="button"
              @click="deleteWorkspace(workspace)"
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
  grid-template-columns: repeat(2, minmax(0, 1fr)) auto;
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
.admin-primary-button,
.admin-secondary-button {
  height: var(--admin-control-height);
  border-radius: var(--admin-radius-control);
}

.admin-field input {
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

.workspace-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.workspace-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px;
  border: 1px solid #d8e6e8;
  border-radius: var(--admin-radius-panel);
  background: var(--admin-color-card-soft);
}

.workspace-row strong {
  color: var(--admin-color-text);
}

.workspace-row span {
  color: var(--admin-color-muted);
  font-size: 13px;
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

@media (max-width: 768px) {
  .admin-form,
  .workspace-row {
    grid-template-columns: 1fr;
  }

  .row-actions {
    justify-content: flex-start;
  }
}
</style>
