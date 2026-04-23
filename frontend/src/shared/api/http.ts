import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import type {
  ApiEnvelope,
  ApiRecord,
  JsonObject,
  RequestOptions,
  StreamEventHandlers,
  StreamRequest
} from './types'

const API_BASE_URL = String(import.meta.env.VITE_API_BASE_URL || '').trim()
const REQUEST_TIMEOUT = 30000

export class APIError extends Error {
  status: number
  declare cause: unknown

  constructor(message: string, status: number, cause?: unknown) {
    super(message)
    this.name = 'APIError'
    this.status = status
    this.cause = cause
  }
}

const rawTextTransform = [(data: unknown): unknown => data]

const jsonClient = axios.create({
  baseURL: API_BASE_URL || undefined,
  timeout: REQUEST_TIMEOUT,
  responseType: 'text',
  transformResponse: rawTextTransform,
  headers: {
    'Content-Type': 'application/json'
  }
})

const multipartClient = axios.create({
  baseURL: API_BASE_URL || undefined,
  timeout: REQUEST_TIMEOUT,
  responseType: 'text',
  transformResponse: rawTextTransform
})

const streamClient = axios.create({
  baseURL: API_BASE_URL || undefined,
  timeout: 0,
  adapter: 'fetch',
  responseType: 'stream',
  headers: {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream'
  }
})

function normalizeRawText(rawData: unknown): string {
  if (typeof rawData === 'string') {
    return rawData
  }

  if (rawData == null) {
    return ''
  }

  return JSON.stringify(rawData)
}

function parseJsonPayload<T>(rawData: unknown, status: number): T | null {
  if (rawData == null || rawData === '') {
    return null
  }

  if (typeof rawData !== 'string') {
    return rawData as T
  }

  try {
    return JSON.parse(rawData) as T
  } catch (error) {
    throw new APIError(`无法解析后端响应: ${rawData}`, status, error)
  }
}

function readResponseMessage(rawData: unknown, status: number): string {
  const rawText = normalizeRawText(rawData)
  if (!rawText) {
    return `请求失败，状态码 ${status}`
  }

  try {
    const payload = JSON.parse(rawText) as ApiEnvelope<unknown>
    return payload.message || payload.error || rawText
  } catch {
    return rawText
  }
}

function normalizeAxiosError(error: unknown): APIError {
  if (error instanceof APIError) {
    return error
  }

  if (axios.isCancel(error)) {
    return new APIError('请求已取消', 499, error)
  }

  if (axios.isAxiosError(error) && error.response) {
    return new APIError(
      readResponseMessage(error.response.data, error.response.status),
      error.response.status,
      error
    )
  }

  if (axios.isAxiosError(error) && error.code === 'ECONNABORTED') {
    return new APIError(`请求超时，超过 ${REQUEST_TIMEOUT}ms`, 408, error)
  }

  if (error instanceof Error) {
    return new APIError(error.message || '网络请求失败', 500, error)
  }

  return new APIError('网络请求失败', 500, error)
}

async function requestWithClient<TResponse, TBody = unknown>(
  client: AxiosInstance,
  path: string,
  options: RequestOptions<TBody>
): Promise<AxiosResponse<TResponse>> {
  try {
    return await client.request<TResponse>({
      url: path,
      method: options.method,
      headers: options.headers,
      data: options.body,
      signal: options.signal
    })
  } catch (error) {
    throw normalizeAxiosError(error)
  }
}

async function requestJson<T, TBody = unknown>(
  path: string,
  options: RequestOptions<TBody> = {}
): Promise<T | null> {
  const response = await requestWithClient<unknown, TBody>(jsonClient, path, {
    method: options.method || 'GET',
    headers: options.headers,
    body: options.body,
    signal: options.signal
  })

  if (response.status === 204) {
    return null
  }

  return parseJsonPayload<T>(response.data, response.status)
}

function unwrapApiResponse<T>(
  payload: ApiEnvelope<T> | null,
  fallbackMessage = '请求失败'
): T | null {
  const code = String(payload?.code ?? '')
  if (code !== '0') {
    throw new APIError(payload?.message || fallbackMessage, Number(payload?.code || 500), payload)
  }
  return payload?.data ?? null
}

export async function requestApiEnvelope<T, TBody = unknown>(
  path: string,
  options: RequestOptions<TBody> = {}
): Promise<T | null> {
  const payload = await requestJson<ApiEnvelope<T>, TBody>(path, {
    method: options.method || 'POST',
    headers: options.headers,
    body: options.body,
    signal: options.signal
  })

  return unwrapApiResponse(payload)
}

export async function requestMultipartApiEnvelope<T>(
  path: string,
  formData: FormData,
  options: Omit<RequestOptions<never>, 'body'> = {}
): Promise<T | null> {
  const response = await requestWithClient<unknown, FormData>(multipartClient, path, {
    method: options.method || 'POST',
    headers: options.headers,
    body: formData,
    signal: options.signal
  })

  const payload = parseJsonPayload<ApiEnvelope<T>>(response.data, response.status)
  return unwrapApiResponse(payload)
}

function dispatchStreamPayload<TEvent extends ApiRecord>(
  rawPayload: string,
  handlers: StreamEventHandlers<TEvent>
): void {
  if (!rawPayload) {
    return
  }

  const payload = rawPayload.trim()
  if (!payload || payload === '[DONE]') {
    return
  }

  try {
    handlers.onEvent?.(JSON.parse(payload) as TEvent)
  } catch (error) {
    throw new APIError(`无法解析后端流式事件: ${payload}`, 500, error)
  }
}

function readSseDataPayload(block: string): string {
  // SSE 一个事件块里可能有多行 data，这里只抽取业务 payload，忽略 event/id/comment 等控制字段。
  const dataLineList: string[] = []

  block.split(/\r?\n/).forEach((line) => {
    if (!line || line.startsWith(':')) {
      return
    }

    const separatorIndex = line.indexOf(':')
    const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex)
    const rawValue = separatorIndex === -1 ? '' : line.slice(separatorIndex + 1)
    const value = rawValue.startsWith(' ') ? rawValue.slice(1) : rawValue

    if (field === 'data') {
      dataLineList.push(value)
    }
  })

  return dataLineList.join('\n')
}

function consumeEventBlock<TEvent extends ApiRecord>(
  block: string,
  handlers: StreamEventHandlers<TEvent>
): void {
  const normalizedBlock = block.trim()
  if (!normalizedBlock) {
    return
  }

  const payload = readSseDataPayload(normalizedBlock)
  if (!payload) {
    return
  }

  dispatchStreamPayload(payload, handlers)
}

async function consumeEventStream<TEvent extends ApiRecord>(
  stream: ReadableStream<Uint8Array>,
  handlers: StreamEventHandlers<TEvent>
): Promise<void> {
  const reader = stream.getReader()
  const decoder = new TextDecoder('utf-8')
  // 网络分片不一定刚好落在 SSE 边界上，buffer 用来拼齐 "\n\n" 分隔出的完整事件块。
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })

    let boundaryIndex = buffer.search(/\r?\n\r?\n/)
    while (boundaryIndex !== -1) {
      const block = buffer.slice(0, boundaryIndex)
      const separatorMatch = buffer.slice(boundaryIndex).match(/^\r?\n\r?\n/)
      const separatorLength = separatorMatch ? separatorMatch[0].length : 2
      buffer = buffer.slice(boundaryIndex + separatorLength)
      consumeEventBlock(block, handlers)
      boundaryIndex = buffer.search(/\r?\n\r?\n/)
    }

    if (done) {
      const tail = decoder.decode()
      if (tail) {
        buffer += tail
      }
      if (buffer.trim()) {
        consumeEventBlock(buffer, handlers)
      }
      return
    }
  }
}

export function openEventStream<TEvent extends ApiRecord = JsonObject>(
  path: string,
  payload: JsonObject,
  handlers: StreamEventHandlers<TEvent> = {}
): StreamRequest {
  const controller = new AbortController()

  const done = (async () => {
    try {
      // 返回 controller 给调用方取消请求，done 则代表整个流读取和事件分发的完成状态。
      const response = await streamClient.request<ReadableStream<Uint8Array>>({
        url: path,
        method: 'POST',
        data: payload,
        signal: controller.signal
      })

      if (!response.data || typeof response.data.getReader !== 'function') {
        throw new APIError('当前浏览器不支持流式响应', 500)
      }

      await consumeEventStream(response.data, handlers)
    } catch (error) {
      throw normalizeAxiosError(error)
    }
  })()

  return {
    controller,
    done
  }
}
