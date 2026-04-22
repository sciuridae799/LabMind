<script setup lang="ts">
type FormField = {
  label: string
  placeholder: string
}

const fields: FormField[] = [
  { label: '文档名称', placeholder: '不填则使用原始文件名' },
  { label: '知识域编码', placeholder: '例如 operation_rule' },
  { label: '知识域名称', placeholder: '例如 运营规则' },
  { label: '业务分类', placeholder: '例如 手册 / 规则 / 介绍' },
  { label: '文档标签', placeholder: '多个标签用英文逗号分隔' }
]
</script>

<template>
  <section class="document-page">
    <article class="panel">
      <h1 class="section-title">上传资料并进入推荐流程</h1>

      <div class="form-grid">
        <label
          v-for="field in fields"
          :key="field.label"
          class="field"
        >
          <span class="field-label">{{ field.label }}</span>
          <input :placeholder="field.placeholder">
        </label>

        <div class="field field-full">
          <span class="field-label">选择文件</span>
          <div class="file-picker">
            <button
              type="button"
              class="file-button"
            >
              选择文件
            </button>
            <span class="file-name">未选择文件</span>
          </div>
        </div>
      </div>

      <div class="upload-summary">
        <div class="summary-copy">
          <p class="summary-label">支持 PDF / DOC / DOCX / TXT / MD / HTML</p>
          <strong class="summary-name">尚未选择文件</strong>
        </div>

        <div class="summary-actions">
          <button
            type="button"
            class="button secondary"
          >
            清空
          </button>
          <button
            type="button"
            class="button primary"
          >
            上传并解析
          </button>
        </div>
      </div>
    </article>

    <article class="panel">
      <div class="list-header">
        <div>
          <h2 class="list-title">文档列表</h2>
          <p class="list-meta">共 0 份文档，当前第 1 页。</p>
        </div>

        <div class="search-bar">
          <input placeholder="搜索文档名称或原始文件名">
          <button
            type="button"
            class="button primary"
          >
            搜索
          </button>
        </div>
      </div>

      <div class="empty-actions">
        <button
          type="button"
          class="button secondary"
        >
          去上传资料
        </button>
      </div>
    </article>
  </section>
</template>

<style scoped>
.document-page,
.document-page * {
  box-sizing: border-box;
}

.document-page {
  display: grid;
  gap: 16px;
}

.panel {
  padding: 20px;
  border: 1px solid #e5e5e5;
  border-radius: 16px;
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

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.field {
  display: grid;
  gap: 6px;
}

.field-full {
  grid-column: 1 / -1;
}

.field-label {
  color: #555555;
  font-size: 13px;
  font-weight: 600;
}

input {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  border: 1px solid #dddddd;
  border-radius: 10px;
  outline: none;
  background: #ffffff;
  color: #222222;
  font-size: 13px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

input::placeholder {
  color: #aaaaaa;
}

input:focus {
  border-color: #c5dcff;
  box-shadow: 0 0 0 3px rgba(197, 220, 255, 0.35);
}

.file-picker {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 4px;
  border: 1px solid #dddddd;
  border-radius: 10px;
  background: #ffffff;
}

.file-button,
.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.file-button {
  border: 1px solid #e7eaee;
  background: #f7f8fa;
  color: #444444;
}

.file-name {
  color: #999999;
  font-size: 13px;
}

.upload-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px dashed #e5e7eb;
  border-radius: 12px;
  background: #fafafa;
}

.summary-label {
  margin: 0;
  color: #999999;
  font-size: 12px;
}

.summary-name {
  display: block;
  margin-top: 4px;
  color: #222222;
  font-size: 14px;
}

.summary-actions {
  display: flex;
  gap: 8px;
}

.button {
  border: 1px solid #e7eaee;
  background: #ffffff;
  color: #444444;
}

.button.primary {
  border-color: #222222;
  background: #222222;
  color: #ffffff;
}

.button:hover,
.file-button:hover {
  transform: translateY(-1px);
}

.button.primary:hover {
  background: #111111;
  border-color: #111111;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.list-meta {
  margin: 10px 0 0;
  color: #555555;
  font-size: 13px;
  line-height: 1.7;
}

.search-bar {
  display: flex;
  gap: 8px;
  width: 360px;
  max-width: 100%;
}

.search-bar input {
  flex: 1;
}

.empty-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid #f0f0f0;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .list-header,
  .upload-summary {
    flex-direction: column;
    align-items: stretch;
  }

  .search-bar {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .panel {
    padding: 18px;
  }

  .section-title,
  .list-title {
    font-size: 20px;
  }
}
</style>
