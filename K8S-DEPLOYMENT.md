# Kubernetes Deployment Guide — sky47 Cloud (CCE + SWR + ICAgent/LTS)

Deploy the sample app to a Kubernetes (CCE) cluster on sky47 cloud, pulling images from sky47 SWR (SoftWare Repository for Container) and collecting container logs with ICAgent into LTS (Log Tank Service) via a CCE ingestion configuration.

sky47 is a Huawei Cloud Stack (HCS) based provider — console menu names below follow HCS conventions. Reference docs: https://docs.financialkhazanapk.cloud/mohelpcenter/operation/en-us/index.html

## Architecture

```
Browser → ELB (public EIP) → frontend Service (LoadBalancer)
            → frontend pod (nginx) → /api/ proxied → backend Service (ClusterIP) → backend pod (Spring Boot)
Container stdout → node log files → ICAgent (on every node) → LTS log stream (CCE ingestion configuration)
```

No application code changes are required:
- `frontend/nginx.conf` proxies `/api/` to `http://backend:8080` — in k8s this resolves via the ClusterIP **Service named `backend`** in the same namespace.
- Spring Boot and nginx both log to **stdout/stderr**, which is exactly what the log collector picks up.

## Prerequisites

- A CCE cluster created in the sky47 console (**Cloud Container Engine → Buy/Create Cluster**) with at least one node (2 vCPU / 4 GB is enough)
- `kubectl` installed on your workstation or a jump host
- Docker installed locally (to build and push images)
- Permissions for SWR, CCE, LTS, and ELB in your sky47 account

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
docker build -t $SWR/$ORG/sample-frontend:1.0.0 ./frontend

docker push $SWR/$ORG/sample-backend:1.0.0
docker push $SWR/$ORG/sample-frontend:1.0.0
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

## 6. Edit the manifests and deploy

The image paths in `k8s/backend.yaml` and `k8s/frontend.yaml` are already set to `swr.pk-isb-1.sky47.com/pk-beta-repo/...`. Only one placeholder remains — in `k8s/frontend.yaml` replace:

| Placeholder | Value |
|---|---|
| `<ELB-ID>` | ID of an existing ELB (console → **ELB → your load balancer → ID**), or switch to the `elb.autocreate` annotation in the file |

Then apply (backend first, so the `backend` Service DNS name exists before nginx starts):

```bash
kubectl apply -f k8s/backend.yaml
kubectl rollout status deployment/backend -n sample-app
kubectl apply -f k8s/frontend.yaml
kubectl rollout status deployment/frontend -n sample-app
```

Get the public address:

```bash
kubectl get svc frontend -n sample-app
# EXTERNAL-IP column = ELB address; open http://<EXTERNAL-IP>/ in a browser
```

Quick checks:

```bash
kubectl get pods -n sample-app                                   # all Running/Ready
kubectl logs deploy/backend -n sample-app                        # Spring Boot startup logs
kubectl exec -n sample-app deploy/frontend -- wget -qO- http://backend:8080/api/health
curl http://<EXTERNAL-IP>/                                       # frontend HTML
curl http://<EXTERNAL-IP>/api/health                             # {"status":"UP",...} via nginx proxy
```

## 7. Collect container logs with ICAgent (LTS CCE ingestion)

Both containers already write logs to stdout, so only collection needs configuring. ICAgent on each node collects container stdout and reports it to LTS via a **CCE (Cloud Container Engine)** ingestion configuration — no in-cluster `LogConfig` resource and no manual node file paths are needed (the earlier `k8s/logconfig.yaml` / log-agent approach was removed).

Full step-by-step instructions (based on the LTS 2.5.0 User Guide): **[CCE-LOG-COLLECTION.md](CCE-LOG-COLLECTION.md)**. In short:

1. **LTS → Host Management → Hosts → CCE Cluster tab** — install/upgrade ICAgent on the cluster (auto-creates log group + host group `k8s-log-{ClusterID}`).
2. **LTS → Log Ingestion → Ingestion Center → CCE** — pick fixed or custom log stream, run the dependency check (**Auto Correct**), keep host group `k8s-log-{ClusterID}`, data source **Container standard output**, namespace regex `^sample-app$`. Ensure **Output to AOM is disabled**.
3. Verify in **LTS → Log Management** → your stream after generating traffic.

## 8. Releasing an update

```bash
docker build -t $SWR/$ORG/sample-backend:1.0.1 ./backend
docker push $SWR/$ORG/sample-backend:1.0.1
kubectl set image deployment/backend backend=$SWR/$ORG/sample-backend:1.0.1 -n sample-app
kubectl rollout status deployment/backend -n sample-app
```

(Or edit the tag in `k8s/backend.yaml` and `kubectl apply -f` it — keeps the file as the source of truth.)

## 9. Network / security group notes

- **Worker node security group** (created with the cluster): keep the CCE-generated rules; do not delete them. No extra inbound rule is needed for pod traffic — the ELB reaches NodePorts via the VPC.
- **ELB**: must be a **public** ELB (has an EIP) for browser access. Listener port 80 is created automatically by the Service.
- Do **not** expose the backend with its own LoadBalancer/NodePort — it stays ClusterIP, reachable only inside the cluster via nginx.
- If kubectl access from your laptop is needed, bind an EIP to the cluster API server and restrict its allowed CIDR to your IP.

## Troubleshooting

| Problem | Fix |
|---|---|
| Pod `ImagePullBackOff` | `kubectl describe pod` — check image path matches SWR exactly; ensure `default-secret` exists in `sample-app` (step 5); if image is Private, secret must use a long-term key |
| `exec format error` in pod logs | Image built for wrong CPU arch — rebuild with `--platform linux/amd64` |
| frontend pod CrashLoop: `host not found in upstream "backend"` | Backend Service missing — apply `k8s/backend.yaml` first, then restart frontend: `kubectl rollout restart deploy/frontend -n sample-app` |
| Service `EXTERNAL-IP` stuck `<pending>` | Wrong/missing `kubernetes.io/elb.id`, or autocreate JSON invalid — `kubectl describe svc frontend -n sample-app` shows the event error |
| Browser can't reach EXTERNAL-IP | ELB is private (no EIP) — use a public ELB, or bind an EIP to it |
| No logs in the LTS stream (but `kubectl logs` works) | Check ICAgent status is **Running** on the LTS → Host Management → CCE Cluster tab; ensure **Output to AOM is disabled**; rerun the ingestion wizard's dependency check (**Auto Correct**); confirm the namespace regex matches `sample-app` — see [CCE-LOG-COLLECTION.md](CCE-LOG-COLLECTION.md) troubleshooting |
