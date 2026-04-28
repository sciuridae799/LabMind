<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { manageApi } from '../../shared/api/manage'

interface RouteCandidate {
  documentId: string
  documentName: string
  scopeCode: string
  scopeName: string
  topicCode: string
  topicName: string
  score: number
  termScore: number
  patternScore: number
  hitTerms: string[]
  matchedPatterns: string[]
  hitReason: string
}

interface RouteTrace {
  conversationId: string
  exchangeId: string
  question: string
  knowledgeRoute: string
  candidates: RouteCandidate[]
  createTime: string
}

interface RouteTracePage {
  pageNo: number
  pageSize: number
  totalSize: number
  totalPages: number
  traces: RouteTrace[]
}

const keyword = ref('')
const traces = ref<RouteTrace[]>([])
const selectedExchangeId = ref('')
const pageNo = ref(1)
const pageSize = 20
const totalSize = ref(0)
const statusMessage = ref('')
const isLoading = ref(false)

const selectedTrace = computed(() => {
  return traces.value.find((trace) => trace.exchangeId === selectedExchangeId.value) || traces.value[0] || null
})

function normalizeError(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败'
}

function normalizeTracePage(payload: unknown): RouteTracePage {
  const page = payload as Partial<RouteTracePage>
  return {
    pageNo: Number(page.pageNo || 1),
    pageSize: Number(page.pageSize || pageSize),
    totalSize: Number(page.totalSize || 0),
    totalPages: Number(page.totalPages || 0),
    traces: Array.isArray(page.traces) ? page.traces : []
  }
}

async function loadTraces(targetPage = pageNo.value): Promise<void> {
  if (isLoading.value) {
    return
  }
  isLoading.value = true
  statusMessage.value = ''
  try {
    const page = normalizeTracePage(await manageApi.queryKnowledgeRouteTracePage({
      keyword: keyword.value.trim(),
      pageNo: String(targetPage),
      pageSize: String(pageSize)
    }))
    pageNo.value = page.pageNo
    totalSize.value = page.totalSize
    traces.value = page.traces
    if (!traces.value.some((trace) => trace.exchangeId === selectedExchangeId.value)) {
      selectedExchangeId.value = traces.value[0]?.exchangeId || ''
    }
    if (traces.value.length === 0) {
      statusMessage.value = '当前没有自动知识问答路由记录'
    }
  } catch (error) {
    traces.value = []
    selectedExchangeId.value = ''
    statusMessage.value = normalizeError(error)
  } finally {
    isLoading.value = false
  }
}

function selectTrace(trace: RouteTrace): void {
  selectedExchangeId.value = trace.exchangeId
}

onMounted(() => {
  void loadTraces(1)
})
</script>

<template>
  <section class="trace-page">
    <article class="panel">
      <div class="page-head">
        <div>
          <h1 class="section-title">路由追踪</h1>
          <p class="section-subtitle">自动知识问答 {{ totalSize }} 条记录</p>
        </div>
        <div class="trace-search">
          <input
            v-model="keyword"
            placeholder="搜索问题或会话"
            @keydown.enter="loadTraces(1)"
          >
          <button
            type="button"
            class="button primary"
            :disabled="isLoading"
            @click="loadTraces(1)"
          >
            搜索
          </button>
        </div>
      </div>

      <p
        v-if="statusMessage"
        class="status-message"
      >
        {{ statusMessage }}
      </p>

      <div
        v-if="traces.length > 0"
        class="trace-layout"
      >
        <div class="trace-list">
          <button
            v-for="trace in traces"
            :key="trace.exchangeId"
            type="button"
            class="trace-row"
            :class="{ 'is-active': trace.exchangeId === selectedTrace?.exchangeId }"
            @click="selectTrace(trace)"
          >
            <strong>{{ trace.question }}</strong>
            <span>{{ trace.conversationId }} · {{ trace.createTime }}</span>
          </button>
        </div>

        <section
          v-if="selectedTrace"
          class="trace-detail"
        >
          <div class="detail-head">
            <div>
              <h2>{{ selectedTrace.question }}</h2>
              <p>{{ selectedTrace.conversationId }} / {{ selectedTrace.exchangeId }}</p>
            </div>
            <span class="route-state">{{ selectedTrace.knowledgeRoute }}</span>
          </div>

          <div
            v-if="selectedTrace.candidates.length === 0"
            class="empty-candidates"
          >
            未记录候选文档
          </div>

          <div
            v-else
            class="candidate-list"
          >
            <article
              v-for="candidate in selectedTrace.candidates"
              :key="candidate.documentId"
              class="candidate-item"
            >
              <div class="candidate-main">
                <strong>{{ candidate.documentName }}</strong>
                <span>{{ candidate.scopeName }} / {{ candidate.topicName }}</span>
                <div class="tag-line">
                  <span
                    v-for="term in candidate.hitTerms"
                    :key="term"
                  >{{ term }}</span>
                  <span
                    v-for="pattern in candidate.matchedPatterns"
                    :key="pattern"
                  >{{ pattern }}</span>
                </div>
              </div>
              <div class="candidate-score">
                <b>{{ candidate.score.toFixed(2) }}</b>
                <small>术语 {{ candidate.termScore.toFixed(2) }} / 模式 {{ candidate.patternScore.toFixed(2) }}</small>
              </div>
            </article>
          </div>
        </section>
      </div>
    </article>
  </section>
</template>

<style scoped>
.trace-page,
.trace-page * {
  box-sizing: border-box;
}

.panel {
  padding: 20px;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  background: #ffffff;
}

.page-head,
.detail-head,
.candidate-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.section-title,
.trace-detail h2 {
  margin: 0;
  color: #222222;
  font-weight: 700;
}

.section-title {
  font-size: 28px;
  line-height: 1.15;
}

.trace-detail h2 {
  font-size: 20px;
  line-height: 1.35;
}

.section-subtitle,
.status-message,
.trace-row span,
.detail-head p,
.candidate-main span,
.candidate-score small,
.empty-candidates {
  color: #777777;
  font-size: 13px;
  line-height: 1.55;
}

.section-subtitle,
.status-message,
.detail-head p {
  margin: 8px 0 0;
}

.trace-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: min(440px, 100%);
}

input {
  width: 100%;
  height: 42px;
  padding: 0 12px;
  border: 1px solid #dddddd;
  border-radius: 8px;
  outline: none;
  background: #ffffff;
  color: #222222;
  font-size: 13px;
}

input:focus {
  border-color: #8fb8ff;
  box-shadow: 0 0 0 3px rgba(143, 184, 255, 0.2);
}

.button {
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid #222222;
  border-radius: 8px;
  background: #222222;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.trace-layout {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(0, 1.4fr);
  gap: 16px;
  margin-top: 18px;
}

.trace-list {
  display: grid;
  align-content: start;
  gap: 8px;
}

.trace-row {
  width: 100%;
  padding: 12px;
  border: 1px solid #eeeeee;
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.trace-row.is-active {
  border-color: #8fb8ff;
  background: #f5f9ff;
}

.trace-row strong,
.candidate-main strong {
  display: block;
  color: #222222;
  font-size: 15px;
  line-height: 1.45;
}

.trace-detail {
  min-width: 0;
  padding: 16px;
  border: 1px solid #eeeeee;
  border-radius: 8px;
}

.route-state {
  max-width: 220px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f1f3f5;
  color: #333333;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  text-align: center;
  word-break: break-word;
}

.empty-candidates {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  margin-top: 16px;
  border: 1px dashed #e5e7eb;
  border-radius: 8px;
}

.candidate-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.candidate-item {
  padding: 14px;
  border: 1px solid #eeeeee;
  border-radius: 8px;
}

.candidate-score {
  min-width: 170px;
  text-align: right;
}

.candidate-score b {
  display: block;
  color: #222222;
  font-size: 22px;
}

.tag-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.tag-line span {
  max-width: 100%;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f1f3f5;
  color: #444444;
  font-size: 12px;
  line-height: 1.35;
  word-break: break-word;
}

@media (max-width: 900px) {
  .page-head,
  .trace-layout,
  .candidate-item {
    display: grid;
    grid-template-columns: 1fr;
  }

  .trace-search {
    width: 100%;
  }

  .candidate-score {
    min-width: 0;
    text-align: left;
  }
}
</style>
