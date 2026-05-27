<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { manageApi } from '../../shared/api/manage'
import { useAuthSession } from '../../shared/auth/authSession'

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

interface RouteAsset {
  documentId: string
  documentName: string
  originalFileName: string
  scopeCode: string
  scopeName: string
  topicCode: string
  topicName: string
  summaryText: string
  terms: string[]
  questionPatterns: string[]
  routeStatus: string
  updateTime: string
}

interface RouteAssetPage {
  pageNo: number
  pageSize: number
  totalSize: number
  totalPages: number
  assets: RouteAsset[]
}

const question = ref('')
const assetKeyword = ref('')
const candidates = ref<RouteCandidate[]>([])
const assets = ref<RouteAsset[]>([])
const selectedAssetId = ref('')
const pageNo = ref(1)
const pageSize = 20
const totalSize = ref(0)
const statusMessage = ref('')
const assetStatusMessage = ref('')
const isRouting = ref(false)
const isLoadingAssets = ref(false)
const authSession = useAuthSession()

const selectedAsset = computed(() => {
  return assets.value.find((asset) => asset.documentId === selectedAssetId.value) || assets.value[0] || null
})

function normalizeError(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败'
}

function normalizeAssetPage(payload: unknown): RouteAssetPage {
  const page = payload as Partial<RouteAssetPage>
  return {
    pageNo: Number(page.pageNo || 1),
    pageSize: Number(page.pageSize || pageSize),
    totalSize: Number(page.totalSize || 0),
    totalPages: Number(page.totalPages || 0),
    assets: Array.isArray(page.assets) ? page.assets : []
  }
}

async function loadRouteAssets(targetPage = pageNo.value): Promise<void> {
  if (isLoadingAssets.value) {
    return
  }
  isLoadingAssets.value = true
  assetStatusMessage.value = ''
  try {
    const page = normalizeAssetPage(await manageApi.queryKnowledgeRouteAssetPage({
      workspaceId: authSession.value?.workspaceId,
      keyword: assetKeyword.value.trim(),
      pageNo: String(targetPage),
      pageSize: String(pageSize)
    }))
    pageNo.value = page.pageNo
    totalSize.value = page.totalSize
    assets.value = page.assets
    if (!assets.value.some((asset) => asset.documentId === selectedAssetId.value)) {
      selectedAssetId.value = assets.value[0]?.documentId || ''
    }
    if (assets.value.length === 0) {
      assetStatusMessage.value = '当前没有可参与路由的文档资产'
    }
  } catch (error) {
    assets.value = []
    selectedAssetId.value = ''
    assetStatusMessage.value = normalizeError(error)
  } finally {
    isLoadingAssets.value = false
  }
}

async function previewRoute(): Promise<void> {
  const normalizedQuestion = question.value.trim()
  if (!normalizedQuestion || isRouting.value) {
    statusMessage.value = '请输入要验证的问题'
    return
  }

  isRouting.value = true
  statusMessage.value = ''
  try {
    candidates.value = await manageApi.previewKnowledgeRoute({
      workspaceId: authSession.value?.workspaceId,
      question: normalizedQuestion,
      limit: '10'
    }) as RouteCandidate[]
    if (candidates.value.length === 0) {
      statusMessage.value = '未命中文档候选'
    }
  } catch (error) {
    candidates.value = []
    statusMessage.value = normalizeError(error)
  } finally {
    isRouting.value = false
  }
}

function selectAsset(asset: RouteAsset): void {
  selectedAssetId.value = asset.documentId
}

onMounted(() => {
  void loadRouteAssets(1)
})
</script>

<template>
  <section class="route-page">
    <article class="panel route-preview-panel">
      <div class="section-heading">
        <div>
          <h1 class="section-title">路由试算</h1>
          <p class="section-subtitle">输入问题后预览候选文档和命中依据。</p>
        </div>
        <div class="metric-strip">
          <div>
            <span>资产</span>
            <strong>{{ totalSize }}</strong>
          </div>
          <div>
            <span>候选</span>
            <strong>{{ candidates.length }}</strong>
          </div>
        </div>
      </div>

      <form
        class="route-input"
        @submit.prevent="previewRoute"
      >
        <label>
          <span>路由试算</span>
          <input
            v-model="question"
            placeholder="例如：差旅报销需要哪些材料？"
          >
        </label>
        <button
          type="submit"
          class="button primary"
          :disabled="isRouting"
        >
          {{ isRouting ? '路由中' : '预览路由' }}
        </button>
      </form>
      <p
        v-if="statusMessage"
        class="status-message"
      >
        {{ statusMessage }}
      </p>

      <div
        v-if="candidates.length > 0"
        class="candidate-list"
      >
        <article
          v-for="candidate in candidates"
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
    </article>

    <article class="panel asset-panel">
      <div class="list-header">
        <div>
          <h2 class="list-title">路由资产</h2>
          <p class="list-subtitle">解析成功并生成画像后，文档会进入这里。</p>
        </div>
        <div class="asset-search">
          <input
            v-model="assetKeyword"
            placeholder="搜索文档、标签"
            @keydown.enter="loadRouteAssets(1)"
          >
          <button
            type="button"
            class="button"
            :disabled="isLoadingAssets"
            @click="loadRouteAssets(1)"
          >
            搜索
          </button>
        </div>
      </div>

      <p
        v-if="assetStatusMessage"
        class="status-message"
      >
        {{ assetStatusMessage }}
      </p>

      <div
        v-if="assets.length > 0"
        class="asset-layout"
      >
        <div class="asset-list">
          <button
            v-for="asset in assets"
            :key="asset.documentId"
            type="button"
            class="asset-row"
            :class="{ 'is-active': asset.documentId === selectedAsset?.documentId }"
            @click="selectAsset(asset)"
          >
            <strong>{{ asset.documentName }}</strong>
            <span>{{ asset.scopeName }} / {{ asset.topicName }}</span>
          </button>
        </div>

        <section
          v-if="selectedAsset"
          class="asset-detail"
        >
          <div class="detail-head">
            <div>
              <h3>{{ selectedAsset.documentName }}</h3>
              <p>{{ selectedAsset.originalFileName }}</p>
            </div>
            <span class="route-badge">{{ selectedAsset.routeStatus }}</span>
          </div>
          <p class="summary-text">{{ selectedAsset.summaryText }}</p>
          <dl class="detail-grid">
            <div>
              <dt>知识域</dt>
              <dd>{{ selectedAsset.scopeName }} / {{ selectedAsset.scopeCode }}</dd>
            </div>
            <div>
              <dt>专题</dt>
              <dd>{{ selectedAsset.topicName }} / {{ selectedAsset.topicCode }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ selectedAsset.updateTime || '-' }}</dd>
            </div>
          </dl>
          <div class="tag-block">
            <h4>路由术语</h4>
            <div class="tag-line">
              <span
                v-for="term in selectedAsset.terms"
                :key="term"
              >{{ term }}</span>
            </div>
          </div>
          <div class="tag-block">
            <h4>问题模式</h4>
            <div class="tag-line">
              <span
                v-for="pattern in selectedAsset.questionPatterns"
                :key="pattern"
              >{{ pattern }}</span>
            </div>
          </div>
        </section>
      </div>
    </article>
  </section>
</template>

<style scoped>
.route-page,
.route-page * {
  box-sizing: border-box;
}

.route-page {
  display: grid;
  gap: var(--admin-page-gap);
  color: #173033;
}

.panel {
  padding: var(--admin-panel-padding);
  border: 1px solid var(--admin-color-border);
  border-radius: var(--admin-radius-panel);
  background: var(--admin-color-card);
  box-shadow: var(--admin-shadow-panel);
}

.route-preview-panel {
  border-color: var(--admin-color-border);
  background:
    linear-gradient(180deg, rgba(238, 248, 246, 0.74) 0%, rgba(255, 255, 255, 0.94) 68%);
}

.section-heading,
.list-header,
.detail-head,
.candidate-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.section-title,
.list-title,
.asset-detail h3,
.tag-block h4 {
  margin: 0;
  color: var(--admin-color-title);
  font-weight: 700;
}

.section-title {
  font-size: var(--admin-title-size);
  line-height: 1.15;
}

.list-title {
  font-size: 20px;
  line-height: 1.25;
}

.asset-detail h3 {
  font-size: 18px;
  line-height: 1.3;
}

.section-subtitle,
.list-subtitle,
.status-message,
.asset-row span,
.candidate-main span,
.candidate-score small,
.detail-head p,
.summary-text,
dt,
dd {
  color: var(--admin-color-muted);
  font-size: var(--admin-subtitle-size);
  line-height: 1.55;
}

.section-subtitle,
.list-subtitle,
.status-message,
.detail-head p,
.summary-text {
  margin: 8px 0 0;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(76px, 1fr));
  gap: 8px;
  min-width: 170px;
}

.metric-strip div {
  padding: 10px 12px;
  border: 1px solid #d8e6e8;
  border-radius: 8px;
  background: rgba(251, 254, 254, 0.88);
}

.metric-strip span,
.route-input label span {
  display: block;
  color: #60787f;
  font-size: 12px;
  line-height: 1.3;
}

.metric-strip strong {
  display: block;
  margin-top: 4px;
  color: #173033;
  font-size: 20px;
  line-height: 1.1;
}

.route-input,
.asset-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.route-input {
  align-items: end;
  margin-top: 16px;
}

.route-input label {
  min-width: 0;
}

.asset-search {
  width: min(420px, 100%);
}

input {
  width: 100%;
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--admin-color-field-border);
  border-radius: var(--admin-radius-control);
  outline: none;
  background: #ffffff;
  color: var(--admin-color-text);
  font-size: var(--admin-control-font-size);
}

.route-input label input {
  margin-top: 6px;
}

input:focus {
  border-color: #5cc3b8;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.14);
}

.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid #c9dde1;
  border-radius: 8px;
  background: #ffffff;
  color: #284247;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.button.primary {
  border-color: #0f766e;
  background: #0f766e;
  color: #ffffff;
}

.button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.candidate-list,
.asset-layout {
  margin-top: 16px;
}

.candidate-list {
  display: grid;
  gap: 10px;
}

.candidate-item {
  padding: 14px;
  border: 1px solid #d8e6e8;
  border-radius: 8px;
  background: #fbfefe;
}

.candidate-main strong,
.asset-row strong {
  display: block;
  color: #173033;
  font-size: 15px;
}

.candidate-score {
  min-width: 170px;
  text-align: right;
}

.candidate-score b {
  display: block;
  color: #0f766e;
  font-size: 20px;
}

.asset-layout {
  display: grid;
  grid-template-columns: minmax(240px, 0.85fr) minmax(0, 1.5fr);
  gap: 16px;
}

.asset-list {
  display: grid;
  align-content: start;
  gap: 8px;
}

.asset-row {
  width: 100%;
  padding: 12px;
  border: 1px solid #d8e6e8;
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.asset-row.is-active {
  border-color: #95cfc8;
  background: #eef8f6;
  box-shadow: inset 3px 0 0 #0f766e;
}

.asset-detail {
  min-width: 0;
  padding: 16px;
  border: 1px solid #d8e6e8;
  border-radius: 8px;
  background: #fbfefe;
}

.route-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid #b8d8d4;
  border-radius: 999px;
  background: #eef8f6;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 16px 0 0;
}

dt,
dd {
  margin: 0;
}

dt {
  color: #60787f;
}

dd {
  color: #173033;
  font-weight: 600;
  word-break: break-word;
}

.tag-block {
  margin-top: 16px;
}

.tag-block h4 {
  font-size: 14px;
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
  background: #eef8f6;
  color: #0f766e;
  font-size: 12px;
  line-height: 1.35;
  word-break: break-word;
}

@media (max-width: 900px) {
  .list-header,
  .candidate-item,
  .asset-layout,
  .detail-grid {
    display: grid;
    grid-template-columns: 1fr;
  }

  .candidate-score {
    min-width: 0;
    text-align: left;
  }

  .asset-search {
    width: 100%;
  }

  .section-heading {
    display: grid;
    grid-template-columns: 1fr;
  }

  .metric-strip {
    min-width: 0;
  }
}

@media (max-width: 520px) {
  .panel {
    padding: var(--admin-panel-padding);
  }

  .route-input,
  .asset-search {
    grid-template-columns: 1fr;
  }

  .button {
    width: 100%;
  }
}
</style>
