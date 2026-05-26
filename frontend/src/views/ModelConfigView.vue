<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import {
  chatApi,
  modelApiBaseUrlOptions,
  modelApiModelOptions,
  modelApiProviderOptions,
  type ModelApiConfig,
  type ModelApiProvider
} from '../shared/api/chat'

interface ModelConfigForm {
  id: string
  provider: ModelApiProvider
  displayName: string
  baseUrl: string
  modelName: string
  apiKey: string
  enabled: boolean
}

const emptyForm = (): ModelConfigForm => ({
  id: '',
  provider: 'ZHIPU',
  displayName: '',
  baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
  modelName: '',
  apiKey: '',
  enabled: true
})

const configs = ref<ModelApiConfig[]>([])
const form = ref<ModelConfigForm>(emptyForm())
const editingConfig = ref<ModelApiConfig | null>(null)
const formMode = ref<'idle' | 'create' | 'edit'>('idle')
const statusMessage = ref('')
const isLoading = ref(false)
const isSaving = ref(false)
const deletingId = ref('')
const togglingEnabledId = ref('')
const movingConfigId = ref('')
const draggingConfigId = ref('')
const dragOverConfigId = ref('')
const deleteConfirmConfigId = ref('')

const isEditing = computed<boolean>(() => formMode.value === 'edit')

const isFormVisible = computed<boolean>(() => formMode.value !== 'idle')

const formTitle = computed<string>(() => (isEditing.value ? 'Edit model API' : 'Add model API'))

const baseUrlOptions = computed(() => modelApiBaseUrlOptions[form.value.provider])

const modelOptions = computed(() => modelApiModelOptions[form.value.provider])

function resetForm(): void {
  form.value = emptyForm()
  editingConfig.value = null
  formMode.value = 'idle'
}

function startCreate(): void {
  form.value = emptyForm()
  editingConfig.value = null
  formMode.value = 'create'
  applyProviderDefaults()
  statusMessage.value = ''
}

function startEdit(config: ModelApiConfig): void {
  editingConfig.value = config
  formMode.value = 'edit'
  form.value = {
    id: config.id,
    provider: config.provider,
    displayName: config.displayName,
    baseUrl: config.baseUrl,
    modelName: config.modelName,
    apiKey: '',
    enabled: config.enabled
  }
  statusMessage.value = ''
}

function applyProviderDefaults(): void {
  form.value.baseUrl = baseUrlOptions.value[0]?.value ?? ''
  form.value.modelName = modelOptions.value[0]?.value ?? ''
  form.value.displayName = buildDisplayName(form.value.modelName)
}

function handleProviderChange(): void {
  applyProviderDefaults()
}

function handleModelChange(): void {
  form.value.displayName = buildDisplayName(form.value.modelName)
}

function buildDisplayName(modelName: string): string {
  return modelName
}

async function loadConfigs(): Promise<void> {
  isLoading.value = true
  statusMessage.value = ''
  try {
    configs.value = await chatApi.listModelConfigs()
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : '配置加载失败'
  } finally {
    isLoading.value = false
  }
}

async function saveConfig(): Promise<void> {
  isSaving.value = true
  statusMessage.value = ''
  try {
    await chatApi.saveModelConfig({
      id: formMode.value === 'edit' ? form.value.id : undefined,
      provider: form.value.provider,
      displayName: form.value.displayName,
      baseUrl: form.value.baseUrl,
      modelName: form.value.modelName,
      apiKey: form.value.apiKey || undefined,
      enabled: form.value.enabled
    })
    resetForm()
    await loadConfigs()
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : '配置保存失败'
  } finally {
    isSaving.value = false
  }
}

async function deleteConfig(config: ModelApiConfig): Promise<void> {
  deletingId.value = config.id
  statusMessage.value = ''
  try {
    await chatApi.deleteModelConfig(config.id)
    if (editingConfig.value?.id === config.id) {
      resetForm()
    }
    await loadConfigs()
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : '配置删除失败'
  } finally {
    deletingId.value = ''
    deleteConfirmConfigId.value = ''
  }
}

function requestDeleteConfig(config: ModelApiConfig): void {
  if (deletingId.value || togglingEnabledId.value) {
    return
  }

  deleteConfirmConfigId.value = config.id
}

function cancelDeleteConfig(): void {
  deleteConfirmConfigId.value = ''
}

function confirmDeleteConfig(config: ModelApiConfig): void {
  void deleteConfig(config)
}

async function toggleConfigEnabled(config: ModelApiConfig): Promise<void> {
  togglingEnabledId.value = config.id
  statusMessage.value = ''
  try {
    await chatApi.saveModelConfig({
      id: config.id,
      provider: config.provider,
      displayName: config.displayName,
      baseUrl: config.baseUrl,
      modelName: config.modelName,
      enabled: !config.enabled
    })
    if (editingConfig.value?.id === config.id) {
      form.value.enabled = !config.enabled
      editingConfig.value = {
        ...editingConfig.value,
        enabled: !config.enabled
      }
    }
    await loadConfigs()
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : '配置状态更新失败'
  } finally {
    togglingEnabledId.value = ''
  }
}

function handleConfigDragStart(event: DragEvent, config: ModelApiConfig): void {
  if (movingConfigId.value || deletingId.value || togglingEnabledId.value) {
    event.preventDefault()
    return
  }

  draggingConfigId.value = config.id
  event.dataTransfer?.setData('text/plain', config.id)
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function handleConfigDragOver(event: DragEvent, config: ModelApiConfig): void {
  if (!draggingConfigId.value || draggingConfigId.value === config.id) {
    return
  }

  event.preventDefault()
  dragOverConfigId.value = config.id
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

async function handleConfigDrop(event: DragEvent, targetConfig: ModelApiConfig): Promise<void> {
  event.preventDefault()
  const sourceId = draggingConfigId.value || event.dataTransfer?.getData('text/plain') || ''
  const sourceIndex = configs.value.findIndex((config) => config.id === sourceId)
  const targetIndex = configs.value.findIndex((config) => config.id === targetConfig.id)
  draggingConfigId.value = ''
  dragOverConfigId.value = ''

  if (sourceIndex < 0 || targetIndex < 0 || sourceIndex === targetIndex) {
    return
  }

  movingConfigId.value = sourceId
  statusMessage.value = ''
  try {
    const direction: 'UP' | 'DOWN' = sourceIndex < targetIndex ? 'DOWN' : 'UP'
    const moveCount = Math.abs(targetIndex - sourceIndex)
    for (let index = 0; index < moveCount; index += 1) {
      await chatApi.moveModelConfig(sourceId, direction)
    }
    await loadConfigs()
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : '配置顺序更新失败'
  } finally {
    movingConfigId.value = ''
  }
}

function handleConfigDragEnd(): void {
  draggingConfigId.value = ''
  dragOverConfigId.value = ''
}

onMounted(() => {
  void loadConfigs()
})
</script>

<template>
  <div class="model-config-page">
    <aside class="config-sidebar">
      <RouterLink
        to="/chat"
        class="sidebar-action"
      >
        返回问答
      </RouterLink>
    </aside>

    <main class="config-main">
      <header class="config-header">
        <div>
          <h1>模型配置</h1>
          <p>管理实验室文档助手可用的模型服务；只有完整启用的配置会出现在聊天页。</p>
        </div>
        <button
          type="button"
          class="primary-action"
          @click="startCreate"
        >
          新增模型
        </button>
      </header>

      <p
        v-if="statusMessage"
        class="status-message"
      >
        {{ statusMessage }}
      </p>

      <section class="config-content">
        <div class="config-list">
          <p
            v-if="isLoading"
            class="empty-text"
          >
            加载中
          </p>
          <p
            v-else-if="configs.length === 0"
            class="empty-text"
          >
            暂无模型 API 配置
          </p>

          <template v-else>
            <article
              v-for="(config, index) in configs"
              :key="config.id"
              class="config-row"
              :class="{
                disabled: !config.enabled,
                dragging: draggingConfigId === config.id,
                'drag-over': dragOverConfigId === config.id
              }"
              draggable="true"
              @dragstart="handleConfigDragStart($event, config)"
              @dragover="handleConfigDragOver($event, config)"
              @drop="handleConfigDrop($event, config)"
              @dragend="handleConfigDragEnd"
            >
              <button
                type="button"
                class="config-row-main"
                @click="startEdit(config)"
              >
                <span class="config-title-row">
                  <span class="drag-handle" aria-hidden="true">⋮⋮</span>
                  <span class="config-name">{{ config.displayName }}</span>
                  <span
                    v-if="index === 0"
                    class="config-default"
                  >
                    默认模型
                  </span>
                </span>
                <span class="config-meta">{{ config.provider }} · {{ config.modelName }}</span>
                <span class="config-url">{{ config.baseUrl }}</span>
              </button>
              <div class="config-row-side">
                <span
                  class="config-badge"
                  :class="{ muted: !config.apiKeyConfigured }"
                >
                  {{ config.apiKeyConfigured ? 'Key 已配置' : '未配置 Key' }}
                </span>
                <button
                  type="button"
                  class="config-badge config-toggle"
                  :class="{ muted: !config.enabled }"
                  :disabled="togglingEnabledId === config.id"
                  @click="toggleConfigEnabled(config)"
                >
                  {{ config.enabled ? '已启用' : '已停用' }}
                </button>
                <div class="row-actions">
                  <button
                    type="button"
                    class="text-action danger"
                    :disabled="deletingId === config.id"
                    @click="requestDeleteConfig(config)"
                  >
                    删除
                  </button>
                  <div
                    v-if="deleteConfirmConfigId === config.id"
                    class="delete-popover config-delete-popover"
                  >
                    <strong>删除模型配置？</strong>
                    <span>删除后该模型不会再出现在聊天页。</span>
                    <div class="delete-popover-actions">
                      <button
                        type="button"
                        class="delete-popover-cancel"
                        :disabled="deletingId.length > 0"
                        @click="cancelDeleteConfig"
                      >
                        取消
                      </button>
                      <button
                        type="button"
                        class="delete-popover-confirm"
                        :disabled="deletingId.length > 0"
                        @click="confirmDeleteConfig(config)"
                      >
                        删除
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </article>
          </template>
        </div>

        <form
          v-if="isFormVisible"
          class="config-form"
          @submit.prevent="saveConfig"
        >
          <div class="form-heading">
            <h2>{{ formTitle }}</h2>
            <span>{{ form.displayName || '未命名配置' }}</span>
          </div>

          <label>
            <span>供应商</span>
            <select
              v-model="form.provider"
              @change="handleProviderChange"
            >
              <option
                v-for="provider in modelApiProviderOptions"
                :key="provider.value"
                :value="provider.value"
              >
                {{ provider.value }}
              </option>
            </select>
          </label>

          <label>
            <span>Base URL</span>
            <select
              v-model="form.baseUrl"
              required
            >
              <option
                v-for="option in baseUrlOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.value }}
              </option>
            </select>
          </label>

          <label>
            <span>模型</span>
            <select
              v-model="form.modelName"
              required
              @change="handleModelChange"
            >
              <option
                v-for="option in modelOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.value }}
              </option>
            </select>
          </label>

          <label>
            <span>显示名称</span>
            <input
              v-model="form.displayName"
              required
              maxlength="64"
              placeholder="deepseek-v4-flash"
            >
          </label>

          <label>
            <span>API Key</span>
            <input
              v-model="form.apiKey"
              type="password"
              maxlength="512"
              autocomplete="off"
              :placeholder="editingConfig?.apiKeyConfigured ? '留空则不修改已配置 Key' : '可先留空，补齐后才进入聊天可选模型'"
            >
          </label>

          <label class="enabled-toggle">
            <input
              v-model="form.enabled"
              type="checkbox"
            >
            <span>启用</span>
          </label>

          <div class="form-actions">
            <button
              type="button"
              class="secondary-action"
              @click="resetForm"
            >
              取消
            </button>
            <button
              type="submit"
              class="primary-action"
              :disabled="isSaving"
            >
              保存
            </button>
          </div>
        </form>
      </section>
    </main>
  </div>
</template>

<style scoped>
.model-config-page,
.model-config-page * {
  box-sizing: border-box;
}

.model-config-page {
  display: flex;
  min-height: calc(100vh - 56px);
  color: #173033;
  background:
    linear-gradient(rgba(21, 94, 99, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(21, 94, 99, 0.035) 1px, transparent 1px),
    linear-gradient(135deg, rgba(230, 246, 245, 0.88) 0%, rgba(243, 248, 251, 0.96) 45%, #edf5f7 100%);
  background-size: 28px 28px, 28px 28px, auto;
}

.config-sidebar {
  width: 260px;
  padding: 16px 12px;
  border-right: 1px solid #d8e6e8;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.74) 0%, rgba(245, 250, 249, 0.94) 100%),
    repeating-linear-gradient(0deg, rgba(15, 118, 110, 0.035) 0, rgba(15, 118, 110, 0.035) 1px, transparent 1px, transparent 24px);
}

.sidebar-action {
  display: flex;
  align-items: center;
  width: 100%;
  height: 42px;
  padding: 0 12px;
  color: #284247;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  border: 1px solid #cae4e0;
  border-radius: 8px;
  background: #eef8f6;
  box-shadow: 0 1px 2px rgba(17, 78, 84, 0.06);
}

.sidebar-action:hover {
  color: #0f766e;
  border-color: #95cfc8;
  background: #e5f4f2;
}

.config-main {
  flex: 1;
  min-width: 0;
  padding: 32px 38px 48px;
}

.config-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  max-width: 1040px;
  margin: 0 auto 18px;
}

.config-header h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: 0;
  color: #102a2f;
}

.config-header p {
  max-width: 640px;
  margin: 6px 0 0;
  color: #60787f;
  font-size: 13px;
  line-height: 1.6;
}

.status-message {
  max-width: 1040px;
  margin: 0 auto 16px;
  padding: 10px 12px;
  color: #7f1d1d;
  font-size: 13px;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
}

.config-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 18px;
  align-items: start;
  max-width: 1120px;
  margin: 0 auto;
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.empty-text {
  margin: 0;
  padding: 28px 0;
  color: #60787f;
  font-size: 14px;
}

.config-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  padding: 14px;
  border: 1px solid #cfe1e4;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(17, 78, 84, 0.06);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;
  cursor: grab;
}

.config-row:hover {
  border-color: #95cfc8;
  box-shadow: 0 14px 30px rgba(17, 78, 84, 0.1);
}

.config-row.dragging {
  opacity: 0.52;
  cursor: grabbing;
}

.config-row.drag-over {
  border-color: #0f766e;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.14);
}

.config-row.disabled {
  opacity: 0.68;
}

.config-row-main {
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.config-name,
.config-meta,
.config-url {
  display: block;
}

.config-title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.drag-handle {
  color: #789098;
  font-size: 15px;
  line-height: 1;
  cursor: grab;
}

.config-name {
  min-width: 0;
  color: #173033;
  font-size: 15px;
  font-weight: 650;
  overflow-wrap: anywhere;
}

.config-meta {
  margin-top: 5px;
  color: #385156;
  font-size: 13px;
}

.config-url {
  margin-top: 4px;
  color: #60787f;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.config-row-side {
  display: flex;
  min-width: 178px;
  flex-direction: column;
  align-items: flex-end;
  gap: 7px;
}

.config-default {
  flex: 0 0 auto;
  padding: 3px 8px;
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
  border: 1px solid #99d8d0;
  border-radius: 999px;
  background: #e8f7f5;
}

.config-badge {
  color: #0f766e;
  font-size: 12px;
}

.config-toggle {
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.config-badge.muted {
  color: #60787f;
}

.config-toggle:disabled {
  color: #789098;
  cursor: not-allowed;
}

.row-actions {
  position: relative;
  display: flex;
  gap: 8px;
}

.text-action {
  padding: 0;
  color: #0f766e;
  font-size: 12px;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.text-action.danger {
  color: #c53030;
}

.text-action:disabled {
  color: #789098;
  cursor: not-allowed;
}

.delete-popover {
  position: absolute;
  z-index: 20;
  display: flex;
  width: 236px;
  flex-direction: column;
  gap: 7px;
  padding: 12px;
  color: #284247;
  text-align: left;
  border: 1px solid #f2b8b5;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 40px rgba(17, 78, 84, 0.16);
}

.config-delete-popover {
  top: calc(100% + 8px);
  right: 0;
}

.delete-popover strong {
  color: #9f1d1d;
  font-size: 13px;
  font-weight: 650;
}

.delete-popover span {
  color: #60787f;
  font-size: 12px;
  line-height: 1.45;
}

.delete-popover-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 2px;
}

.delete-popover-cancel,
.delete-popover-confirm {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 7px;
  cursor: pointer;
}

.delete-popover-cancel {
  color: #284247;
  border: 1px solid #c9dde1;
  background: #ffffff;
}

.delete-popover-confirm {
  color: #ffffff;
  border: 1px solid #c53030;
  background: #c53030;
}

.delete-popover-cancel:disabled,
.delete-popover-confirm:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.config-form {
  display: flex;
  flex-direction: column;
  gap: 13px;
  padding: 20px;
  border: 1px solid #cfe1e4;
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(249, 253, 253, 0.94) 100%),
    repeating-linear-gradient(90deg, rgba(15, 118, 110, 0.035) 0, rgba(15, 118, 110, 0.035) 1px, transparent 1px, transparent 34px);
  box-shadow: 0 14px 32px rgba(17, 78, 84, 0.08);
}

.form-heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 2px;
}

.form-heading h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  color: #102a2f;
}

.form-heading span {
  color: #60787f;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.config-form label {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.config-form label > span {
  color: #385156;
  font-size: 12px;
  font-weight: 600;
}

.config-form input,
.config-form select {
  width: 100%;
  height: 40px;
  padding: 0 11px;
  color: #173033;
  font-size: 13px;
  border: 1px solid #c9dde1;
  border-radius: 8px;
  outline: none;
  background: #ffffff;
}

.config-form input:focus,
.config-form select:focus {
  border-color: #5cc3b8;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.14);
}

.enabled-toggle {
  flex-direction: row !important;
  align-items: center;
}

.enabled-toggle input {
  width: 16px;
  height: 16px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

.primary-action,
.secondary-action {
  height: 34px;
  padding: 0 13px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  border-radius: 7px;
  cursor: pointer;
}

.primary-action {
  color: #ffffff;
  border: 1px solid #0f766e;
  background: #0f766e;
}

.secondary-action {
  color: #284247;
  border: 1px solid #c9dde1;
  background: #ffffff;
}

.primary-action:disabled {
  border-color: #a8b9bd;
  background: #a8b9bd;
  cursor: not-allowed;
}

@media (max-width: 960px) {
  .model-config-page {
    flex-direction: column;
  }

  .config-sidebar {
    width: 100%;
    border-right: 0;
    border-bottom: 1px solid #d8e6e8;
  }

  .config-main {
    padding: 26px 18px 36px;
  }

  .config-header {
    align-items: flex-start;
    gap: 14px;
  }

  .config-header h1 {
    font-size: 21px;
  }

  .config-header p {
    font-size: 12px;
  }

  .config-content {
    grid-template-columns: 1fr;
  }

  .config-row {
    grid-template-columns: 1fr;
  }

  .config-row-side {
    align-items: flex-start;
  }
}
</style>
