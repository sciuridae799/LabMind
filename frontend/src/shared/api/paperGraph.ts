import { requestApiEnvelope, requestBlob, requestMultipartApiEnvelope } from './http'

export type PaperEntityType =
  | 'Paper'
  | 'Method'
  | 'Task'
  | 'Dataset'
  | 'MetricResult'
  | 'Baseline'
  | 'Limitation'

export type PaperRelationType =
  | 'PROPOSES'
  | 'SOLVES'
  | 'USES'
  | 'ACHIEVES'
  | 'OUTPERFORMS'
  | 'HAS_LIMITATION'

export type PaperDocumentStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'EXTRACTING'
  | 'VALIDATING'
  | 'COMPLETED'
  | 'FAILED'

export interface PaperGraph {
  id: string
  name: string
  description: string | null
  status: 'ACTIVE'
  documentCount: number
  completedDocumentCount: number
  nodeCount: number
  edgeCount: number
  createdAt: string
  updatedAt: string
}

export interface PaperDocument {
  id: string
  graphId: string
  filename: string
  version: number
  status: PaperDocumentStatus
  errorMessage: string | null
  chunkCount: number
  edgeCount: number
  reused: boolean
  createdAt: string
  updatedAt: string
}

export interface PaperGraphNode {
  id: string
  name: string
  entityType: PaperEntityType
  properties: Record<string, string | number | boolean>
}

export interface PaperGraphEdge {
  id: string
  source: string
  target: string
  relationType: PaperRelationType
  documentId: string
}

export interface PaperGraphVisualization {
  nodes: PaperGraphNode[]
  edges: PaperGraphEdge[]
  nodeLimit: 200
  edgeLimit: 400
}

export interface PaperNodeDetail extends PaperGraphNode {
  sourceDocuments: Array<{
    documentId: string
    filename: string
    version: number
  }>
}

export interface PaperEdgeEvidence {
  id: string
  sourceName: string
  targetName: string
  relationType: PaperRelationType
  documentId: string
  filename: string
  version: number
  chunkId: string
  pageNumber: number
  sectionName: string
  evidenceQuote: string
}

function requiredData<T>(data: T | null, operation: string): T {
  if (data === null) {
    throw new Error(`${operation}未返回数据`)
  }
  return data
}

export const paperGraphApi = {
  async createGraph(payload: { name: string; description?: string }): Promise<PaperGraph> {
    const data = await requestApiEnvelope<PaperGraph>('/api/paper-graphs', {
      method: 'POST',
      body: payload
    })
    return requiredData(data, '创建论文图谱')
  },

  async listGraphs(): Promise<PaperGraph[]> {
    const data = await requestApiEnvelope<PaperGraph[]>('/api/paper-graphs', {
      method: 'GET'
    })
    return requiredData(data, '查询论文图谱')
  },

  async getGraph(graphId: string): Promise<PaperGraph> {
    const data = await requestApiEnvelope<PaperGraph>(`/api/paper-graphs/${graphId}`, {
      method: 'GET'
    })
    return requiredData(data, '查询论文图谱详情')
  },

  async deleteGraph(graphId: string): Promise<void> {
    await requestApiEnvelope(`/api/paper-graphs/${graphId}`, { method: 'DELETE' })
  },

  async uploadDocument(graphId: string, file: File): Promise<PaperDocument> {
    const formData = new FormData()
    formData.append('file', file)
    const data = await requestMultipartApiEnvelope<PaperDocument>(
      `/api/paper-graphs/${graphId}/documents`,
      formData
    )
    return requiredData(data, '上传论文')
  },

  async listDocuments(graphId: string): Promise<PaperDocument[]> {
    const data = await requestApiEnvelope<PaperDocument[]>(
      `/api/paper-graphs/${graphId}/documents`,
      { method: 'GET' }
    )
    return requiredData(data, '查询论文列表')
  },

  async getDocumentStatus(documentId: string): Promise<PaperDocument> {
    const data = await requestApiEnvelope<PaperDocument>(
      `/api/paper-documents/${documentId}/status`,
      { method: 'GET' }
    )
    return requiredData(data, '查询构建状态')
  },

  async rebuildDocument(documentId: string): Promise<PaperDocument> {
    const data = await requestApiEnvelope<PaperDocument>(
      `/api/paper-documents/${documentId}/rebuild`,
      { method: 'POST' }
    )
    return requiredData(data, '重建论文图谱')
  },

  async deleteDocument(documentId: string): Promise<void> {
    await requestApiEnvelope(`/api/paper-documents/${documentId}`, { method: 'DELETE' })
  },

  downloadDocument(documentId: string): Promise<Blob> {
    return requestBlob(`/api/paper-documents/${documentId}/download`)
  },

  async visualization(
    graphId: string,
    filters: { documentId?: string; entityTypes?: PaperEntityType[]; query?: string }
  ): Promise<PaperGraphVisualization> {
    const params = new URLSearchParams()
    if (filters.documentId) {
      params.set('documentId', filters.documentId)
    }
    filters.entityTypes?.forEach((entityType) => params.append('entityType', entityType))
    if (filters.query?.trim()) {
      params.set('query', filters.query.trim())
    }
    const suffix = params.size ? `?${params.toString()}` : ''
    const data = await requestApiEnvelope<PaperGraphVisualization>(
      `/api/paper-graphs/${graphId}/visualization${suffix}`,
      { method: 'GET' }
    )
    return requiredData(data, '加载论文图谱')
  },

  async nodeDetail(graphId: string, nodeId: string): Promise<PaperNodeDetail> {
    const data = await requestApiEnvelope<PaperNodeDetail>(
      `/api/paper-graphs/${graphId}/nodes/${nodeId}`,
      { method: 'GET' }
    )
    return requiredData(data, '查询节点详情')
  },

  async neighbors(graphId: string, nodeId: string): Promise<PaperGraphVisualization> {
    const data = await requestApiEnvelope<PaperGraphVisualization>(
      `/api/paper-graphs/${graphId}/nodes/${nodeId}/neighbors`,
      { method: 'GET' }
    )
    return requiredData(data, '展开一跳邻居')
  },

  async edgeEvidence(graphId: string, edgeId: string): Promise<PaperEdgeEvidence> {
    const data = await requestApiEnvelope<PaperEdgeEvidence>(
      `/api/paper-graphs/${graphId}/edges/${edgeId}/evidence`,
      { method: 'GET' }
    )
    return requiredData(data, '查询关系证据')
  }
}
