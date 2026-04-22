import { openEventStream, requestApiEnvelope } from './http'
import type { ApiCatalogGroup, ApiCatalogItem, JsonObject, StreamEventHandlers, StreamRequest } from './types'

type PageLike = string | number | bigint | null | undefined
type ExchangeId = string | number | bigint

export interface SessionListQuery {
  keyword?: string
  chatMode?: string
  turnStatus?: string
  pageNo?: PageLike
  pageSize?: PageLike
}

export interface SessionListPage {
  pageNo: string
  pageSize: string
  totalSize: string
  totalPages: string
  sessions: Record<string, unknown>[]
}

export interface ChatApi {
  /**
   * 查询聊天页可选择的知识文档项。
   * 这一步决定了提问时可挂载哪些文档上下文。
   */
  listKnowledgeDocumentOptions(): Promise<unknown>

  /**
   * 分页查询会话列表。
   * 业务筛选维度包括关键词、聊天模式和当前轮次状态。
   */
  listSessionsPage(query?: SessionListQuery): Promise<SessionListPage>

  /**
   * 查询单个会话详情。
   * 用于回显完整对话内容、会话元信息和当前上下文状态。
   */
  getSession(conversationId: string): Promise<unknown>

  /**
   * 查询单轮 exchange 详情。
   * 适合查看某一问答轮次的输入、输出和关联明细。
   */
  getExchangeDetail(conversationId: string, exchangeId: ExchangeId): Promise<unknown>

  /**
   * 重置会话。
   * 业务含义不是单纯删一条前端记录，而是收口运行、清理业务数据和 Graph checkpoint。
   */
  deleteSession(conversationId: string): Promise<unknown>

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

  /**
   * 打开对话流式接口。
   * payload 是本次提问入参，handlers.onEvent 用于逐条消费后端 SSE 事件。
   */
  openStream(payload: JsonObject, handlers?: StreamEventHandlers): StreamRequest
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
    summary: '重置会话并清理该会话关联的业务数据与 checkpoint。',
    requestMethod: 'POST',
    path: '/api/chat/session/reset',
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
    keyInputs: 'payload, handlers.onEvent'
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

function normalizePageString(value: PageLike, fallbackValue: string): string {
  const normalized = String(value ?? fallbackValue).trim()
  return normalized || fallbackValue
}

export const chatApi: ChatApi = {
  listKnowledgeDocumentOptions() {
    return requestApiEnvelope('/api/chat/document/options', {
      method: 'POST',
      body: {}
    })
  },

  listSessionsPage(query = {}) {
    return requestApiEnvelope<Partial<SessionListPage>, JsonObject>('/api/chat/session/list', {
      method: 'POST',
      body: {
        keyword: String(query.keyword || '').trim(),
        chatMode: String(query.chatMode || 'ALL').trim(),
        turnStatus: String(query.turnStatus || 'ALL').trim(),
        pageNo: normalizePageString(query.pageNo, '1'),
        pageSize: normalizePageString(query.pageSize, '20')
      }
    }).then((data) => ({
      pageNo: data?.pageNo || '1',
      pageSize: data?.pageSize || '20',
      totalSize: data?.totalSize || '0',
      totalPages: data?.totalPages || '0',
      sessions: data?.sessions || []
    }))
  },

  getSession(conversationId) {
    return requestApiEnvelope('/api/chat/session/detail', {
      method: 'POST',
      body: {
        conversationId
      }
    })
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
    return requestApiEnvelope('/api/chat/session/reset', {
      method: 'POST',
      body: {
        conversationId
      }
    })
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

  openStream(payload, handlers = {}) {
    return openEventStream('/api/chat/stream', payload, handlers)
  }
}
