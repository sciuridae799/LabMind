import { openEventStream, requestApiEnvelope } from './http'
import type { ApiCatalogGroup, ApiCatalogItem, JsonObject, StreamEventHandlers, StreamRequest } from './types'

type PageLike = string | number | bigint | null | undefined
type ExchangeId = string | number | bigint

export const businessChatModeOptions = [
  { value: 'CURRENT_DOCUMENT', label: '当前文档问答' },
  { value: 'KNOWLEDGE_BASE', label: '自动知识问答' },
  { value: 'OPEN_ENDED', label: '开放式提问' }
] as const

export type BusinessChatMode = (typeof businessChatModeOptions)[number]['value']

export const modelApiProviderOptions = [
  { value: 'DASHSCOPE', label: 'DASHSCOPE' },
  { value: 'DEEPSEEK', label: 'DeepSeek' }
] as const

export type ModelApiProvider = (typeof modelApiProviderOptions)[number]['value']

export const modelApiBaseUrlOptions: Record<ModelApiProvider, Array<{ value: string; label: string }>> = {
  DASHSCOPE: [
    { value: 'https://dashscope.aliyuncs.com/compatible-mode', label: '北京' },
    { value: 'https://dashscope-intl.aliyuncs.com/compatible-mode', label: '新加坡' },
    { value: 'https://dashscope-us.aliyuncs.com/compatible-mode', label: '弗吉尼亚' },
    { value: 'https://cn-hongkong.dashscope.aliyuncs.com/compatible-mode', label: '中国香港' }
  ],
  DEEPSEEK: [
    { value: 'https://api.deepseek.com', label: 'OpenAI compatible' }
  ]
}

export const modelApiModelOptions: Record<ModelApiProvider, Array<{ value: string; label: string }>> = {
  DASHSCOPE: [
    { value: 'qwen-plus', label: 'qwen-plus' },
    { value: 'qwen-plus-latest', label: 'qwen-plus-latest' },
    { value: 'qwen-turbo', label: 'qwen-turbo' },
    { value: 'qwen-turbo-latest', label: 'qwen-turbo-latest' },
    { value: 'qwen-max', label: 'qwen-max' },
    { value: 'qwen-max-latest', label: 'qwen-max-latest' },
    { value: 'qwq-32b', label: 'qwq-32b' },
    { value: 'qwen3-235b-a22b', label: 'qwen3-235b-a22b' },
    { value: 'qwen3-32b', label: 'qwen3-32b' }
  ],
  DEEPSEEK: [
    { value: 'deepseek-v4-flash', label: 'deepseek-v4-flash' },
    { value: 'deepseek-v4-pro', label: 'deepseek-v4-pro' },
    { value: 'deepseek-chat', label: 'deepseek-chat' },
    { value: 'deepseek-reasoner', label: 'deepseek-reasoner' }
  ]
}

export interface OpenChatStreamPayload extends JsonObject {
  question: string
  conversationId?: string
  chatMode: BusinessChatMode
  modelConfigId: string
  selectedDocumentId?: string
}

export interface KnowledgeDocumentOption {
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

export interface ModelApiConfig {
  id: string
  provider: ModelApiProvider
  displayName: string
  baseUrl: string
  modelName: string
  apiKeyConfigured: boolean
  enabled: boolean
}

export interface SaveModelApiConfigPayload extends JsonObject {
  id?: string
  provider: ModelApiProvider
  displayName: string
  baseUrl: string
  modelName: string
  apiKey?: string
  enabled: boolean
}

export interface BusinessChatStreamEvent {
  eventType?: string
  conversationId?: string
  exchangeId?: string | number | null
  chatMode?: string | null
  textDelta?: string | null
  functionSupplement?: string | null
  sourceSnapshotList?: string[] | null
  followUpSuggestionList?: string[] | null
  message?: string | null
  agentType?: string | null
  agentName?: string | null
  firstTokenLatencyMs?: string | number | null
  totalLatencyMs?: string | number | null
}

export interface SessionListQuery {
  keyword?: string
  chatMode?: string
  turnStatus?: string
  pageNo?: PageLike
  pageSize?: PageLike
}

export interface BusinessChatSessionListItem {
  conversationId: string
  title: string
  chatMode: BusinessChatMode
  turnStatus: string
  lastExchangeId: string | number | null
  lastQuestion: string
  lastReply: string
  updateTime: string
}

export interface SessionListPage {
  pageNo: number
  pageSize: number
  totalSize: number
  totalPages: number
  sessions: BusinessChatSessionListItem[]
}

export interface BusinessChatSessionExchange {
  exchangeId: string | number
  userPrompt: string
  replyContent: string
  sourceSnapshotList: string[]
  followUpSuggestionList: string[]
  toolTraceList: string[]
  exchangeState: string
  finishNote: string | null
  firstTokenLatencyMs: string | number | null
  totalLatencyMs: string | number | null
  createTime: string
}

export interface BusinessChatSessionDetail {
  conversationId: string
  title: string
  chatMode: BusinessChatMode
  dialogueStage: string
  selectedDocumentId: string | number | null
  selectedDocumentName: string | null
  summaryText: string | null
  summaryJson: unknown
  exchanges: BusinessChatSessionExchange[]
}

export interface ChatApi {
  /**
   * 查询聊天页可选择的知识文档项。
   * 这一步决定了提问时可挂载哪些文档上下文。
   */
  listKnowledgeDocumentOptions(): Promise<KnowledgeDocumentOption[]>

  /**
   * 分页查询会话列表。
   * 业务筛选维度包括关键词、聊天模式和当前轮次状态。
   */
  listSessionsPage(query: SessionListQuery): Promise<SessionListPage>

  /**
   * 查询单个会话详情。
   * 用于回显完整对话内容、会话元信息和当前上下文状态。
   */
  getSession(conversationId: string): Promise<BusinessChatSessionDetail>

  /**
   * 查询单轮 exchange 详情。
   * 适合查看某一问答轮次的输入、输出和关联明细。
   */
  getExchangeDetail(conversationId: string, exchangeId: ExchangeId): Promise<unknown>

  /**
   * 删除会话。
   * 业务含义不是单纯删一条前端记录，而是删除整条会话归档。
   */
  deleteSession(conversationId: string): Promise<void>

  /**
   * 停止当前会话正在运行的生成任务。
   * 只终止运行态，不清理会话本身。
   */
  stopSession(conversationId: string): Promise<unknown>

  /**
   * 手动触发会话长期摘要重建。
   * 适合演示、排查或需要立即刷新摘要的管理场景。
   */
  rebuildConversationSummary(conversationId: string): Promise<unknown>

  /**
   * 查询某一轮对话的检索结果。
   * 用于解释回答命中了哪些知识内容，以及召回链路是否正确。
   */
  getRetrievalResults(conversationId: string, exchangeId: ExchangeId): Promise<unknown>

  /**
   * 查询某一轮对话中各 channel 的执行明细。
   * 适合排查多通道执行顺序、耗时和结果分发。
   */
  getChannelExecutions(conversationId: string, exchangeId: ExchangeId): Promise<unknown>

  /**
   * 查询对话阶段基准数据。
   * 一般用于展示或分析不同阶段的耗时与统计指标。
   */
  getStageBenchmarks(): Promise<unknown>

  listModelConfigs(): Promise<ModelApiConfig[]>

  listAvailableModelConfigs(): Promise<ModelApiConfig[]>

  saveModelConfig(payload: SaveModelApiConfigPayload): Promise<ModelApiConfig>

  deleteModelConfig(id: string): Promise<void>

  clearModelConfigApiKey(id: string): Promise<void>

  moveModelConfig(id: string, direction: 'UP' | 'DOWN'): Promise<void>

  /**
   * 打开对话流式接口。
   * payload 是本次提问入参，conversationId 可省略；省略时以后端 SSE 返回的 conversationId 为准。
   */
  openStream(
    payload: OpenChatStreamPayload,
    handlers?: StreamEventHandlers<BusinessChatStreamEvent>
  ): StreamRequest
}

const chatApiCatalogDefinitions = {
  listKnowledgeDocumentOptions: {
    summary: '查询聊天页提问时可挂载的知识文档候选项。',
    requestMethod: 'POST',
    path: '/api/chat/document/options',
    keyInputs: '无'
  },
  listSessionsPage: {
    summary: '按关键词、聊天模式和轮次状态分页查询会话列表。',
    requestMethod: 'POST',
    path: '/api/chat/session/list',
    keyInputs: 'keyword, chatMode, turnStatus, pageNo, pageSize'
  },
  getSession: {
    summary: '查询单个会话详情，回显完整对话内容和会话元信息。',
    requestMethod: 'POST',
    path: '/api/chat/session/detail',
    keyInputs: 'conversationId'
  },
  getExchangeDetail: {
    summary: '查询单轮 exchange 的输入、输出和关联明细。',
    requestMethod: 'POST',
    path: '/api/chat/exchange/detail',
    keyInputs: 'conversationId, exchangeId'
  },
  deleteSession: {
    summary: '删除整条会话归档及其关联数据。',
    requestMethod: 'POST',
    path: '/api/chat/session/delete',
    keyInputs: 'conversationId'
  },
  stopSession: {
    summary: '停止当前会话正在运行的生成任务，不删除会话本身。',
    requestMethod: 'POST',
    path: '/api/chat/session/stop',
    keyInputs: 'conversationId'
  },
  rebuildConversationSummary: {
    summary: '手动触发会话长期摘要重建，刷新摘要结果。',
    requestMethod: 'POST',
    path: '/api/chat/session/summary/rebuild',
    keyInputs: 'conversationId'
  },
  getRetrievalResults: {
    summary: '查询某轮对话命中的知识检索结果。',
    requestMethod: 'POST',
    path: '/api/chat/exchange/retrieval/results',
    keyInputs: 'conversationId, exchangeId'
  },
  getChannelExecutions: {
    summary: '查询某轮对话各 channel 的执行顺序、耗时和结果分发。',
    requestMethod: 'POST',
    path: '/api/chat/exchange/channel/executions',
    keyInputs: 'conversationId, exchangeId'
  },
  getStageBenchmarks: {
    summary: '查询对话阶段的耗时与统计基准数据。',
    requestMethod: 'POST',
    path: '/api/chat/stage/benchmarks',
    keyInputs: '无'
  },
  openStream: {
    summary: '发起对话生成并持续接收服务端 SSE 事件流。',
    requestMethod: 'POST · SSE',
    path: '/api/chat/stream',
    keyInputs: 'question, chatMode, modelConfigId, conversationId(可选), handlers.onEvent'
  },
  listModelConfigs: {
    summary: '查询全部模型 API 配置，API Key 只返回是否已配置。',
    requestMethod: 'POST',
    path: '/api/chat/model-config/list',
    keyInputs: '无'
  },
  listAvailableModelConfigs: {
    summary: '查询聊天可选择的模型 API 配置。',
    requestMethod: 'POST',
    path: '/api/chat/model-config/available',
    keyInputs: '无'
  },
  saveModelConfig: {
    summary: '新增或更新模型 API 配置。',
    requestMethod: 'POST',
    path: '/api/chat/model-config/save',
    keyInputs: 'id(可选), provider, displayName, baseUrl, modelName, apiKey(可选), enabled'
  },
  deleteModelConfig: {
    summary: '删除模型 API 配置。',
    requestMethod: 'POST',
    path: '/api/chat/model-config/delete',
    keyInputs: 'id'
  },
  clearModelConfigApiKey: {
    summary: '清除模型 API 配置中的 API Key。',
    requestMethod: 'POST',
    path: '/api/chat/model-config/clear-api-key',
    keyInputs: 'id'
  },
  moveModelConfig: {
    summary: '调整模型 API 配置顺序，列表最上方的可用配置作为默认模型。',
    requestMethod: 'POST',
    path: '/api/chat/model-config/move',
    keyInputs: 'id, direction'
  }
} satisfies Record<keyof ChatApi, Omit<ApiCatalogItem, 'name'>>

export const chatApiCatalogGroup: ApiCatalogGroup = {
  title: 'chatApi',
  description: '对话主链路、会话生命周期、交换详情与流式生成',
  items: (Object.entries(chatApiCatalogDefinitions) as Array<
    [keyof ChatApi & string, Omit<ApiCatalogItem, 'name'>]
  >).map(([name, item]) => ({
    name,
    ...item
  }))
}

export function createConversationId(): string {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`
}

function normalizeRequiredText(value: unknown, fieldName: string): string {
  const normalized = String(value ?? '').trim()
  if (!normalized) {
    throw new Error(`${fieldName} is required`)
  }
  return normalized
}

function requireChatApiPayload<T>(data: T | null, errorMessage: string): T {
  if (data == null) {
    throw new Error(errorMessage)
  }
  return data
}

export const chatApi: ChatApi = {
  listKnowledgeDocumentOptions() {
    return requestApiEnvelope<KnowledgeDocumentOption[]>('/api/chat/document/options', {
      method: 'POST',
      body: {}
    }).then((data) => requireChatApiPayload(data, '文档选项响应为空'))
  },

  listSessionsPage(query) {
    return requestApiEnvelope<SessionListPage, JsonObject>('/api/chat/session/list', {
      method: 'POST',
      body: {
        keyword: String(query.keyword ?? '').trim(),
        chatMode: normalizeRequiredText(query.chatMode, 'chatMode'),
        turnStatus: normalizeRequiredText(query.turnStatus, 'turnStatus'),
        pageNo: normalizeRequiredText(query.pageNo, 'pageNo'),
        pageSize: normalizeRequiredText(query.pageSize, 'pageSize')
      }
    }).then((data) => requireChatApiPayload(data, '会话列表响应为空'))
  },

  getSession(conversationId) {
    return requestApiEnvelope<BusinessChatSessionDetail, JsonObject>('/api/chat/session/detail', {
      method: 'POST',
      body: {
        conversationId
      }
    }).then((data) => requireChatApiPayload(data, '会话详情响应为空'))
  },

  getExchangeDetail(conversationId, exchangeId) {
    return requestApiEnvelope('/api/chat/exchange/detail', {
      method: 'POST',
      body: {
        conversationId,
        exchangeId: String(exchangeId)
      }
    })
  },

  deleteSession(conversationId) {
    return requestApiEnvelope<void, JsonObject>('/api/chat/session/delete', {
      method: 'POST',
      body: {
        conversationId
      }
    }).then(() => undefined)
  },

  stopSession(conversationId) {
    return requestApiEnvelope('/api/chat/session/stop', {
      method: 'POST',
      body: {
        conversationId
      }
    })
  },

  rebuildConversationSummary(conversationId) {
    return requestApiEnvelope('/api/chat/session/summary/rebuild', {
      method: 'POST',
      body: {
        conversationId
      }
    })
  },

  getRetrievalResults(conversationId, exchangeId) {
    return requestApiEnvelope('/api/chat/exchange/retrieval/results', {
      method: 'POST',
      body: {
        conversationId,
        exchangeId: String(exchangeId)
      }
    })
  },

  getChannelExecutions(conversationId, exchangeId) {
    return requestApiEnvelope('/api/chat/exchange/channel/executions', {
      method: 'POST',
      body: {
        conversationId,
        exchangeId: String(exchangeId)
      }
    })
  },

  getStageBenchmarks() {
    return requestApiEnvelope('/api/chat/stage/benchmarks', {
      method: 'POST',
      body: {}
    })
  },

  listModelConfigs() {
    return requestApiEnvelope<ModelApiConfig[], JsonObject>('/api/chat/model-config/list', {
      method: 'POST',
      body: {}
    }).then((data) => data ?? [])
  },

  listAvailableModelConfigs() {
    return requestApiEnvelope<ModelApiConfig[], JsonObject>('/api/chat/model-config/available', {
      method: 'POST',
      body: {}
    }).then((data) => data ?? [])
  },

  saveModelConfig(payload) {
    return requestApiEnvelope<ModelApiConfig, JsonObject>('/api/chat/model-config/save', {
      method: 'POST',
      body: payload
    }).then((data) => requireChatApiPayload(data, '模型配置保存响应为空'))
  },

  deleteModelConfig(id) {
    return requestApiEnvelope<void, JsonObject>('/api/chat/model-config/delete', {
      method: 'POST',
      body: { id }
    }).then(() => undefined)
  },

  clearModelConfigApiKey(id) {
    return requestApiEnvelope<void, JsonObject>('/api/chat/model-config/clear-api-key', {
      method: 'POST',
      body: { id }
    }).then(() => undefined)
  },

  moveModelConfig(id, direction) {
    return requestApiEnvelope<void, JsonObject>('/api/chat/model-config/move', {
      method: 'POST',
      body: { id, direction }
    }).then(() => undefined)
  },

  openStream(payload, handlers = {}) {
    return openEventStream('/api/chat/stream', payload, handlers)
  }
}
