<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const conversationId = computed(() => String(route.params.conversationId || ''))
</script>

<template>
  <section class="admin-content-page">
    <div class="admin-content-panel">
      <p class="admin-content-eyebrow">Session Trace</p>
      <h2 class="admin-content-title">会话链路</h2>
      <p class="admin-content-copy">
        当前会话 ID：
        <span class="admin-content-inline-code">{{ conversationId || '未提供 conversationId' }}</span>
      </p>
    </div>
  </section>
</template>
