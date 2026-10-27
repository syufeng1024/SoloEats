<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

function goBack(): void {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.replace('/')
}
</script>

<template>
  <el-card shadow="never" class="placeholder-card">
    <el-empty description="该页面开发中">
      <div class="placeholder-body">
        <p class="placeholder-label">当前路由</p>
        <p class="placeholder-path mono">{{ route.path }}</p>
        <el-button type="primary" @click="goBack">返回</el-button>
      </div>
    </el-empty>
  </el-card>
</template>

<style scoped>
.placeholder-card {
  border: 1px solid var(--app-border);
}

.placeholder-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.placeholder-label {
  font-size: 12px;
  color: var(--app-muted);
}

.placeholder-path {
  font-size: 13px;
  color: #303133;
  word-break: break-all;
}
</style>
