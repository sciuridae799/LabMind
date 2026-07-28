import { chatApi, chatApiCatalogGroup, createConversationId } from './chat'
import { manageApi, manageApiCatalogGroup } from './manage'
import { paperGraphApi } from './paperGraph'

export { APIError } from './http'
export { chatApi, createConversationId, chatApiCatalogGroup }
export { manageApi, manageApiCatalogGroup }
export { paperGraphApi }

export const apiCatalogGroups = [chatApiCatalogGroup, manageApiCatalogGroup]

export type {
  ApiCatalogGroup,
  ApiCatalogItem,
  ApiEnvelope,
  ApiRecord,
  JsonObject,
  JsonValue,
  ManageObject,
  ManageValue,
  RequestOptions,
  StreamEventHandlers,
  StreamRequest
} from './types'
export type { ChatApi, SessionListPage, SessionListQuery } from './chat'
export type { ManageApi, UploadDocumentInput } from './manage'
export type {
  PaperDocument,
  PaperDocumentStatus,
  PaperEdgeEvidence,
  PaperEntityType,
  PaperGraph,
  PaperGraphEdge,
  PaperGraphNode,
  PaperGraphVisualization,
  PaperNodeDetail,
  PaperRelationType
} from './paperGraph'
