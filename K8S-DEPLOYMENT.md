# Kubernetes Deployment Guide — sky47 Cloud (CCE + SWR + LTS)

Deploy the sample app to a Kubernetes (CCE) cluster on sky47 cloud, pulling images from sky47 SWR (SoftWare Repository for Container) and shipping container logs to the sky47 log service (LTS / AOM Log Center).

sky47 is a Huawei Cloud Stack (HCS) based provider — console menu names below follow HCS conventions. Reference docs: https://docs.financialkhazanapk.cloud/mohelpcenter/operation/en-us/index.html

## Architecture

```
Browser → ELB (public EIP) → frontend Service (LoadBalancer)
            → frontend pod (nginx) → /api/ proxied → backend Service (ClusterIP) → backend pod (Spring Boot)
Container stdout → ICAgent / log-agent (DaemonSet on nodes) → LTS log group/stream
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

## 7. Ship container logs to the sky47 log service (LTS)

Both containers already write logs to stdout, so only collection needs configuring. CCE clusters ship logs via a node-level agent; depending on the CCE version sky47 runs, it is either **ICAgent (AOM)** or the **log-agent add-on (Cloud Native Logging)**. Check which add-on your cluster has: Console → **CCE → your cluster → Add-ons**.

### 7.1 Create the LTS log group and stream

1. Console → **LTS (Log Tank Service) → Log Management → Create Log Group**, e.g. `sample-app-logs` (set retention, e.g. 7 days).
2. Inside it, **Create Log Stream**, e.g. `sample-app-stdout`.
3. Note the **log group ID** and **log stream ID** (shown in each item's details).

### 7.2 Option A — log-agent add-on (Cloud Native Logging)

1. CCE → cluster → **Add-ons → Cloud Native Logging (log-agent) → Install** (accept defaults). The CRD-based `LogConfig` policy below requires add-on version **1.6.1 or later**.
2. Edit `k8s/logconfig.yaml`: replace `<LTS-LOG-GROUP-ID>` and `<LTS-LOG-STREAM-ID>` with the IDs from 7.1, then:
   ```bash
   kubectl apply -f k8s/logconfig.yaml
   ```
   This collects stdout of **all containers in the `sample-app` namespace** into your LTS stream.

   > ⚠️ The `LogConfig` resource must live in **`kube-system`** — that namespace is fixed by
   > the add-on, not a choice, because the log-agent only watches collection rules there. The rule
   > selects the app namespace via `spec.inputDetail.containerStdout.namespaces`, *not* via
   > `metadata.namespace`. Applying it to `sample-app` succeeds and `kubectl get logconfig -n sample-app`
   > shows it as created, but nothing ever collects and the LTS stream stays empty.
3. Alternatively do the same from the console: CCE → cluster → **Logging → Collection Policies → Create**, source = container stdout, namespace = `sample-app`, target = your LTS group/stream.

### 7.3 Option B — ICAgent + AOM (older HCS clusters)

1. CCE → cluster → **Add-ons → ICAgent** — ensure it is installed and Running on every node (`kubectl get pods -n kube-system | grep icagent`).
2. ICAgent collects container stdout automatically. View logs under **AOM → Log → Log Search**, filtering by cluster / namespace `sample-app` / workload name.
3. To also store them in LTS: **AOM → Log → Log Dump** (or LTS → Access → CCE) and map the `sample-app` workloads to the log group/stream from 7.1.

### 7.4 Verify logs arrive

1. Confirm the collection rule is registered where the agent reads it (Option A only):
   ```bash
   kubectl get logconfig -n kube-system                              # sample-app-stdout must be listed here
   kubectl get logconfig sample-app-stdout -n kube-system -o yaml    # check namespaces + LTS IDs
   ```
2. Generate traffic: open the app and click Health / Info / Echo a few times.
3. Confirm the lines exist at the source: `kubectl logs deploy/backend -n sample-app | tail`.
4. Console → **LTS → your log group → sample-app-stdout** — you should see nginx access lines and Spring Boot log lines within ~1 minute.
5. If the stream is still empty, check the agent for LTS errors: `kubectl logs -n kube-system -l app=log-agent --tail=100`.
6. Optional: set up structuring/alarms in LTS as needed.

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
| No logs in LTS (but `kubectl logs` works) | Most common cause: the `LogConfig` was applied to the app namespace instead of `kube-system` — it is accepted there but never read. Verify with `kubectl get logconfig -n kube-system`. Then check agent pods in `kube-system` are Running; confirm the log group/stream IDs in `logconfig.yaml` belong to the same region/project as the cluster; confirm `spec.inputDetail.containerStdout.namespaces` includes `sample-app` |
