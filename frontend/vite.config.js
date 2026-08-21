import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/orders-api': {
        // Locally the orders backend runs on 8081 (8080 is taken by the catalog backend)
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
