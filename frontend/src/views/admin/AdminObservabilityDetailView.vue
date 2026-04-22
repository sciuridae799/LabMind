<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const conversationId = computed(() => String(route.params.conversationId || ''))
const exchangeId = computed(() => String(route.params.exchangeId || ''))
</script>

<template>
  <section class="admin-content-page">
    <div class="admin-content-panel">
      <p class="admin-content-eyebrow">Exchange Detail</p>
      <h2 class="admin-content-title">轮次详情</h2>
      <p class="admin-content-copy">
        当前会话 ID：
        <span class="admin-content-inline-code">{{ conversationId || '未提供 conversationId' }}</span>
      </p>
      <p class="admin-content-copy">
        当前轮次 ID：
        <span class="admin-content-inline-code">{{ exchangeId || '未提供 exchangeId' }}</span>
      </p>
    </div>
  </section>
</template>
