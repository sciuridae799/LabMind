export type ApiRecord = Record<string, unknown>

export interface ApiEnvelope<T> {
  code?: string | number | null
  message?: string | null
  error?: string | null
  data?: T | null
}

export interface RequestOptions<TBody = unknown> {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  headers?: Record<string, string>
  body?: TBody
  signal?: AbortSignal
}

export interface StreamEventHandlers<TEvent = ApiRecord> {
  onEvent?: (payload: TEvent) => void
}

export interface StreamRequest {
  controller: AbortController
  done: Promise<void>
}

export interface ApiCatalogItem {
  name: string
  summary: string
  requestMethod: string
  path: string
  keyInputs: string
}

export interface ApiCatalogGroup {
  title: string
  description: string
  items: ApiCatalogItem[]
}

export type JsonPrimitive = string | number | boolean | null
export type JsonValue = JsonPrimitive | JsonObject | JsonValue[]

export interface JsonObject {
  [key: string]: JsonValue | undefined
}

export type ManageScalar = string | number | bigint | boolean | null | undefined
export type ManageValue = ManageScalar | ManageObject | ManageValue[]

export interface ManageObject {
  [key: string]: ManageValue
}
