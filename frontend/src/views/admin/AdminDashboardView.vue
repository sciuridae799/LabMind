<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { manageApi } from '../../shared/api/manage'

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
      <h1 class="section-title">当前状态</h1>
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
}

.panel {
  padding: 20px;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.section-title {
  margin: 0;
  color: #222222;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 600;
}

.status-message {
  margin: 10px 0 0;
  color: #b42318;
  font-size: 13px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.stat-card {
  padding: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;
}

.stat-title {
  margin: 0;
  color: #777777;
  font-size: 12px;
  font-weight: 700;
}

.stat-value {
  display: block;
  margin-top: 10px;
  color: #222222;
  font-size: 24px;
  line-height: 1;
  font-weight: 600;
}

.stat-note {
  margin: 10px 0 0;
  color: #555555;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 1080px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .panel {
    padding: 18px;
  }

  .section-title {
    font-size: 20px;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
