<script setup lang="ts">
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  businessChatModeOptions,
  type BusinessChatSessionDetail,
  type BusinessChatSessionExchange,
  type BusinessChatSessionListItem,
  chatApi,
  createConversationId,
  type KnowledgeDocumentOption,
  type BusinessChatMode,
  type ModelApiConfig,
  type BusinessChatStreamEvent
} from '../shared/api/chat'
import { manageApi } from '../shared/api/manage'

interface ConversationHistoryItem {
  conversationId: string
  title: string
  time: string
  chatMode: BusinessChatMode
  turnStatus: string
}

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  functionSupplementItems: string[]
  sourceSnapshotList: string[]
  followUpSuggestionList: string[]
  errorMessage: string
  status: 'streaming' | 'finished' | 'failed'
  exchangeId: string
  createdTime: string
}

interface ConversationTurn {
  id: string
  userMessage: ChatMessage
  assistantMessage: ChatMessage
}

interface DocumentProfile {
  summaryText?: string | null
  terms?: string[]
  answerableQuestions?: string[]
  unanswerableQuestions?: string[]
  businessEntities?: string[]
  questionPatterns?: string[]
}

const starterPrompts = [
  {
    icon: 'write',
    question: '我想写一篇内容（比如文章/文案），你可以帮我从思路到成稿一步步完成吗？'
  },
  {
    icon: 'chart',
    question: '我有一段数据或信息需要分析，你能帮我整理重点并给出结论吗？'
  },
  {
    icon: 'plan',
    question: '我有一个实际问题需要解决，你可以给我一个具体可执行的方案吗？'
  }
] as const

const currentMode = ref<BusinessChatMode>('OPEN_ENDED')
const currentModelConfigId = ref('')
const availableModelConfigs = ref<ModelApiConfig[]>([])
const selectedDoc = ref('')
const knowledgeDocumentOptions = ref<KnowledgeDocumentOption[]>([])
const isDocumentOptionsLoading = ref(false)
const documentOptionsStatusMessage = ref('')
const detailDocument = ref<KnowledgeDocumentOption | null>(null)
const detailProfile = ref<DocumentProfile | null>(null)
const isDocumentDetailLoading = ref(false)
const documentDetailStatusMessage = ref('')
const isDocContextVisible = ref(false)
const conversationHistory = ref<ConversationHistoryItem[]>([])
const activeConversationId = ref('')
const activeConversationTitle = ref('')
const messageList = ref<ChatMessage[]>([])
const userQuestion = ref('')
const streamStatusMessage = ref('')
const historyStatusMessage = ref('')
const modelConfigStatusMessage = ref('')
const isHistoryLoading = ref(false)
const isModelConfigLoading = ref(false)
const deletingConversationId = ref('')
const deleteConfirmConversationId = ref('')
const isStreaming = ref(false)
const conversationId = ref(createConversationId())
const activeStreamRequest = ref<ReturnType<typeof chatApi.openStream> | null>(null)
const expandedThinkingTurnIds = ref<string[]>([])
const conversationScrollRegion = ref<HTMLElement | null>(null)
const shouldAutoScroll = ref(true)
const isTextareaComposing = ref(false)
const modelPickerElement = ref<HTMLElement | null>(null)
const isModelPickerOpen = ref(false)
const modelPickerPlacement = ref<'up' | 'down'>('up')
let docContextHideTimer: ReturnType<typeof setTimeout> | null = null
let scrollAnimationFrame: number | null = null

const AUTO_SCROLL_BOTTOM_THRESHOLD = 80

function clearDocContextHideTimer(): void {
  if (!docContextHideTimer) {
    return
  }

  clearTimeout(docContextHideTimer)
  docContextHideTimer = null
}

function scheduleDocContextHide(): void {
  clearDocContextHideTimer()

  if (currentMode.value !== 'CURRENT_DOCUMENT') {
    isDocContextVisible.value = false
    return
  }

  docContextHideTimer = setTimeout(() => {
    isDocContextVisible.value = false
    docContextHideTimer = null
  }, 200)
}

function keepDocContextVisible(): void {
  clearDocContextHideTimer()

  if (currentMode.value === 'CURRENT_DOCUMENT') {
    isDocContextVisible.value = true
  }
}

function selectMode(mode: BusinessChatMode): void {
  currentMode.value = mode
  clearDocContextHideTimer()
  isDocContextVisible.value = mode === 'CURRENT_DOCUMENT'
}

function handleModePointerEnter(mode: BusinessChatMode): void {
  if (mode !== 'CURRENT_DOCUMENT' || currentMode.value !== 'CURRENT_DOCUMENT') {
    return
  }

  keepDocContextVisible()
}

function createMessageId(): string {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`
}

function resolveErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败'
}

function createAssistantMessage(): ChatMessage {
  return {
    id: createMessageId(),
    role: 'assistant',
    text: '',
    functionSupplementItems: [],
    sourceSnapshotList: [],
    followUpSuggestionList: [],
    errorMessage: '',
    status: 'streaming',
    exchangeId: '',
    createdTime: new Date().toISOString()
  }
}

function findMessage(messageId: string): ChatMessage | undefined {
  return messageList.value.find((message) => message.id === messageId)
}

function isConversationScrolledNearBottom(element: HTMLElement): boolean {
  return element.scrollHeight - element.scrollTop - element.clientHeight <= AUTO_SCROLL_BOTTOM_THRESHOLD
}

function updateAutoScrollPreference(): void {
  const scrollRegion = conversationScrollRegion.value
  if (!scrollRegion) {
    shouldAutoScroll.value = true
    return
  }

  shouldAutoScroll.value = isConversationScrolledNearBottom(scrollRegion)
}

function scheduleScrollToLatest(force = false): void {
  if (force) {
    shouldAutoScroll.value = true
  }

  if (!force && !shouldAutoScroll.value) {
    return
  }

  void nextTick(() => {
    if (!force && !shouldAutoScroll.value) {
      return
    }

    if (scrollAnimationFrame !== null) {
      cancelAnimationFrame(scrollAnimationFrame)
    }

    scrollAnimationFrame = requestAnimationFrame(() => {
      const scrollRegion = conversationScrollRegion.value
      if (!scrollRegion) {
        scrollAnimationFrame = null
        return
      }

      scrollRegion.scrollTop = scrollRegion.scrollHeight
      scrollAnimationFrame = null
    })
  })
}

function parseDateText(value: string): Date | null {
  const normalizedValue = String(value || '').trim()
  if (!normalizedValue) {
    return null
  }

  const parsedDate = new Date(normalizedValue.replace(' ', 'T'))
  return Number.isNaN(parsedDate.getTime()) ? null : parsedDate
}

function formatMessageTime(value: string): string {
  const parsedDate = parseDateText(value)
  if (!parsedDate) {
    return ''
  }

  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  }).format(parsedDate)
}

function formatHistoryTime(value: string): string {
  const parsedDate = parseDateText(value)
  if (!parsedDate) {
    return String(value || '').trim()
  }

  const now = new Date()
  const sameDay = now.getFullYear() === parsedDate.getFullYear() &&
    now.getMonth() === parsedDate.getMonth() &&
    now.getDate() === parsedDate.getDate()

  return new Intl.DateTimeFormat(
    'zh-CN',
    sameDay ? { hour: '2-digit', minute: '2-digit' } : { month: '2-digit', day: '2-digit' }
  ).format(parsedDate)
}

function parseFunctionSupplementItems(value: string): string[] {
  const normalizedValue = String(value || '')
    .replace(/\r\n/g, '\n')
    .trim()

  if (!normalizedValue) {
    return []
  }

  const paragraphItems = normalizedValue
    .split(/\n{2,}/)
    .map((item) => item.trim())
    .filter(Boolean)

  if (paragraphItems.length > 1) {
    return paragraphItems
  }

  return normalizedValue
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
}

function mapConversationHistoryItem(session: BusinessChatSessionListItem): ConversationHistoryItem {
  return {
    conversationId: session.conversationId,
    title: session.title,
    time: formatHistoryTime(session.updateTime),
    chatMode: session.chatMode,
    turnStatus: session.turnStatus
  }
}

function buildAssistantMessageStatus(exchange: BusinessChatSessionExchange): ChatMessage['status'] {
  if (exchange.exchangeState === 'RUNNING') {
    return 'streaming'
  }

  if (exchange.exchangeState === 'FAILED' || exchange.exchangeState === 'STOPPED') {
    return 'failed'
  }

  return 'finished'
}

function buildAssistantErrorMessage(exchange: BusinessChatSessionExchange): string {
  if (exchange.exchangeState === 'FAILED') {
    return String(exchange.finishNote || '本轮对话执行失败')
  }

  if (exchange.exchangeState === 'STOPPED') {
    return String(exchange.finishNote || '本轮回答已中止')
  }

  return ''
}

function buildMessageListFromSession(sessionDetail: BusinessChatSessionDetail): ChatMessage[] {
  // 后端详情按 exchange 返回一轮问答，这里拆成前端消息流里的 user + assistant 两条消息。
  return sessionDetail.exchanges.flatMap((exchange) => {
    const exchangeId = String(exchange.exchangeId)

    return [
      {
        id: `user-${exchangeId}`,
        role: 'user',
        text: exchange.userPrompt,
        functionSupplementItems: [],
        sourceSnapshotList: [],
        followUpSuggestionList: [],
        errorMessage: '',
        status: 'finished',
        exchangeId,
        createdTime: exchange.createTime
      },
      {
        id: `assistant-${exchangeId}`,
        role: 'assistant',
        text: exchange.replyContent,
        functionSupplementItems: exchange.toolTraceList
          .map((item) => String(item || '').trim())
          .filter(Boolean),
        sourceSnapshotList: [...exchange.sourceSnapshotList],
        followUpSuggestionList: [...exchange.followUpSuggestionList],
        errorMessage: buildAssistantErrorMessage(exchange),
        status: buildAssistantMessageStatus(exchange),
        exchangeId,
        createdTime: exchange.createTime
      }
    ]
  })
}

const conversationTurns = computed<ConversationTurn[]>(() => {
  const turns: ConversationTurn[] = []

  for (let index = 0; index < messageList.value.length; index += 1) {
    const userMessage = messageList.value[index]
    if (!userMessage || userMessage.role !== 'user') {
      continue
    }

    const assistantMessage = messageList.value[index + 1]
    if (!assistantMessage || assistantMessage.role !== 'assistant') {
      continue
    }

    turns.push({
      id: userMessage.id,
      userMessage,
      assistantMessage
    })
    index += 1
  }

  return turns
})

const hasConversation = computed<boolean>(() => messageList.value.length > 0)

const canSendMessage = computed<boolean>(() => {
  return !isStreaming.value &&
    userQuestion.value.trim().length > 0 &&
    currentModelConfigId.value.length > 0 &&
    (currentMode.value !== 'CURRENT_DOCUMENT' || selectedDoc.value.length > 0)
})

const currentModelConfig = computed<ModelApiConfig | null>(() => {
  return availableModelConfigs.value.find((config) => config.id === currentModelConfigId.value) ?? null
})

const selectedDocumentOption = computed<KnowledgeDocumentOption | null>(() => {
  return knowledgeDocumentOptions.value.find((document) => document.documentId === selectedDoc.value) ?? null
})

const latestTurnId = computed<string>(() => {
  const turns = conversationTurns.value
  return turns.length > 0 ? turns[turns.length - 1].id : ''
})

function renderMarkdown(value: string): string {
  const html = marked.parse(value, {
    async: false,
    breaks: true,
    gfm: true
  }) as string
  const sanitizedHtml = DOMPurify.sanitize(html)
  const template = document.createElement('template')
  template.innerHTML = sanitizedHtml

  template.content.querySelectorAll('pre').forEach((preElement) => {
    const wrapper = document.createElement('div')
    wrapper.className = 'markdown-code-block'

    const copyButton = document.createElement('button')
    copyButton.type = 'button'
    copyButton.className = 'markdown-code-copy'
    copyButton.dataset.copyCodeButton = 'true'
    copyButton.textContent = '复制'

    preElement.replaceWith(wrapper)
    wrapper.append(copyButton, preElement)
  })

  return template.innerHTML
}

function handleMarkdownClick(event: MouseEvent): void {
  const target = event.target
  if (!(target instanceof Element)) {
    return
  }

  const copyButton = target.closest<HTMLButtonElement>('[data-copy-code-button="true"]')
  if (!copyButton) {
    return
  }

  const codeElement = copyButton.closest('.markdown-code-block')?.querySelector('pre code')
  const codeText = codeElement?.textContent ?? ''
  if (!codeText) {
    return
  }

  void navigator.clipboard.writeText(codeText).then(() => {
    copyButton.textContent = '已复制'
    window.setTimeout(() => {
      copyButton.textContent = '复制'
    }, 1200)
  })
}

function isThinkingExpanded(turnId: string): boolean {
  return expandedThinkingTurnIds.value.includes(turnId)
}

function toggleThinking(turnId: string): void {
  if (isThinkingExpanded(turnId)) {
    expandedThinkingTurnIds.value = expandedThinkingTurnIds.value.filter((item) => item !== turnId)
    return
  }

  expandedThinkingTurnIds.value = [...expandedThinkingTurnIds.value, turnId]
}

function resetThinkingExpansion(): void {
  expandedThinkingTurnIds.value = []
}

function buildThinkingSummary(count: number): string {
  return `${count} 条 Agent 执行`
}

function upsertAgentEventMessage(assistantMessage: ChatMessage, event: BusinessChatStreamEvent): void {
  const agentName = String(event.agentName || '').trim()
  const message = String(event.message || '').trim()
  const text = [message, agentName].filter(Boolean).join('：')
  if (!text) {
    return
  }

  const existingIndex = assistantMessage.functionSupplementItems.findIndex((item) => {
    return agentName.length > 0 && item.endsWith(`：${agentName}`)
  })
  if (existingIndex < 0) {
    assistantMessage.functionSupplementItems = [...assistantMessage.functionSupplementItems, text]
    return
  }

  assistantMessage.functionSupplementItems = assistantMessage.functionSupplementItems.map((item, index) => {
    return index === existingIndex ? text : item
  })
}

async function loadConversationHistory(preservedConversationId: string | null = activeConversationId.value): Promise<void> {
  isHistoryLoading.value = true
  historyStatusMessage.value = ''

  try {
    // 历史列表只承载会话入口；真正的消息内容在点击会话后再通过详情接口加载。
    const sessionPage = await chatApi.listSessionsPage({
      keyword: '',
      chatMode: 'ALL',
      turnStatus: 'ALL',
      pageNo: '1',
      pageSize: '50'
    })
    conversationHistory.value = sessionPage.sessions.map(mapConversationHistoryItem)
    const activeHistoryItem = conversationHistory.value.find((item) => item.conversationId === preservedConversationId)

    if (
      preservedConversationId &&
      activeHistoryItem
    ) {
      activeConversationId.value = preservedConversationId
      activeConversationTitle.value = activeHistoryItem.title
      return
    }

    if (messageList.value.length === 0) {
      activeConversationId.value = ''
      activeConversationTitle.value = ''
    }
  } catch (error) {
    conversationHistory.value = []
    historyStatusMessage.value = resolveErrorMessage(error)
  } finally {
    isHistoryLoading.value = false
  }
}

async function loadAvailableModelConfigs(): Promise<void> {
  isModelConfigLoading.value = true
  modelConfigStatusMessage.value = ''

  try {
    const configs = await chatApi.listAvailableModelConfigs()
    availableModelConfigs.value = configs
    if (configs.some((config) => config.id === currentModelConfigId.value)) {
      return
    }
    currentModelConfigId.value = configs[0]?.id ?? ''
    isModelPickerOpen.value = false
  } catch (error) {
    availableModelConfigs.value = []
    currentModelConfigId.value = ''
    modelConfigStatusMessage.value = resolveErrorMessage(error)
  } finally {
    isModelConfigLoading.value = false
  }
}

async function loadKnowledgeDocumentOptions(): Promise<void> {
  isDocumentOptionsLoading.value = true
  documentOptionsStatusMessage.value = ''

  try {
    const documents = await chatApi.listKnowledgeDocumentOptions()
    knowledgeDocumentOptions.value = documents
    if (selectedDoc.value && !documents.some((document) => document.documentId === selectedDoc.value)) {
      selectedDoc.value = ''
    }
  } catch (error) {
    knowledgeDocumentOptions.value = []
    selectedDoc.value = ''
    documentOptionsStatusMessage.value = resolveErrorMessage(error)
  } finally {
    isDocumentOptionsLoading.value = false
  }
}

function closeDocumentDetail(): void {
  detailDocument.value = null
  detailProfile.value = null
  documentDetailStatusMessage.value = ''
}

async function openSelectedDocumentDetail(): Promise<void> {
  const document = selectedDocumentOption.value
  if (!document || isDocumentDetailLoading.value) {
    return
  }

  detailDocument.value = document
  detailProfile.value = null
  documentDetailStatusMessage.value = ''
  isDocumentDetailLoading.value = true

  try {
    detailProfile.value = await manageApi.queryDocumentProfile({
      documentId: document.documentId
    }) as DocumentProfile
  } catch (error) {
    documentDetailStatusMessage.value = resolveErrorMessage(error)
  } finally {
    isDocumentDetailLoading.value = false
  }
}

function toggleModelPicker(): void {
  if (isStreaming.value || isModelConfigLoading.value || availableModelConfigs.value.length === 0) {
    return
  }

  if (!isModelPickerOpen.value) {
    updateModelPickerPlacement()
  }
  isModelPickerOpen.value = !isModelPickerOpen.value
}

function updateModelPickerPlacement(): void {
  const element = modelPickerElement.value
  if (!element) {
    modelPickerPlacement.value = 'up'
    return
  }

  const rect = element.getBoundingClientRect()
  const spaceBelow = window.innerHeight - rect.bottom
  const spaceAbove = rect.top
  modelPickerPlacement.value = spaceBelow >= 180 || spaceBelow >= spaceAbove ? 'down' : 'up'
}

function selectModelConfig(configId: string): void {
  currentModelConfigId.value = configId
  isModelPickerOpen.value = false
}

function handleDocumentClick(event: MouseEvent): void {
  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  if (!modelPickerElement.value?.contains(target)) {
    isModelPickerOpen.value = false
  }
}

async function openConversation(conversationIdToOpen: string): Promise<void> {
  if (isStreaming.value || deletingConversationId.value) {
    return
  }

  deleteConfirmConversationId.value = ''
  streamStatusMessage.value = '正在加载会话历史'

  try {
    // 打开历史会话时，用后端详情作为唯一数据源回填当前模式、会话编号和消息列表。
    const sessionDetail = await chatApi.getSession(conversationIdToOpen)
    activeConversationId.value = sessionDetail.conversationId
    activeConversationTitle.value = sessionDetail.title
    conversationId.value = sessionDetail.conversationId
    currentMode.value = sessionDetail.chatMode
    selectedDoc.value = sessionDetail.selectedDocumentId == null ? '' : String(sessionDetail.selectedDocumentId)
    isDocContextVisible.value = false
    messageList.value = buildMessageListFromSession(sessionDetail)
    resetThinkingExpansion()
    userQuestion.value = ''
    streamStatusMessage.value = ''
    scheduleScrollToLatest(true)
  } catch (error) {
    streamStatusMessage.value = resolveErrorMessage(error)
  }
}

async function deleteConversation(conversationIdToDelete: string): Promise<void> {
  if (isStreaming.value || deletingConversationId.value) {
    return
  }

  deletingConversationId.value = conversationIdToDelete
  historyStatusMessage.value = ''

  try {
    await chatApi.deleteSession(conversationIdToDelete)

    if (activeConversationId.value === conversationIdToDelete) {
      startNewConversation()
      await loadConversationHistory()
      return
    }

    await loadConversationHistory(activeConversationId.value)
  } catch (error) {
    historyStatusMessage.value = resolveErrorMessage(error)
  } finally {
    deletingConversationId.value = ''
    deleteConfirmConversationId.value = ''
  }
}

function requestDeleteConversation(conversationIdToDelete: string): void {
  if (isStreaming.value || deletingConversationId.value) {
    return
  }

  deleteConfirmConversationId.value = conversationIdToDelete
}

function cancelDeleteConversation(): void {
  deleteConfirmConversationId.value = ''
}

function confirmDeleteConversation(conversationIdToDelete: string): void {
  void deleteConversation(conversationIdToDelete)
}

function consumeStreamEvent(assistantMessageId: string, event: BusinessChatStreamEvent): void {
  const assistantMessage = findMessage(assistantMessageId)
  if (!assistantMessage) {
    return
  }

  // 后端 SSE 按事件类型推进本轮助手消息：正文增量、补充信息、推荐追问和终态都落到同一条消息上。
  if (event.exchangeId != null) {
    assistantMessage.exchangeId = String(event.exchangeId)
  }

  switch (String(event.eventType || '')) {
    case 'AGENT_STARTED':
      upsertAgentEventMessage(assistantMessage, event)
      streamStatusMessage.value = String(event.agentName || 'Agent') + '开始处理'
      break
    case 'AGENT_FINISHED':
      upsertAgentEventMessage(assistantMessage, event)
      streamStatusMessage.value = String(event.agentName || 'Agent') + '处理完成'
      break
    case 'EXECUTION_PROGRESS':
      streamStatusMessage.value = String(event.message || '正在生成执行计划')
      break
    case 'TEXT_DELTA':
      assistantMessage.text += String(event.textDelta || '')
      streamStatusMessage.value = ''
      break
    case 'FUNCTION_SUPPLEMENT':
      streamStatusMessage.value = ''
      break
    case 'REFERENCE_SUPPLEMENT':
      assistantMessage.sourceSnapshotList = Array.isArray(event.sourceSnapshotList)
        ? [...event.sourceSnapshotList]
        : []
      streamStatusMessage.value = '正在补发引用信息'
      break
    case 'FOLLOW_UP_RECOMMENDATION':
      assistantMessage.followUpSuggestionList = Array.isArray(event.followUpSuggestionList)
        ? [...event.followUpSuggestionList]
        : []
      streamStatusMessage.value = '正在补发推荐追问'
      break
    case 'TURN_FINISHED':
      assistantMessage.status = 'finished'
      streamStatusMessage.value = ''
      break
    case 'TURN_REJECTED':
      assistantMessage.status = 'failed'
      assistantMessage.errorMessage = String(event.message || '该会话当前正在执行中')
      streamStatusMessage.value = assistantMessage.errorMessage
      break
    case 'TURN_FAILED':
      assistantMessage.status = 'failed'
      assistantMessage.errorMessage = String(event.message || '本轮对话执行失败')
      streamStatusMessage.value = assistantMessage.errorMessage
      break
    default:
      break
  }

  scheduleScrollToLatest()
}

async function handleSend(): Promise<void> {
  const question = userQuestion.value.trim()
  if (!question || isStreaming.value || !currentModelConfigId.value) {
    return
  }

  if (currentMode.value === 'CURRENT_DOCUMENT' && !selectedDoc.value) {
    streamStatusMessage.value = '请先选择要问答的上传文档'
    isDocContextVisible.value = true
    return
  }
  // 前端先把用户消息和占位助手消息放入本地消息流，随后用 SSE 事件持续填充这条助手消息。
  const currentConversationId = conversationId.value || createConversationId()
  conversationId.value = currentConversationId
  activeConversationId.value = currentConversationId

  messageList.value.push({
    id: createMessageId(),
    role: 'user',
    text: question,
    functionSupplementItems: [],
    sourceSnapshotList: [],
    followUpSuggestionList: [],
    errorMessage: '',
    status: 'finished',
    exchangeId: '',
    createdTime: new Date().toISOString()
  })

  const assistantMessage = createAssistantMessage()
  const assistantMessageId = assistantMessage.id
  messageList.value.push(assistantMessage)
  scheduleScrollToLatest(true)

  userQuestion.value = ''
  isStreaming.value = true
  streamStatusMessage.value = '正在建立执行链路'

  const streamRequest = chatApi.openStream(
    {
      question,
      conversationId: currentConversationId,
      chatMode: currentMode.value,
      modelConfigId: currentModelConfigId.value,
      selectedDocumentId: currentMode.value === 'CURRENT_DOCUMENT' ? selectedDoc.value : undefined
    },
    {
      // 每条 SSE 事件只更新当前轮助手消息，避免历史消息被正在进行的流式响应污染。
      onEvent: (event) => consumeStreamEvent(assistantMessage.id, event)
    }
  )
  activeStreamRequest.value = streamRequest

  try {
    await streamRequest.done
    const currentAssistantMessage = findMessage(assistantMessageId)
    if (currentAssistantMessage?.status === 'streaming') {
      currentAssistantMessage.status = 'finished'
      streamStatusMessage.value = ''
    }
  } catch (error) {
    const currentAssistantMessage = findMessage(assistantMessageId)
    if (currentAssistantMessage) {
      currentAssistantMessage.status = 'failed'
      currentAssistantMessage.errorMessage = error instanceof Error ? error.message : '流式请求失败'
      streamStatusMessage.value = currentAssistantMessage.errorMessage
      scheduleScrollToLatest()
    }
  } finally {
    activeStreamRequest.value = null
    isStreaming.value = false
  }

  await loadConversationHistory(currentConversationId)
}

function startNewConversation(): void {
  activeStreamRequest.value?.controller.abort()
  activeStreamRequest.value = null
  isStreaming.value = false
  deleteConfirmConversationId.value = ''
  userQuestion.value = ''
  streamStatusMessage.value = ''
  selectedDoc.value = ''
  activeConversationId.value = ''
  activeConversationTitle.value = ''
  messageList.value = []
  shouldAutoScroll.value = true
  resetThinkingExpansion()
  conversationId.value = createConversationId()
  void chatApi.clearActiveSession().catch((error) => {
    historyStatusMessage.value = resolveErrorMessage(error)
  })
}

function useFollowUpSuggestion(question: string): void {
  if (isStreaming.value) {
    return
  }

  userQuestion.value = question
  void handleSend()
}

function useStarterPrompt(question: string): void {
  userQuestion.value = question
}

function handleTextareaKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || isTextareaComposing.value) {
    return
  }
  event.preventDefault()
  void handleSend()
}

function handleTextareaCompositionStart(): void {
  isTextareaComposing.value = true
}

function handleTextareaCompositionEnd(): void {
  isTextareaComposing.value = false
}

async function restoreChatPage(): Promise<void> {
  await loadKnowledgeDocumentOptions()
  await loadConversationHistory()
  const activeSession = await chatApi.getActiveSession()
  const activeConversationId = String(activeSession.conversationId || '').trim()
  if (activeConversationId) {
    await openConversation(activeConversationId)
  }
}

onMounted(() => {
  void restoreChatPage().catch((error) => {
    historyStatusMessage.value = resolveErrorMessage(error)
  })
  void loadAvailableModelConfigs()
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  clearDocContextHideTimer()
  if (scrollAnimationFrame !== null) {
    cancelAnimationFrame(scrollAnimationFrame)
  }
  document.removeEventListener('click', handleDocumentClick)
  activeStreamRequest.value?.controller.abort()
})
</script>

<template>
  <div class="codex-layout">
    <aside class="sidebar">
      <nav class="top-nav">
        <button
          type="button"
          class="nav-item nav-item-primary"
          @click="startNewConversation"
        >
          <span
            class="nav-item-icon"
            aria-hidden="true"
          >
            <svg viewBox="0 0 20 20">
              <path
                d="M4.167 14.583 5.11 11.63a1.67 1.67 0 0 1 .405-.672l6.61-6.61a1.667 1.667 0 1 1 2.357 2.357l-6.61 6.61a1.67 1.67 0 0 1-.672.405l-2.953.943h-.08Z"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.5"
              />
              <path
                d="M11.25 5.833 14.167 8.75"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.5"
              />
            </svg>
          </span>
          <span class="nav-item-label">New chat</span>
        </button>
        <RouterLink
          to="/model-config"
          class="nav-item nav-item-secondary"
        >
          <span
            class="nav-item-icon"
            aria-hidden="true"
          >
            <svg viewBox="0 0 20 20">
              <path
                d="M5.833 4.167h8.334a1.667 1.667 0 0 1 1.666 1.666v8.334a1.667 1.667 0 0 1-1.666 1.666H5.833a1.667 1.667 0 0 1-1.666-1.666V5.833a1.667 1.667 0 0 1 1.666-1.666Z"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
              />
              <path
                d="M7.5 8.333h5M7.5 11.667h3.333"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-width="1.5"
              />
            </svg>
          </span>
          <span class="nav-item-label">Config</span>
        </RouterLink>
      </nav>

      <div class="history-section">
        <div class="section-header">
          <span>History</span>
          <span
            v-if="isHistoryLoading"
            class="section-status"
          >
            加载中
          </span>
        </div>

        <p
          v-if="historyStatusMessage"
          class="history-status history-status-error"
        >
          {{ historyStatusMessage }}
        </p>

        <p
          v-else-if="!isHistoryLoading && conversationHistory.length === 0"
          class="history-status"
        >
          暂无历史对话
        </p>

        <ul
          v-else
          class="history-list"
        >
          <li
            v-for="conversation in conversationHistory"
            :key="conversation.conversationId"
            class="history-row"
            :class="{ active: activeConversationId === conversation.conversationId }"
          >
            <button
              type="button"
              class="history-open-button"
              :disabled="isStreaming || deletingConversationId.length > 0"
              @click="openConversation(conversation.conversationId)"
            >
              <span class="truncate">{{ conversation.title }}</span>
            </button>
            <div
              class="history-action-slot"
              :data-delete-visible="deletingConversationId === conversation.conversationId || deleteConfirmConversationId === conversation.conversationId ? 'true' : 'false'"
            >
              <span class="time">{{ conversation.time }}</span>
              <button
                type="button"
                class="history-delete-button"
                :data-visible="deletingConversationId === conversation.conversationId || deleteConfirmConversationId === conversation.conversationId ? 'true' : 'false'"
                :disabled="isStreaming || deletingConversationId.length > 0"
                @click="requestDeleteConversation(conversation.conversationId)"
              >
                {{ deletingConversationId === conversation.conversationId ? '删除中' : '删除' }}
              </button>
              <div
                v-if="deleteConfirmConversationId === conversation.conversationId"
                class="delete-popover history-delete-popover"
              >
                <strong>删除这段对话？</strong>
                <span>删除后历史消息将无法恢复。</span>
                <div class="delete-popover-actions">
                  <button
                    type="button"
                    class="delete-popover-cancel"
                    :disabled="deletingConversationId.length > 0"
                    @click="cancelDeleteConversation"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    class="delete-popover-confirm"
                    :disabled="deletingConversationId.length > 0"
                    @click="confirmDeleteConversation(conversation.conversationId)"
                  >
                    Delete
                  </button>
                </div>
              </div>
            </div>
          </li>
        </ul>
      </div>

    </aside>

    <main class="main-content">
      <div
        class="workspace"
        :class="{ 'workspace-has-conversation': hasConversation }"
      >
        <header
          v-if="hasConversation"
          class="chat-page-header"
        >
          <h1 class="chat-page-title">{{ activeConversationTitle || '智能对话' }}</h1>
        </header>

        <h1
          v-else
          class="greeting"
        >
          What should we build?
        </h1>

        <div
          class="interaction-container"
          :class="{ 'interaction-container-has-conversation': hasConversation }"
        >
          <div
            class="conversation-scroll-region"
            :class="{ 'conversation-scroll-region-has-conversation': hasConversation }"
            ref="conversationScrollRegion"
            @scroll="updateAutoScrollPreference"
          >
            <div
              v-if="conversationTurns.length > 0"
              class="message-list"
            >
              <section
                v-for="turn in conversationTurns"
                :key="turn.id"
                class="turn-block"
              >
                <div class="user-turn">
                  <div class="user-turn-stack">
                    <article class="user-bubble">
                      <p class="user-bubble-text">
                        {{ turn.userMessage.text }}
                      </p>
                    </article>

                    <div
                      v-if="turn.userMessage.createdTime"
                      class="user-bubble-meta"
                    >
                      <span class="message-time">{{ formatMessageTime(turn.userMessage.createdTime) }}</span>
                    </div>
                  </div>
                </div>

                <div
                  v-if="turn.assistantMessage.functionSupplementItems.length > 0"
                  class="thinking-divider"
                >
                  <button
                    type="button"
                    class="thinking-toggle"
                    :aria-expanded="isThinkingExpanded(turn.id)"
                    @click="toggleThinking(turn.id)"
                  >
                    <span class="thinking-toggle-text">
                      {{ buildThinkingSummary(turn.assistantMessage.functionSupplementItems.length) }}
                    </span>
                    <span
                      class="thinking-toggle-icon"
                      :class="{ expanded: isThinkingExpanded(turn.id) }"
                      aria-hidden="true"
                    >
                      <svg viewBox="0 0 20 20">
                        <path
                          d="M7.5 5.833 12.5 10l-5 4.167"
                          fill="none"
                          stroke="currentColor"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          stroke-width="1.5"
                        />
                      </svg>
                    </span>
                  </button>
                </div>

                <div
                  v-if="turn.assistantMessage.functionSupplementItems.length > 0 && isThinkingExpanded(turn.id)"
                  class="thinking-panel"
                >
                  <ol class="thinking-list">
                    <li
                      v-for="(item, itemIndex) in turn.assistantMessage.functionSupplementItems"
                      :key="`${turn.id}-${itemIndex}`"
                      class="thinking-item"
                    >
                      {{ item }}
                    </li>
                  </ol>
                </div>

                <article
                  class="assistant-response"
                  :class="`assistant-response-${turn.assistantMessage.status}`"
                >
                  <div
                    v-if="turn.assistantMessage.text"
                    class="assistant-text markdown-body"
                    v-html="renderMarkdown(turn.assistantMessage.text)"
                    @click="handleMarkdownClick"
                  />

                  <p
                    v-else-if="turn.assistantMessage.status === 'streaming'"
                    class="assistant-placeholder"
                  >
                    <span class="typing-dots">
                      <span />
                      <span />
                      <span />
                    </span>
                  </p>

                  <p
                    v-if="turn.assistantMessage.errorMessage"
                    class="message-error"
                  >
                    {{ turn.assistantMessage.errorMessage }}
                  </p>
                </article>

                <div
                  v-if="turn.id === latestTurnId && turn.assistantMessage.followUpSuggestionList.length > 0 && turn.assistantMessage.status === 'finished'"
                  class="follow-up-list"
                >
                  <button
                    v-for="suggestion in turn.assistantMessage.followUpSuggestionList"
                    :key="`${turn.id}-${suggestion}`"
                    type="button"
                    class="follow-up-item"
                    :disabled="isStreaming"
                    @click="useFollowUpSuggestion(suggestion)"
                  >
                    {{ suggestion }}
                  </button>
                </div>
              </section>
            </div>

            <div
              v-if="streamStatusMessage"
              class="stream-status"
            >
              {{ streamStatusMessage }}
            </div>
          </div>

          <div
            class="composer-section"
            :class="{ 'composer-section-has-conversation': hasConversation }"
          >
            <div
              class="custom-mode-selector"
              @mouseleave="scheduleDocContextHide"
            >
              <div class="mode-tabs">
                <button
                  v-for="mode in businessChatModeOptions"
                  :key="mode.value"
                  type="button"
                  class="mode-btn"
                  :class="{ active: currentMode === mode.value }"
                  @click="selectMode(mode.value)"
                  @mouseenter="handleModePointerEnter(mode.value)"
                >
                  {{ mode.label }}
                </button>
              </div>

              <transition name="doc-context">
                <div
                  v-if="currentMode === 'CURRENT_DOCUMENT' && isDocContextVisible"
                  class="doc-selector-wrapper"
                  @mouseenter="keepDocContextVisible"
                >
                  <div class="doc-context-panel">
                    <span class="doc-context-label">关联文档上下文</span>
                    <div class="doc-select-row">
                      <select
                        v-model="selectedDoc"
                        class="doc-select"
                        :disabled="isDocumentOptionsLoading || knowledgeDocumentOptions.length === 0"
                      >
                        <option
                          disabled
                          value=""
                        >
                          {{ isDocumentOptionsLoading ? '正在加载上传文档...' : '选择上传的文档...' }}
                        </option>
                        <option
                          v-for="document in knowledgeDocumentOptions"
                          :key="document.documentId"
                          :value="document.documentId"
                        >
                          {{ document.documentName }}
                        </option>
                      </select>
                      <button
                        type="button"
                        class="doc-detail-button"
                        :disabled="!selectedDocumentOption || isDocumentDetailLoading"
                        @click="openSelectedDocumentDetail"
                      >
                        详情
                      </button>
                    </div>
                    <p
                      v-if="documentOptionsStatusMessage || (!isDocumentOptionsLoading && knowledgeDocumentOptions.length === 0)"
                      class="doc-context-hint"
                    >
                      {{ documentOptionsStatusMessage || '暂无可选择的上传文档' }}
                    </p>
                  </div>
                </div>
              </transition>
            </div>

            <div
              class="input-panel"
              :class="{ 'input-panel-compact': hasConversation }"
            >
              <textarea
                v-model="userQuestion"
                placeholder="输入你的问题、需求或改动目标，我们直接开始。"
                @compositionstart="handleTextareaCompositionStart"
                @compositionend="handleTextareaCompositionEnd"
                @keydown="handleTextareaKeydown"
              />

              <div class="input-toolbar">
                <div
                  ref="modelPickerElement"
                  class="model-provider-select-wrap"
                  :class="{ open: isModelPickerOpen }"
                >
                  <button
                    type="button"
                    class="model-provider-trigger"
                    :disabled="isStreaming || isModelConfigLoading || availableModelConfigs.length === 0"
                    @click.stop="toggleModelPicker"
                  >
                    <span class="model-provider-select-label">模型</span>
                    <span class="model-provider-current">
                      {{ currentModelConfig?.modelName || '暂无可用模型' }}
                    </span>
                    <span
                      class="model-provider-arrow"
                      aria-hidden="true"
                    />
                  </button>

                  <div
                    v-if="isModelPickerOpen"
                    class="model-provider-menu"
                    :class="`model-provider-menu-${modelPickerPlacement}`"
                  >
                    <button
                      v-for="config in availableModelConfigs"
                      :key="config.id"
                      type="button"
                      class="model-provider-option"
                      :class="{ active: config.id === currentModelConfigId }"
                      @click="selectModelConfig(config.id)"
                    >
                      {{ config.modelName }}
                    </button>
                  </div>
                </div>

                <button
                  type="button"
                  class="send-btn"
                  :disabled="!canSendMessage"
                  @click="handleSend"
                >
                  ↑
                </button>
              </div>

              <p
                v-if="modelConfigStatusMessage || (!isModelConfigLoading && availableModelConfigs.length === 0)"
                class="model-config-hint"
              >
                {{ modelConfigStatusMessage || '请先到 Config 配置可用模型 API' }}
              </p>
            </div>
          </div>

          <div
            v-if="!hasConversation"
            class="suggestions-list"
          >
            <button
              v-for="prompt in starterPrompts"
              :key="prompt.question"
              type="button"
              class="suggestion-item"
              :disabled="isStreaming"
              @click="useStarterPrompt(prompt.question)"
            >
              <span
                class="suggestion-icon"
                aria-hidden="true"
              >
                <svg
                  v-if="prompt.icon === 'write'"
                  viewBox="0 0 20 20"
                >
                  <path
                    d="M4.167 15.833h11.666M5.833 12.917l.487-2.436a1.25 1.25 0 0 1 .343-.641l6.094-6.094a1.355 1.355 0 0 1 1.916 1.916L8.58 11.756a1.25 1.25 0 0 1-.641.343l-2.106.818Z"
                    fill="none"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="1.6"
                  />
                </svg>
                <svg
                  v-else-if="prompt.icon === 'chart'"
                  viewBox="0 0 20 20"
                >
                  <path
                    d="M4.167 15.833V4.167M4.167 15.833h11.666M7.5 12.5V9.167M10 12.5V6.667M12.5 12.5v-2.083M15 12.5V7.917"
                    fill="none"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="1.6"
                  />
                </svg>
                <svg
                  v-else
                  viewBox="0 0 20 20"
                >
                  <path
                    d="M5 5.833h.008M8.333 5.833H15M5 10h.008M8.333 10H15M5 14.167h.008M8.333 14.167h4.584"
                    fill="none"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="1.7"
                  />
                </svg>
              </span>
              <span>{{ prompt.question }}</span>
            </button>
          </div>

        </div>
      </div>
    </main>

    <div
      v-if="detailDocument"
      class="document-detail-mask"
      @click.self="closeDocumentDetail"
    >
      <article class="document-detail-modal">
        <header class="document-detail-header">
          <div>
            <h2>文档详情</h2>
            <p>{{ detailDocument.documentName }}</p>
          </div>
          <button
            type="button"
            class="document-detail-close"
            @click="closeDocumentDetail"
          >
            关闭
          </button>
        </header>

        <div class="document-detail-grid">
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
          <strong>解析 {{ detailDocument.parseStatus }} / 策略 {{ detailDocument.strategyStatus }} / 索引 {{ detailDocument.indexStatus }}</strong>
          <span>创建时间</span>
          <strong>{{ detailDocument.createTime || '-' }}</strong>
        </div>

        <section class="document-profile-block">
          <h3>画像摘要</h3>
          <p v-if="isDocumentDetailLoading">正在加载画像</p>
          <p v-else-if="documentDetailStatusMessage">{{ documentDetailStatusMessage }}</p>
          <template v-else>
            <p>{{ detailProfile?.summaryText || '-' }}</p>
            <div class="document-profile-tags">
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
  </div>
</template>

<style scoped>
.codex-layout,
.codex-layout * {
  box-sizing: border-box;
}

.codex-layout {
  --top-bar-height: 56px;
  --layout-bg: #ffffff;
  --layout-text: #333333;
  --sidebar-bg: #f9f9f9;
  --sidebar-border: #eaeaea;
  --nav-text: #444444;
  --nav-muted: #666666;
  --nav-hover: #f1f3f5;
  --nav-primary-bg: #f7f8fa;
  --nav-primary-border: #e7eaee;
  --nav-primary-shadow: none;
  --section-text: #999999;
  --history-text: #555555;
  --history-hover: #eeeeee;
  --main-bg: #ffffff;
  --title-text: #222222;
  --panel-bg: #ffffff;
  --panel-border: #e5e5e5;
  --panel-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  --panel-text: #333333;
  --placeholder-text: #aaaaaa;
  --tabs-bg: #f5f5f5;
  --tab-text: #555555;
  --tab-active-bg: #ffffff;
  --tab-active-text: #111111;
  --tab-active-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  --doc-panel-border: #ececec;
  --doc-panel-bg: linear-gradient(180deg, #ffffff 0%, #fafafa 100%);
  --doc-panel-shadow: 0 10px 20px rgba(15, 23, 42, 0.05);
  --doc-label: #8a8a8a;
  --doc-select-bg: #ffffff;
  --doc-select-border: #dddddd;
  --doc-select-focus: #c5dcff;
  --toolbar-icon: #888888;
  --send-bg: #999999;
  --send-text: #ffffff;
  --follow-up-border: #e6e9ee;
  --follow-up-text: #344054;
  --follow-up-hover: #f6f8fa;
  --suggestion-border: #edf0f4;
  --suggestion-text: #475467;
  --suggestion-hover: #f8fafc;
  --nav-icon-color: #5b6472;
  display: flex;
  width: 100%;
  height: calc(100vh - var(--top-bar-height));
  min-height: calc(100vh - var(--top-bar-height));
  background-color: var(--layout-bg);
  font-family: "SF Pro Display", "PingFang SC", "Helvetica Neue", sans-serif;
  color: var(--layout-text);
  overflow: hidden;
}

@supports (height: 100dvh) {
  .codex-layout {
    height: calc(100dvh - var(--top-bar-height));
    min-height: calc(100dvh - var(--top-bar-height));
  }
}

ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

button,
textarea,
select {
  font: inherit;
}

.sidebar {
  display: flex;
  width: 260px;
  flex-shrink: 0;
  flex-direction: column;
  padding: 16px 12px;
  background-color: var(--sidebar-bg);
  border-right: 1px solid var(--sidebar-border);
  overflow-y: auto;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease;
}

.top-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.nav-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  color: var(--nav-text);
  font-size: 13px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.nav-item:hover {
  background-color: var(--nav-hover);
}

.nav-item-label {
  font-size: 14px;
  font-weight: 600;
}

.nav-item-icon {
  display: inline-flex;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  color: var(--nav-icon-color);
  transition: color 0.2s ease;
}

.nav-item-icon svg {
  width: 16px;
  height: 16px;
}

.nav-item-primary {
  gap: 10px;
  justify-content: flex-start;
  padding: 12px 12px;
  border: 1px solid var(--nav-primary-border);
  border-radius: 12px;
  background: var(--nav-primary-bg);
  box-shadow: var(--nav-primary-shadow);
}

.nav-item-primary:hover {
  background-color: #f3f5f7;
  border-color: #dde2e8;
  transform: none;
}

.nav-item-secondary {
  gap: 10px;
  justify-content: flex-start;
  padding: 10px 12px;
  border: 1px solid transparent;
}

.nav-item-secondary:hover {
  border-color: #e5e7eb;
}

.history-section {
  flex: 1;
  overflow-y: auto;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px;
  margin-bottom: 12px;
  color: var(--section-text);
  font-size: 11px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.section-status {
  color: var(--section-text);
  font-size: 11px;
}

.history-status {
  margin: 0;
  padding: 0 10px;
  color: var(--section-text);
  font-size: 12px;
  line-height: 1.6;
}

.history-status-error {
  color: #c53030;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
  border-radius: 8px;
}

.history-row.active {
  background-color: #eceff3;
}

.history-open-button {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  padding: 6px 10px;
  color: var(--history-text);
  font-size: 13px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.history-open-button:hover {
  background-color: var(--history-hover);
}

.history-open-button:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.history-action-slot {
  position: relative;
  width: 52px;
  height: 28px;
  flex-shrink: 0;
}

.time,
.history-delete-button {
  position: absolute;
  top: 50%;
  right: 8px;
  transform: translateY(-50%);
  transition:
    opacity 0.2s ease,
    visibility 0.2s ease;
}

.history-delete-button {
  padding: 0;
  color: #c53030;
  font-size: 11px;
  border: 0;
  background: transparent;
  cursor: pointer;
  opacity: 0;
  visibility: hidden;
}

.history-action-slot:hover .time,
.history-action-slot[data-delete-visible='true'] .time {
  opacity: 0;
  visibility: hidden;
}

.history-action-slot:hover .history-delete-button,
.history-action-slot[data-delete-visible='true'] .history-delete-button {
  opacity: 1;
  visibility: visible;
}

.history-delete-button:disabled {
  cursor: not-allowed;
}

.delete-popover {
  position: absolute;
  z-index: 30;
  display: flex;
  width: 218px;
  flex-direction: column;
  gap: 7px;
  padding: 12px;
  color: #344054;
  border: 1px solid #f2b8b5;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 40px rgb(16 24 40 / 16%);
}

.history-delete-popover {
  top: calc(100% + 6px);
  right: 0;
}

.delete-popover strong {
  color: #9f1d1d;
  font-size: 13px;
  font-weight: 650;
}

.delete-popover span {
  color: #667085;
  font-size: 12px;
  line-height: 1.45;
}

.delete-popover-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 2px;
}

.delete-popover-cancel,
.delete-popover-confirm {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 7px;
  cursor: pointer;
}

.delete-popover-cancel {
  color: #344054;
  border: 1px solid #d0d5dd;
  background: #ffffff;
}

.delete-popover-confirm {
  color: #ffffff;
  border: 1px solid #c53030;
  background: #c53030;
}

.delete-popover-cancel:disabled,
.delete-popover-confirm:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.truncate {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.time {
  flex-shrink: 0;
  color: var(--section-text);
  font-size: 11px;
}

.main-content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  background-color: var(--main-bg);
  transition: background-color 0.2s ease;
}

.workspace {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  align-items: center;
  padding: 10vh 24px 32px;
  overflow-y: auto;
}

.workspace-has-conversation {
  padding-top: 24px;
  padding-bottom: 20px;
  overflow: hidden;
}

.chat-page-header {
  width: 100%;
  max-width: 720px;
  margin-bottom: 20px;
}

.chat-page-title {
  margin: 0;
  color: var(--title-text);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
  text-align: left;
  transition: color 0.2s ease;
}

.greeting {
  width: 100%;
  max-width: 720px;
  margin: 0 0 24px;
  color: var(--title-text);
  font-size: 28px;
  font-weight: 500;
  line-height: 1.25;
  letter-spacing: 0;
  text-align: center;
}

.interaction-container {
  width: 100%;
  max-width: 720px;
}

.interaction-container-has-conversation {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.conversation-scroll-region-has-conversation {
  flex: 1;
  min-height: 0;
  padding-right: 24px;
  margin-right: -24px;
  margin-bottom: 12px;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.composer-section-has-conversation {
  flex-shrink: 0;
  padding-top: 12px;
  background: #ffffff;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 28px;
  margin-bottom: 20px;
}

.turn-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.user-turn {
  display: flex;
  justify-content: flex-end;
}

.user-turn-stack {
  display: flex;
  max-width: min(100%, 640px);
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.user-bubble {
  width: fit-content;
  max-width: 100%;
  padding: 13px 16px 12px;
  border-radius: 16px;
  background: #f3f4f6;
  box-shadow: inset 0 0 0 1px #ebeef2;
}

.user-bubble-text {
  margin: 0;
  color: #1f2937;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.user-bubble-meta {
  display: flex;
  justify-content: flex-end;
}

.message-time {
  color: #6b7280;
  font-size: 12px;
}

.thinking-divider {
  display: flex;
  align-items: center;
  gap: 14px;
}

.thinking-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #eceff3;
}

.thinking-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  color: #6b7280;
  font-size: 14px;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.thinking-toggle-text {
  white-space: nowrap;
}

.thinking-toggle-icon {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease;
}

.thinking-toggle-icon svg {
  width: 16px;
  height: 16px;
}

.thinking-toggle-icon.expanded {
  transform: rotate(90deg);
}

.thinking-panel {
  padding: 16px 18px;
  border: 1px solid #eceff3;
  border-radius: 18px;
  background: #fafbfc;
}

.thinking-list {
  display: flex;
  margin: 0;
  padding-left: 20px;
  flex-direction: column;
  gap: 10px;
}

.thinking-item {
  color: #475467;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.assistant-response {
  color: #243041;
}

.assistant-response-streaming {
  opacity: 0.92;
}

.assistant-response-failed {
  color: #7f1d1d;
}

.assistant-text {
  margin: 0;
  color: inherit;
  font-size: 15px;
  line-height: 1.85;
}

.markdown-body :deep(*) {
  letter-spacing: 0;
}

.markdown-body :deep(p),
.markdown-body :deep(ul),
.markdown-body :deep(ol),
.markdown-body :deep(blockquote),
.markdown-body :deep(pre),
.markdown-body :deep(table) {
  margin: 0 0 12px;
}

.markdown-body :deep(p:last-child),
.markdown-body :deep(ul:last-child),
.markdown-body :deep(ol:last-child),
.markdown-body :deep(blockquote:last-child),
.markdown-body :deep(pre:last-child),
.markdown-body :deep(table:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
}

.markdown-body :deep(li + li) {
  margin-top: 6px;
}

.markdown-body :deep(code) {
  padding: 2px 5px;
  color: #344054;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 0.92em;
  border-radius: 5px;
  background: #f2f4f7;
}

.markdown-body :deep(pre) {
  padding: 12px 14px;
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f8fafc;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
}

.markdown-body :deep(.markdown-code-block) {
  position: relative;
  margin: 0 0 12px;
}

.markdown-body :deep(.markdown-code-block:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(.markdown-code-block pre) {
  margin: 0;
  padding-top: 38px;
}

.markdown-body :deep(.markdown-code-copy) {
  position: absolute;
  top: 8px;
  right: 8px;
  height: 24px;
  padding: 0 9px;
  color: #475467;
  font-size: 12px;
  border: 1px solid #d0d5dd;
  border-radius: 7px;
  background: #ffffff;
  cursor: pointer;
}

.markdown-body :deep(.markdown-code-copy:hover) {
  color: #1f2937;
  border-color: #98a2b3;
}

.markdown-body :deep(blockquote) {
  padding-left: 12px;
  color: #667085;
  border-left: 2px solid #d0d5dd;
}

.markdown-body :deep(a) {
  color: #2563eb;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.follow-up-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.follow-up-item {
  max-width: 100%;
  padding: 7px 11px;
  color: var(--follow-up-text);
  font-size: 13px;
  line-height: 1.4;
  border: 1px solid var(--follow-up-border);
  border-radius: 999px;
  background: #ffffff;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.follow-up-item:hover {
  background-color: var(--follow-up-hover);
  border-color: #d7dce3;
}

.follow-up-item:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.assistant-placeholder {
  display: flex;
  min-height: 25px;
  align-items: center;
  margin: 0;
  color: #98a2b3;
  font-size: 14px;
  line-height: 1.8;
}

.typing-dots {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.typing-dots span {
  display: block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #98a2b3;
  animation: typing-dot-pulse 1.2s ease-in-out infinite;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.16s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes typing-dot-pulse {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.message-error {
  margin: 16px 0 0;
  color: #c53030;
  font-size: 13px;
}

.stream-status {
  margin-bottom: 14px;
  padding: 10px 14px;
  color: #455468;
  font-size: 13px;
  border: 1px solid #e7ebf1;
  border-radius: 999px;
  background: #f8fafc;
}

.custom-mode-selector {
  position: relative;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  z-index: 4;
}

.model-provider-select-wrap {
  position: relative;
  display: inline-flex;
  min-width: 156px;
  max-width: min(260px, 100%);
}

.model-provider-trigger {
  display: inline-flex;
  width: 100%;
  height: 32px;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border: 1px solid #d9e0ea;
  border-radius: 10px;
  color: inherit;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  box-shadow: 0 1px 2px rgb(16 24 40 / 5%);
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    background 0.18s ease;
}

.model-provider-trigger:hover {
  border-color: #b9c6d8;
  background: #ffffff;
}

.model-provider-select-wrap.open .model-provider-trigger,
.model-provider-trigger:focus-visible {
  border-color: #8bb5f8;
  box-shadow:
    0 0 0 3px rgb(37 99 235 / 12%),
    0 1px 2px rgb(16 24 40 / 5%);
}

.model-provider-select-label {
  color: #667085;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}

.model-provider-current {
  min-width: 0;
  max-width: 170px;
  color: #182230;
  font-size: 13px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-provider-arrow {
  width: 7px;
  height: 7px;
  margin-left: auto;
  border-right: 1.5px solid #667085;
  border-bottom: 1.5px solid #667085;
  transform: translateY(-2px) rotate(45deg);
}

.model-provider-select-wrap.open .model-provider-arrow {
  transform: translateY(2px) rotate(225deg);
}

.model-provider-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.model-provider-menu {
  position: absolute;
  left: 0;
  z-index: 24;
  min-width: 100%;
  max-width: min(320px, calc(100vw - 32px));
  max-height: 220px;
  padding: 4px;
  overflow-y: auto;
  border: 1px solid #d9e0ea;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 16px 36px rgb(16 24 40 / 14%);
}

.model-provider-menu-up {
  bottom: calc(100% + 8px);
}

.model-provider-menu-down {
  top: calc(100% + 8px);
}

.model-provider-option {
  display: block;
  width: 100%;
  min-height: 32px;
  padding: 7px 9px;
  color: #344054;
  font-size: 13px;
  font-weight: 550;
  text-align: left;
  white-space: nowrap;
  border: 0;
  border-radius: 7px;
  background: transparent;
  cursor: pointer;
}

.model-provider-option:hover,
.model-provider-option.active {
  color: #175cd3;
  background: #eff6ff;
}

.model-config-hint {
  margin: 8px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
}

.mode-tabs {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px;
  padding: 4px;
  background-color: var(--tabs-bg);
  border-radius: 20px;
  margin: 0 auto;
  transition: background-color 0.2s ease;
}

.mode-btn {
  padding: 6px 16px;
  color: var(--tab-text);
  font-size: 13px;
  border: 0;
  border-radius: 16px;
  background: transparent;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mode-btn.active {
  background-color: var(--tab-active-bg);
  color: var(--tab-active-text);
  box-shadow: var(--tab-active-shadow);
  font-weight: 500;
}

.doc-selector-wrapper {
  position: absolute;
  top: calc(100% + 12px);
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  z-index: 6;
  pointer-events: none;
}

.doc-context-panel {
  width: min(100%, 440px);
  padding: 12px;
  border: 1px solid var(--doc-panel-border);
  border-radius: 16px;
  background: var(--doc-panel-bg);
  box-shadow: var(--doc-panel-shadow);
  pointer-events: auto;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease;
}

.doc-context-label {
  display: block;
  margin-bottom: 8px;
  color: var(--doc-label);
  font-size: 12px;
  letter-spacing: 0.02em;
}

.doc-select-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.doc-select {
  width: 100%;
  height: 42px;
  padding: 0 12px;
  color: var(--panel-text);
  font-size: 13px;
  border: 1px solid var(--doc-select-border);
  border-radius: 12px;
  outline: none;
  background-color: var(--doc-select-bg);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.doc-select:focus {
  border-color: var(--doc-select-focus);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--doc-select-focus) 35%, transparent);
}

.doc-detail-button {
  height: 42px;
  padding: 0 14px;
  color: #175cd3;
  font-size: 13px;
  font-weight: 650;
  border: 1px solid #bcd7ff;
  border-radius: 8px;
  background: #eff6ff;
  cursor: pointer;
}

.doc-detail-button:disabled {
  color: #98a2b3;
  border-color: #e4e7ec;
  background: #f8fafc;
  cursor: not-allowed;
}

.doc-context-hint {
  margin: 8px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.45;
}

.document-detail-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgb(15 23 42 / 38%);
}

.document-detail-modal {
  width: min(720px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
  padding: 20px;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 48px rgb(15 23 42 / 20%);
}

.document-detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #eaecf0;
}

.document-detail-header h2 {
  margin: 0;
  color: #101828;
  font-size: 18px;
}

.document-detail-header p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
}

.document-detail-close {
  height: 34px;
  padding: 0 14px;
  color: #344054;
  font-size: 13px;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  background: #ffffff;
  cursor: pointer;
}

.document-detail-grid {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 12px 16px;
  margin-top: 16px;
  color: #344054;
  font-size: 13px;
}

.document-detail-grid span {
  color: #667085;
}

.document-detail-grid strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.document-profile-block {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #eaecf0;
}

.document-profile-block h3 {
  margin: 0 0 8px;
  color: #101828;
  font-size: 15px;
}

.document-profile-block p {
  margin: 0;
  color: #475467;
  font-size: 13px;
  line-height: 1.7;
}

.document-profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.document-profile-tags span {
  padding: 4px 8px;
  color: #175cd3;
  font-size: 12px;
  border-radius: 999px;
  background: #eff6ff;
}

.input-panel {
  position: relative;
  z-index: 1;
  margin-bottom: 24px;
  padding: 16px 16px 14px;
  background-color: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 14px;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.04);
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.input-panel-compact {
  margin-bottom: 0;
  padding: 14px 16px 12px;
  border-radius: 14px;
}

.input-panel textarea {
  width: 100%;
  min-height: 108px;
  color: var(--panel-text);
  font-size: 15px;
  border: 0;
  outline: none;
  resize: none;
  line-height: 1.6;
  background: transparent;
}

.input-panel-compact textarea {
  min-height: 72px;
  max-height: 156px;
}

.input-panel textarea::placeholder {
  color: var(--placeholder-text);
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
}

.input-panel-compact .input-toolbar {
  margin-top: 12px;
}

.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--send-text);
  border: 0;
  border-radius: 999px;
  background-color: var(--send-bg);
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.send-btn:disabled {
  cursor: not-allowed;
  background-color: #c5cbd3;
}

.send-btn:not(:disabled):hover {
  background-color: #2563eb;
}

.suggestions-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 10px;
}

.suggestion-item {
  display: flex;
  width: fit-content;
  max-width: 100%;
  align-items: center;
  gap: 10px;
  padding: 8px 6px;
  color: var(--suggestion-text);
  font-size: 14px;
  line-height: 1.45;
  text-align: left;
  border: 0;
  background: transparent;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.suggestion-item:hover {
  color: #2563eb;
}

.suggestion-icon {
  display: inline-flex;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  color: #667085;
  line-height: 1;
  border: 1px solid #d9dee6;
  border-radius: 999px;
  background: #ffffff;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.suggestion-icon svg {
  width: 14px;
  height: 14px;
}

.suggestion-item:hover .suggestion-icon {
  color: #2563eb;
  border-color: #9dbcf8;
  background: #eff6ff;
}

.suggestion-item:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.nav-item:focus-visible,
.mode-btn:focus-visible,
.markdown-body :deep(.markdown-code-copy:focus-visible),
.model-provider-option:focus-visible,
.doc-select:focus-visible,
.send-btn:focus-visible,
.suggestion-item:focus-visible,
.follow-up-item:focus-visible {
  outline: 2px solid #c5dcff;
  outline-offset: 2px;
}

@media (max-width: 960px) {
  .codex-layout {
    height: auto;
    min-height: calc(100vh - var(--top-bar-height));
    flex-direction: column;
    overflow: auto;
  }

  @supports (min-height: 100dvh) {
    .codex-layout {
      min-height: calc(100dvh - var(--top-bar-height));
    }
  }

  .sidebar {
    width: 100%;
    border-right: 0;
    border-bottom: 1px solid #eaeaea;
  }

  .workspace {
    padding-top: 48px;
  }

  .workspace-has-conversation {
    padding-top: 20px;
    padding-bottom: 16px;
  }
}

@media (max-width: 640px) {

  .workspace {
    padding: 32px 16px 24px;
  }

  .workspace-has-conversation {
    padding: 16px 12px 12px;
  }

  .chat-page-header {
    margin-bottom: 16px;
  }

  .input-toolbar {
    margin-top: 14px;
    flex-wrap: wrap;
  }

  .model-provider-select-wrap {
    min-width: 148px;
  }

  .doc-select {
    width: 100%;
  }

  .input-panel-compact {
    padding: 12px 14px 10px;
  }

  .input-panel-compact textarea {
    min-height: 64px;
  }
}
</style>
