<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { apiCatalogGroups } from '../shared/api'

const totalApiCount = apiCatalogGroups.reduce((count, group) => count + group.items.length, 0)
const apiGroupCount = apiCatalogGroups.length
const openDelayMs = 1000
const closeDelayMs = 200
const detailGapPx = 10
const viewportPaddingPx = 16
const catalogCardPaddingPx = 20

const activeItemId = ref<string | null>(null)
const activeItemPlacement = ref<'below' | 'above'>('below')

let openTimerId: number | null = null
let openTargetId: string | null = null
let closeTimerId: number | null = null
let closeTargetId: string | null = null

function buildItemId(groupTitle: string, itemName: string): string {
  return `${groupTitle}:${itemName}`
}

function clearOpenTimer(): void {
  if (openTimerId !== null) {
    window.clearTimeout(openTimerId)
  }
  openTimerId = null
  openTargetId = null
}

function clearCloseTimer(): void {
  if (closeTimerId !== null) {
    window.clearTimeout(closeTimerId)
  }
  closeTimerId = null
  closeTargetId = null
}

function findApiItemElement(itemId: string): HTMLElement | null {
  return document.querySelector<HTMLElement>(`[data-api-item-id="${itemId}"]`)
}

function resolveItemPlacement(itemId: string): 'below' | 'above' {
  const itemElement = findApiItemElement(itemId)
  const detailElement = itemElement?.querySelector<HTMLElement>('.api-item-detail')
  const catalogCardElement = itemElement?.closest<HTMLElement>('.catalog-card')

  if (!itemElement || !detailElement) {
    return 'below'
  }

  const itemRect = itemElement.getBoundingClientRect()
  const detailRect = detailElement.getBoundingClientRect()
  const detailHeight = detailRect.height
  const viewportBelow = window.innerHeight - itemRect.bottom - detailGapPx - viewportPaddingPx
  const viewportAbove = itemRect.top - detailGapPx - viewportPaddingPx
  const catalogCardRect = catalogCardElement?.getBoundingClientRect()
  const sectionBelow = catalogCardRect
    ? catalogCardRect.bottom - itemRect.bottom - detailGapPx - catalogCardPaddingPx
    : viewportBelow
  const fitsBelowViewport = detailHeight <= viewportBelow
  const fitsBelowSection = detailHeight <= sectionBelow
  const fitsBelow = fitsBelowViewport && fitsBelowSection
  const fitsAbove = detailHeight <= viewportAbove

  if (!fitsBelow && fitsAbove) {
    return 'above'
  }

  const effectiveBelow = Math.min(viewportBelow, sectionBelow)

  if (!fitsBelow && !fitsAbove && viewportAbove > effectiveBelow) {
    return 'above'
  }

  return 'below'
}

async function activateItem(itemId: string): Promise<void> {
  clearOpenTimer()
  clearCloseTimer()
  activeItemId.value = itemId
  activeItemPlacement.value = resolveItemPlacement(itemId)

  await nextTick()

  if (activeItemId.value === itemId) {
    activeItemPlacement.value = resolveItemPlacement(itemId)
  }
}

function openItemNow(itemId: string): void {
  void activateItem(itemId)
}

function scheduleOpen(itemId: string): void {
  if (closeTargetId === itemId) {
    clearCloseTimer()
  }

  if (activeItemId.value === itemId) {
    return
  }

  clearOpenTimer()
  openTargetId = itemId
  openTimerId = window.setTimeout(() => {
    void activateItem(itemId)
    clearOpenTimer()
  }, openDelayMs)
}

function scheduleClose(itemId: string): void {
  if (openTargetId === itemId) {
    clearOpenTimer()
  }

  if (activeItemId.value !== itemId) {
    return
  }

  clearCloseTimer()
  closeTargetId = itemId
  closeTimerId = window.setTimeout(() => {
    if (activeItemId.value === itemId) {
      activeItemId.value = null
      activeItemPlacement.value = 'below'
    }
    clearCloseTimer()
  }, closeDelayMs)
}

function isActiveItem(itemId: string): boolean {
  return activeItemId.value === itemId
}

function isActiveItemAbove(itemId: string): boolean {
  return activeItemId.value === itemId && activeItemPlacement.value === 'above'
}

function syncActiveItemPlacement(): void {
  if (!activeItemId.value) {
    return
  }

  activeItemPlacement.value = resolveItemPlacement(activeItemId.value)
}

onMounted(() => {
  window.addEventListener('resize', syncActiveItemPlacement)
  window.addEventListener('scroll', syncActiveItemPlacement, true)
})

onBeforeUnmount(() => {
  clearOpenTimer()
  clearCloseTimer()
  window.removeEventListener('resize', syncActiveItemPlacement)
  window.removeEventListener('scroll', syncActiveItemPlacement, true)
})
</script>

<template>
  <main class="page-shell">
    <section class="hero-card">
      <p class="eyebrow">API Query Catalog</p>
      <h1>当前页面 API 查询目录</h1>
      <p class="hero-copy">
        目录直接消费接口层导出的元数据。鼠标悬停 1 秒后展开当前 API 细节，移开 0.2 秒后自动收起，并根据可视空间自动向上或向下展开。
      </p>

      <dl class="hero-stats">
        <div>
          <dt>接口函数总数</dt>
          <dd>{{ totalApiCount }}</dd>
        </div>
        <div>
          <dt>接口分组数</dt>
          <dd>{{ apiGroupCount }}</dd>
        </div>
        <div>
          <dt>悬浮展开节奏</dt>
          <dd>1 秒开 / 0.2 秒关</dd>
        </div>
      </dl>
    </section>

    <section
      v-for="group in apiCatalogGroups"
      :key="group.title"
      class="catalog-card"
    >
      <div class="section-head">
        <div>
          <p class="section-label">{{ group.title }}</p>
          <h2>{{ group.description }}</h2>
        </div>
        <span class="section-count">{{ group.items.length }} 个</span>
      </div>

      <ul class="api-list">
        <li
          v-for="item in group.items"
          :key="buildItemId(group.title, item.name)"
        >
          <article
            class="api-item"
            :class="{
              'is-active': isActiveItem(buildItemId(group.title, item.name)),
              'opens-upward': isActiveItemAbove(buildItemId(group.title, item.name))
            }"
            :data-api-item-id="buildItemId(group.title, item.name)"
            tabindex="0"
            @mouseenter="scheduleOpen(buildItemId(group.title, item.name))"
            @mouseleave="scheduleClose(buildItemId(group.title, item.name))"
            @focusin="openItemNow(buildItemId(group.title, item.name))"
            @focusout="scheduleClose(buildItemId(group.title, item.name))"
          >
            <div class="api-item-head">
              <code>{{ item.name }}</code>
              <span class="api-method">{{ item.requestMethod }}</span>
            </div>
            <div class="api-item-detail">
              <p class="api-item-summary">{{ item.summary }}</p>
              <dl class="api-item-meta">
                <div>
                  <dt>路径</dt>
                  <dd>
                    <code>{{ item.path }}</code>
                  </dd>
                </div>
                <div>
                  <dt>关键入参</dt>
                  <dd>{{ item.keyInputs }}</dd>
                </div>
              </dl>
            </div>
          </article>
        </li>
      </ul>
    </section>
  </main>
</template>

<style scoped>
.page-shell {
  min-height: 100vh;
  padding: 32px 20px 48px;
  background:
    radial-gradient(circle at top left, rgba(255, 216, 160, 0.7), transparent 32%),
    radial-gradient(circle at top right, rgba(129, 199, 255, 0.6), transparent 28%),
    linear-gradient(180deg, #fff8ee 0%, #f5f7fb 52%, #eef2f8 100%);
  color: #1f2937;
}

.hero-card,
.catalog-card {
  width: min(1100px, 100%);
  margin: 0 auto;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(16px);
}

.hero-card {
  padding: 32px;
}

.catalog-card {
  margin-top: 20px;
  padding: 24px 28px 28px;
}

.eyebrow,
.section-label {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #b45309;
}

h1,
h2 {
  margin: 0;
  font-family: "Avenir Next", "PingFang SC", "Hiragino Sans GB", sans-serif;
}

h1 {
  font-size: clamp(32px, 4vw, 48px);
  line-height: 1.05;
}

h2 {
  font-size: 22px;
  line-height: 1.3;
}

.hero-copy {
  max-width: 760px;
  margin: 18px 0 0;
  font-size: 16px;
  line-height: 1.7;
  color: #475569;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin: 28px 0 0;
}

.hero-stats div {
  padding: 18px 20px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(249, 250, 251, 0.88));
}

dt {
  font-size: 13px;
  color: #64748b;
}

dd {
  margin: 10px 0 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

code {
  word-break: break-all;
  font-family: "SFMono-Regular", "JetBrains Mono", "Fira Code", monospace;
}

.section-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.section-count {
  padding: 8px 12px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.api-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 12px;
  margin: 20px 0 0;
  padding: 0;
  list-style: none;
}

.api-list li {
  position: relative;
  min-width: 0;
}

.api-item {
  position: relative;
  min-height: 84px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.18);
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease,
    background-color 180ms ease,
    z-index 0ms linear 180ms;
  outline: none;
  overflow: visible;
}

.api-item:hover,
.api-item:focus-visible,
.api-item.is-active {
  border-color: rgba(37, 99, 235, 0.35);
  box-shadow: 0 18px 40px rgba(37, 99, 235, 0.12);
  transform: translateY(-1px);
  background: linear-gradient(180deg, #f8fbff 0%, #eff6ff 100%);
}

.api-item.is-active {
  z-index: 4;
}

.api-item-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.api-method {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(191, 219, 254, 0.75);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.api-item-summary {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #475569;
}

.api-item-detail {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  right: 0;
  padding: 16px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 22px 44px rgba(15, 23, 42, 0.14);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(8px);
  transition:
    opacity 180ms ease,
    transform 180ms ease,
    visibility 0ms linear 180ms;
}

.api-item.opens-upward .api-item-detail {
  top: auto;
  bottom: calc(100% + 10px);
  transform: translateY(-8px);
}

.api-item.is-active .api-item-detail {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateY(0);
  transition:
    opacity 180ms ease,
    transform 180ms ease,
    visibility 0ms linear 0ms;
}

.api-item-meta {
  display: grid;
  gap: 10px;
  margin: 14px 0 0;
}

.api-item-meta div {
  display: grid;
  gap: 6px;
}

.api-item-meta dt {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.api-item-meta dd {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .page-shell {
    padding: 20px 14px 36px;
  }

  .hero-card,
  .catalog-card {
    border-radius: 20px;
  }

  .hero-card {
    padding: 24px 20px;
  }

  .catalog-card {
    padding: 20px;
  }

  .section-head {
    flex-direction: column;
  }

  .api-item-detail {
    left: -4px;
    right: -4px;
  }
}
</style>
