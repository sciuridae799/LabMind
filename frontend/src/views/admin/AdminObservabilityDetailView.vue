<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { chatApi } from '../../shared/api/chat'
import type { BusinessChatExchangeDetail, ExchangeModelCallTrace, ExchangeTraceStage } from '../../shared/api/chat'

const route = useRoute()
const router = useRouter()

const conversationId = computed(() => String(route.params.conversationId || ''))
const exchangeId = computed(() => String(route.params.exchangeId || ''))
const detail = ref<BusinessChatExchangeDetail | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const usage = computed(() => detail.value?.usageSummary || null)

const maxStageDuration = computed(() => {
  const stages = detail.value?.stages || []
  return Math.max(1, ...stages.map((stage) => Number(stage.durationMs ?? 0)).filter(Number.isFinite))
})

const modelProgressWidth = computed(() => {
  const currentUsage = usage.value
  if (!currentUsage || currentUsage.modelCallLimit <= 0) {
    return '0%'
  }
  return `${Math.min(100, (currentUsage.modelCallCount / currentUsage.modelCallLimit) * 100)}%`
})

const toolProgressWidth = computed(() => {
  const currentUsage = usage.value
  if (!currentUsage || currentUsage.toolCallLimit <= 0) {
    return '0%'
  }
  return `${Math.min(100, (currentUsage.toolCallCount / currentUsage.toolCallLimit) * 100)}%`
})

function formatNumber(value: unknown): string {
  const numberValue = Number(value ?? 0)
  return Number.isFinite(numberValue) ? numberValue.toLocaleString('zh-CN') : '0'
}

function formatCost(value: unknown, currency?: string): string {
  const numberValue = Number(value ?? 0)
  const symbol = currency === 'CNY' || !currency ? '¥' : `${currency} `
  return `${symbol}${Number.isFinite(numberValue) ? numberValue.toFixed(6) : '0.000000'}`
}

function formatDuration(value: unknown): string {
  const numberValue = Number(value ?? 0)
  return Number.isFinite(numberValue) ? `${numberValue.toLocaleString('zh-CN')} ms` : '-'
}

function formatDateTime(value: string | null): string {
  return value ? value.replace('T', ' ') : '-'
}

function modelLabel(call: ExchangeModelCallTrace): string {
  return `${call.provider} / ${call.modelName}`
}

function stageProgressWidth(stage: ExchangeTraceStage): string {
  const duration = Number(stage.durationMs ?? 0)
  if (!Number.isFinite(duration) || duration <= 0) {
    return '3%'
  }
  return `${Math.max(3, Math.min(100, (duration / maxStageDuration.value) * 100))}%`
}

function stageLevelClass(stage: ExchangeTraceStage): string {
  return Number(stage.stageLevel || 1) > 1 ? 'is-child' : 'is-root'
}

function stateLabel(state: string): string {
  if (state === 'COMPLETED') {
    return '已完成'
  }
  if (state === 'RUNNING') {
    return '运行中'
  }
  if (state === 'FAILED') {
    return '失败'
  }
  return state
}

function snapshotPreview(snapshot: unknown): string {
  if (!snapshot || typeof snapshot !== 'object') {
    return ''
  }
  return Object.entries(snapshot as Record<string, unknown>)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .slice(0, 4)
    .map(([key, value]) => `${key}: ${Array.isArray(value) ? value.length : value}`)
    .join(' · ')
}

async function loadDetail(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    detail.value = await chatApi.getExchangeDetail(conversationId.value, exchangeId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '轮次详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<template>
  <section class="exchange-detail-page">
    <header class="detail-header">
      <div>
        <p class="admin-content-eyebrow">Trace Detail</p>
        <h2 class="admin-content-title">轮次详情</h2>
      </div>
      <button class="ghost-button" type="button" @click="router.back()">返回</button>
    </header>

    <div v-if="loading" class="admin-content-panel">正在加载轮次详情...</div>
    <div v-else-if="errorMessage" class="admin-content-panel error-panel">{{ errorMessage }}</div>

    <template v-else-if="detail && usage">
      <section class="usage-grid">
        <div class="metric-block">
          <span>输入 Token</span>
          <strong>{{ formatNumber(usage.inputTokens) }}</strong>
        </div>
        <div class="metric-block">
          <span>输出 Token</span>
          <strong>{{ formatNumber(usage.outputTokens) }}</strong>
        </div>
        <div class="metric-block">
          <span>总 Token</span>
          <strong>{{ formatNumber(usage.totalTokens) }}</strong>
        </div>
        <div class="metric-block">
          <span>费用估算</span>
          <strong>{{ formatCost(usage.estimatedCost, usage.currency) }}</strong>
        </div>
      </section>

      <section class="limit-grid">
        <div class="limit-block">
          <div class="limit-head">
            <span>模型调用</span>
            <strong>{{ usage.modelCallCount }}/{{ usage.modelCallLimit }}</strong>
          </div>
          <div class="progress-track"><span :style="{ width: modelProgressWidth }"></span></div>
        </div>
        <div class="limit-block">
          <div class="limit-head">
            <span>工具调用</span>
            <strong>{{ usage.toolCallCount }}/{{ usage.toolCallLimit }}</strong>
          </div>
          <div class="progress-track"><span :style="{ width: toolProgressWidth }"></span></div>
        </div>
        <div class="limit-block">
          <div class="limit-head">
            <span>限制触发</span>
            <strong>{{ usage.limitTriggered ? '已触发' : '未触发' }}</strong>
          </div>
          <p>{{ usage.limitTriggerReason || '本轮没有触发模型或工具调用上限。' }}</p>
        </div>
      </section>

      <section class="admin-content-panel exchange-summary">
        <h3>问题与回答</h3>
        <p class="question-text">{{ detail.userPrompt }}</p>
        <p class="answer-text">{{ detail.replyContent || detail.finishNote || '暂无回答内容' }}</p>
      </section>

      <section class="admin-content-panel">
        <h3>按阶段分组的模型使用明细</h3>
        <div class="detail-table">
          <div class="table-row table-head">
            <span>阶段</span>
            <span>模型</span>
            <span>输入</span>
            <span>输出</span>
            <span>总量</span>
            <span>成本</span>
            <span>耗时</span>
            <span>状态</span>
          </div>
          <div v-for="call in detail.modelCalls" :key="`${call.stageCode}-${call.callType}-${call.durationMs}`" class="table-row">
            <span>{{ call.stageName }}</span>
            <span>{{ modelLabel(call) }}</span>
            <span>{{ formatNumber(call.inputTokens) }}</span>
            <span>{{ formatNumber(call.outputTokens) }}</span>
            <span>{{ formatNumber(call.totalTokens) }}</span>
            <span>{{ formatCost(call.estimatedCost, call.currency) }}</span>
            <span>{{ formatDuration(call.durationMs) }}</span>
            <span>{{ call.callState }}</span>
          </div>
          <p v-if="detail.modelCalls.length === 0" class="empty-text">本轮还没有模型调用用量记录。</p>
        </div>
      </section>

      <section class="admin-content-panel">
        <h3>执行阶段时间线</h3>
        <div class="timeline-list">
          <div
            v-for="stage in detail.stages"
            :key="`${stage.stageCode}-${stage.stageOrder}`"
            class="timeline-item"
            :class="stageLevelClass(stage)"
          >
            <span class="timeline-dot" :class="stage.stageState.toLowerCase()"></span>
            <div class="timeline-content">
              <div class="timeline-head">
                <strong>{{ stage.stageOrder }}. {{ stage.stageName }}</strong>
                <span>{{ formatDateTime(stage.startTime) }}</span>
              </div>
              <div class="timeline-meta">
                <span :class="['state-pill', stage.stageState.toLowerCase()]">{{ stateLabel(stage.stageState) }}</span>
                <span>耗时 {{ formatDuration(stage.durationMs) }}</span>
                <span>{{ stage.stageCode }}</span>
                <span v-if="stage.parentStageId">父阶段 {{ stage.parentStageId }}</span>
              </div>
              <p>{{ stage.summaryText || stage.errorMessage || '无阶段摘要' }}</p>
              <div class="stage-progress">
                <span :style="{ width: stageProgressWidth(stage) }"></span>
              </div>
              <p v-if="snapshotPreview(stage.snapshot)" class="snapshot-text">{{ snapshotPreview(stage.snapshot) }}</p>
            </div>
          </div>
          <p v-if="detail.stages.length === 0" class="empty-text">本轮还没有执行阶段记录。</p>
        </div>
      </section>

      <section class="admin-content-panel">
        <h3>工具调用</h3>
        <div v-for="tool in detail.toolCalls" :key="`${tool.toolName}-${tool.durationMs}`" class="stage-item">
          <strong>{{ tool.toolName }}</strong>
          <span>{{ tool.callState }} · {{ formatDuration(tool.durationMs) }}</span>
          <p>{{ tool.errorMessage || '调用完成' }}</p>
        </div>
        <p v-if="detail.toolCalls.length === 0" class="empty-text">本轮没有工具调用。</p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.exchange-detail-page {
  display: grid;
  width: min(100%, 1120px);
  gap: 16px;
  margin: 0 auto;
}

.detail-header,
.usage-grid,
.limit-grid {
  display: grid;
  gap: 12px;
}

.detail-header {
  grid-template-columns: 1fr auto;
  align-items: center;
}

.ghost-button {
  height: 36px;
  padding: 0 14px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  background: #ffffff;
  color: #101828;
  font-weight: 700;
  cursor: pointer;
}

.usage-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.limit-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-block,
.limit-block {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  padding: 16px;
  box-shadow: 0 8px 22px rgb(16 24 40 / 5%);
}

.metric-block span,
.limit-head span {
  color: #667085;
  font-size: 13px;
  font-weight: 700;
}

.metric-block strong {
  display: block;
  margin-top: 10px;
  color: #101828;
  font-size: 24px;
}

.limit-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.limit-block p {
  margin: 12px 0 0;
  color: #667085;
  line-height: 1.6;
}

.progress-track {
  height: 8px;
  margin-top: 14px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef2f6;
}

.progress-track span {
  display: block;
  height: 100%;
  background: #2563eb;
}

.exchange-summary h3,
.admin-content-panel h3 {
  margin: 0 0 12px;
  color: #101828;
}

.question-text,
.answer-text,
.stage-item p,
.empty-text {
  color: #344054;
  line-height: 1.7;
}

.answer-text {
  white-space: pre-wrap;
}

.detail-table {
  overflow-x: auto;
  border: 1px solid #eef2f6;
  border-radius: 8px;
  scrollbar-width: thin;
}

.table-row {
  display: grid;
  grid-template-columns: 1.05fr 1.55fr repeat(6, minmax(72px, 1fr));
  min-width: 840px;
  border-bottom: 1px solid #eaecf0;
}

.table-row span {
  padding: 12px;
  color: #101828;
}

.table-head {
  background: #f8fafc;
  font-weight: 800;
}

.stage-item {
  padding: 12px 0;
  border-bottom: 1px solid #eaecf0;
}

.stage-item strong,
.stage-item span {
  display: block;
}

.stage-item span {
  margin-top: 4px;
  color: #667085;
  font-size: 13px;
}

.timeline-list {
  display: grid;
}

.timeline-item {
  position: relative;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  padding-bottom: 18px;
}

.timeline-item::before {
  position: absolute;
  top: 18px;
  bottom: 0;
  left: 6px;
  width: 2px;
  background: #e4e7ec;
  content: '';
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-item:last-child::before {
  display: none;
}

.timeline-item.is-child {
  grid-template-columns: 56px minmax(0, 1fr);
}

.timeline-item.is-child::before {
  left: 34px;
}

.timeline-item.is-child .timeline-dot {
  margin-left: 28px;
}

.timeline-item.is-child .timeline-content {
  background: #fbfcfe;
}

.timeline-dot {
  position: relative;
  z-index: 1;
  width: 14px;
  height: 14px;
  margin-top: 4px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: #175cd3;
  box-shadow: 0 0 0 2px #d1e9ff;
}

.timeline-dot.completed {
  background: #027a48;
  box-shadow: 0 0 0 2px #d1fadf;
}

.timeline-dot.failed {
  background: #b42318;
  box-shadow: 0 0 0 2px #fee4e2;
}

.timeline-dot.running {
  background: #b54708;
  box-shadow: 0 0 0 2px #fedf89;
}

.timeline-content {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 8px 22px rgb(16 24 40 / 4%);
}

.timeline-head,
.timeline-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.timeline-head {
  justify-content: space-between;
}

.timeline-head strong {
  color: #101828;
  font-size: 16px;
}

.timeline-head span,
.timeline-meta {
  color: #667085;
  font-size: 13px;
  font-weight: 700;
}

.timeline-content p {
  margin: 0;
  color: #344054;
  line-height: 1.6;
}

.state-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 6px;
  background: #eef4ff;
  color: #3538cd;
}

.state-pill.completed {
  background: #d1fadf;
  color: #027a48;
}

.state-pill.failed {
  background: #fee4e2;
  color: #b42318;
}

.state-pill.running {
  background: #fef0c7;
  color: #b54708;
}

.stage-progress {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef2f6;
}

.stage-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #2e90fa;
}

.snapshot-text {
  color: #667085 !important;
  font-size: 13px;
}

.error-panel {
  color: #b42318;
}

@media (max-width: 900px) {
  .usage-grid,
  .limit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
