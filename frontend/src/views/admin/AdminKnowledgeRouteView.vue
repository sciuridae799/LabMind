<script setup lang="ts">
import { ref } from 'vue'
import { manageApi } from '../../shared/api/manage'

interface RouteCandidate {
  documentId: string
  documentName: string
  scopeCode: string
  scopeName: string
  topicCode: string
  topicName: string
  score: number
  hitReason: string
}

const question = ref('')
const candidates = ref<RouteCandidate[]>([])
const statusMessage = ref('')
const isRouting = ref(false)

function normalizeError(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败'
}

async function previewRoute(): Promise<void> {
  const normalizedQuestion = question.value.trim()
  if (!normalizedQuestion || isRouting.value) {
    statusMessage.value = '请输入要验证的问题'
    return
  }

  isRouting.value = true
  statusMessage.value = ''
  try {
    candidates.value = await manageApi.previewKnowledgeRoute({
      question: normalizedQuestion,
      limit: '10'
    }) as RouteCandidate[]
    if (candidates.value.length === 0) {
      statusMessage.value = '未命中文档候选'
    }
  } catch (error) {
    candidates.value = []
    statusMessage.value = normalizeError(error)
  } finally {
    isRouting.value = false
  }
}
</script>

<template>
  <section class="route-page">
    <article class="panel">
      <h1 class="section-title">知识路由预览</h1>
      <div class="route-input">
        <input
          v-model="question"
          placeholder="例如：年假怎么申请？"
          @keydown.enter="previewRoute"
        >
        <button
          type="button"
          class="button primary"
          :disabled="isRouting"
          @click="previewRoute"
        >
          {{ isRouting ? '路由中' : '预览路由' }}
        </button>
      </div>
      <p
        v-if="statusMessage"
        class="status-message"
      >
        {{ statusMessage }}
      </p>
    </article>

    <article class="panel">
      <div class="list-header">
        <h2 class="list-title">候选文档</h2>
        <span class="list-meta">{{ candidates.length }} 条候选</span>
      </div>

      <div
        v-if="candidates.length === 0"
        class="empty-state"
      >
        输入问题后查看 Neo4j 路由候选
      </div>

      <div
        v-else
        class="candidate-list"
      >
        <article
          v-for="candidate in candidates"
          :key="candidate.documentId"
          class="candidate-item"
        >
          <div>
            <strong>{{ candidate.documentName }}</strong>
            <span>{{ candidate.scopeName }} / {{ candidate.topicName }}</span>
          </div>
          <div class="candidate-meta">
            <span>{{ candidate.hitReason }}</span>
            <b>{{ candidate.score.toFixed(2) }}</b>
          </div>
        </article>
      </div>
    </article>
  </section>
</template>

<style scoped>
.route-page,
.route-page * {
  box-sizing: border-box;
}

.route-page {
  display: grid;
  gap: 16px;
}

.panel {
  padding: 20px;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.section-title,
.list-title {
  margin: 0;
  color: #222222;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 600;
}

.route-input {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-top: 16px;
}

input {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  border: 1px solid #dddddd;
  border-radius: 8px;
  outline: none;
  background: #ffffff;
  color: #222222;
  font-size: 13px;
}

input:focus {
  border-color: #c5dcff;
  box-shadow: 0 0 0 3px rgba(197, 220, 255, 0.35);
}

.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid #222222;
  border-radius: 8px;
  background: #222222;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.status-message,
.list-meta {
  margin: 8px 0 0;
  color: #777777;
  font-size: 13px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 140px;
  margin-top: 16px;
  border: 1px dashed #e5e7eb;
  border-radius: 8px;
  color: #777777;
  font-size: 13px;
}

.candidate-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.candidate-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border: 1px solid #eeeeee;
  border-radius: 8px;
}

.candidate-item strong,
.candidate-item span {
  display: block;
}

.candidate-item span {
  margin-top: 4px;
  color: #777777;
  font-size: 13px;
}

.candidate-meta {
  min-width: 160px;
  text-align: right;
}

.candidate-meta b {
  display: block;
  margin-top: 4px;
  color: #222222;
}

@media (max-width: 760px) {
  .route-input,
  .candidate-item {
    grid-template-columns: 1fr;
  }

  .route-input,
  .candidate-item {
    display: grid;
  }

  .candidate-meta {
    min-width: 0;
    text-align: left;
  }
}
</style>
