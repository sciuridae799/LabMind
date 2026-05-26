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
    <section class="filter-panel">
      <div class="filter-heading">
        <h1 class="filter-title">链路检索</h1>
        <p class="filter-subtitle">按关键词、对话模式和轮次状态查看问答链路。</p>
      </div>
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
      <button class="ghost-button" type="button" :disabled="loading" @click="loadSessions">
        刷新
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
  width: min(100%, var(--admin-page-width));
  gap: var(--admin-page-gap);
  margin: 0 auto;
}

.filter-panel,
.table-meta,
.pagination-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.table-meta,
.pagination-bar {
  justify-content: space-between;
}

.filter-panel,
.table-panel,
.error-panel {
  border: 1px solid var(--admin-color-border);
  border-radius: var(--admin-radius-panel);
  background: var(--admin-color-card);
  padding: var(--admin-panel-padding);
  box-shadow: var(--admin-shadow-panel);
}

.filter-panel {
  align-items: end;
  flex-wrap: wrap;
}

.filter-heading {
  flex: 1 0 100%;
}

.filter-title {
  margin: 0;
  color: var(--admin-color-title);
  font-size: var(--admin-title-size);
  line-height: 1.2;
  font-weight: 700;
}

.filter-subtitle {
  margin: 8px 0 0;
  color: var(--admin-color-muted);
  font-size: var(--admin-subtitle-size);
  line-height: 1.6;
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
  color: var(--admin-color-muted);
  font-size: 12px;
  font-weight: 700;
}

.filter-field input,
.filter-field select {
  width: 100%;
  height: var(--admin-control-height);
  border: 1px solid var(--admin-color-field-border);
  border-radius: var(--admin-radius-control);
  outline: none;
  padding: 0 12px;
  color: var(--admin-color-text);
  background: #ffffff;
  font-size: var(--admin-control-font-size);
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.filter-field input::placeholder {
  color: #9aaeaf;
}

.filter-field input:focus,
.filter-field select:focus {
  border-color: #5cc3b8;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.14);
}

.primary-button,
.ghost-button {
  height: var(--admin-control-height);
  border-radius: var(--admin-radius-control);
  font-weight: 700;
  cursor: pointer;
}

.primary-button {
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #ffffff;
  padding: 0 18px;
}

.ghost-button {
  border: 1px solid #c9dde1;
  background: #ffffff;
  color: #284247;
  padding: 0 14px;
}

.primary-button:disabled,
.ghost-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.error-panel {
  border-color: #f3b3b3;
  background: #fff7f7;
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
  color: #173033;
  border: 1px solid #d8e6e8;
  border-radius: 8px;
  background: #fbfefe;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.session-card:hover {
  border-color: #95cfc8;
  background: #eef8f6;
  box-shadow: 0 10px 24px rgba(17, 78, 84, 0.08);
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
  color: #173033;
  font-size: 15px;
}

.state-pill {
  display: inline-flex;
  height: 22px;
  align-items: center;
  padding: 0 8px;
  color: #0f766e;
  border-radius: 999px;
  background: #eef8f6;
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
  color: #173033;
  font-size: 14px;
  font-weight: 700;
}

.session-card-text.answer {
  color: #60787f;
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
  color: #173033;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-text {
  color: #0f766e;
  font-weight: 800;
}

.empty-text {
  margin: 18px 0 0;
  color: #60787f;
  font-size: 14px;
}

.pagination-bar {
  margin-top: 16px;
}

@media (max-width: 768px) {
  .filter-panel {
    display: grid;
  }

  .filter-field,
  .primary-button,
  .ghost-button {
    width: 100%;
  }

  .session-card {
    grid-template-columns: 1fr;
  }
}
</style>
