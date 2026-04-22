<script setup lang="ts">
const stats = [
  {
    title: '文档总数',
    value: '0',
    note: '当前还没有接入业务资料'
  },
  {
    title: '解析成功',
    value: '0',
    note: '还没有进入策略确认阶段'
  },
  {
    title: '策略已确认',
    value: '0',
    note: '还没有形成最终切块链路'
  },
  {
    title: '索引完成',
    value: '0',
    note: '还没有可参与检索的内容'
  }
] as const
</script>

<template>
  <section class="overview-page">
    <article class="panel">
      <h1 class="section-title">当前状态</h1>

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
  border-radius: 16px;
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

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.stat-card {
  padding: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  background: #fafafa;
}

.stat-title {
  margin: 0;
  color: #999999;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
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
