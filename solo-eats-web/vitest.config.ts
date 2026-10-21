import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

// 单元测试只覆盖纯 TS 工具与常量，不加载 Vue 插件，避免 Vite 版本类型互相干扰。
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.spec.ts'],
  },
})
