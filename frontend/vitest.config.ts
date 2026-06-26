import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    environmentOptions: {
      jsdom: {
        // Nao usar localstorage-file (causa localStorage.getItem is not a function)
        storageQuota: 10000000,
      },
    },
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
