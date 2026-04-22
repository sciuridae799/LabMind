import { chatApi, chatApiCatalogGroup, createConversationId } from './chat'
import { manageApi, manageApiCatalogGroup } from './manage'

export { APIError } from './http'
export { chatApi, createConversationId, chatApiCatalogGroup }
export { manageApi, manageApiCatalogGroup }

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
