<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  businessChatModeOptions,
  chatApi,
  type BusinessChatSessionDetail,
  type BusinessChatSessionExchange
} from '../../shared/api/chat'

const route = useRoute()
const router = useRouter()

const conversationId = computed(() => String(route.params.conversationId || ''))
const detail = ref<BusinessChatSessionDetail | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const exchangeCount = computed(() => detail.value?.exchanges.length || 0)

function modeLabel(value: string): string {
  return businessChatModeOptions.find((item) => item.value === value)?.label || value
}

function formatDuration(value: unknown): string {
  const numberValue = Number(value ?? 0)
  return Number.isFinite(numberValue) && numberValue > 0
    ? `${numberValue.toLocaleString('zh-CN')} ms`
    : '-'
}

function previewText(value: unknown): string {
  const normalized = String(value || '').replace(/\s+/g, ' ').trim()
  return normalized || '-'
}

function openExchange(exchange: BusinessChatSessionExchange): void {
  void router.push({
    name: 'AdminObservabilityExchangeDetail',
    params: {
      conversationId: conversationId.value,
      exchangeId: String(exchange.exchangeId)
    }
  })
}

async function loadSession(): Promise<void> {
  if (!conversationId.value) {
    errorMessage.value = 'conversationId 为空'
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    detail.value = await chatApi.getSession(conversationId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '会话链路加载失败'
  } finally {
    loading.value = false
  }
}

watch(conversationId, () => {
  void loadSession()
})

onMounted(loadSession)
</script>

<template>
  <section class="session-trace-page">
    <header class="session-header">
      <div>
        <p class="admin-content-eyebrow">Session Trace</p>
        <h2 class="admin-content-title">会话链路</h2>
      </div>
      <div class="header-actions">
        <button class="ghost-button" type="button" @click="router.push({ name: 'AdminObservabilityList' })">
          返回列表
        </button>
        <button class="ghost-button" type="button" :disabled="loading" @click="loadSession">
          刷新
        </button>
      </div>
    </header>

    <div v-if="loading" class="state-panel">正在加载会话链路...</div>
    <div v-else-if="errorMessage" class="state-panel error-panel">{{ errorMessage }}</div>

    <template v-else-if="detail">
      <section class="summary-panel">
        <div class="summary-main">
          <h3>{{ previewText(detail.title) }}</h3>
          <span class="conversation-code">{{ detail.conversationId }}</span>
        </div>
        <div class="summary-grid">
          <div>
            <span>对话模式</span>
            <strong>{{ modeLabel(detail.chatMode) }}</strong>
          </div>
          <div>
            <span>对话阶段</span>
            <strong>{{ detail.dialogueStage || '-' }}</strong>
          </div>
          <div>
            <span>文档范围</span>
            <strong>{{ detail.selectedDocumentName || detail.selectedDocumentId || '-' }}</strong>
          </div>
          <div>
            <span>轮次数</span>
            <strong>{{ exchangeCount }}</strong>
          </div>
        </div>
        <p v-if="detail.summaryText" class="summary-text">{{ detail.summaryText }}</p>
      </section>

      <section class="exchange-panel">
        <div class="panel-title-row">
          <h3>轮次链路</h3>
          <span>{{ exchangeCount }} 轮</span>
        </div>

        <div class="exchange-list">
          <button
            v-for="exchange in detail.exchanges"
            :key="String(exchange.exchangeId)"
            class="exchange-card"
            type="button"
            @click="openExchange(exchange)"
          >
            <span class="exchange-card-main">
              <span class="exchange-card-head">
                <strong>#{{ exchange.exchangeId }}</strong>
                <span class="state-pill">{{ exchange.exchangeState }}</span>
                <small>{{ exchange.createTime || '-' }}</small>
              </span>
              <span class="exchange-card-text question">{{ previewText(exchange.userPrompt) }}</span>
              <span class="exchange-card-text answer">{{ previewText(exchange.replyContent || exchange.finishNote) }}</span>
            </span>
            <span class="exchange-card-side">
              <span>
                <small>首 Token</small>
                <strong>{{ formatDuration(exchange.firstTokenLatencyMs) }}</strong>
              </span>
              <span>
                <small>总耗时</small>
                <strong>{{ formatDuration(exchange.totalLatencyMs) }}</strong>
              </span>
              <span class="exchange-card-meta">
                来源 {{ exchange.sourceSnapshotList.length }} · 追问 {{ exchange.followUpSuggestionList.length }} · 工具 {{ exchange.toolTraceList.length }}
              </span>
              <span class="link-text">轮次详情</span>
            </span>
          </button>
        </div>

        <p v-if="detail.exchanges.length === 0" class="empty-text">暂无轮次记录</p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.session-trace-page {
  display: grid;
  width: min(100%, 1120px);
  gap: 16px;
  margin: 0 auto;
}

.session-header,
.header-actions,
.panel-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.session-header,
.panel-title-row {
  justify-content: space-between;
}

.header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.summary-panel,
.exchange-panel,
.state-panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  padding: 16px;
  box-shadow: 0 8px 22px rgb(16 24 40 / 5%);
}

.error-panel {
  color: #b42318;
  font-weight: 700;
}

.ghost-button {
  height: 38px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  background: #ffffff;
  color: #101828;
  padding: 0 14px;
  font-weight: 700;
  cursor: pointer;
}

.ghost-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.summary-main {
  display: grid;
  gap: 6px;
}

.summary-main h3,
.panel-title-row h3 {
  margin: 0;
  color: #101828;
  font-size: 18px;
  line-height: 1.35;
}

.conversation-code {
  color: #667085;
  font-family:
    ui-monospace,
    SFMono-Regular,
    SF Mono,
    Menlo,
    Monaco,
    Consolas,
    Liberation Mono,
    Courier New,
    monospace;
  font-size: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.summary-grid div {
  display: grid;
  gap: 8px;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  padding: 12px;
}

.summary-grid span,
.panel-title-row span,
.table-head,
.empty-text {
  color: #667085;
  font-size: 12px;
  font-weight: 700;
}

.summary-grid strong {
  min-width: 0;
  overflow: hidden;
  color: #101828;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-text {
  margin: 16px 0 0;
  color: #344054;
  font-size: 14px;
  line-height: 1.7;
}

.exchange-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.exchange-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
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

.exchange-card:hover {
  border-color: #d0d5dd;
  background: #f9fafb;
  box-shadow: 0 10px 24px rgb(16 24 40 / 6%);
}

.exchange-card-main,
.exchange-card-side {
  display: grid;
  min-width: 0;
}

.exchange-card-main {
  gap: 8px;
}

.exchange-card-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.exchange-card-head strong {
  color: #101828;
  font-size: 14px;
}

.exchange-card-head small,
.exchange-card-side small,
.exchange-card-meta {
  color: #667085;
  font-size: 12px;
  font-weight: 700;
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

.exchange-card-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exchange-card-text.question {
  color: #101828;
  font-size: 14px;
  font-weight: 700;
}

.exchange-card-text.answer {
  color: #475467;
  font-size: 13px;
}

.exchange-card-side {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
  gap: 10px 12px;
}

.exchange-card-side span {
  min-width: 0;
}

.exchange-card-side strong {
  display: block;
  margin-top: 3px;
  color: #101828;
  font-size: 13px;
}

.exchange-card-meta,
.link-text {
  grid-column: 1 / -1;
}

.link-text {
  color: #175cd3;
  font-weight: 800;
}

.empty-text {
  margin: 18px 0 0;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .exchange-card {
    grid-template-columns: 1fr;
  }

  .exchange-card-side {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .exchange-card-meta,
  .link-text {
    grid-column: auto;
  }
}

@media (max-width: 640px) {
  .session-header {
    display: grid;
    justify-items: start;
  }

  .header-actions {
    justify-content: flex-start;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .exchange-card-side {
    grid-template-columns: 1fr;
  }
}
</style>
