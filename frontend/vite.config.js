import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxy = Object.fromEntries([
    ['users', env.USER_SERVICE_URL || 'http://localhost:8082'],
    ['trips', env.TRIP_SERVICE_URL || 'http://localhost:8083'],
    ['expenses', env.EXPENSES_SERVICE_URL || 'http://localhost:8084'],
    ['weather', env.WEATHER_SERVICE_URL || 'http://localhost:8085'],
    ['currency', env.CURRENCY_SERVICE_URL || 'http://localhost:8086'],
  ].map(([service, target]) => [`/api/${service}`, {
    target, changeOrigin: true, rewrite: path => path.replace(new RegExp(`^/api/${service}`), ''),
  }]))
  return { plugins: [react()], server: { proxy }, preview: { proxy } }
})
