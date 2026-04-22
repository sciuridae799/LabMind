<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const documentId = computed(() => String(route.params.documentId || ''))
</script>

<template>
  <section class="admin-content-page">
    <div class="admin-content-panel">
      <p class="admin-content-eyebrow">Document Detail</p>
      <h2 class="admin-content-title">文档详情</h2>
      <p class="admin-content-copy">
        当前文档 ID：
        <span class="admin-content-inline-code">{{ documentId || '未提供 documentId' }}</span>
      </p>
    </div>
  </section>
</template>
