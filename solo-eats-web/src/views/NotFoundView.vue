<template>
  <div class="not-found">
    <div class="code mono">404</div>
    <h1>页面不存在</h1>
    <p>请求的路径未在路由表中注册。</p>
    <el-button type="primary" @click="$router.replace('/')">返回控制台</el-button>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px;
  text-align: center;
}

.code {
  font-size: 56px;
  color: var(--el-color-primary);
}

.not-found p {
  color: var(--app-muted);
  margin-bottom: 8px;
}
</style>
