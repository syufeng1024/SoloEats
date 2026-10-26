<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome(): void {
  void router.replace('/orders')
}
</script>

<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在或已被移除。</p>
    <el-button type="primary" @click="goHome">返回订单管理</el-button>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 70vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.not-found h1 {
  font-size: 56px;
  color: var(--el-color-primary);
}

.not-found p {
  color: var(--app-muted);
}
</style>
