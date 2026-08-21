# Sample Project — sky47 Deployment Test

Java 11 backend (Spring Boot + MySQL) + Vue 3 frontend product catalog for validating new deployments in the sky47 cloud environment (CCE + SWR + RDS + LTS).

## Structure

```
sample-project/
├── backend/            # Spring Boot 2.7 (Java 11), JPA + MySQL, product CRUD REST API
├── frontend/           # Vue 3 + Vite product catalog UI, served by nginx in Docker
├── dbscripts/          # SQL scripts: schema + seed data for RDS for MySQL
├── k8s/                # Kubernetes manifests for sky47 CCE
├── K8S-DEPLOYMENT.md   # SWR + CCE + RDS + ICAgent/LTS deployment guide
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

## Frontend

Requires Node 18+.

```bash
cd frontend
npm install
npm run dev
```

Runs on http://localhost:5173 and proxies `/api` to the backend on port 8080.

The UI has backend health/info checks plus a full product catalog (list, add, edit, delete).

Production build: `npm run build` (output in `frontend/dist`).

## Deployment (sky47 CCE / SWR / RDS)

Images are built from `backend/Dockerfile` and `frontend/Dockerfile`, pushed to sky47 **SWR** (SoftWare Repository for Container), and deployed to a **CCE** Kubernetes cluster backed by an **RDS for MySQL 5.7.44** instance, with container logs collected by **ICAgent** into **LTS** — see [K8S-DEPLOYMENT.md](K8S-DEPLOYMENT.md) for the full step-by-step guide (including RDS setup) and [CCE-LOG-COLLECTION.md](CCE-LOG-COLLECTION.md) for the log collection setup.

- Backend image: multi-stage Maven build → Temurin 11 JRE, exposes 8080
- Frontend image: Node build → nginx, exposes 80, proxies `/api/` to `backend:8080` (the k8s backend Service must be named `backend`)
- DB credentials come from a k8s Secret (`mysql-secret`) referenced in `k8s/backend.yaml` — see `k8s/mysql-secret.example.yaml`
- Use `/actuator/health` or `/api/health` as the probe/LB health check path
