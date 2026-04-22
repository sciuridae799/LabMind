<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

const modes = ['当前文档问答', '自动知识问答', '开放式提问'] as const

type ChatMode = (typeof modes)[number]

const currentMode = ref<ChatMode>('开放式提问')
const selectedDoc = ref('')
const isDocContextVisible = ref(false)
const conversationHistory = [] as Array<{
  id: string
  title: string
  time: string
}>
let docContextHideTimer: ReturnType<typeof setTimeout> | null = null

function clearDocContextHideTimer(): void {
  if (!docContextHideTimer) {
    return
  }

  clearTimeout(docContextHideTimer)
  docContextHideTimer = null
}

function scheduleDocContextHide(): void {
  clearDocContextHideTimer()

  if (currentMode.value !== '当前文档问答') {
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

  if (currentMode.value === '当前文档问答') {
    isDocContextVisible.value = true
  }
}

function selectMode(mode: ChatMode): void {
  currentMode.value = mode
  clearDocContextHideTimer()
  isDocContextVisible.value = mode === '当前文档问答'
}

function handleModePointerEnter(mode: ChatMode): void {
  if (mode !== '当前文档问答' || currentMode.value !== '当前文档问答') {
    return
  }

  keepDocContextVisible()
}

onBeforeUnmount(() => {
  clearDocContextHideTimer()
})
</script>

<template>
  <div class="codex-layout">
    <aside class="sidebar">
      <nav class="top-nav">
        <button
          type="button"
          class="nav-item nav-item-primary"
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
      </nav>

      <div
        v-if="conversationHistory.length > 0"
        class="history-section"
      >
        <div class="section-header">
          <span>History</span>
        </div>

        <ul class="history-list">
          <li
            v-for="conversation in conversationHistory"
            :key="conversation.id"
          >
            <span class="truncate">{{ conversation.title }}</span>
            <span class="time">{{ conversation.time }}</span>
          </li>
        </ul>
      </div>

    </aside>

    <main class="main-content">
      <div class="workspace">
        <h1 class="greeting">超级智能，连接知识、推理与执行</h1>

        <div class="interaction-container">
          <div
            class="custom-mode-selector"
            @mouseenter="keepDocContextVisible"
            @mouseleave="scheduleDocContextHide"
          >
            <div class="mode-tabs">
              <button
                v-for="mode in modes"
                :key="mode"
                type="button"
                class="mode-btn"
                :class="{ active: currentMode === mode }"
                @click="selectMode(mode)"
                @mouseenter="handleModePointerEnter(mode)"
              >
                {{ mode }}
              </button>
            </div>

            <transition name="doc-context">
              <div
                v-if="currentMode === '当前文档问答' && isDocContextVisible"
                class="doc-selector-wrapper"
              >
                <div class="doc-context-panel">
                  <span class="doc-context-label">关联文档上下文</span>
                  <select
                    v-model="selectedDoc"
                    class="doc-select"
                  >
                    <option
                      disabled
                      value=""
                    >
                      选择关联的文档上下文...
                    </option>
                    <option value="doc1">
                      ChatBI 架构文档.pdf
                    </option>
                    <option value="doc2">
                      前端 Vue3 组件规范.md
                    </option>
                  </select>
                </div>
              </div>
            </transition>
          </div>

          <div class="input-panel">
            <textarea placeholder="输入你的问题、需求或改动目标，我们直接开始。"></textarea>

            <div class="input-toolbar">
              <button
                type="button"
                class="icon-btn"
              >
                +
              </button>
              <button
                type="button"
                class="send-btn"
              >
                ↑
              </button>
            </div>
          </div>

          <div class="suggestions-list">
            <button
              type="button"
              class="suggestion-item"
            >
              <span class="icon">💬</span>
              <span>接通左侧会话历史和详情链路</span>
            </button>
            <button
              type="button"
              class="suggestion-item"
            >
              <span class="icon">🐞</span>
              <span>补上聊天调试明细面板</span>
            </button>
            <button
              type="button"
              class="suggestion-item"
            >
              <span class="icon">🖥</span>
              <span>把管理后台入口做成真实页面</span>
            </button>
          </div>
        </div>
      </div>
    </main>
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
  --suggestion-border: #f0f0f0;
  --suggestion-text: #555555;
  --suggestion-icon: #999999;
  --suggestion-hover: #f9f9f9;
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

.history-section {
  flex: 1;
  overflow-y: auto;
}

.section-header {
  display: flex;
  align-items: center;
  padding: 0 10px;
  margin-bottom: 12px;
  color: var(--section-text);
  font-size: 11px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.history-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 10px;
  color: var(--history-text);
  font-size: 13px;
  cursor: pointer;
  border-radius: 6px;
  transition: background-color 0.2s ease;
}

.history-list li:hover {
  background-color: var(--history-hover);
}

.truncate {
  overflow: hidden;
  max-width: 140px;
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

.greeting {
  margin: 0 0 40px;
  color: var(--title-text);
  font-size: 28px;
  font-weight: 500;
  text-align: center;
  transition: color 0.2s ease;
}

.interaction-container {
  width: 100%;
  max-width: 720px;
}

.custom-mode-selector {
  position: relative;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 16px;
  z-index: 4;
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

.input-panel {
  position: relative;
  z-index: 1;
  margin-bottom: 24px;
  padding: 18px 18px 16px;
  background-color: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 16px;
  box-shadow: var(--panel-shadow);
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
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

.input-panel textarea::placeholder {
  color: var(--placeholder-text);
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.icon-btn,
.suggestion-item {
  border: 0;
  background: transparent;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--toolbar-icon);
  font-size: 18px;
  cursor: pointer;
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

.suggestions-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.suggestion-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  color: var(--suggestion-text);
  font-size: 14px;
  text-align: left;
  border-top: 1px solid var(--suggestion-border);
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.suggestion-item:hover {
  background-color: var(--suggestion-hover);
  border-radius: 8px;
}

.suggestion-item .icon {
  color: var(--suggestion-icon);
}

.nav-item:focus-visible,
.mode-btn:focus-visible,
.doc-select:focus-visible,
.icon-btn:focus-visible,
.send-btn:focus-visible,
.suggestion-item:focus-visible {
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
}

@media (max-width: 640px) {

  .workspace {
    padding: 32px 16px 24px;
  }

  .greeting {
    font-size: 24px;
    margin-bottom: 28px;
  }

  .input-toolbar {
    margin-top: 14px;
  }

  .doc-select {
    width: 100%;
  }

  .suggestion-item {
    padding-left: 12px;
    padding-right: 12px;
  }
}
</style>
