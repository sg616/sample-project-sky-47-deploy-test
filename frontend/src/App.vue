<template>
  <main class="container">
    <h1>Sample Deployment Test App</h1>
    <p class="subtitle">Java 11 backend + Vue 3 frontend + MySQL (sky47)</p>

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
      <div class="catalog-header">
        <h2>Product Catalog</h2>
        <div>
          <button v-if="!formVisible" @click="openCreate">+ Add Product</button>
          <button v-else class="secondary" @click="cancelForm">Cancel</button>
          <button class="ghost" @click="loadProducts" :disabled="loading">Refresh</button>
        </div>
      </div>

      <form v-if="formVisible" class="product-form" @submit.prevent="save">
        <h3>{{ editingId ? 'Edit Product' : 'New Product' }}</h3>
        <label>Name *<input v-model.trim="form.name" maxlength="100" required /></label>
        <label>Description<textarea v-model.trim="form.description" maxlength="500" rows="2"></textarea></label>
        <label>Price *<input v-model.number="form.price" type="number" min="0" step="0.01" required /></label>
        <label>Stock *<input v-model.number="form.stock" type="number" min="0" step="1" required /></label>
        <button type="submit" :disabled="submitting">{{ submitting ? 'Saving…' : 'Save' }}</button>
      </form>

      <table v-if="products.length" class="catalog">
        <thead>
          <tr>
            <th>Name</th>
            <th>Description</th>
            <th class="num">Price</th>
            <th class="num">Stock</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in products" :key="p.id">
            <td>{{ p.name }}</td>
            <td class="desc">{{ p.description }}</td>
            <td class="num">${{ formatPrice(p.price) }}</td>
            <td class="num"><span class="badge" :class="stockClass(p.stock)">{{ p.stock === 0 ? 'Out of stock' : p.stock }}</span></td>
            <td class="actions">
              <button class="small secondary" @click="openEdit(p)">Edit</button>
              <button class="small danger" @click="remove(p)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else-if="!loading" class="empty">No products yet — add one above.</p>
    </section>

    <p v-if="error" class="error">{{ error }}</p>
  </main>
</template>

<script>
import { ref, reactive, onMounted } from 'vue'

export default {
  name: 'App',
  setup() {
    const health = ref('')
    const info = ref('')
    const products = ref([])
    const formVisible = ref(false)
    const editingId = ref(null)
    const form = reactive({ name: '', description: '', price: null, stock: null })
    const error = ref('')
    const loading = ref(false)
    const submitting = ref(false)

    async function callApi(path, options) {
      error.value = ''
      const res = await fetch(path, options)
      const body = await res.json().catch(() => null)
      if (!res.ok) {
        const message = body && body.message
          ? body.message
          : body && body.errors
            ? Object.values(body.errors).join('; ')
            : `HTTP ${res.status}`
        throw new Error(message)
      }
      return body
    }

    async function checkHealth() {
      loading.value = true
      try {
        health.value = JSON.stringify(await callApi('/api/health'), null, 2)
      } catch (e) {
        error.value = `Health check failed: ${e.message}`
      } finally {
        loading.value = false
      }
    }

    async function fetchInfo() {
      loading.value = true
      try {
        info.value = JSON.stringify(await callApi('/api/info'), null, 2)
      } catch (e) {
        error.value = `Info request failed: ${e.message}`
      } finally {
        loading.value = false
      }
    }

    async function loadProducts() {
      loading.value = true
      try {
        products.value = await callApi('/api/products')
      } catch (e) {
        error.value = `Failed to load products: ${e.message}`
      } finally {
        loading.value = false
      }
    }

    function openCreate() {
      editingId.value = null
      Object.assign(form, { name: '', description: '', price: null, stock: null })
      formVisible.value = true
    }

    function openEdit(p) {
      editingId.value = p.id
      Object.assign(form, {
        name: p.name,
        description: p.description || '',
        price: Number(p.price),
        stock: p.stock
      })
      formVisible.value = true
    }

    function cancelForm() {
      formVisible.value = false
      editingId.value = null
    }

    async function save() {
      submitting.value = true
      try {
        const payload = {
          name: form.name,
          description: form.description,
          price: form.price,
          stock: form.stock
        }
        if (editingId.value) {
          await callApi(`/api/products/${editingId.value}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
          })
        } else {
          await callApi('/api/products', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
          })
        }
        cancelForm()
        await loadProducts()
      } catch (e) {
        error.value = `Save failed: ${e.message}`
      } finally {
        submitting.value = false
      }
    }

    async function remove(p) {
      if (!window.confirm(`Delete "${p.name}"?`)) return
      loading.value = true
      try {
        await callApi(`/api/products/${p.id}`, { method: 'DELETE' })
        await loadProducts()
      } catch (e) {
        error.value = `Delete failed: ${e.message}`
      } finally {
        loading.value = false
      }
    }

    function formatPrice(value) {
      return Number(value).toFixed(2)
    }

    function stockClass(stock) {
      if (stock === 0) return 'out'
      if (stock <= 10) return 'low'
      return 'ok'
    }

    onMounted(loadProducts)

    return {
      health, info, products, formVisible, editingId, form, error, loading, submitting,
      checkHealth, fetchInfo, loadProducts, openCreate, openEdit, cancelForm, save, remove,
      formatPrice, stockClass
    }
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
  max-width: 760px;
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
.catalog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.catalog-header h2 {
  margin: 0;
}
.catalog-header button {
  margin-left: 0.5rem;
}
.product-form {
  display: grid;
  gap: 0.5rem;
  margin: 1rem 0;
  padding: 1rem;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.product-form h3 {
  margin: 0;
}
.product-form label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #374151;
}
.product-form input,
.product-form textarea {
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font: inherit;
  resize: vertical;
}
table.catalog {
  width: 100%;
  border-collapse: collapse;
  margin-top: 1rem;
}
table.catalog th,
table.catalog td {
  text-align: left;
  padding: 0.5rem;
  border-bottom: 1px solid #e5e7eb;
  vertical-align: top;
}
table.catalog th {
  font-size: 0.8rem;
  text-transform: uppercase;
  color: #6b7280;
}
td.num {
  text-align: right;
  white-space: nowrap;
}
td.desc {
  color: #6b7280;
  font-size: 0.9rem;
}
td.actions {
  white-space: nowrap;
}
.badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.8rem;
}
.badge.ok { background: #d1fae5; color: #065f46; }
.badge.low { background: #fef3c7; color: #92400e; }
.badge.out { background: #fee2e2; color: #991b1b; }
.empty {
  color: #6b7280;
  margin-top: 1rem;
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
button.secondary {
  background: #4b5563;
}
button.ghost {
  background: #fff;
  color: #2563eb;
  border: 1px solid #d1d5db;
}
button.danger {
  background: #dc2626;
}
button.small {
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
  margin-right: 0.25rem;
}
input {
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
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
