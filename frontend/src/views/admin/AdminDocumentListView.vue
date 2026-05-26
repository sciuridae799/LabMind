<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { manageApi } from '../../shared/api/manage'

interface DocumentRow {
  documentId: string
  documentName: string
  originalFileName: string
  knowledgeScopeCode: string
  knowledgeScopeName: string
  businessCategory: string
  documentTags: string
  parseStatus: string
  strategyStatus: string
  indexStatus: string
  createTime: string
}

interface DocumentProfile {
  summaryText?: string
  answerableQuestions?: string[]
  terms?: string[]
  questionPatterns?: string[]
}

const formState = ref({
  documentName: '',
  knowledgeScopeCode: '',
  knowledgeScopeName: '',
  knowledgeTopicCode: '',
  knowledgeTopicName: '',
  businessCategory: '',
  documentTags: ''
})
const selectedFile = ref<File | null>(null)
const documentRows = ref<DocumentRow[]>([])
const keyword = ref('')
const statusMessage = ref('')
const isUploading = ref(false)
const isLoading = ref(false)
const totalSize = ref(0)
const deletingDocumentId = ref('')
const detailDocument = ref<DocumentRow | null>(null)
const detailProfile = ref<DocumentProfile | null>(null)
const isDetailLoading = ref(false)
const fullTextDocument = ref<DocumentRow | null>(null)
const fullTextContent = ref('')
const isFullTextLoading = ref(false)
let refreshTimer: ReturnType<typeof window.setTimeout> | null = null

const hasParsingDocuments = computed(() => documentRows.value.some((row) => {
  return row.parseStatus === '1' || row.parseStatus === '2'
}))

function normalizeError(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败'
}

function handleFileChange(event: Event): void {
  const input = event.target
  if (!(input instanceof HTMLInputElement)) {
    return
  }
  selectedFile.value = input.files?.[0] ?? null
}

function resetForm(): void {
  formState.value = {
    documentName: '',
    knowledgeScopeCode: '',
    knowledgeScopeName: '',
    knowledgeTopicCode: '',
    knowledgeTopicName: '',
    businessCategory: '',
    documentTags: ''
  }
  selectedFile.value = null
  statusMessage.value = ''
}

function clearRefreshTimer(): void {
  if (refreshTimer === null) {
    return
  }
  window.clearTimeout(refreshTimer)
  refreshTimer = null
}

function scheduleDocumentRefresh(): void {
  clearRefreshTimer()
  if (!hasParsingDocuments.value) {
    return
  }
  refreshTimer = window.setTimeout(() => {
    void loadDocuments({ silent: true })
  }, 2500)
}

async function loadDocuments(options: { silent?: boolean } = {}): Promise<void> {
  if (!options.silent) {
    isLoading.value = true
    statusMessage.value = ''
  }
  try {
    const response = await manageApi.queryDocumentPage({
      keyword: keyword.value,
      pageNo: '1',
      pageSize: '20'
    }) as { documents?: DocumentRow[]; totalSize?: string | number }
    documentRows.value = response.documents ?? []
    totalSize.value = Number(response.totalSize ?? 0)
    if (detailDocument.value) {
      const updatedDetailRow = documentRows.value.find((row) => row.documentId === detailDocument.value?.documentId)
      if (updatedDetailRow) {
        detailDocument.value = updatedDetailRow
      }
    }
    scheduleDocumentRefresh()
  } catch (error) {
    if (!options.silent) {
      statusMessage.value = normalizeError(error)
    }
  } finally {
    if (!options.silent) {
      isLoading.value = false
    }
  }
}

async function uploadDocument(): Promise<void> {
  if (!selectedFile.value || isUploading.value) {
    statusMessage.value = '请选择要上传的文件'
    return
  }

  isUploading.value = true
  statusMessage.value = ''
  try {
    await manageApi.uploadDocument({
      file: selectedFile.value,
      ...formState.value
    })
    resetForm()
    statusMessage.value = '文档已上传，并生成知识路由资产'
    await loadDocuments()
  } catch (error) {
    statusMessage.value = normalizeError(error)
  } finally {
    isUploading.value = false
  }
}

async function openDocumentDetail(row: DocumentRow): Promise<void> {
  detailDocument.value = row
  detailProfile.value = null
  isDetailLoading.value = true
  statusMessage.value = ''
  try {
    detailDocument.value = await manageApi.queryDocumentDetail(row.documentId) as DocumentRow
    detailProfile.value = await manageApi.queryDocumentProfile({
      documentId: row.documentId
    }) as DocumentProfile
  } catch (error) {
    statusMessage.value = normalizeError(error)
  } finally {
    isDetailLoading.value = false
  }
}

function closeDocumentDetail(): void {
  detailDocument.value = null
  detailProfile.value = null
}

async function openDocumentFullText(row: DocumentRow): Promise<void> {
  fullTextDocument.value = row
  fullTextContent.value = ''
  isFullTextLoading.value = true
  statusMessage.value = ''
  try {
    fullTextContent.value = String(await manageApi.queryDocumentParsedText({
      documentId: row.documentId
    }) || '')
  } catch (error) {
    statusMessage.value = normalizeError(error)
  } finally {
    isFullTextLoading.value = false
  }
}

function closeDocumentFullText(): void {
  fullTextDocument.value = null
  fullTextContent.value = ''
}

async function deleteDocument(row: DocumentRow): Promise<void> {
  if (deletingDocumentId.value) {
    return
  }

  const confirmed = window.confirm(`确认删除「${row.documentName}」吗？删除后该文档会从列表和知识路由中移除。`)
  if (!confirmed) {
    return
  }

  deletingDocumentId.value = row.documentId
  statusMessage.value = ''
  try {
    await manageApi.deleteDocument({
      documentId: row.documentId
    })
    if (detailDocument.value?.documentId === row.documentId) {
      closeDocumentDetail()
    }
    statusMessage.value = '文档已删除'
    await loadDocuments()
  } catch (error) {
    statusMessage.value = normalizeError(error)
  } finally {
    deletingDocumentId.value = ''
  }
}

function documentStatusText(row: DocumentRow): string {
  if (row.parseStatus === '3') {
    return '解析完成，可问答'
  }
  if (row.parseStatus === '4') {
    return '解析失败'
  }
  if (row.parseStatus === '2') {
    return '解析中'
  }
  return '待解析'
}

onMounted(() => {
  void loadDocuments()
})

onUnmounted(() => {
  clearRefreshTimer()
})
</script>

<template>
  <section class="document-page">
    <article class="panel">
      <div class="section-heading">
        <div>
          <h1 class="section-title">上传资料并进入路由流程</h1>
          <p class="section-subtitle">接入实验室资料，生成可检索、可引用的知识资产。</p>
        </div>
      </div>

      <div class="form-grid">
        <label class="field">
          <span class="field-label">文档名称</span>
          <input
            v-model="formState.documentName"
            placeholder="不填则使用原始文件名"
          >
        </label>
        <label class="field">
          <span class="field-label">知识域编码（可空）</span>
          <input
            v-model="formState.knowledgeScopeCode"
            placeholder="留空则由系统识别，例如 hr_policy"
          >
        </label>
        <label class="field">
          <span class="field-label">知识域名称（可空）</span>
          <input
            v-model="formState.knowledgeScopeName"
            placeholder="留空则由系统识别，例如 人事制度"
          >
        </label>
        <label class="field">
          <span class="field-label">专题编码（可空）</span>
          <input
            v-model="formState.knowledgeTopicCode"
            placeholder="留空则由系统识别，例如 leave_policy"
          >
        </label>
        <label class="field">
          <span class="field-label">专题名称（可空）</span>
          <input
            v-model="formState.knowledgeTopicName"
            placeholder="留空则由系统识别，例如 请假制度"
          >
        </label>
        <label class="field">
          <span class="field-label">业务分类</span>
          <input
            v-model="formState.businessCategory"
            placeholder="例如 流程 / 规则 / 手册"
          >
        </label>
        <label class="field field-full">
          <span class="field-label">文档标签</span>
          <input
            v-model="formState.documentTags"
            placeholder="多个标签用英文逗号分隔，例如 年假,请假,审批"
          >
        </label>

        <div class="field field-full">
          <span class="field-label">选择文件</span>
          <label class="file-picker">
            <input
              class="file-input"
              type="file"
              accept=".pdf,.doc,.docx,.txt,.md,.html,.htm,.ppt,.pptx"
              @change="handleFileChange"
            >
            <span class="file-button">选择文件</span>
            <span class="file-name">{{ selectedFile?.name || '未选择文件' }}</span>
          </label>
        </div>
      </div>

      <div class="upload-summary">
        <div class="summary-copy">
          <p class="summary-label">支持 PDF / Word / TXT / MD / HTML / PPT</p>
          <strong class="summary-name">{{ selectedFile?.name || '尚未选择文件' }}</strong>
        </div>

        <div class="summary-actions">
          <button
            type="button"
            class="button secondary"
            :disabled="isUploading"
            @click="resetForm"
          >
            清空
          </button>
          <button
            type="button"
            class="button primary"
            :disabled="isUploading"
            @click="uploadDocument"
          >
            {{ isUploading ? '上传中' : '上传并生成路由资产' }}
          </button>
        </div>
      </div>

      <p
        v-if="statusMessage"
        class="status-message"
      >
        {{ statusMessage }}
      </p>
    </article>

    <article class="panel">
      <div class="list-header">
        <div>
          <h2 class="list-title">文档列表</h2>
          <p class="list-meta">共 {{ totalSize }} 份文档，当前展示前 20 条。</p>
        </div>

        <div class="search-bar">
          <input
            v-model="keyword"
            placeholder="搜索文档名称或原始文件名"
            @keydown.enter="loadDocuments()"
          >
          <button
            type="button"
            class="button primary"
            :disabled="isLoading"
            @click="loadDocuments()"
          >
            搜索
          </button>
        </div>
      </div>

      <div
        v-if="documentRows.length === 0"
        class="empty-actions"
      >
        <span>{{ isLoading ? '正在加载文档' : '暂无文档' }}</span>
      </div>

      <div
        v-else
        class="document-table"
      >
        <div class="table-row table-head">
          <span class="cell-document">文档</span>
          <span class="cell-scope">知识域</span>
          <span class="cell-category">分类</span>
          <span class="cell-status">状态</span>
          <span class="cell-actions">操作</span>
        </div>
        <div
          v-for="row in documentRows"
          :key="row.documentId"
          class="table-row"
        >
          <span class="cell-document">
            <strong>{{ row.documentName }}</strong>
            <small>{{ row.originalFileName }}</small>
          </span>
          <span class="cell-scope">
            <strong>{{ row.knowledgeScopeName }}</strong>
            <small>{{ row.knowledgeScopeCode }}</small>
          </span>
          <span class="cell-category">{{ row.businessCategory || '-' }}</span>
          <span class="cell-status">{{ documentStatusText(row) }}</span>
          <span class="row-actions cell-actions">
            <button
              type="button"
              class="button secondary compact"
              @click="openDocumentDetail(row)"
            >
              详情
            </button>
            <button
              type="button"
              class="button secondary compact"
              @click="openDocumentFullText(row)"
            >
              全文
            </button>
            <button
              type="button"
              class="button danger compact"
              :disabled="deletingDocumentId === row.documentId"
              @click="deleteDocument(row)"
            >
              {{ deletingDocumentId === row.documentId ? '删除中' : '删除' }}
            </button>
          </span>
        </div>
      </div>
    </article>

    <div
      v-if="detailDocument"
      class="modal-mask"
      @click.self="closeDocumentDetail"
    >
      <article class="detail-modal">
        <header class="modal-header">
          <div>
            <h2 class="list-title">文档详情</h2>
            <p class="list-meta">{{ detailDocument.documentName }}</p>
          </div>
          <button
            type="button"
            class="button secondary compact"
            @click="closeDocumentDetail"
          >
            关闭
          </button>
        </header>

        <div class="detail-grid">
          <span>文档 ID</span>
          <strong>{{ detailDocument.documentId }}</strong>
          <span>原始文件名</span>
          <strong>{{ detailDocument.originalFileName }}</strong>
          <span>知识域</span>
          <strong>{{ detailDocument.knowledgeScopeName }} / {{ detailDocument.knowledgeScopeCode }}</strong>
          <span>业务分类</span>
          <strong>{{ detailDocument.businessCategory || '-' }}</strong>
          <span>文档标签</span>
          <strong>{{ detailDocument.documentTags || '-' }}</strong>
          <span>处理状态</span>
          <strong>{{ documentStatusText(detailDocument) }}</strong>
          <span>创建时间</span>
          <strong>{{ detailDocument.createTime || '-' }}</strong>
        </div>

        <section class="profile-block">
          <h3>画像摘要</h3>
          <p v-if="isDetailLoading">正在加载画像</p>
          <template v-else>
            <p>{{ detailProfile?.summaryText || '-' }}</p>
            <div class="profile-tags">
              <span
                v-for="term in detailProfile?.terms || []"
                :key="term"
              >
                {{ term }}
              </span>
            </div>
          </template>
        </section>
      </article>
    </div>

    <div
      v-if="fullTextDocument"
      class="modal-mask"
      @click.self="closeDocumentFullText"
    >
      <article class="full-text-modal">
        <header class="modal-header">
          <div>
            <h2 class="list-title">浏览全文</h2>
            <p class="list-meta">{{ fullTextDocument.documentName }}</p>
          </div>
          <button
            type="button"
            class="button secondary compact"
            @click="closeDocumentFullText"
          >
            关闭
          </button>
        </header>

        <p
          v-if="isFullTextLoading"
          class="full-text-loading"
        >
          正在加载正文
        </p>
        <pre
          v-else
          class="full-text-content"
        >{{ fullTextContent || '暂无正文' }}</pre>
      </article>
    </div>
  </section>
</template>

<style scoped>
.document-page,
.document-page * {
  box-sizing: border-box;
}

.document-page {
  display: grid;
  gap: var(--admin-page-gap);
}

.panel {
  padding: var(--admin-panel-padding);
  border: 1px solid var(--admin-color-border);
  border-radius: var(--admin-radius-panel);
  background: var(--admin-color-card);
  box-shadow: var(--admin-shadow-panel);
}

.section-title,
.list-title {
  margin: 0;
  color: var(--admin-color-title);
  line-height: 1.2;
  font-weight: 600;
}

.section-title {
  font-size: var(--admin-title-size);
}

.list-title {
  font-size: 20px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-subtitle {
  margin: 8px 0 0;
  color: var(--admin-color-muted);
  font-size: var(--admin-subtitle-size);
  line-height: 1.6;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.field {
  display: grid;
  gap: 6px;
}

.field-full {
  grid-column: 1 / -1;
}

.field-label {
  color: var(--admin-color-subtle);
  font-size: var(--admin-subtitle-size);
  font-weight: 600;
}

input {
  width: 100%;
  height: var(--admin-control-height);
  padding: 0 12px;
  border: 1px solid var(--admin-color-field-border);
  border-radius: var(--admin-radius-control);
  outline: none;
  background: #ffffff;
  color: var(--admin-color-text);
  font-size: var(--admin-control-font-size);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

input::placeholder {
  color: #9aaeaf;
}

input:focus {
  border-color: #5cc3b8;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.14);
}

.file-picker {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 4px;
  border: 1px solid #c9dde1;
  border-radius: 8px;
  background: #ffffff;
}

.file-input {
  display: none;
}

.file-button,
.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.file-button {
  border: 1px solid #cfe1e4;
  background: #eef8f6;
  color: #284247;
}

.file-name {
  color: #60787f;
  font-size: 13px;
}

.upload-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px dashed #b8d8d4;
  border-radius: 8px;
  background: rgba(238, 248, 246, 0.62);
}

.summary-label {
  margin: 0;
  color: #6d858b;
  font-size: 12px;
}

.summary-name {
  display: block;
  margin-top: 4px;
  color: #173033;
  font-size: 14px;
}

.summary-actions {
  display: flex;
  gap: 8px;
}

.button {
  border: 1px solid #c9dde1;
  background: #ffffff;
  color: #284247;
}

.button.primary {
  border-color: #0f766e;
  background: #0f766e;
  color: #ffffff;
}

.button.danger {
  border-color: #f0b6b6;
  background: #fff5f5;
  color: #b42318;
}

.button.compact {
  min-height: 32px;
  padding: 0 10px;
}

.button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.button:hover:not(:disabled),
.file-button:hover {
  transform: translateY(-1px);
}

.button.primary:hover:not(:disabled) {
  background: #0d9488;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.list-meta,
.status-message {
  margin: 6px 0 0;
  color: #60787f;
  font-size: 13px;
}

.search-bar {
  display: flex;
  gap: 8px;
  width: min(420px, 100%);
}

.empty-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  margin-top: 16px;
  border: 1px dashed #b8d8d4;
  border-radius: 8px;
  color: #60787f;
}

.document-table {
  display: grid;
  margin-top: 16px;
  border: 1px solid #d8e6e8;
  border-radius: 8px;
  overflow-x: auto;
}

.table-row {
  display: grid;
  grid-template-columns: minmax(260px, 2fr) minmax(180px, 1.35fr) minmax(150px, 1fr) minmax(150px, 0.9fr) 230px;
  gap: 12px;
  align-items: center;
  min-width: 980px;
  min-height: 58px;
  padding: 10px 12px;
  border-top: 1px solid #d8e6e8;
  color: #284247;
  font-size: 13px;
}

.row-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.cell-status {
  color: #0f766e;
  font-weight: 600;
}

.cell-actions {
  justify-self: end;
}

.table-head {
  min-height: 40px;
  border-top: 0;
  background: #eef8f6;
  color: #536d73;
  font-size: 12px;
  font-weight: 700;
}

.table-row strong,
.table-row small {
  display: block;
}

.table-row small {
  margin-top: 4px;
  color: #60787f;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(17, 78, 84, 0.34);
}

.detail-modal,
.full-text-modal {
  width: min(720px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
  padding: 20px;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 48px rgba(17, 78, 84, 0.2);
}

.full-text-modal {
  width: min(860px, 100%);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #d8e6e8;
}

.detail-grid {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 12px 16px;
  margin-top: 16px;
  color: #284247;
  font-size: 13px;
}

.detail-grid span {
  color: #60787f;
}

.detail-grid strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.profile-block {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #d8e6e8;
}

.profile-block h3 {
  margin: 0 0 8px;
  color: #102a2f;
  font-size: 15px;
}

.profile-block p {
  margin: 0;
  color: #385156;
  font-size: 13px;
  line-height: 1.7;
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.profile-tags span {
  padding: 5px 8px;
  border: 1px solid #cfe1e4;
  border-radius: 8px;
  background: #eef8f6;
  color: #385156;
  font-size: 12px;
}

.full-text-loading {
  margin: 16px 0 0;
  color: #60787f;
  font-size: 13px;
}

.full-text-content {
  max-height: calc(100vh - 180px);
  margin: 16px 0 0;
  overflow: auto;
  padding: 14px;
  color: #173033;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid #d8e6e8;
  border-radius: 8px;
  background: #f6fbfb;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .list-header,
  .upload-summary {
    align-items: stretch;
    flex-direction: column;
  }

  .search-bar {
    width: 100%;
  }

  .row-actions {
    justify-content: flex-end;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
