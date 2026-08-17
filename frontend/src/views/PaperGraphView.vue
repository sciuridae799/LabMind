<script setup lang="ts">
import { GraphChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { init, use, type EChartsType } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import {
  paperGraphApi,
  type PaperDocument,
  type PaperDocumentStatus,
  type PaperEdgeEvidence,
  type PaperEntityType,
  type PaperGraph,
  type PaperGraphEdge,
  type PaperGraphNode,
  type PaperGraphVisualization,
  type PaperNodeDetail,
  type PaperRelationType
} from '../shared/api/paperGraph'
import { canWriteDocuments } from '../shared/auth/authSession'

use([GraphChart, TooltipComponent, CanvasRenderer])

interface EntityOption {
  type: PaperEntityType
  label: string
  color: string
}

const entityOptions: EntityOption[] = [
  { type: 'Paper', label: '论文', color: '#2563eb' },
  { type: 'Method', label: '方法', color: '#16a34a' },
  { type: 'Task', label: '任务', color: '#ea580c' },
  { type: 'Dataset', label: '数据集', color: '#9333ea' },
  { type: 'MetricResult', label: '指标结果', color: '#dc2626' },
  { type: 'Baseline', label: '基线', color: '#64748b' },
  { type: 'Limitation', label: '局限', color: '#ca8a04' }
]

const relationLabels: Record<PaperRelationType, string> = {
  PROPOSES: '提出',
  SOLVES: '解决',
  USES: '使用',
  ACHIEVES: '获得',
  OUTPERFORMS: '优于',
  HAS_LIMITATION: '存在局限'
}

const statusLabels: Record<PaperDocumentStatus, string> = {
  UPLOADED: '等待构建',
  PARSING: '解析论文',
  EXTRACTING: '抽取图谱',
  VALIDATING: '校验证据',
  COMPLETED: '构建完成',
  FAILED: '构建失败'
}

const buildingStatuses: PaperDocumentStatus[] = [
  'UPLOADED',
  'PARSING',
  'EXTRACTING',
  'VALIDATING'
]
const MAX_PDF_FILE_SIZE_BYTES = 10 * 1024 * 1024

const canWrite = computed(() => canWriteDocuments())
const graphs = ref<PaperGraph[]>([])
const documents = ref<PaperDocument[]>([])
const selectedGraphId = ref('')
const selectedDocumentId = ref('')
const selectedEntityTypes = ref<PaperEntityType[]>([])
const searchQuery = ref('')
const visualization = ref<PaperGraphVisualization>({
  nodes: [],
  edges: [],
  nodeLimit: 200,
  edgeLimit: 400
})
const selectedNode = ref<PaperNodeDetail | null>(null)
const selectedEvidence = ref<PaperEdgeEvidence | null>(null)
const graphName = ref('')
const graphDescription = ref('')
const showCreateForm = ref(false)
const isLoading = ref(false)
const isCreating = ref(false)
const isUploading = ref(false)
const activeDocumentActionId = ref('')
const statusMessage = ref('')
const errorMessage = ref('')
const chartElement = ref<HTMLDivElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

let chart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null
let statusTimer: ReturnType<typeof setInterval> | null = null
let workspaceGeneration = 0
let detailGeneration = 0
const openedPdfUrls = new Set<string>()

const selectedGraph = computed(() => (
  graphs.value.find((graph) => graph.id === selectedGraphId.value) ?? null
))

const hasBuildingDocument = computed(() => documents.value.some((document) => (
  buildingStatuses.includes(document.status)
)))

function entityColor(type: PaperEntityType): string {
  const option = entityOptions.find((item) => item.type === type)
  if (!option) {
    throw new Error(`未知实体类型：${type}`)
  }
  return option.color
}

function entityLabel(type: PaperEntityType): string {
  const option = entityOptions.find((item) => item.type === type)
  if (!option) {
    throw new Error(`未知实体类型：${type}`)
  }
  return option.label
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function resolveError(error: unknown): string {
  return error instanceof Error ? error.message : '论文知识图谱请求失败'
}

function clearFeedback(): void {
  statusMessage.value = ''
  errorMessage.value = ''
}

function setError(error: unknown): void {
  errorMessage.value = resolveError(error)
  statusMessage.value = ''
}

async function refreshGraphs(): Promise<void> {
  graphs.value = await paperGraphApi.listGraphs()
}

async function createGraph(): Promise<void> {
  const name = graphName.value.trim()
  if (!name || isCreating.value) {
    return
  }
  clearFeedback()
  isCreating.value = true
  try {
    const created = await paperGraphApi.createGraph({
      name,
      description: graphDescription.value.trim() || undefined
    })
    await refreshGraphs()
    graphName.value = ''
    graphDescription.value = ''
    showCreateForm.value = false
    statusMessage.value = '图谱空间已创建'
    await selectGraph(created.id)
  } catch (error) {
    setError(error)
  } finally {
    isCreating.value = false
  }
}

async function deleteSelectedGraph(): Promise<void> {
  const graph = selectedGraph.value
  if (!graph || !canWrite.value || !window.confirm(`删除图谱“${graph.name}”及全部论文？`)) {
    return
  }
  clearFeedback()
  try {
    await paperGraphApi.deleteGraph(graph.id)
    await refreshGraphs()
    if (graph.id !== selectedGraphId.value) {
      return
    }
    selectedGraphId.value = ''
    detailGeneration += 1
    documents.value = []
    visualization.value = { nodes: [], edges: [], nodeLimit: 200, edgeLimit: 400 }
    selectedNode.value = null
    selectedEvidence.value = null
    if (graphs.value[0]) {
      await selectGraph(graphs.value[0].id)
    } else {
      renderChart()
    }
    statusMessage.value = '图谱空间已删除'
  } catch (error) {
    if (graph.id === selectedGraphId.value) {
      setError(error)
    }
  }
}

async function selectGraph(graphId: string): Promise<void> {
  if (!graphId) {
    return
  }
  selectedGraphId.value = graphId
  detailGeneration += 1
  selectedDocumentId.value = ''
  selectedEntityTypes.value = []
  searchQuery.value = ''
  selectedNode.value = null
  selectedEvidence.value = null
  await loadGraphWorkspace(graphId)
}

async function loadGraphWorkspace(graphId: string): Promise<void> {
  const generation = ++workspaceGeneration
  isLoading.value = true
  clearFeedback()
  try {
    const [graph, documentList, graphData] = await Promise.all([
      paperGraphApi.getGraph(graphId),
      paperGraphApi.listDocuments(graphId),
      paperGraphApi.visualization(graphId, {})
    ])
    if (generation !== workspaceGeneration || graphId !== selectedGraphId.value) {
      return
    }
    const graphIndex = graphs.value.findIndex((item) => item.id === graph.id)
    if (graphIndex >= 0) {
      graphs.value[graphIndex] = graph
    }
    documents.value = documentList
    visualization.value = graphData
    await nextTick()
    renderChart()
    configureStatusPolling()
  } catch (error) {
    if (generation === workspaceGeneration) {
      setError(error)
    }
  } finally {
    if (generation === workspaceGeneration) {
      isLoading.value = false
    }
  }
}

async function applyFilters(): Promise<void> {
  const graphId = selectedGraphId.value
  if (!graphId) {
    return
  }
  const generation = ++workspaceGeneration
  detailGeneration += 1
  isLoading.value = true
  clearFeedback()
  try {
    const graphData = await paperGraphApi.visualization(graphId, {
      documentId: selectedDocumentId.value || undefined,
      entityTypes: selectedEntityTypes.value,
      query: searchQuery.value
    })
    if (generation !== workspaceGeneration || graphId !== selectedGraphId.value) {
      return
    }
    visualization.value = graphData
    selectedNode.value = null
    selectedEvidence.value = null
    renderChart()
  } catch (error) {
    if (generation === workspaceGeneration) {
      setError(error)
    }
  } finally {
    if (generation === workspaceGeneration) {
      isLoading.value = false
    }
  }
}

function toggleEntityType(type: PaperEntityType): void {
  selectedEntityTypes.value = selectedEntityTypes.value.includes(type)
    ? selectedEntityTypes.value.filter((item) => item !== type)
    : [...selectedEntityTypes.value, type]
}

function triggerUpload(): void {
  if (canWrite.value && selectedGraphId.value && !isUploading.value) {
    fileInput.value?.click()
  }
}

async function handleFileSelection(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  const graphId = selectedGraphId.value
  if (!file || !graphId) {
    return
  }
  clearFeedback()
  if (file.size > MAX_PDF_FILE_SIZE_BYTES) {
    errorMessage.value = 'PDF 文件不能超过 10 MB'
    return
  }
  isUploading.value = true
  try {
    const document = await paperGraphApi.uploadDocument(graphId, file)
    const documentList = await paperGraphApi.listDocuments(graphId)
    if (graphId !== selectedGraphId.value) {
      return
    }
    documents.value = documentList
    statusMessage.value = document.reused
      ? `文件内容未变化，沿用版本 v${document.version}`
      : `论文已上传，开始构建版本 v${document.version}`
    configureStatusPolling()
  } catch (error) {
    if (graphId === selectedGraphId.value) {
      setError(error)
    }
  } finally {
    isUploading.value = false
  }
}

async function rebuildDocument(document: PaperDocument): Promise<void> {
  if (!canWrite.value || activeDocumentActionId.value) {
    return
  }
  activeDocumentActionId.value = document.id
  clearFeedback()
  try {
    await paperGraphApi.rebuildDocument(document.id)
    const documentList = await paperGraphApi.listDocuments(document.graphId)
    if (document.graphId !== selectedGraphId.value) {
      return
    }
    documents.value = documentList
    statusMessage.value = `已重新提交 ${document.filename} v${document.version}`
    configureStatusPolling()
  } catch (error) {
    if (document.graphId === selectedGraphId.value) {
      setError(error)
    }
  } finally {
    activeDocumentActionId.value = ''
  }
}

async function deleteDocument(document: PaperDocument): Promise<void> {
  if (
    !canWrite.value ||
    activeDocumentActionId.value ||
    !window.confirm(`删除 ${document.filename} v${document.version} 及其图谱证据？`)
  ) {
    return
  }
  activeDocumentActionId.value = document.id
  clearFeedback()
  try {
    await paperGraphApi.deleteDocument(document.id)
    if (document.graphId !== selectedGraphId.value) {
      return
    }
    if (selectedDocumentId.value === document.id) {
      selectedDocumentId.value = ''
    }
    await loadGraphWorkspace(document.graphId)
    if (document.graphId === selectedGraphId.value) {
      statusMessage.value = '论文及对应图谱数据已删除'
    }
  } catch (error) {
    if (document.graphId === selectedGraphId.value) {
      setError(error)
    }
  } finally {
    activeDocumentActionId.value = ''
  }
}

function configureStatusPolling(): void {
  if (statusTimer) {
    clearInterval(statusTimer)
    statusTimer = null
  }
  if (hasBuildingDocument.value) {
    statusTimer = setInterval(() => {
      void pollDocumentStatuses()
    }, 3000)
  }
}

async function pollDocumentStatuses(): Promise<void> {
  const graphId = selectedGraphId.value
  if (!graphId || !hasBuildingDocument.value) {
    configureStatusPolling()
    return
  }
  try {
    const previousStatuses = new Map(
      documents.value.map((document) => [document.id, document.status])
    )
    const updated = await Promise.all(
      documents.value.map((document) => paperGraphApi.getDocumentStatus(document.id))
    )
    if (graphId !== selectedGraphId.value) {
      return
    }
    documents.value = updated
    const reachedTerminalState = updated.some((document) => (
      previousStatuses.get(document.id) !== document.status &&
      (document.status === 'COMPLETED' || document.status === 'FAILED')
    ))
    if (reachedTerminalState) {
      await loadGraphWorkspace(graphId)
    } else {
      configureStatusPolling()
    }
  } catch (error) {
    if (graphId === selectedGraphId.value) {
      setError(error)
      if (statusTimer) {
        clearInterval(statusTimer)
        statusTimer = null
      }
    }
  }
}

function renderChart(): void {
  if (!chartElement.value) {
    return
  }
  if (!chart) {
    chart = init(chartElement.value)
  }
  const categories = entityOptions.map((option) => ({
    name: option.type,
    itemStyle: { color: option.color }
  }))
  const nodes = visualization.value.nodes.map((node) => ({
    ...node,
    category: entityOptions.findIndex((option) => option.type === node.entityType),
    symbolSize: node.entityType === 'Paper' ? 50 : node.entityType === 'Method' ? 42 : 34,
    itemStyle: { color: entityColor(node.entityType) }
  }))
  const links = visualization.value.edges.map((edge) => ({
    ...edge,
    value: relationLabels[edge.relationType],
    lineStyle: { color: '#8aa4a9', curveness: 0.08 }
  }))
  chart.setOption({
    animationDurationUpdate: 350,
    tooltip: {
      trigger: 'item',
      formatter: (params: { dataType?: string; data?: { name?: string; value?: string } }) => (
        params.dataType === 'edge' ? params.data?.value ?? '' : params.data?.name ?? ''
      )
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      data: nodes,
      links,
      categories,
      force: {
        repulsion: 260,
        edgeLength: [90, 170],
        gravity: 0.08
      },
      label: {
        show: nodes.length <= 80,
        position: 'right',
        color: '#274247',
        fontSize: 11,
        formatter: '{b}'
      },
      edgeLabel: {
        show: links.length <= 100,
        color: '#60777c',
        fontSize: 10,
        formatter: (params: { data?: { value?: string } }) => params.data?.value ?? ''
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 2.5, color: '#0f766e' }
      }
    }]
  }, true)
  chart.off('click')
  chart.off('dblclick')
  chart.on('click', (params) => {
    if (params.dataType === 'node') {
      const node = params.data as PaperGraphNode
      void loadNodeDetail(node.id)
    } else if (params.dataType === 'edge') {
      const edge = params.data as PaperGraphEdge
      void loadEdgeEvidence(edge.id)
    }
  })
  chart.on('dblclick', (params) => {
    if (params.dataType === 'node') {
      const node = params.data as PaperGraphNode
      void expandNeighbors(node.id)
    }
  })
}

async function loadNodeDetail(nodeId: string): Promise<void> {
  const graphId = selectedGraphId.value
  if (!graphId) {
    return
  }
  const generation = ++detailGeneration
  clearFeedback()
  try {
    const node = await paperGraphApi.nodeDetail(graphId, nodeId)
    if (generation !== detailGeneration || graphId !== selectedGraphId.value) {
      return
    }
    selectedNode.value = node
    selectedEvidence.value = null
  } catch (error) {
    if (generation === detailGeneration) {
      setError(error)
    }
  }
}

async function loadEdgeEvidence(edgeId: string): Promise<void> {
  const graphId = selectedGraphId.value
  if (!graphId) {
    return
  }
  const generation = ++detailGeneration
  clearFeedback()
  try {
    const evidence = await paperGraphApi.edgeEvidence(graphId, edgeId)
    if (generation !== detailGeneration || graphId !== selectedGraphId.value) {
      return
    }
    selectedEvidence.value = evidence
    selectedNode.value = null
  } catch (error) {
    if (generation === detailGeneration) {
      setError(error)
    }
  }
}

async function expandNeighbors(nodeId: string): Promise<void> {
  const graphId = selectedGraphId.value
  if (!graphId) {
    return
  }
  const generation = ++workspaceGeneration
  clearFeedback()
  try {
    const neighbors = await paperGraphApi.neighbors(graphId, nodeId)
    if (generation !== workspaceGeneration || graphId !== selectedGraphId.value) {
      return
    }
    const nodeById = new Map(
      [...neighbors.nodes, ...visualization.value.nodes].map((node) => [node.id, node])
    )
    const edgeById = new Map(
      [...neighbors.edges, ...visualization.value.edges].map((edge) => [edge.id, edge])
    )
    visualization.value = {
      nodes: [...nodeById.values()].slice(0, 200),
      edges: [...edgeById.values()].slice(0, 400),
      nodeLimit: 200,
      edgeLimit: 400
    }
    renderChart()
    statusMessage.value = '已展开一跳邻居'
  } catch (error) {
    if (generation === workspaceGeneration) {
      setError(error)
    }
  }
}

async function openEvidencePdf(): Promise<void> {
  const evidence = selectedEvidence.value
  if (!evidence) {
    return
  }
  clearFeedback()
  try {
    const blob = await paperGraphApi.downloadDocument(evidence.documentId)
    const objectUrl = URL.createObjectURL(blob)
    const opened = window.open(`${objectUrl}#page=${evidence.pageNumber}`, '_blank', 'noopener')
    if (!opened) {
      URL.revokeObjectURL(objectUrl)
      throw new Error('浏览器阻止了 PDF 页面打开')
    }
    openedPdfUrls.add(objectUrl)
  } catch (error) {
    setError(error)
  }
}

onMounted(async () => {
  resizeObserver = new ResizeObserver(() => chart?.resize())
  if (chartElement.value) {
    resizeObserver.observe(chartElement.value)
  }
  isLoading.value = true
  try {
    await refreshGraphs()
    if (graphs.value[0]) {
      await selectGraph(graphs.value[0].id)
    }
  } catch (error) {
    setError(error)
  } finally {
    isLoading.value = false
  }
})

onBeforeUnmount(() => {
  workspaceGeneration += 1
  detailGeneration += 1
  if (statusTimer) {
    clearInterval(statusTimer)
  }
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
  openedPdfUrls.forEach((objectUrl) => URL.revokeObjectURL(objectUrl))
  openedPdfUrls.clear()
})
</script>

<template>
  <main class="paper-graph-page">
    <aside class="graph-sidebar panel-column">
      <header class="column-header">
        <div>
          <p class="eyebrow">PAPER GRAPH</p>
          <h1>论文知识图谱</h1>
        </div>
        <button
          v-if="canWrite"
          type="button"
          class="icon-button"
          title="新建图谱"
          @click="showCreateForm = !showCreateForm"
        >
          +
        </button>
      </header>

      <form
        v-if="showCreateForm"
        class="create-form"
        @submit.prevent="createGraph"
      >
        <input
          v-model="graphName"
          maxlength="160"
          placeholder="图谱名称"
          required
        >
        <textarea
          v-model="graphDescription"
          maxlength="2000"
          rows="2"
          placeholder="描述（可选）"
        />
        <div class="inline-actions">
          <button type="button" class="text-button" @click="showCreateForm = false">取消</button>
          <button type="submit" class="primary-button" :disabled="isCreating || !graphName.trim()">
            {{ isCreating ? '创建中' : '创建' }}
          </button>
        </div>
      </form>

      <section class="sidebar-section graph-list-section">
        <div class="section-heading">
          <span>图谱空间</span>
          <small>{{ graphs.length }}</small>
        </div>
        <div v-if="graphs.length" class="graph-list">
          <button
            v-for="graph in graphs"
            :key="graph.id"
            type="button"
            class="graph-list-item"
            :class="{ active: graph.id === selectedGraphId }"
            @click="selectGraph(graph.id)"
          >
            <strong>{{ graph.name }}</strong>
            <span>{{ graph.documentCount }} 篇论文 · {{ graph.nodeCount }} 个节点</span>
          </button>
        </div>
        <p v-else class="empty-copy">还没有论文图谱空间。</p>
      </section>

      <section v-if="selectedGraph" class="sidebar-section document-section">
        <div class="section-heading">
          <span>论文与状态</span>
          <button
            v-if="canWrite"
            type="button"
            class="upload-button"
            :disabled="isUploading"
            @click="triggerUpload"
          >
            {{ isUploading ? '上传中' : '上传 PDF（≤ 10 MB）' }}
          </button>
          <input
            ref="fileInput"
            class="visually-hidden"
            type="file"
            accept="application/pdf,.pdf"
            @change="handleFileSelection"
          >
        </div>
        <div class="document-list">
          <article v-for="document in documents" :key="document.id" class="document-card">
            <div class="document-card-main">
              <strong :title="document.filename">{{ document.filename }}</strong>
              <span>v{{ document.version }} · {{ formatTime(document.updatedAt) }}</span>
            </div>
            <div class="document-status-row">
              <span class="status-pill" :data-status="document.status">
                {{ statusLabels[document.status] }}
              </span>
              <div v-if="canWrite" class="document-actions">
                <button
                  type="button"
                  :disabled="activeDocumentActionId.length > 0 || buildingStatuses.includes(document.status)"
                  @click="rebuildDocument(document)"
                >重建</button>
                <button
                  type="button"
                  class="danger-text"
                  :disabled="activeDocumentActionId.length > 0 || buildingStatuses.includes(document.status)"
                  @click="deleteDocument(document)"
                >删除</button>
              </div>
            </div>
            <p v-if="document.errorMessage" class="document-error">{{ document.errorMessage }}</p>
          </article>
          <p v-if="documents.length === 0" class="empty-copy">上传计算机领域论文后开始构图。</p>
        </div>
      </section>
    </aside>

    <section class="graph-canvas-column">
      <header class="graph-toolbar">
        <div class="toolbar-copy">
          <strong>{{ selectedGraph?.name || '选择图谱空间' }}</strong>
          <span v-if="selectedGraph">
            {{ selectedGraph.nodeCount }} 个节点 · {{ selectedGraph.edgeCount }} 条证据关系
          </span>
        </div>
        <div v-if="selectedGraph" class="toolbar-controls">
          <select v-model="selectedDocumentId" :disabled="isLoading" aria-label="按论文筛选">
            <option value="">全部论文</option>
            <option
              v-for="document in documents.filter((item) => item.status === 'COMPLETED')"
              :key="document.id"
              :value="document.id"
            >
              {{ document.filename }} · v{{ document.version }}
            </option>
          </select>
          <div class="search-control">
            <input
              v-model="searchQuery"
              :disabled="isLoading"
              placeholder="搜索节点名称"
              @keydown.enter="applyFilters"
            >
            <button type="button" :disabled="isLoading" @click="applyFilters">筛选</button>
          </div>
          <button
            v-if="canWrite"
            type="button"
            class="danger-outline-button"
            :disabled="hasBuildingDocument"
            @click="deleteSelectedGraph"
          >删除图谱</button>
        </div>
      </header>

      <div v-if="selectedGraph" class="entity-filter-bar">
        <button
          v-for="option in entityOptions"
          :key="option.type"
          type="button"
          class="entity-filter"
          :class="{ active: selectedEntityTypes.includes(option.type) }"
          :disabled="isLoading"
          @click="toggleEntityType(option.type)"
        >
          <span :style="{ backgroundColor: option.color }" />
          {{ option.label }}
        </button>
      </div>

      <div class="canvas-shell">
        <div ref="chartElement" class="graph-chart" />
        <div v-if="isLoading" class="canvas-state">正在加载图谱…</div>
        <div
          v-else-if="selectedGraph && visualization.nodes.length === 0"
          class="canvas-state"
        >
          <strong>当前没有可展示的节点</strong>
          <span>上传论文并等待构建完成，或调整筛选条件。</span>
        </div>
        <div v-else-if="!selectedGraph" class="canvas-state">
          <strong>选择或创建一个图谱空间</strong>
          <span>论文知识图谱与文档问答保持独立。</span>
        </div>
        <div v-if="selectedGraph && visualization.nodes.length" class="canvas-hint">
          拖拽与滚轮浏览 · 单击查看详情 · 双击展开一跳邻居
        </div>
      </div>

      <div v-if="statusMessage || errorMessage" class="feedback-bar" :class="{ error: errorMessage }">
        {{ errorMessage || statusMessage }}
      </div>
    </section>

    <aside class="detail-sidebar panel-column">
      <header class="column-header detail-header">
        <div>
          <p class="eyebrow">EVIDENCE</p>
          <h2>节点与证据</h2>
        </div>
      </header>

      <section v-if="selectedNode" class="detail-content">
        <div class="detail-kind">
          <span :style="{ backgroundColor: entityColor(selectedNode.entityType) }" />
          {{ entityLabel(selectedNode.entityType) }}
        </div>
        <h3>{{ selectedNode.name }}</h3>
        <dl v-if="Object.keys(selectedNode.properties).length" class="property-list">
          <template v-for="(value, key) in selectedNode.properties" :key="key">
            <dt>{{ key }}</dt>
            <dd>{{ value }}</dd>
          </template>
        </dl>
        <div class="source-section">
          <span>来源论文</span>
          <article v-for="document in selectedNode.sourceDocuments" :key="document.documentId">
            <strong>{{ document.filename }}</strong>
            <small>版本 v{{ document.version }}</small>
          </article>
        </div>
      </section>

      <section v-else-if="selectedEvidence" class="detail-content">
        <div class="detail-kind relation-kind">关系证据</div>
        <h3>{{ selectedEvidence.sourceName }}</h3>
        <div class="relation-line">
          <span>{{ relationLabels[selectedEvidence.relationType] }}</span>
          <strong>→ {{ selectedEvidence.targetName }}</strong>
        </div>
        <dl class="evidence-meta">
          <div>
            <dt>来源论文</dt>
            <dd>{{ selectedEvidence.filename }} · v{{ selectedEvidence.version }}</dd>
          </div>
          <div>
            <dt>定位</dt>
            <dd>第 {{ selectedEvidence.pageNumber }} 页 · {{ selectedEvidence.sectionName }}</dd>
          </div>
        </dl>
        <blockquote>{{ selectedEvidence.evidenceQuote }}</blockquote>
        <button type="button" class="primary-button full-width" @click="openEvidencePdf">
          打开 PDF 对应页
        </button>
      </section>

      <div v-else class="detail-empty">
        <div class="detail-empty-icon">◎</div>
        <strong>选择图中的节点或关系</strong>
        <span>节点显示属性与来源论文，关系显示页码和原文证据。</span>
      </div>
    </aside>
  </main>
</template>

<style scoped>
* {
  box-sizing: border-box;
}

.paper-graph-page {
  --border: #d4e3e6;
  --muted: #647b80;
  --text: #183337;
  --accent: #0f766e;
  height: calc(100vh - 56px);
  min-height: 620px;
  display: grid;
  grid-template-columns: 286px minmax(440px, 1fr) 326px;
  overflow: hidden;
  color: var(--text);
  background:
    linear-gradient(rgba(21, 94, 99, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(21, 94, 99, 0.035) 1px, transparent 1px),
    #edf5f7;
  background-size: 28px 28px;
}

.panel-column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: rgba(249, 252, 252, 0.96);
  overflow: hidden;
}

.graph-sidebar {
  border-right: 1px solid var(--border);
}

.detail-sidebar {
  border-left: 1px solid var(--border);
}

.column-header {
  min-height: 78px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--border);
}

.column-header h1,
.column-header h2 {
  margin: 4px 0 0;
  color: #102a2f;
  font-size: 18px;
  line-height: 1.2;
}

.eyebrow {
  margin: 0;
  color: #789095;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

button,
input,
textarea,
select {
  font: inherit;
}

button {
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.52;
}

.icon-button {
  width: 34px;
  height: 34px;
  border: 1px solid #b9d9d6;
  border-radius: 8px;
  background: #eef9f7;
  color: var(--accent);
  font-size: 22px;
}

.create-form {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  background: #f1f9f8;
}

.create-form input,
.create-form textarea,
.toolbar-controls input,
.toolbar-controls select {
  width: 100%;
  border: 1px solid #c7dadd;
  border-radius: 7px;
  background: #fff;
  color: var(--text);
  outline: none;
}

.create-form input,
.create-form textarea {
  padding: 9px 10px;
  font-size: 13px;
}

.create-form input:focus,
.create-form textarea:focus,
.toolbar-controls input:focus,
.toolbar-controls select:focus {
  border-color: #5cc3b8;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}

.inline-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.primary-button,
.text-button,
.upload-button,
.danger-outline-button {
  min-height: 34px;
  border-radius: 7px;
  padding: 0 12px;
}

.primary-button {
  border: 1px solid var(--accent);
  background: var(--accent);
  color: #fff;
  font-weight: 700;
}

.text-button,
.upload-button {
  border: 1px solid #c7dadd;
  background: #fff;
  color: #36545a;
}

.sidebar-section {
  min-height: 0;
  padding: 14px;
  border-bottom: 1px solid var(--border);
}

.graph-list-section {
  flex: 0 1 42%;
  overflow: auto;
}

.document-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  color: #536d72;
  font-size: 12px;
  font-weight: 800;
}

.section-heading small {
  color: #91a3a7;
}

.upload-button {
  min-height: 28px;
  padding: 0 9px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}

.graph-list,
.document-list {
  display: grid;
  gap: 7px;
}

.document-list {
  overflow-y: auto;
}

.graph-list-item {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 10px 11px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--text);
  text-align: left;
}

.graph-list-item:hover {
  background: #edf7f6;
}

.graph-list-item.active {
  border-color: #b9dcd8;
  background: #e8f6f3;
  color: #0d6a63;
}

.graph-list-item strong,
.document-card strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.graph-list-item span,
.document-card-main span {
  color: var(--muted);
  font-size: 11px;
}

.document-card {
  padding: 10px;
  border: 1px solid #d9e6e8;
  border-radius: 8px;
  background: #fff;
}

.document-card-main {
  display: grid;
  gap: 3px;
}

.document-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
}

.status-pill {
  padding: 3px 7px;
  border-radius: 999px;
  background: #edf4f5;
  color: #536d72;
  font-size: 10px;
  font-weight: 800;
}

.status-pill[data-status='COMPLETED'] {
  background: #e8f7ed;
  color: #15803d;
}

.status-pill[data-status='FAILED'] {
  background: #fff0f0;
  color: #b91c1c;
}

.status-pill[data-status='PARSING'],
.status-pill[data-status='EXTRACTING'],
.status-pill[data-status='VALIDATING'] {
  background: #e8f3ff;
  color: #2563eb;
}

.document-actions {
  display: flex;
  gap: 6px;
}

.document-actions button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #557278;
  font-size: 10px;
}

.document-actions .danger-text {
  color: #b91c1c;
}

.document-error {
  margin: 8px 0 0;
  color: #b91c1c;
  font-size: 10px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.empty-copy {
  margin: 12px 2px;
  color: #83979b;
  font-size: 12px;
  line-height: 1.6;
}

.graph-canvas-column {
  min-width: 0;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  overflow: hidden;
}

.graph-toolbar {
  min-height: 78px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--border);
  background: rgba(250, 253, 253, 0.9);
}

.toolbar-copy {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.toolbar-copy strong {
  overflow: hidden;
  color: #132e33;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-copy span {
  color: var(--muted);
  font-size: 11px;
}

.toolbar-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.toolbar-controls select {
  width: 180px;
  height: 34px;
  padding: 0 8px;
  font-size: 11px;
}

.search-control {
  display: flex;
}

.search-control input {
  width: 150px;
  height: 34px;
  padding: 0 9px;
  border-radius: 7px 0 0 7px;
  font-size: 11px;
}

.search-control button {
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--accent);
  border-radius: 0 7px 7px 0;
  background: var(--accent);
  color: #fff;
  font-size: 11px;
}

.danger-outline-button {
  border: 1px solid #efb2b2;
  background: #fff;
  color: #b91c1c;
  font-size: 11px;
}

.entity-filter-bar {
  display: flex;
  gap: 6px;
  padding: 9px 18px;
  border-bottom: 1px solid var(--border);
  background: rgba(247, 251, 251, 0.92);
  overflow-x: auto;
}

.entity-filter {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 28px;
  padding: 0 9px;
  border: 1px solid #d4e2e4;
  border-radius: 999px;
  background: #fff;
  color: #587176;
  font-size: 10px;
}

.entity-filter span,
.detail-kind span {
  width: 7px;
  height: 7px;
  border-radius: 999px;
}

.entity-filter.active {
  border-color: #8dcfc7;
  background: #e9f7f4;
  color: var(--accent);
  font-weight: 800;
}

.canvas-shell {
  position: relative;
  min-height: 0;
  overflow: hidden;
}

.graph-chart {
  width: 100%;
  height: 100%;
}

.canvas-state {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  gap: 8px;
  color: #647b80;
  text-align: center;
  pointer-events: none;
}

.canvas-state strong {
  color: #29474c;
  font-size: 15px;
}

.canvas-state span {
  font-size: 12px;
}

.canvas-hint {
  position: absolute;
  left: 16px;
  bottom: 14px;
  padding: 6px 9px;
  border: 1px solid rgba(183, 211, 214, 0.9);
  border-radius: 6px;
  background: rgba(250, 253, 253, 0.9);
  color: #6c8388;
  font-size: 10px;
  pointer-events: none;
}

.feedback-bar {
  padding: 8px 16px;
  border-top: 1px solid #bfe2dc;
  background: #eaf8f5;
  color: #0f766e;
  font-size: 11px;
}

.feedback-bar.error {
  border-color: #f2caca;
  background: #fff3f3;
  color: #b91c1c;
}

.detail-content {
  min-height: 0;
  padding: 20px;
  overflow-y: auto;
}

.detail-kind {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #557278;
  font-size: 11px;
  font-weight: 800;
}

.relation-kind {
  padding: 4px 8px;
  border-radius: 999px;
  background: #e9f7f4;
  color: var(--accent);
}

.detail-content h3 {
  margin: 12px 0 18px;
  color: #102a2f;
  font-size: 20px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.property-list {
  display: grid;
  grid-template-columns: minmax(80px, auto) 1fr;
  gap: 8px 12px;
  margin: 0;
  padding: 14px;
  border: 1px solid #dbe7e9;
  border-radius: 8px;
  background: #fff;
  font-size: 12px;
}

.property-list dt,
.evidence-meta dt {
  color: #71878c;
  font-weight: 700;
}

.property-list dd,
.evidence-meta dd {
  margin: 0;
  color: #29474c;
  overflow-wrap: anywhere;
}

.source-section {
  display: grid;
  gap: 8px;
  margin-top: 20px;
}

.source-section > span {
  color: #71878c;
  font-size: 11px;
  font-weight: 800;
}

.source-section article {
  display: grid;
  gap: 3px;
  padding: 10px;
  border: 1px solid #dbe7e9;
  border-radius: 8px;
  background: #fff;
}

.source-section article strong {
  font-size: 12px;
  overflow-wrap: anywhere;
}

.source-section article small {
  color: #71878c;
}

.relation-line {
  display: grid;
  gap: 5px;
  margin: -6px 0 18px;
  color: #557278;
  font-size: 12px;
}

.relation-line span {
  color: var(--accent);
  font-weight: 800;
}

.relation-line strong {
  color: #17363a;
  font-size: 14px;
}

.evidence-meta {
  display: grid;
  gap: 10px;
  margin: 0 0 16px;
}

.evidence-meta div {
  display: grid;
  gap: 3px;
  font-size: 11px;
}

blockquote {
  margin: 0 0 18px;
  padding: 14px;
  border: 1px solid #cfe3e1;
  border-left: 3px solid var(--accent);
  border-radius: 7px;
  background: #f3faf9;
  color: #2d4c51;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.full-width {
  width: 100%;
}

.detail-empty {
  flex: 1;
  display: grid;
  place-content: center;
  gap: 8px;
  padding: 24px;
  color: #71878c;
  text-align: center;
}

.detail-empty-icon {
  margin: 0 auto 4px;
  color: #8cc9c3;
  font-size: 34px;
}

.detail-empty strong {
  color: #36545a;
  font-size: 14px;
}

.detail-empty span {
  font-size: 11px;
  line-height: 1.6;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1180px) {
  .paper-graph-page {
    grid-template-columns: 250px minmax(400px, 1fr) 280px;
  }

  .graph-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-controls {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

@media (max-width: 900px) {
  .paper-graph-page {
    height: auto;
    min-height: calc(100vh - 64px);
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .panel-column,
  .graph-canvas-column {
    overflow: visible;
  }

  .graph-sidebar,
  .detail-sidebar {
    border: 0;
    border-bottom: 1px solid var(--border);
  }

  .graph-list-section,
  .document-section {
    flex: none;
    max-height: 360px;
  }

  .canvas-shell {
    height: 620px;
  }

  .detail-sidebar {
    min-height: 340px;
  }
}
</style>
