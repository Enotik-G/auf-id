import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // При разработке интерфейс живёт на http://localhost:5173, а AUF ID — на :8080. Запросы к API
    // Vite пересылает в AUF ID: для браузера они идут на тот же адрес, что и страница, — как будет
    // на сервере, где интерфейс и API отдаёт одно приложение.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
