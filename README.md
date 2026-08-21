# Sample Project — sky47 Deployment Test

Two Java 11 backends (Spring Boot + MySQL) behind one Vue 3 frontend: a product catalog (`/api`, `sampleapp` db) and an orders service (`/orders-api`, `ordersdb` db on the same RDS host), for validating new deployments in the sky47 cloud environment (CCE + SWR + RDS + LTS). The two path-based routes are shaped for a future API gateway.

## Structure

```
sample-project/
├── backend/            # Spring Boot 2.7 (Java 11), JPA + MySQL, product CRUD REST API (/api)
├── orders-backend/     # Second Spring Boot 2.7 (Java 11) service, orders CRUD (/orders-api, ordersdb)
├── frontend/           # Vue 3 + Vite dashboard UI, served by nginx in Docker
├── dbscripts/          # SQL scripts: schema + seed data for RDS for MySQL (both databases)
├── k8s/                # Kubernetes manifests for sky47 CCE
├── K8S-DEPLOYMENT.md   # SWR + CCE + RDS + ICAgent/LTS deployment guide (from scratch)
├── NEW-SETUP-DEPLOYMENT.md # Updating the already-running cluster to the RDS + CRUD setup
└── CCE-LOG-COLLECTION.md # CCE container log collection via ICAgent + LTS
```

## Backend

Requires Java 11 and Maven. Connects to MySQL using the `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` env vars (defaults: `localhost:3306/sampleapp`, root/root).

```bash
cd backend
mvn spring-boot:run
```

Runs on http://localhost:8080

| Endpoint | Method | Description |
|---|---|---|
| `/api/health` | GET | App health + timestamp |
| `/api/info` | GET | App/Java version, environment (`APP_ENV`), database status |
| `/api/products` | GET | List all products |
| `/api/products/{id}` | GET | Get one product |
| `/api/products` | POST | Create a product (name, description, price, stock) |
| `/api/products/{id}` | PUT | Update a product |
| `/api/products/{id}` | DELETE | Delete a product |
| `/api/echo` | POST | Echoes JSON body back |
| `/actuator/health` | GET | Spring Actuator health (for LB health checks) |

Schema setup (before first run): `dbscripts/001_create_products.sql`, then optionally `dbscripts/002_seed_products.sql` against the `sampleapp` database.

## Orders backend (second service)

Same Java 11 / Spring Boot 2.7 stack as the catalog backend, but serves everything under the `/orders-api` context path and connects to the **`ordersdb`** database on the same RDS host. Locally, run it on port 8081 so it can sit next to the catalog backend:

```bash
cd orders-backend
PORT=8081 mvn spring-boot:run
```

| Endpoint | Method | Description |
|---|---|---|
| `/orders-api/health` | GET | Service health + timestamp |
| `/orders-api/info` | GET | App/Java version, environment, database status |
| `/orders-api/orders` | GET | List all orders |
| `/orders-api/orders/{id}` | GET | Get one order |
| `/orders-api/orders` | POST | Create an order (customerName, productName, quantity, totalAmount, status) |
| `/orders-api/orders/{id}` | PUT | Update an order |
| `/orders-api/orders/{id}` | DELETE | Delete an order |

Schema setup: `dbscripts/003_create_orders_db.sql`, then optionally `dbscripts/004_seed_orders.sql` (creates and uses `ordersdb`).

## Frontend

Requires Node 18+.

```bash
cd frontend
npm install
npm run dev
```

Runs on http://localhost:5173 and proxies `/api` to the catalog backend on port 8080 and `/orders-api` to the orders backend on port 8081.

The UI is a dashboard with live health indicators for both services, stats (products, stock, orders, revenue), a full product catalog (list, add, edit, delete) and a full orders panel (list, add, edit, delete).

Production build: `npm run build` (output in `frontend/dist`).

## Deployment (sky47 CCE / SWR / RDS)

Images are built from `backend/Dockerfile` and `frontend/Dockerfile`, pushed to sky47 **SWR** (SoftWare Repository for Container), and deployed to a **CCE** Kubernetes cluster backed by an **RDS for MySQL 5.7.44** instance, with container logs collected by **ICAgent** into **LTS** — see [K8S-DEPLOYMENT.md](K8S-DEPLOYMENT.md) for the full step-by-step guide (including RDS setup) and [CCE-LOG-COLLECTION.md](CCE-LOG-COLLECTION.md) for the log collection setup. Already have the previous version running in the cluster? Use [NEW-SETUP-DEPLOYMENT.md](NEW-SETUP-DEPLOYMENT.md) to roll out the RDS + CRUD version over it.

- Backend image: multi-stage Maven build → Temurin 11 JRE, exposes 8080
- Orders backend image: same multi-stage Maven build → Temurin 11 JRE, exposes 8080, context path `/orders-api`
- Frontend image: Node build → nginx, exposes 80, proxies `/api/` to `backend:8080` and `/orders-api/` to `orders-backend:8080` (the k8s Services must keep those names)
- DB credentials come from a k8s Secret (`mysql-secret`) referenced in `k8s/backend.yaml` and `k8s/orders-backend.yaml` — see `k8s/mysql-secret.example.yaml`
- Use `/api/health` and `/orders-api/health` as the probe/LB health check paths
