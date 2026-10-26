import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    // 独立部署在 /merchant/，与运营后台的 / 分离
    base: '/merchant/',
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
        // 共享层位于同仓 solo-eats-web/shared，两工程只共享 api/utils/constants/stores
        '@shared': fileURLToPath(new URL('../solo-eats-web/shared', import.meta.url)),
      },
    },
    server: {
      port: 5174,
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
