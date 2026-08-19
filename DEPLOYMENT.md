# Deployment Guide — Build, Push to Docker Hub, Deploy with Docker Compose

Steps to build the images locally, push them to Docker Hub, and deploy on a target host (e.g., sky47 environment) using Docker Compose.

## Prerequisites

- Docker Desktop (or Docker Engine) running locally
- A Docker Hub account and access to a terminal on the deployment host with Docker + Docker Compose installed
- Commands below use the Docker Hub username `sajeewa616`

## 1. Log in to Docker Hub

```bash
docker login
# Enter your Docker Hub username and password/access token
```

> Tip: Use a Docker Hub **access token** (Account Settings → Security) instead of your password.

## 2. Build the images locally

Run from the `sample-project` root:

```bash
# Backend (Java 11 / Spring Boot)
docker build -t sajeewa616/sample-backend:1.0.0 ./backend

# Frontend (Vue 3 + nginx)
docker build -t sajeewa616/sample-frontend:1.0.0 ./frontend
```

Optionally also tag as `latest`:

```bash
docker tag sajeewa616/sample-backend:1.0.0 sajeewa616/sample-backend:latest
docker tag sajeewa616/sample-frontend:1.0.0 sajeewa616/sample-frontend:latest
```

> If the deployment host CPU architecture differs from your local machine (e.g., building on Apple Silicon for an amd64 server), build with:
> `docker buildx build --platform linux/amd64 -t <image> --push ./backend`

## 3. Test locally before pushing (optional but recommended)

```bash
docker compose up --build
```

- Frontend: http://localhost
- Backend health: http://localhost:8080/api/health

Stop with `docker compose down`.

## 4. Push the images to Docker Hub

```bash
docker push sajeewa616/sample-backend:1.0.0
docker push sajeewa616/sample-frontend:1.0.0

# If you tagged latest:
docker push sajeewa616/sample-backend:latest
docker push sajeewa616/sample-frontend:latest
```

Verify at https://hub.docker.com/repositories

## 5. Create the deploy compose file on the target host

On the deployment host, create a directory (e.g., `~/sample-app`) with this `docker-compose.yml` — it pulls images instead of building:

```yaml
services:
  backend:
    image: sajeewa616/sample-backend:1.0.0
    ports:
      - "8080:8080"
    environment:
      - APP_ENV=sky47
    restart: unless-stopped

  frontend:
    image: sajeewa616/sample-frontend:1.0.0
    ports:
      - "80:80"
    depends_on:
      - backend
    restart: unless-stopped
```

> The frontend's nginx proxies `/api/` to `http://backend:8080` — the service name `backend` must stay the same, or update `frontend/nginx.conf` and rebuild.

## 6. Deploy on the target host

```bash
cd ~/sample-app

# Log in if the repos are private
docker login

docker compose pull
docker compose up -d
```

## 7. Verify the deployment

```bash
docker compose ps                      # both services should be "running"
curl http://localhost:8080/api/health  # {"status":"UP", ...}
curl http://localhost/                 # returns the frontend HTML
docker compose logs -f                 # watch logs if something is wrong
```

From a browser, open `http://<host-ip>/` and use the buttons (Health / Info / Echo) — all three should return JSON from the backend.

## 8. Releasing an update

1. Make code changes locally
2. Rebuild with a new tag, e.g. `1.0.1`, and push:
   ```bash
   docker build -t sajeewa616/sample-backend:1.0.1 ./backend
   docker push sajeewa616/sample-backend:1.0.1
   ```
3. On the host, update the image tag in `docker-compose.yml`, then:
   ```bash
   docker compose pull && docker compose up -d
   ```

## 9. Alternative: Git clone and build directly on the remote server

Instead of pushing images to Docker Hub, you can clone this repo on the deployment host and build the images there. No Docker Hub account is needed.

### 9.1 Install prerequisites on the server (Ubuntu 24.04)

```bash
sudo apt-get update
sudo apt-get install -y git docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker $USER   # log out and back in for this to take effect
```

Verify:

```bash
docker --version
docker compose version
```

### 9.2 Clone the repository

```bash
cd ~
git clone https://github.com/sg616/sample-project-sky-47-deploy-test.git
cd sample-project-sky-47-deploy-test
```

> If the repo is private, use a GitHub **personal access token**:
> `git clone https://<username>:<token>@github.com/sg616/sample-project-sky-47-deploy-test.git`
> or set up an SSH deploy key and clone via SSH.

### 9.3 Build and deploy

The repo's `docker-compose.yml` already builds from source (`build: ./backend`, `build: ./frontend`), so a single command builds both images on the server and starts them:

```bash
docker compose up -d --build
```

Verify as in section 7:

```bash
docker compose ps
curl http://localhost:8080/api/health
curl http://localhost/
```

### 9.4 Updating to a new version

```bash
cd ~/sample-project-sky-47-deploy-test
git pull
docker compose up -d --build   # rebuilds only what changed
docker image prune -f          # optional: clean up old dangling images
```

### 9.5 Security group changes (AWS)

Inbound rules needed on the instance's security group:

| Port | Protocol | Source | Purpose |
|---|---|---|---|
| 22 | TCP | Your IP / admin CIDR only | SSH for clone/build/deploy |
| 80 | TCP | 0.0.0.0/0 (or your users' CIDR) | Frontend (nginx) |
| 443 | TCP | 0.0.0.0/0 | Only if you later add TLS |

Outbound rules (default allow-all is fine). If outbound is restricted, allow:

| Port | Protocol | Destination | Purpose |
|---|---|---|---|
| 443 | TCP | 0.0.0.0/0 | `git clone` from GitHub, pulling base images from Docker Hub, OS packages |
| 80 | TCP | 0.0.0.0/0 | Some package mirrors |

> **Do NOT open port 8080 to the internet.** The frontend's nginx proxies `/api/` to the backend inside the Docker network, so the backend never needs external exposure. To enforce this, change the backend port mapping in `docker-compose.yml` to `"127.0.0.1:8080:8080"` (or remove the `ports:` entry entirely) so it is reachable only from localhost and other containers.

## Troubleshooting

| Problem | Fix |
|---|---|
| `denied: requested access to the resource is denied` on push | Run `docker login`; ensure image name starts with your Docker Hub username |
| Frontend loads but API buttons fail | Check `docker compose logs backend`; ensure backend service is named `backend` |
| `exec format error` on host | Architecture mismatch — rebuild with `--platform linux/amd64` |
| Port 80/8080 already in use on host | Change the left-hand port in compose, e.g. `"8081:8080"` |
