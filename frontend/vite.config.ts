import path from 'path'
import { fileURLToPath } from 'url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// Repo kökü `.env`: lokal `npm run dev` / `npm run build`.
// Docker image build: Dockerfile `ENV DOCKER_BUILD=1` — sadece bu klasördeki `.env` dosyası okunur;
// Grafana vb. değerler ARG/ENV ile process.env’e gelir (parent `/` envDir karışıklığı olmasın diye).
export default defineConfig({
    plugins: [react()],
    envDir:
        process.env.DOCKER_BUILD === '1'
            ? path.resolve(__dirname)
            : path.resolve(__dirname, '..'),
})
