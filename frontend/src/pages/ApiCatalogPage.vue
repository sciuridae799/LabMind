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
const completedApiItemIdSet = new Set([
  'chatApi:openStream',
  'chatApi:listSessionsPage',
  'chatApi:getSession',
  'chatApi:deleteSession'
])

let openTimerId: number | null = null
let openTargetId: string | null = null
let closeTimerId: number | null = null
let closeTargetId: string | null = null

function buildItemId(groupTitle: string, itemName: string): string {
  return `${groupTitle}:${itemName}`
}

function isCompletedItem(itemId: string): boolean {
  return completedApiItemIdSet.has(itemId)
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
  const catalogCardElement = itemElement?.closest<HTMLElement>('.admin-content-panel')

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
  <section class="admin-content-page">
    <article class="admin-content-panel">
      <h1 class="admin-content-title">前端 API 目录</h1>

      <dl class="catalog-stats">
        <div class="catalog-stat-card">
          <dt>接口函数总数</dt>
          <dd>{{ totalApiCount }}</dd>
        </div>
        <div class="catalog-stat-card">
          <dt>接口分组数</dt>
          <dd>{{ apiGroupCount }}</dd>
        </div>
      </dl>
    </article>

    <article
      v-for="group in apiCatalogGroups"
      :key="group.title"
      class="admin-content-panel"
    >
      <div class="catalog-section-head">
        <div>
          <h2 class="catalog-section-title">{{ group.description }}</h2>
        </div>
        <span class="catalog-section-count">{{ group.items.length }} 个</span>
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
              <code class="api-item-name">{{ item.name }}</code>
              <div class="api-item-badges">
                <span
                  v-if="isCompletedItem(buildItemId(group.title, item.name))"
                  class="api-status api-status-completed"
                >
                  已完成
                </span>
                <span class="api-method">{{ item.requestMethod }}</span>
              </div>
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
    </article>
  </section>
</template>

<style scoped>
.admin-content-page {
  gap: 16px;
}

.catalog-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0 0;
}

.catalog-stat-card {
  padding: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  background: #fafafa;
}

.catalog-stat-card dt {
  margin: 0;
  color: #999999;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.catalog-stat-card dd {
  margin: 10px 0 0;
  color: #222222;
  font-size: 24px;
  line-height: 1;
  font-weight: 600;
}

.catalog-section-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.catalog-section-title {
  margin: 0;
  color: #222222;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 600;
}

.catalog-section-count {
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid #e7eaee;
  background: #f7f8fa;
  color: #444444;
  font-size: 12px;
  font-weight: 600;
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
  border-radius: 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
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
  border-color: #d7dce2;
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
  background: #ffffff;
}

.api-item.is-active {
  z-index: 4;
}

.api-item-head {
  display: grid;
  gap: 10px;
  align-items: start;
}

.api-item-badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 8px;
}

.api-item-name,
.api-item-detail code {
  overflow-wrap: anywhere;
  word-break: normal;
  font-family:
    ui-monospace,
    SFMono-Regular,
    SF Mono,
    Menlo,
    Monaco,
    Consolas,
    Liberation Mono,
    Courier New,
    monospace;
}

.api-item-name {
  color: #222222;
  font-size: 13px;
  font-weight: 600;
}

.api-method {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid #e7eaee;
  background: #ffffff;
  color: #444444;
  font-size: 12px;
  font-weight: 600;
}

.api-status {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.api-status-completed {
  border: 1px solid #b7e4c7;
  background: #eefbf3;
  color: #166534;
}

.api-item-summary {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #555555;
}

.api-item-detail {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  right: 0;
  padding: 16px;
  border: 1px solid #e5e5e5;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.08);
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
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #999999;
}

.api-item-meta dd {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.6;
  color: #333333;
}

@media (max-width: 1080px) {
  .catalog-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .catalog-stats {
    grid-template-columns: 1fr;
  }

  .catalog-section-head {
    flex-direction: column;
  }

  .api-item-detail {
    left: -4px;
    right: -4px;
  }
}
</style>
