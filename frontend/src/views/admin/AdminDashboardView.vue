<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { manageApi } from '../../shared/api/manage'
import { useAuthSession } from '../../shared/auth/authSession'

interface DocumentRow {
  parseStatus: string
  strategyStatus: string
  indexStatus: string
}

interface DocumentPageResponse {
  documents?: DocumentRow[]
  totalSize?: string | number
}

const documentRows = ref<DocumentRow[]>([])
const totalSize = ref(0)
const statusMessage = ref('')
const authSession = useAuthSession()

const parsedSuccessCount = computed(() => {
  return documentRows.value.filter((row) => row.parseStatus === '3').length
})

const strategyConfirmedCount = computed(() => {
  return documentRows.value.filter((row) => row.strategyStatus === '3').length
})

const indexedCount = computed(() => {
  return documentRows.value.filter((row) => row.indexStatus === '3').length
})

const stats = computed(() => [
  {
    title: '文档总数',
    value: String(totalSize.value),
    note: totalSize.value > 0 ? '已接入业务资料' : '当前还没有接入业务资料'
  },
  {
    title: '解析完成',
    value: String(parsedSuccessCount.value),
    note: parsedSuccessCount.value > 0 ? '可进入当前文档问答' : '还没有可问答正文'
  },
  {
    title: '策略已确认',
    value: String(strategyConfirmedCount.value),
    note: strategyConfirmedCount.value > 0 ? '已有确认后的处理方案' : '还没有确认策略'
  },
  {
    title: '索引完成',
    value: String(indexedCount.value),
    note: indexedCount.value > 0 ? '已有可参与检索的内容' : '还没有可参与检索的内容'
  }
])

function normalizeError(error: unknown): string {
  return error instanceof Error ? error.message : '状态加载失败'
}

async function loadDashboardStats(): Promise<void> {
  try {
    const response = await manageApi.queryDocumentPage({
      workspaceId: authSession.value?.workspaceId ?? '',
      keyword: '',
      pageNo: '1',
      pageSize: '1000'
    }) as DocumentPageResponse
    documentRows.value = response.documents ?? []
    totalSize.value = Number(response.totalSize ?? documentRows.value.length)
    statusMessage.value = ''
  } catch (error) {
    statusMessage.value = normalizeError(error)
  }
}

onMounted(() => {
  void loadDashboardStats()
})
</script>

<template>
  <section class="overview-page">
    <article class="panel">
      <div class="section-heading">
        <div>
          <h1 class="section-title">当前状态</h1>
          <p class="section-subtitle">{{ authSession?.workspaceName || '当前工作组' }} 的文档接入、解析、策略和索引状态</p>
        </div>
      </div>
      <p
        v-if="statusMessage"
        class="status-message"
      >
        {{ statusMessage }}
      </p>

      <div class="stats-grid">
        <article
          v-for="item in stats"
          :key="item.title"
          class="stat-card"
        >
          <p class="stat-title">{{ item.title }}</p>
          <strong class="stat-value">{{ item.value }}</strong>
          <p class="stat-note">{{ item.note }}</p>
        </article>
      </div>
    </article>
  </section>
</template>

<style scoped>
.overview-page,
.overview-page * {
  box-sizing: border-box;
}

.overview-page {
  display: grid;
  gap: var(--admin-page-gap);
}

.panel {
  padding: var(--admin-panel-padding);
  border: 1px solid var(--admin-color-border);
  border-radius: var(--admin-radius-panel);
  background: var(--admin-color-card);
  box-shadow: var(--admin-shadow-panel);
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  margin: 0;
  color: var(--admin-color-title);
  font-size: var(--admin-title-size);
  line-height: 1.2;
  font-weight: 600;
}

.section-subtitle {
  margin: 8px 0 0;
  color: var(--admin-color-muted);
  font-size: var(--admin-subtitle-size);
  line-height: 1.6;
}

.status-message {
  margin: 14px 0 0;
  padding: 10px 12px;
  border: 1px solid var(--admin-color-danger-border);
  border-radius: var(--admin-radius-control);
  background: var(--admin-color-danger-soft);
  color: var(--admin-color-danger);
  font-size: var(--admin-subtitle-size);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.stat-card {
  padding: 16px;
  border: 1px solid #d8e6e8;
  border-radius: var(--admin-radius-panel);
  background: var(--admin-color-card-soft);
}

.stat-title {
  margin: 0;
  color: var(--admin-color-muted);
  font-size: 12px;
  font-weight: 700;
}

.stat-value {
  display: block;
  margin-top: 10px;
  color: var(--admin-color-text);
  font-size: 24px;
  line-height: 1;
  font-weight: 600;
}

.stat-note {
  margin: 10px 0 0;
  color: var(--admin-color-subtle);
  font-size: var(--admin-subtitle-size);
  line-height: 1.6;
}

@media (max-width: 768px) {
  .panel {
    padding: var(--admin-panel-padding);
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
