# Kubernetes Deployment Guide — sky47 Cloud (CCE + SWR + RDS + ICAgent/LTS)

Deploy the sample app to a Kubernetes (CCE) cluster on sky47 cloud, pulling images from sky47 SWR (SoftWare Repository for Container), storing data in an RDS for MySQL database, and collecting container logs with ICAgent into LTS (Log Tank Service) via a CCE ingestion configuration.

sky47 is a Huawei Cloud Stack (HCS) based provider — console menu names below follow HCS conventions. Reference docs: https://docs.financialkhazanapk.cloud/mohelpcenter/operation/en-us/index.html

## Architecture

```
Browser → ELB (public EIP) → frontend Service (LoadBalancer)
            → frontend pod (nginx) → /api/        proxied → backend Service (ClusterIP)        → backend pod (Spring Boot)
                                   → /orders-api/ proxied → orders-backend Service (ClusterIP) → orders-backend pod (Spring Boot)
            → RDS for MySQL 5.7.44 (sampleapp db ← backend, ordersdb db ← orders-backend; same host)
Container stdout → node log files → ICAgent (on every node) → LTS log stream (CCE ingestion configuration)
```

No application code changes are required:
- `frontend/nginx.conf` proxies `/api/` to `http://backend:8080` and `/orders-api/` to `http://orders-backend:8080` — in k8s these resolve via the ClusterIP **Services named `backend` and `orders-backend`** in the same namespace. The two path-based routes are deliberately shaped like future API-gateway routes.
- Spring Boot and nginx both log to **stdout/stderr**, which is exactly what the log collector picks up.

## Prerequisites

- A CCE cluster created in the sky47 console (**Cloud Container Engine → Buy/Create Cluster**) with at least one node (2 vCPU / 4 GB is enough)
- An RDS for MySQL instance (engine version **5.7.44**) in the same VPC as the cluster
- `kubectl` installed on your workstation or a jump host
- Docker installed locally (to build and push images)
- Permissions for SWR, CCE, RDS, LTS, and ELB in your sky47 account

## 1. Connect kubectl to the cluster

1. Console → **CCE → Clusters → your cluster → Kubectl / Access**.
2. Download the kubeconfig file and follow the shown instructions, e.g.:
   ```bash
   mkdir -p ~/.kube
   mv kubeconfig.json ~/.kube/config
   kubectl cluster-info
   kubectl get nodes    # all nodes should be Ready
   ```
   > If your workstation cannot reach the cluster's private API endpoint, either bind an EIP to the cluster API (cluster details → enable public access) or run kubectl from an ECS in the same VPC.

## 2. Create an SWR organization

1. Console → **SWR (SoftWare Repository for Container) → Organizations → Create Organization**.
2. For this environment the organization is **`pk-beta-repo`** — image paths are `swr.pk-isb-1.sky47.com/pk-beta-repo/<image>:<tag>`.

## 3. Log in to SWR from Docker

1. Console → **SWR → Dashboard (or My Images) → Generate Login Command**.
2. Copy and run the generated command on the build host. For this environment (region `pk-isb-1`) it looks like:
   ```bash
   docker login -u pk-isb-1_<domain>@<AK> -p <login-key> swr.pk-isb-1.sky47.com
   ```
   > The generated command is temporary (valid ~24h). For CI, create a long-term login key (SWR → Access Credentials).
   > Prefer `--password-stdin` over `-p` so the key doesn't land in shell history:
   > `echo '<login-key>' | docker login -u <user> --password-stdin swr.pk-isb-1.sky47.com`

The SWR endpoint for this environment is `swr.pk-isb-1.sky47.com`.

## 4. Build, tag, and push the images to SWR

From the repo root on the build host (the ECS instance works fine since it has Docker and the repo cloned):

```bash
export SWR=swr.pk-isb-1.sky47.com
export ORG=pk-beta-repo

docker build -t $SWR/$ORG/sample-backend:1.0.0 ./backend
docker build -t $SWR/$ORG/sample-orders-backend:1.0.0 ./orders-backend
docker build -t $SWR/$ORG/sample-frontend:1.1.0 ./frontend

docker push $SWR/$ORG/sample-backend:1.0.0
docker push $SWR/$ORG/sample-orders-backend:1.0.0
docker push $SWR/$ORG/sample-frontend:1.1.0
```

Verify: Console → **SWR → My Images** — both repos should appear under the `beta-pk` organization.

> Building on Apple Silicon / ARM: `docker buildx build --platform linux/amd64 -t $SWR/$ORG/sample-backend:1.0.0 --push ./backend`

Optional: set the repos to **Private** (SWR → image → Permissions). Private images require the pull secret in step 5.

## 5. Create the namespace and verify the image pull secret

```bash
kubectl apply -f k8s/namespace.yaml
kubectl get secret default-secret -n sample-app
```

CCE automatically creates `default-secret` (SWR pull credentials) in every namespace. If it is missing, create it manually:

```bash
kubectl create secret docker-registry default-secret -n sample-app \
  --docker-server=$SWR \
  --docker-username=<region>@<AK> \
  --docker-password=<long-term-login-key>
```

## 6. Create the RDS database and run the init scripts

The backend reads its DB connection from environment variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`) and expects a `products` table. The schema is managed by hand-written scripts — Hibernate runs with `ddl-auto: none`.

1. Console → **RDS → Create Instance**: engine **MySQL**, version **5.7.44**. Choose the same VPC as the CCE cluster, set the root password, and create a database named **`sampleapp`** (or create it after provisioning). Note the **instance address** (internal endpoint/IP) and port **3306**.
2. Allow traffic from the cluster to RDS: on the RDS instance's **security group**, add an inbound rule for TCP **3306** from the CCE node subnet (or the cluster's security group).
3. Run the schema + seed scripts from `dbscripts/` against the `sampleapp` database (via the RDS console SQL window / DAS, or a mysql client):
   ```bash
   mysql -h <rds-address> -P 3306 -u root -p sampleapp < dbscripts/001_create_products.sql
   mysql -h <rds-address> -P 3306 -u root -p sampleapp < dbscripts/002_seed_products.sql
   ```
4. Create the **second** database for the orders backend on the **same RDS host** (the orders backend reuses the same credentials Secret but points `DB_NAME` at `ordersdb`):
   ```bash
   mysql -h <rds-address> -P 3306 -u root -p < dbscripts/003_create_orders_db.sql
   mysql -h <rds-address> -P 3306 -u root -p < dbscripts/004_seed_orders.sql
   ```
5. Create the credentials Secret in the cluster (do not commit the real password):
   ```bash
   cp k8s/mysql-secret.example.yaml k8s/mysql-secret.yaml   # then edit DB_USER / DB_PASSWORD
   kubectl apply -f k8s/mysql-secret.yaml
   ```

## 7. Edit the manifests and deploy

The image paths in `k8s/backend.yaml` and `k8s/frontend.yaml` are already set to `swr.pk-isb-1.sky47.com/pk-beta-repo/...`. Two placeholders remain to fill in:

| Placeholder | Where | Value |
|---|---|---|
| `<rds-mysql-instance-address>` | `k8s/backend.yaml` (`DB_HOST` env) | Internal address/IP of the RDS instance from step 6 |
| `<ELB-ID>` | `k8s/frontend.yaml` | ID of an existing ELB (console → **ELB → your load balancer → ID**), or switch to the `elb.autocreate` annotation in the file |
| `<CERT-ID>` | `k8s/frontend.yaml` | ID of the certificate uploaded to the ELB (console → **ELB → Certificates**) — needed for the HTTPS listener |

Then apply (mysql-secret and both backends first, so the `backend` and `orders-backend` Service DNS names exist before nginx starts):

```bash
kubectl apply -f k8s/backend.yaml
kubectl rollout status deployment/backend -n sample-app
kubectl apply -f k8s/orders-backend.yaml
kubectl rollout status deployment/orders-backend -n sample-app
kubectl apply -f k8s/frontend.yaml
kubectl rollout status deployment/frontend -n sample-app
```

Get the public address:

```bash
kubectl get svc frontend -n sample-app
# EXTERNAL-IP column = ELB address; open https://<EXTERNAL-IP>/ in a browser
```

Quick checks:

```bash
kubectl get pods -n sample-app                                   # all Running/Ready
kubectl logs deploy/backend -n sample-app                        # Spring Boot startup logs
kubectl logs deploy/orders-backend -n sample-app                 # orders service startup logs
kubectl exec -n sample-app deploy/frontend -- wget -qO- http://backend:8080/api/health
kubectl exec -n sample-app deploy/frontend -- wget -qO- http://orders-backend:8080/orders-api/health
curl https://<EXTERNAL-IP>/                                      # frontend HTML
curl https://<EXTERNAL-IP>/api/health                            # {"status":"UP",...} via nginx proxy
curl https://<EXTERNAL-IP>/api/products                          # seeded product list (JSON)
curl https://<EXTERNAL-IP>/orders-api/health                     # second backend via its own path
curl https://<EXTERNAL-IP>/orders-api/orders                     # seeded order list (JSON)
```

## 8. Collect container logs with ICAgent (LTS CCE ingestion)

Both containers already write logs to stdout, so only collection needs configuring. ICAgent on each node collects container stdout and reports it to LTS via a **CCE (Cloud Container Engine)** ingestion configuration — no in-cluster `LogConfig` resource and no manual node file paths are needed (the earlier `k8s/logconfig.yaml` / log-agent approach was removed).

Full step-by-step instructions (based on the LTS 2.5.0 User Guide): **[CCE-LOG-COLLECTION.md](CCE-LOG-COLLECTION.md)**. In short:

1. **LTS → Host Management → Hosts → CCE Cluster tab** — install/upgrade ICAgent on the cluster (auto-creates log group + host group `k8s-log-{ClusterID}`).
2. **LTS → Log Ingestion → Ingestion Center → CCE** — pick fixed or custom log stream, run the dependency check (**Auto Correct**), keep host group `k8s-log-{ClusterID}`, data source **Container standard output**, namespace regex `^sample-app$`. Ensure **Output to AOM is disabled**.
3. Verify in **LTS → Log Management** → your stream after generating traffic.

## 9. Releasing an update

```bash
docker build -t $SWR/$ORG/sample-backend:1.0.1 ./backend
docker push $SWR/$ORG/sample-backend:1.0.1
kubectl set image deployment/backend backend=$SWR/$ORG/sample-backend:1.0.1 -n sample-app
kubectl rollout status deployment/backend -n sample-app
```

(Or edit the tag in `k8s/backend.yaml` and `kubectl apply -f` it — keeps the file as the source of truth.)

## 10. Network / security group notes

- **Worker node security group** (created with the cluster): keep the CCE-generated rules; do not delete them. No extra inbound rule is needed for pod traffic — the ELB reaches NodePorts via the VPC.
- **ELB**: must be a **public** ELB (has an EIP) for browser access. An HTTPS listener on port 443 (with the uploaded certificate) is created automatically by the Service; TLS terminates at the ELB and traffic to nginx stays HTTP.
- Do **not** expose the backend with its own LoadBalancer/NodePort — it stays ClusterIP, reachable only inside the cluster via nginx.
- If kubectl access from your laptop is needed, bind an EIP to the cluster API server and restrict its allowed CIDR to your IP.

## Troubleshooting

| Problem | Fix |
|---|---|
| Pod `ImagePullBackOff` | `kubectl describe pod` — check image path matches SWR exactly; ensure `default-secret` exists in `sample-app` (step 5); if image is Private, secret must use a long-term key |
| `exec format error` in pod logs | Image built for wrong CPU arch — rebuild with `--platform linux/amd64` |
| frontend pod CrashLoop: `host not found in upstream "backend"` | Backend Service missing — apply `k8s/backend.yaml` first, then restart frontend: `kubectl rollout restart deploy/frontend -n sample-app` |
| frontend pod CrashLoop: `host not found in upstream "orders-backend"` | Orders backend Service missing — apply `k8s/orders-backend.yaml` first, then restart frontend: `kubectl rollout restart deploy/frontend -n sample-app` |
| Service `EXTERNAL-IP` stuck `<pending>` | Wrong/missing `kubernetes.io/elb.id`, or autocreate JSON invalid — `kubectl describe svc frontend -n sample-app` shows the event error |
| Backend CrashLoopBackOff with `Communications link failure` in logs | RDS unreachable — check `DB_HOST` in `k8s/backend.yaml`, that `mysql-secret` exists with the right credentials, and that the RDS security group allows TCP 3306 from the cluster nodes |
| Backend starts but `/api/products` returns 500 | `products` table missing or schema drift — run `dbscripts/001_create_products.sql` against the `sampleapp` database; confirm `DB_NAME` matches |
| `/api/info` shows `"database":{"status":"DOWN"}` | Backend is up but the DB connection fails — check credentials/security group as above, then look at backend logs |
| Browser can't reach EXTERNAL-IP | ELB is private (no EIP) — use a public ELB, or bind an EIP to it |
| No logs in the LTS stream (but `kubectl logs` works) | Check ICAgent status is **Running** on the LTS → Host Management → CCE Cluster tab; ensure **Output to AOM is disabled**; rerun the ingestion wizard's dependency check (**Auto Correct**); confirm the namespace regex matches `sample-app` — see [CCE-LOG-COLLECTION.md](CCE-LOG-COLLECTION.md) troubleshooting |
