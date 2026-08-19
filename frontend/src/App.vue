<template>
  <main class="container">
    <h1>Sample Deployment Test App</h1>
    <p class="subtitle">Java 11 backend + Vue 3 frontend (sky47)</p>

    <section class="card">
      <h2>Backend Health</h2>
      <button @click="checkHealth" :disabled="loading">Check /api/health</button>
      <pre v-if="health">{{ health }}</pre>
    </section>

    <section class="card">
      <h2>Backend Info</h2>
      <button @click="fetchInfo" :disabled="loading">Fetch /api/info</button>
      <pre v-if="info">{{ info }}</pre>
    </section>

    <section class="card">
      <h2>Echo Test</h2>
      <input v-model="message" placeholder="Type a message" @keyup.enter="sendEcho" />
      <button @click="sendEcho" :disabled="loading || !message">POST /api/echo</button>
      <pre v-if="echo">{{ echo }}</pre>
    </section>

    <p v-if="error" class="error">{{ error }}</p>
  </main>
</template>

<script>
import { ref } from 'vue'

export default {
  name: 'App',
  setup() {
    const health = ref('')
    const info = ref('')
    const echo = ref('')
    const message = ref('')
    const error = ref('')
    const loading = ref(false)

    async function callApi(path, options) {
      loading.value = true
      error.value = ''
      try {
        const res = await fetch(path, options)
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return JSON.stringify(await res.json(), null, 2)
      } catch (e) {
        error.value = `Request failed: ${e.message}`
        return ''
      } finally {
        loading.value = false
      }
    }

    async function checkHealth() {
      health.value = await callApi('/api/health')
    }

    async function fetchInfo() {
      info.value = await callApi('/api/info')
    }

    async function sendEcho() {
      echo.value = await callApi('/api/echo', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: message.value })
      })
    }

    return { health, info, echo, message, error, loading, checkHealth, fetchInfo, sendEcho }
  }
}
</script>

<style>
body {
  margin: 0;
  font-family: system-ui, -apple-system, sans-serif;
  background: #f5f7fa;
  color: #1f2937;
}
.container {
  max-width: 640px;
  margin: 2rem auto;
  padding: 0 1rem;
}
.subtitle {
  color: #6b7280;
}
.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
}
button {
  background: #2563eb;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 0.5rem 1rem;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
input {
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  margin-right: 0.5rem;
}
pre {
  background: #f3f4f6;
  padding: 0.75rem;
  border-radius: 6px;
  overflow-x: auto;
}
.error {
  color: #dc2626;
}
</style>
