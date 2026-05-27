import { requestApiEnvelope, requestMultipartApiEnvelope } from './http'
import type { ApiCatalogGroup, ApiCatalogItem, ManageObject, ManageValue } from './types'

type DocumentId = string | number | bigint

export interface UploadDocumentInput {
  file: File
  workspaceId?: string
  documentName?: string
  operatorId?: string | number | bigint | null
  knowledgeScopeCode?: string
  knowledgeScopeName?: string
  knowledgeTopicCode?: string
  knowledgeTopicName?: string
  businessCategory?: string
  documentTags?: string
}

export interface ManageApi {
  /**
   * 上传知识文档。
   * 文件和业务元数据一起提交，后端据此归属知识范围、业务分类和标签。
   */
  uploadDocument(payload: UploadDocumentInput): Promise<unknown>

  /**
   * 分页查询知识文档。
   * 用于文档管理主列表和条件筛选结果页。
   */
  queryDocumentPage(payload: ManageObject): Promise<unknown>

  /**
   * 查询单个知识文档详情。
   * 用于查看文档基础信息、处理状态和业务归属。
   */
  queryDocumentDetail(documentId: DocumentId): Promise<unknown>

  /**
   * 删除知识文档。
   * 会影响文档本体以及后续依赖它的切片、索引和关联关系。
   */
  deleteDocument(payload: ManageObject): Promise<unknown>

  /**
   * 查询文档切分/索引前的策略规划。
   * 用于在真正执行前确认处理方案是否符合业务预期。
   */
  queryStrategyPlan(documentId: DocumentId): Promise<unknown>

  /**
   * 确认文档处理策略。
   * 这是从“查看方案”进入“按该方案执行”的确认动作。
   */
  confirmStrategy(payload: ManageObject): Promise<unknown>

  /**
   * 触发文档索引构建。
   * 这一步把文档内容正式写入检索链路可用的索引体系。
   */
  buildIndex(payload: ManageObject): Promise<unknown>

  /**
   * 查询文档切片列表。
   * 用于核对切分效果、片段顺序和内容边界。
   */
  queryDocumentChunks(payload: ManageObject): Promise<unknown>

  /**
   * 查询单个切片详情。
   * 适合查看具体分片内容及其处理结果。
   */
  queryDocumentChunkDetail(payload: ManageObject): Promise<unknown>

  /**
   * 查询文档任务日志。
   * 用于排查上传、切分、索引等后台任务的执行过程。
   */
  queryTaskLogs(payload: ManageObject): Promise<unknown>

  /**
   * 新增或更新知识范围。
   * 知识范围是文档与专题归属的上层业务边界。
   */
  saveKnowledgeScope(payload: ManageObject): Promise<unknown>

  /**
   * 删除知识范围。
   * 删除前需要确认下游文档和专题是否已经完成迁移或清理。
   */
  deleteKnowledgeScope(payload: ManageObject): Promise<unknown>

  /**
   * 查询全部知识范围。
   * 常用于范围下拉框、映射关系维护和管理总览。
   */
  listKnowledgeScopes(): Promise<unknown>

  /**
   * 新增或更新知识专题。
   * 专题用于把同一业务主题下的文档做二级组织。
   */
  saveKnowledgeTopic(payload: ManageObject): Promise<unknown>

  /**
   * 删除知识专题。
   * 会直接影响专题下的文档归属关系。
   */
  deleteKnowledgeTopic(payload: ManageObject): Promise<unknown>

  /**
   * 查询知识专题列表。
   * 可按知识范围等条件筛选，用于专题管理和选择器回显。
   */
  listKnowledgeTopics(payload?: ManageObject): Promise<unknown>

  /**
   * 查询文档画像详情。
   * 文档画像是知识路由、推荐和解释展示的重要业务摘要。
   */
  queryDocumentProfile(payload: ManageObject): Promise<unknown>

  /**
   * 查询文档解析后的纯文本。
   * 当前文档问答会把这份正文与画像一起作为模型上下文。
   */
  queryDocumentParsedText(payload: ManageObject): Promise<unknown>

  /**
   * 重新生成单个文档画像。
   * 当文档内容或画像策略变化后，需要用它刷新画像结果。
   */
  regenerateDocumentProfile(payload: ManageObject): Promise<unknown>

  /**
   * 批量重建文档画像。
   * 适合规则变更后做一次集中修正。
   */
  batchRegenerateDocumentProfiles(payload: ManageObject): Promise<unknown>

  /**
   * 查询专题下挂载的文档列表。
   * 用于维护专题和文档之间的业务关联关系。
   */
  listTopicDocuments(payload?: ManageObject): Promise<unknown>

  /**
   * 保存专题和文档的关联关系。
   * 这是把某篇文档正式挂到某个业务专题下的入口。
   */
  saveTopicDocumentRelation(payload: ManageObject): Promise<unknown>

  /**
   * 删除专题和文档的关联关系。
   * 只解除专题挂载，不代表删除文档本体。
   */
  removeTopicDocumentRelation(payload: ManageObject): Promise<unknown>

  /**
   * 分页查询知识路由追踪记录。
   * 用于回放问题命中了哪些范围、专题和文档，定位路由是否正确。
   */
  queryKnowledgeRouteTracePage(payload?: ManageObject): Promise<unknown>

  /**
   * 分页查询可路由知识资产。
   * 只有解析成功且已生成画像的文档会出现在这条管理链路里。
   */
  queryKnowledgeRouteAssetPage(payload?: ManageObject): Promise<unknown>

  /**
   * 预览知识路由候选。
   * 用于在不进入正式问答前验证问题会命中哪些知识域、专题和文档。
   */
  previewKnowledgeRoute(payload: ManageObject): Promise<unknown>
}

const manageApiCatalogDefinitions = {
  uploadDocument: {
    summary: '上传知识文档，并一并提交文档名称、范围、分类和标签元数据。',
    requestMethod: 'POST · multipart',
    path: '/manage/document/upload',
    keyInputs: 'file, documentName, operatorId, knowledgeScopeCode'
  },
  queryDocumentPage: {
    summary: '分页查询知识文档列表，用于文档管理主列表和筛选结果页。',
    requestMethod: 'POST',
    path: '/manage/document/page/query',
    keyInputs: 'payload（分页与筛选条件）'
  },
  queryDocumentDetail: {
    summary: '查询单个知识文档详情，包括基础信息和处理状态。',
    requestMethod: 'POST',
    path: '/manage/document/detail/query',
    keyInputs: 'documentId'
  },
  deleteDocument: {
    summary: '删除知识文档，并影响其后续切片、索引和关联关系。',
    requestMethod: 'POST',
    path: '/manage/document/delete',
    keyInputs: 'payload（documentId 等删除条件）'
  },
  queryStrategyPlan: {
    summary: '查询文档切分和索引前的策略规划结果。',
    requestMethod: 'POST',
    path: '/manage/document/strategy/plan/query',
    keyInputs: 'documentId'
  },
  confirmStrategy: {
    summary: '确认文档处理策略，正式按选定方案执行。',
    requestMethod: 'POST',
    path: '/manage/document/strategy/confirm',
    keyInputs: 'payload（documentId, strategyConfig）'
  },
  buildIndex: {
    summary: '触发文档索引构建，把文档内容写入检索链路。',
    requestMethod: 'POST',
    path: '/manage/document/index/build',
    keyInputs: 'payload（documentId 等索引参数）'
  },
  queryDocumentChunks: {
    summary: '查询文档切片列表，用于核对切分结果和内容边界。',
    requestMethod: 'POST',
    path: '/manage/document/chunk/query',
    keyInputs: 'payload（documentId, pageNo, pageSize）'
  },
  queryDocumentChunkDetail: {
    summary: '查询单个文档切片详情，查看具体片段内容和处理结果。',
    requestMethod: 'POST',
    path: '/manage/document/chunk/detail/query',
    keyInputs: 'payload（chunkId 或文档切片定位参数）'
  },
  queryTaskLogs: {
    summary: '查询上传、切分、索引等文档任务日志。',
    requestMethod: 'POST',
    path: '/manage/document/task/log/query',
    keyInputs: 'payload（documentId, taskId, pageNo, pageSize）'
  },
  saveKnowledgeScope: {
    summary: '新增或更新知识范围，维护文档与专题的上层业务边界。',
    requestMethod: 'POST',
    path: '/manage/knowledge/scope/save',
    keyInputs: 'payload（scopeCode, scopeName 等范围信息）'
  },
  deleteKnowledgeScope: {
    summary: '删除知识范围，影响该范围下游文档和专题归属。',
    requestMethod: 'POST',
    path: '/manage/knowledge/scope/delete',
    keyInputs: 'payload（scopeCode 或 scopeId）'
  },
  listKnowledgeScopes: {
    summary: '查询全部知识范围，用于下拉框和管理总览。',
    requestMethod: 'POST',
    path: '/manage/knowledge/scope/list',
    keyInputs: '无'
  },
  saveKnowledgeTopic: {
    summary: '新增或更新知识专题，维护业务主题分组。',
    requestMethod: 'POST',
    path: '/manage/knowledge/topic/save',
    keyInputs: 'payload（topicCode, topicName, knowledgeScopeCode）'
  },
  deleteKnowledgeTopic: {
    summary: '删除知识专题，直接影响专题下文档归属关系。',
    requestMethod: 'POST',
    path: '/manage/knowledge/topic/delete',
    keyInputs: 'payload（topicCode 或 topicId）'
  },
  listKnowledgeTopics: {
    summary: '查询知识专题列表，可按知识范围等条件筛选。',
    requestMethod: 'POST',
    path: '/manage/knowledge/topic/list',
    keyInputs: 'payload（knowledgeScopeCode 等筛选条件）'
  },
  queryDocumentProfile: {
    summary: '查询文档画像详情，用于知识路由、推荐和解释展示。',
    requestMethod: 'POST',
    path: '/manage/knowledge/document/profile/detail',
    keyInputs: 'payload（documentId）'
  },
  queryDocumentParsedText: {
    summary: '查询文档解析正文，用于浏览当前可问答全文。',
    requestMethod: 'POST',
    path: '/manage/knowledge/document/parsed-text/query',
    keyInputs: 'payload（documentId）'
  },
  regenerateDocumentProfile: {
    summary: '重新生成单个文档画像，刷新画像结果。',
    requestMethod: 'POST',
    path: '/manage/knowledge/document/profile/regenerate',
    keyInputs: 'payload（documentId）'
  },
  batchRegenerateDocumentProfiles: {
    summary: '批量重建文档画像，适合规则变更后的集中修正。',
    requestMethod: 'POST',
    path: '/manage/knowledge/document/profile/batch/regenerate',
    keyInputs: 'payload（documentIds 或批量筛选条件）'
  },
  listTopicDocuments: {
    summary: '查询专题下挂载的文档列表，查看专题与文档关联关系。',
    requestMethod: 'POST',
    path: '/manage/knowledge/topic/document/list',
    keyInputs: 'payload（topicCode 或 topicId）'
  },
  saveTopicDocumentRelation: {
    summary: '保存专题和文档的关联关系，把文档挂到业务专题下。',
    requestMethod: 'POST',
    path: '/manage/knowledge/topic/document/save',
    keyInputs: 'payload（topicId, documentIds）'
  },
  removeTopicDocumentRelation: {
    summary: '删除专题和文档的关联关系，不删除文档本体。',
    requestMethod: 'POST',
    path: '/manage/knowledge/topic/document/remove',
    keyInputs: 'payload（topicId, documentIds）'
  },
  queryKnowledgeRouteTracePage: {
    summary: '分页查询知识路由追踪记录，回放范围、专题和文档命中结果。',
    requestMethod: 'POST',
    path: '/manage/knowledge/route/trace/page/query',
    keyInputs: 'payload（分页与路由筛选条件）'
  },
  queryKnowledgeRouteAssetPage: {
    summary: '分页查询可参与知识路由的文档资产。',
    requestMethod: 'POST',
    path: '/manage/knowledge/route/asset/page/query',
    keyInputs: 'payload（keyword, knowledgeScopeCode, pageNo, pageSize）'
  },
  previewKnowledgeRoute: {
    summary: '预览知识路由候选，用于验证问题会命中哪些文档。',
    requestMethod: 'POST',
    path: '/manage/knowledge/route/preview',
    keyInputs: 'payload（question, limit）'
  }
} satisfies Record<keyof ManageApi, Omit<ApiCatalogItem, 'name'>>

export const manageApiCatalogGroup: ApiCatalogGroup = {
  title: 'manageApi',
  description: '知识文档、分片、任务日志、知识范围、专题、画像与路由追踪',
  items: (Object.entries(manageApiCatalogDefinitions) as Array<
    [keyof ManageApi & string, Omit<ApiCatalogItem, 'name'>]
  >).map(([name, item]) => ({
    name,
    ...item
  }))
}

function stringifyManageValue(value: ManageValue): ManageValue {
  if (Array.isArray(value)) {
    return value.map((item) => stringifyManageValue(item))
  }

  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, stringifyManageValue(item as ManageValue)])
    ) as ManageObject
  }

  if (typeof value === 'number' || typeof value === 'bigint') {
    return String(value)
  }

  return value
}

function stringifyManagePayload(payload: ManageObject): ManageObject {
  return stringifyManageValue(payload) as ManageObject
}

export const manageApi: ManageApi = {
  uploadDocument({
    file,
    workspaceId,
    documentName,
    operatorId,
    knowledgeScopeCode,
    knowledgeScopeName,
    knowledgeTopicCode,
    knowledgeTopicName,
    businessCategory,
    documentTags
  }) {
    const formData = new FormData()
    formData.append('file', file)

    const meta = stringifyManagePayload({
      documentName: documentName || '',
      workspaceId: workspaceId || '',
      operatorId: operatorId ?? '',
      knowledgeScopeCode: knowledgeScopeCode || '',
      knowledgeScopeName: knowledgeScopeName || '',
      knowledgeTopicCode: knowledgeTopicCode || '',
      knowledgeTopicName: knowledgeTopicName || '',
      businessCategory: businessCategory || '',
      documentTags: documentTags || ''
    })
    formData.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }))

    return requestMultipartApiEnvelope('/manage/document/upload', formData)
  },

  queryDocumentPage(payload) {
    return requestApiEnvelope('/manage/document/page/query', {
      body: stringifyManagePayload(payload)
    })
  },

  queryDocumentDetail(documentId) {
    return requestApiEnvelope('/manage/document/detail/query', {
      body: stringifyManagePayload({
        documentId
      })
    })
  },

  deleteDocument(payload) {
    return requestApiEnvelope('/manage/document/delete', {
      body: stringifyManagePayload(payload)
    })
  },

  queryStrategyPlan(documentId) {
    return requestApiEnvelope('/manage/document/strategy/plan/query', {
      body: stringifyManagePayload({
        documentId
      })
    })
  },

  confirmStrategy(payload) {
    return requestApiEnvelope('/manage/document/strategy/confirm', {
      body: stringifyManagePayload(payload)
    })
  },

  buildIndex(payload) {
    return requestApiEnvelope('/manage/document/index/build', {
      body: stringifyManagePayload(payload)
    })
  },

  queryDocumentChunks(payload) {
    return requestApiEnvelope('/manage/document/chunk/query', {
      body: stringifyManagePayload(payload)
    })
  },

  queryDocumentChunkDetail(payload) {
    return requestApiEnvelope('/manage/document/chunk/detail/query', {
      body: stringifyManagePayload(payload)
    })
  },

  queryTaskLogs(payload) {
    return requestApiEnvelope('/manage/document/task/log/query', {
      body: stringifyManagePayload(payload)
    })
  },

  saveKnowledgeScope(payload) {
    return requestApiEnvelope('/manage/knowledge/scope/save', {
      body: stringifyManagePayload(payload)
    })
  },

  deleteKnowledgeScope(payload) {
    return requestApiEnvelope('/manage/knowledge/scope/delete', {
      body: stringifyManagePayload(payload)
    })
  },

  listKnowledgeScopes() {
    return requestApiEnvelope('/manage/knowledge/scope/list', {
      body: {}
    })
  },

  saveKnowledgeTopic(payload) {
    return requestApiEnvelope('/manage/knowledge/topic/save', {
      body: stringifyManagePayload(payload)
    })
  },

  deleteKnowledgeTopic(payload) {
    return requestApiEnvelope('/manage/knowledge/topic/delete', {
      body: stringifyManagePayload(payload)
    })
  },

  listKnowledgeTopics(payload = {}) {
    return requestApiEnvelope('/manage/knowledge/topic/list', {
      body: stringifyManagePayload(payload)
    })
  },

  queryDocumentProfile(payload) {
    return requestApiEnvelope('/manage/knowledge/document/profile/detail', {
      body: stringifyManagePayload(payload)
    })
  },

  queryDocumentParsedText(payload) {
    return requestApiEnvelope('/manage/knowledge/document/parsed-text/query', {
      body: stringifyManagePayload(payload)
    })
  },

  regenerateDocumentProfile(payload) {
    return requestApiEnvelope('/manage/knowledge/document/profile/regenerate', {
      body: stringifyManagePayload(payload)
    })
  },

  batchRegenerateDocumentProfiles(payload) {
    return requestApiEnvelope('/manage/knowledge/document/profile/batch/regenerate', {
      body: stringifyManagePayload(payload)
    })
  },

  listTopicDocuments(payload = {}) {
    return requestApiEnvelope('/manage/knowledge/topic/document/list', {
      body: stringifyManagePayload(payload)
    })
  },

  saveTopicDocumentRelation(payload) {
    return requestApiEnvelope('/manage/knowledge/topic/document/save', {
      body: stringifyManagePayload(payload)
    })
  },

  removeTopicDocumentRelation(payload) {
    return requestApiEnvelope('/manage/knowledge/topic/document/remove', {
      body: stringifyManagePayload(payload)
    })
  },

  queryKnowledgeRouteTracePage(payload = {}) {
    return requestApiEnvelope('/manage/knowledge/route/trace/page/query', {
      body: stringifyManagePayload(payload)
    })
  },

  queryKnowledgeRouteAssetPage(payload = {}) {
    return requestApiEnvelope('/manage/knowledge/route/asset/page/query', {
      body: stringifyManagePayload(payload)
    })
  },

  previewKnowledgeRoute(payload) {
    return requestApiEnvelope('/manage/knowledge/route/preview', {
      body: stringifyManagePayload(payload)
    })
  }
}
