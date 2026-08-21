<template>
  <div class="app">
    <header class="hero">
      <div class="hero-inner">
        <div>
          <h1>Sky47 Sample Platform</h1>
          <p class="tagline">Two Java backends, two databases, one frontend — ready for an API gateway</p>
        </div>
        <div class="hero-chips">
          <span class="chip">Java 11 &middot; Spring Boot</span>
          <span class="chip">Vue 3</span>
          <span class="chip">MySQL &times; 2</span>
        </div>
      </div>
    </header>

    <main class="container">
      <section class="stats">
        <div class="stat-card">
          <div class="stat-label">Catalog service</div>
          <div class="stat-value">
            <span class="dot" :class="catalogUp ? 'up' : 'down'"></span>
            {{ catalogUp ? 'Online' : 'Offline' }}
          </div>
          <div class="stat-sub">/api &middot; sampleapp db</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Orders service</div>
          <div class="stat-value">
            <span class="dot" :class="ordersUp ? 'up' : 'down'"></span>
            {{ ordersUp ? 'Online' : 'Offline' }}
          </div>
          <div class="stat-sub">/orders-api &middot; ordersdb</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Products</div>
          <div class="stat-value">{{ products.length }}</div>
          <div class="stat-sub">{{ totalStock }} units in stock</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Orders</div>
          <div class="stat-value">{{ orders.length }}</div>
          <div class="stat-sub">${{ formatPrice(orderRevenue) }} revenue</div>
        </div>
      </section>

      <div class="grid">
        <section class="card">
          <div class="card-head">
            <h2>Service Details</h2>
            <button class="ghost small" @click="loadHealth" :disabled="loading">Re-check</button>
          </div>
          <div class="svc">
            <div class="svc-row">
              <span class="svc-name">catalog backend</span>
              <span class="pill" :class="catalogUp ? 'ok' : 'bad'">{{ catalogUp ? 'UP' : 'DOWN' }}</span>
            </div>
            <pre v-if="catalogInfo">{{ catalogInfo }}</pre>
          </div>
          <div class="svc">
            <div class="svc-row">
              <span class="svc-name">orders backend</span>
              <span class="pill" :class="ordersUp ? 'ok' : 'bad'">{{ ordersUp ? 'UP' : 'DOWN' }}</span>
            </div>
            <pre v-if="ordersInfo">{{ ordersInfo }}</pre>
          </div>
        </section>

        <section class="card">
          <div class="card-head">
            <h2>Product Catalog</h2>
            <div class="btn-group">
              <button v-if="!formVisible" @click="openCreate">+ Add Product</button>
              <button v-else class="secondary" @click="cancelForm">Cancel</button>
              <button class="ghost" @click="loadProducts" :disabled="loading">Refresh</button>
            </div>
          </div>

          <form v-if="formVisible" class="form" @submit.prevent="save">
            <h3>{{ editingId ? 'Edit Product' : 'New Product' }}</h3>
            <label>Name *<input v-model.trim="form.name" maxlength="100" required /></label>
            <label>Description<textarea v-model.trim="form.description" maxlength="500" rows="2"></textarea></label>
            <div class="form-row">
              <label>Price *<input v-model.number="form.price" type="number" min="0" step="0.01" required /></label>
              <label>Stock *<input v-model.number="form.stock" type="number" min="0" step="1" required /></label>
            </div>
            <button type="submit" :disabled="submitting">{{ submitting ? 'Saving…' : 'Save Product' }}</button>
          </form>

          <table v-if="products.length" class="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Description</th>
                <th class="num">Price</th>
                <th class="num">Stock</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in products" :key="p.id">
                <td class="strong">{{ p.name }}</td>
                <td class="desc">{{ p.description }}</td>
                <td class="num">${{ formatPrice(p.price) }}</td>
                <td class="num"><span class="badge" :class="stockClass(p.stock)">{{ p.stock === 0 ? 'Out' : p.stock }}</span></td>
                <td class="actions">
                  <button class="small ghost" @click="openEdit(p)">Edit</button>
                  <button class="small danger" @click="remove(p)">Delete</button>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else-if="!loading" class="empty">No products yet — add one above.</p>
        </section>

        <section class="card">
          <div class="card-head">
            <h2>Orders</h2>
            <div class="btn-group">
              <button v-if="!orderFormVisible" @click="openOrderCreate">+ New Order</button>
              <button v-else class="secondary" @click="cancelOrderForm">Cancel</button>
              <button class="ghost" @click="loadOrders" :disabled="loading">Refresh</button>
            </div>
          </div>

          <form v-if="orderFormVisible" class="form" @submit.prevent="saveOrder">
            <h3>{{ editingOrderId ? 'Edit Order' : 'New Order' }}</h3>
            <div class="form-row">
              <label>Customer *<input v-model.trim="orderForm.customerName" maxlength="100" required /></label>
              <label>Product *
                <input v-model.trim="orderForm.productName" list="product-names" maxlength="100" required />
                <datalist id="product-names">
                  <option v-for="p in products" :key="p.id" :value="p.name" />
                </datalist>
              </label>
            </div>
            <div class="form-row">
              <label>Quantity *<input v-model.number="orderForm.quantity" type="number" min="1" step="1" required /></label>
              <label>Total *<input v-model.number="orderForm.totalAmount" type="number" min="0" step="0.01" required /></label>
              <label>Status *
                <select v-model="orderForm.status">
                  <option>PENDING</option>
                  <option>SHIPPED</option>
                  <option>DELIVERED</option>
                  <option>CANCELLED</option>
                </select>
              </label>
            </div>
            <button type="submit" :disabled="submitting">{{ submitting ? 'Saving…' : 'Save Order' }}</button>
          </form>

          <table v-if="orders.length" class="data-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Customer</th>
                <th>Product</th>
                <th class="num">Qty</th>
                <th class="num">Total</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="o in orders" :key="o.id">
                <td class="muted">{{ o.id }}</td>
                <td class="strong">{{ o.customerName }}</td>
                <td class="desc">{{ o.productName }}</td>
                <td class="num">{{ o.quantity }}</td>
                <td class="num">${{ formatPrice(o.totalAmount) }}</td>
                <td><span class="badge" :class="statusClass(o.status)">{{ o.status }}</span></td>
                <td class="actions">
                  <button class="small ghost" @click="openOrderEdit(o)">Edit</button>
                  <button class="small danger" @click="removeOrder(o)">Delete</button>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else-if="!loading" class="empty">No orders yet — create one above.</p>
        </section>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
    </main>

    <footer class="footer">
      sky47 CCE deployment test &middot; catalog → <code>sampleapp</code> db &middot; orders → <code>ordersdb</code> db (same RDS host)
    </footer>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted } from 'vue'

export default {
  name: 'App',
  setup() {
    const catalogInfo = ref('')
    const ordersInfo = ref('')
    const catalogUp = ref(false)
    const ordersUp = ref(false)
    const products = ref([])
    const orders = ref([])
    const formVisible = ref(false)
    const editingId = ref(null)
    const form = reactive({ name: '', description: '', price: null, stock: null })
    const orderFormVisible = ref(false)
    const editingOrderId = ref(null)
    const orderForm = reactive({ customerName: '', productName: '', quantity: 1, totalAmount: null, status: 'PENDING' })
    const error = ref('')
    const loading = ref(false)
    const submitting = ref(false)

    const totalStock = computed(() => products.value.reduce((sum, p) => sum + (p.stock || 0), 0))
    const orderRevenue = computed(() =>
      orders.value
        .filter(o => o.status !== 'CANCELLED')
        .reduce((sum, o) => sum + Number(o.totalAmount || 0), 0)
    )

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

    async function loadHealth() {
      loading.value = true
      try {
        const [catalogHealth, ordersHealth, catalogInfoBody, ordersInfoBody] = await Promise.allSettled([
          callApi('/api/health'),
          callApi('/orders-api/health'),
          callApi('/api/info'),
          callApi('/orders-api/info')
        ])
        catalogUp.value = catalogHealth.status === 'fulfilled'
        ordersUp.value = ordersHealth.status === 'fulfilled'
        catalogInfo.value = catalogInfoBody.status === 'fulfilled'
          ? JSON.stringify(catalogInfoBody.value, null, 2) : 'Unavailable'
        ordersInfo.value = ordersInfoBody.status === 'fulfilled'
          ? JSON.stringify(ordersInfoBody.value, null, 2) : 'Unavailable'
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

    async function loadOrders() {
      loading.value = true
      try {
        orders.value = await callApi('/orders-api/orders')
      } catch (e) {
        error.value = `Failed to load orders: ${e.message}`
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
        const payload = { name: form.name, description: form.description, price: form.price, stock: form.stock }
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

    function openOrderCreate() {
      editingOrderId.value = null
      Object.assign(orderForm, { customerName: '', productName: '', quantity: 1, totalAmount: null, status: 'PENDING' })
      orderFormVisible.value = true
    }

    function openOrderEdit(o) {
      editingOrderId.value = o.id
      Object.assign(orderForm, {
        customerName: o.customerName,
        productName: o.productName,
        quantity: o.quantity,
        totalAmount: Number(o.totalAmount),
        status: o.status
      })
      orderFormVisible.value = true
    }

    function cancelOrderForm() {
      orderFormVisible.value = false
      editingOrderId.value = null
    }

    async function saveOrder() {
      submitting.value = true
      try {
        const payload = {
          customerName: orderForm.customerName,
          productName: orderForm.productName,
          quantity: orderForm.quantity,
          totalAmount: orderForm.totalAmount,
          status: orderForm.status
        }
        if (editingOrderId.value) {
          await callApi(`/orders-api/orders/${editingOrderId.value}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
          })
        } else {
          await callApi('/orders-api/orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
          })
        }
        cancelOrderForm()
        await loadOrders()
      } catch (e) {
        error.value = `Order save failed: ${e.message}`
      } finally {
        submitting.value = false
      }
    }

    async function removeOrder(o) {
      if (!window.confirm(`Delete order #${o.id} (${o.customerName})?`)) return
      loading.value = true
      try {
        await callApi(`/orders-api/orders/${o.id}`, { method: 'DELETE' })
        await loadOrders()
      } catch (e) {
        error.value = `Order delete failed: ${e.message}`
      } finally {
        loading.value = false
      }
    }

    function formatPrice(value) {
      return Number(value || 0).toFixed(2)
    }

    function stockClass(stock) {
      if (stock === 0) return 'out'
      if (stock <= 10) return 'low'
      return 'ok'
    }

    function statusClass(status) {
      if (status === 'DELIVERED') return 'ok'
      if (status === 'CANCELLED') return 'out'
      if (status === 'SHIPPED') return 'info'
      return 'low'
    }

    onMounted(() => {
      loadHealth()
      loadProducts()
      loadOrders()
    })

    return {
      catalogInfo, ordersInfo, catalogUp, ordersUp, products, orders,
      formVisible, editingId, form, orderFormVisible, editingOrderId, orderForm,
      error, loading, submitting, totalStock, orderRevenue,
      loadHealth, loadProducts, loadOrders,
      openCreate, openEdit, cancelForm, save, remove,
      openOrderCreate, openOrderEdit, cancelOrderForm, saveOrder, removeOrder,
      formatPrice, stockClass, statusClass
    }
  }
}
</script>

<style>
:root {
  --bg: #0f172a;
  --surface: #ffffff;
  --surface-2: #f8fafc;
  --border: #e2e8f0;
  --text: #0f172a;
  --muted: #64748b;
  --accent: #4f46e5;
  --accent-hover: #4338ca;
  --green: #059669;
  --amber: #d97706;
  --red: #dc2626;
  --blue: #2563eb;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: 'Inter', system-ui, -apple-system, 'Segoe UI', sans-serif;
  background: var(--surface-2);
  color: var(--text);
}
.app { min-height: 100vh; display: flex; flex-direction: column; }

.hero {
  background: linear-gradient(120deg, #0f172a 0%, #1e1b4b 55%, #312e81 100%);
  color: #fff;
  padding: 2.5rem 1rem 3.5rem;
}
.hero-inner {
  max-width: 1080px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 1rem;
  flex-wrap: wrap;
}
.hero h1 { margin: 0 0 0.35rem; font-size: 1.9rem; letter-spacing: -0.02em; }
.tagline { margin: 0; color: #c7d2fe; font-size: 0.95rem; }
.hero-chips { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.chip {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  padding: 0.3rem 0.8rem;
  font-size: 0.78rem;
  color: #e0e7ff;
}

.container {
  max-width: 1080px;
  width: 100%;
  margin: -2rem auto 2rem;
  padding: 0 1rem;
  flex: 1;
}

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 0.9rem;
  margin-bottom: 1.25rem;
}
.stat-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1rem 1.1rem;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
}
.stat-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--muted);
  margin-bottom: 0.35rem;
}
.stat-value { font-size: 1.35rem; font-weight: 700; display: flex; align-items: center; gap: 0.5rem; }
.stat-sub { font-size: 0.8rem; color: var(--muted); margin-top: 0.25rem; }
.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.dot.up { background: var(--green); box-shadow: 0 0 0 4px rgba(5, 150, 105, 0.15); }
.dot.down { background: var(--red); box-shadow: 0 0 0 4px rgba(220, 38, 38, 0.15); }

.grid { display: grid; gap: 1.25rem; }
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-bottom: 0.9rem;
}
.card-head h2 { margin: 0; font-size: 1.1rem; }
.btn-group { display: flex; gap: 0.5rem; }

.svc { margin-bottom: 1rem; }
.svc:last-child { margin-bottom: 0; }
.svc-row { display: flex; align-items: center; gap: 0.6rem; margin-bottom: 0.5rem; }
.svc-name { font-weight: 600; font-size: 0.92rem; }
.pill {
  font-size: 0.72rem;
  font-weight: 700;
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
}
.pill.ok { background: #d1fae5; color: #065f46; }
.pill.bad { background: #fee2e2; color: #991b1b; }

.form {
  display: grid;
  gap: 0.6rem;
  margin-bottom: 1rem;
  padding: 1rem;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 10px;
}
.form h3 { margin: 0; font-size: 0.95rem; }
.form label { display: grid; gap: 0.25rem; font-size: 0.82rem; color: var(--muted); flex: 1; }
.form input, .form textarea, .form select {
  padding: 0.5rem 0.65rem;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font: inherit;
  background: #fff;
  resize: vertical;
}
.form input:focus, .form textarea:focus, .form select:focus {
  outline: 2px solid rgba(79, 70, 229, 0.35);
  border-color: var(--accent);
}
.form-row { display: flex; gap: 0.75rem; flex-wrap: wrap; }

table.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td {
  text-align: left;
  padding: 0.55rem 0.5rem;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
  font-size: 0.9rem;
}
.data-table th {
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--muted);
}
.data-table tbody tr:hover { background: var(--surface-2); }
td.num, th.num { text-align: right; white-space: nowrap; }
td.desc { color: var(--muted); font-size: 0.85rem; }
td.strong { font-weight: 600; }
td.muted { color: var(--muted); }
td.actions { white-space: nowrap; text-align: right; }

.badge {
  display: inline-block;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
}
.badge.ok { background: #d1fae5; color: #065f46; }
.badge.low { background: #fef3c7; color: #92400e; }
.badge.out { background: #fee2e2; color: #991b1b; }
.badge.info { background: #dbeafe; color: #1e40af; }

.empty { color: var(--muted); padding: 0.75rem 0; }

button {
  background: var(--accent);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 0.5rem 1rem;
  font: inherit;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}
button:hover:not(:disabled) { background: var(--accent-hover); }
button:disabled { opacity: 0.5; cursor: not-allowed; }
button.secondary { background: #475569; }
button.secondary:hover:not(:disabled) { background: #334155; }
button.ghost {
  background: #fff;
  color: var(--accent);
  border: 1px solid #cbd5e1;
}
button.ghost:hover:not(:disabled) { background: #eef2ff; }
button.danger { background: var(--red); }
button.danger:hover:not(:disabled) { background: #b91c1c; }
button.small { padding: 0.28rem 0.65rem; font-size: 0.78rem; margin-left: 0.3rem; }
button.small:first-child { margin-left: 0; }

pre {
  background: #0f172a;
  color: #a5b4fc;
  padding: 0.75rem;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 0.78rem;
  line-height: 1.5;
}
.error {
  color: var(--red);
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 0.65rem 0.9rem;
  margin-top: 1rem;
}

.footer {
  text-align: center;
  color: var(--muted);
  font-size: 0.8rem;
  padding: 1.5rem 1rem 2rem;
}
.footer code { background: #e2e8f0; padding: 0.1rem 0.35rem; border-radius: 4px; }
</style>
