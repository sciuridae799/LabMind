<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  businessChatModeOptions,
  chatApi,
  type BusinessChatSessionListItem,
  type BusinessChatMode
} from '../../shared/api/chat'

const router = useRouter()

const keyword = ref('')
const chatMode = ref<'ALL' | BusinessChatMode>('ALL')
const turnStatus = ref('ALL')
const pageNo = ref(1)
const pageSize = 10
const totalSize = ref(0)
const totalPages = ref(0)
const sessions = ref<BusinessChatSessionListItem[]>([])
const loading = ref(false)
const errorMessage = ref('')

const turnStatusOptions = [
  { value: 'ALL', label: '全部状态' },
  { value: 'RUNNING', label: '运行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'FAILED', label: '失败' },
  { value: 'STOPPED', label: '已停止' }
] as const

const paginationText = computed(() => {
  if (totalSize.value === 0) {
    return '0 / 0'
  }
  return `${pageNo.value} / ${Math.max(totalPages.value, 1)}`
})

function modeLabel(value: string): string {
  return businessChatModeOptions.find((item) => item.value === value)?.label || value
}

function statusLabel(value: string): string {
  return turnStatusOptions.find((item) => item.value === value)?.label || value
}

function previewText(value: unknown): string {
  const normalized = String(value || '').replace(/\s+/g, ' ').trim()
  return normalized || '-'
}

function openSession(row: BusinessChatSessionListItem): void {
  void router.push({
    name: 'AdminObservabilitySession',
    params: {
      conversationId: row.conversationId
    }
  })
}

async function loadSessions(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await chatApi.listSessionsPage({
      keyword: keyword.value,
      chatMode: chatMode.value,
      turnStatus: turnStatus.value,
      pageNo: String(pageNo.value),
      pageSize: String(pageSize)
    })
    sessions.value = page.sessions
    totalSize.value = page.totalSize
    totalPages.value = page.totalPages
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '会话列表加载失败'
  } finally {
    loading.value = false
  }
}

function submitFilters(): void {
  pageNo.value = 1
  void loadSessions()
}

function changePage(delta: number): void {
  const nextPageNo = pageNo.value + delta
  if (nextPageNo < 1 || (totalPages.value > 0 && nextPageNo > totalPages.value)) {
    return
  }
  pageNo.value = nextPageNo
  void loadSessions()
}

watch([chatMode, turnStatus], () => {
  submitFilters()
})

onMounted(loadSessions)
</script>

<template>
  <section class="observability-list-page">
    <header class="list-header">
      <div>
        <p class="admin-content-eyebrow">Conversation Trace</p>
        <h2 class="admin-content-title">对话观测</h2>
      </div>
      <button class="ghost-button" type="button" :disabled="loading" @click="loadSessions">
        刷新
      </button>
    </header>

    <section class="filter-panel">
      <label class="filter-field keyword-field">
        <span>关键词</span>
        <input
          v-model="keyword"
          placeholder="标题、问题或回答"
          @keyup.enter="submitFilters"
        >
      </label>
      <label class="filter-field">
        <span>对话模式</span>
        <select v-model="chatMode">
          <option value="ALL">全部模式</option>
          <option
            v-for="option in businessChatModeOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </option>
        </select>
      </label>
      <label class="filter-field">
        <span>轮次状态</span>
        <select v-model="turnStatus">
          <option
            v-for="option in turnStatusOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </option>
        </select>
      </label>
      <button class="primary-button" type="button" :disabled="loading" @click="submitFilters">
        查询
      </button>
    </section>

    <div v-if="errorMessage" class="error-panel">{{ errorMessage }}</div>

    <section class="table-panel">
      <div class="table-meta">
        <span>共 {{ totalSize }} 条会话</span>
        <span>{{ paginationText }}</span>
      </div>

      <div class="session-list">
        <button
          v-for="row in sessions"
          :key="row.conversationId"
          class="session-card"
          type="button"
          @click="openSession(row)"
        >
          <span class="session-card-main">
            <span class="session-card-head">
              <strong>{{ previewText(row.title) }}</strong>
              <span class="state-pill">{{ statusLabel(row.turnStatus) }}</span>
            </span>
            <small>{{ row.conversationId }}</small>
            <span class="session-card-text question">{{ previewText(row.lastQuestion) }}</span>
            <span class="session-card-text answer">{{ previewText(row.lastReply) }}</span>
          </span>
          <span class="session-card-side">
            <span>
              <small>模式</small>
              <strong>{{ modeLabel(row.chatMode) }}</strong>
            </span>
            <span>
              <small>更新时间</small>
              <strong>{{ row.updateTime || '-' }}</strong>
            </span>
            <span class="link-text">查看链路</span>
          </span>
        </button>
      </div>

      <p v-if="!loading && sessions.length === 0" class="empty-text">暂无会话记录</p>
      <p v-if="loading" class="empty-text">正在加载会话记录...</p>

      <footer class="pagination-bar">
        <button class="ghost-button" type="button" :disabled="loading || pageNo <= 1" @click="changePage(-1)">
          上一页
        </button>
        <button
          class="ghost-button"
          type="button"
          :disabled="loading || totalPages === 0 || pageNo >= totalPages"
          @click="changePage(1)"
        >
          下一页
        </button>
      </footer>
    </section>
  </section>
</template>

<style scoped>
.observability-list-page {
  display: grid;
  width: min(100%, 1120px);
  gap: 16px;
  margin: 0 auto;
}

.list-header,
.filter-panel,
.table-meta,
.pagination-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.list-header,
.table-meta,
.pagination-bar {
  justify-content: space-between;
}

.filter-panel,
.table-panel,
.error-panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  padding: 16px;
  box-shadow: 0 8px 22px rgb(16 24 40 / 5%);
}

.filter-panel {
  align-items: end;
  flex-wrap: wrap;
}

.filter-field {
  display: grid;
  gap: 6px;
  min-width: 180px;
}

.keyword-field {
  flex: 1 1 280px;
}

.filter-field span,
.table-meta,
.session-card-main small,
.session-card-side small {
  color: #667085;
  font-size: 12px;
  font-weight: 700;
}

.filter-field input,
.filter-field select {
  width: 100%;
  height: 38px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  padding: 0 10px;
  color: #101828;
  background: #ffffff;
  font-size: 14px;
}

.primary-button,
.ghost-button {
  height: 38px;
  border-radius: 6px;
  font-weight: 700;
  cursor: pointer;
}

.primary-button {
  border: 1px solid #175cd3;
  background: #175cd3;
  color: #ffffff;
  padding: 0 18px;
}

.ghost-button {
  border: 1px solid #d0d5dd;
  background: #ffffff;
  color: #101828;
  padding: 0 14px;
}

.primary-button:disabled,
.ghost-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.error-panel {
  color: #b42318;
  font-weight: 700;
}

.session-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.session-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 16px;
  width: 100%;
  padding: 14px;
  color: #101828;
  border: 1px solid #eef2f6;
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.session-card:hover {
  border-color: #d0d5dd;
  background: #f9fafb;
  box-shadow: 0 10px 24px rgb(16 24 40 / 6%);
}

.session-card-main,
.session-card-side {
  display: grid;
  min-width: 0;
}

.session-card-main {
  gap: 8px;
}

.session-card-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.session-card-head strong {
  color: #101828;
  font-size: 15px;
}

.state-pill {
  display: inline-flex;
  height: 22px;
  align-items: center;
  padding: 0 8px;
  color: #175cd3;
  border-radius: 999px;
  background: #eff6ff;
  font-size: 12px;
  font-weight: 800;
}

.session-card-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-card-text.question {
  color: #101828;
  font-size: 14px;
  font-weight: 700;
}

.session-card-text.answer {
  color: #475467;
  font-size: 13px;
}

.session-card-side {
  align-content: start;
  gap: 10px;
}

.session-card-side span {
  min-width: 0;
}

.session-card-side strong {
  display: block;
  min-width: 0;
  margin-top: 3px;
  overflow: hidden;
  color: #101828;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-text {
  color: #175cd3;
  font-weight: 800;
}

.empty-text {
  margin: 18px 0 0;
  color: #667085;
  font-size: 14px;
}

.pagination-bar {
  margin-top: 16px;
}

@media (max-width: 768px) {
  .list-header {
    grid-template-columns: 1fr;
    align-items: start;
  }

  .filter-panel {
    display: grid;
  }

  .filter-field,
  .primary-button {
    width: 100%;
  }

  .session-card {
    grid-template-columns: 1fr;
  }
}
</style>
