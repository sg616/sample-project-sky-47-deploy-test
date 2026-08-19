# Sample Project — sky47 Deployment Test

Minimal Java 11 backend (Spring Boot, no database) + Vue 3 frontend for validating new deployments in the sky47 cloud environment.

## Structure

```
sample-project/
├── backend/            # Spring Boot 2.7 (Java 11), REST API, no DB
├── frontend/           # Vue 3 + Vite, served by nginx in Docker
├── k8s/                # Kubernetes manifests for sky47 CCE
└── K8S-DEPLOYMENT.md   # SWR + CCE + LTS deployment guide
```

## Backend

Requires Java 11 and Maven.

```bash
cd backend
mvn spring-boot:run
```

Runs on http://localhost:8080

| Endpoint | Method | Description |
|---|---|---|
| `/api/health` | GET | App health + timestamp |
| `/api/info` | GET | App/Java version, environment (`APP_ENV`) |
| `/api/echo` | POST | Echoes JSON body back |
| `/actuator/health` | GET | Spring Actuator health (for LB health checks) |

## Frontend

Requires Node 18+.

```bash
cd frontend
npm install
npm run dev
```

Runs on http://localhost:5173 and proxies `/api` to the backend on port 8080.

Production build: `npm run build` (output in `frontend/dist`).

## Deployment (sky47 CCE / SWR)

Images are built from `backend/Dockerfile` and `frontend/Dockerfile`, pushed to sky47 **SWR** (SoftWare Repository for Container), and deployed to a **CCE** Kubernetes cluster with logs shipped to **LTS** — see [K8S-DEPLOYMENT.md](K8S-DEPLOYMENT.md) for the full step-by-step guide.

- Backend image: multi-stage Maven build → Temurin 11 JRE, exposes 8080
- Frontend image: Node build → nginx, exposes 80, proxies `/api/` to `backend:8080` (the k8s backend Service must be named `backend`)
- Use `/actuator/health` or `/api/health` as the probe/LB health check path
