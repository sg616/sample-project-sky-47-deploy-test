# New Setup Deployment Guide — Updating the Running Cluster (RDS + Product CRUD)

This guide covers rolling out the **new setup** (backend connected to RDS for MySQL + product CRUD API + catalog frontend) on top of a cluster where the **previous version of the backend and frontend is already running** (namespace `sample-app`, SWR images `sample-backend:1.0.0` / `sample-frontend:1.0.0`, ELB already bound to the frontend Service).

If you are starting from an empty cluster instead, use [K8S-DEPLOYMENT.md](K8S-DEPLOYMENT.md) (this guide skips cluster, namespace, SWR org, image-pull secret, and ELB setup — all of that already exists).

## What changes in this rollout

| Component | Change |
|---|---|
| Backend image | New build with Spring Data JPA + MySQL driver + product CRUD endpoints (tag `1.1.0`) |
| Frontend image | New build with the product catalog UI (tag `1.1.0`) |
| RDS | New RDS for MySQL 5.7.44 instance (engine version 5.7.44 — latest offered by sky47) |
| k8s Secret | New `mysql-secret` in `sample-app` with DB credentials |
| `k8s/backend.yaml` | New image tag + `DB_HOST` / `DB_PORT` / `DB_NAME` env + secret references (already in the file) |

Everything else (Services, ELB, nginx `/api/` proxy, ICAgent/LTS log collection) stays as is. Log collection keeps working unchanged because both containers still log to stdout — see [CCE-LOG-COLLECTION.md](CCE-LOG-COLLECTION.md).

## Prerequisites

- kubectl still connected to the cluster (see K8S-DEPLOYMENT.md §1)
- Docker login to SWR still valid (K8S-DEPLOYMENT.md §3)
- The cluster and the new RDS instance in the **same VPC**

## 1. Provision RDS for MySQL 5.7.44

1. Console → **RDS → Create Instance**: engine **MySQL**, version **5.7.44**, same VPC as the CCE cluster. Set the root password and create a database named **`sampleapp`**. Note the instance address and port **3306**.
2. Security group: allow inbound TCP **3306** from the CCE node subnet (or the cluster security group), so pods can reach RDS.
3. Run the schema + seed scripts against `sampleapp` (RDS console SQL window / DAS, or a mysql client):
   ```bash
   mysql -h <rds-address> -P 3306 -u root -p sampleapp < dbscripts/001_create_products.sql
   mysql -h <rds-address> -P 3306 -u root -p sampleapp < dbscripts/002_seed_products.sql
   ```

## 2. Create the DB credentials Secret

```bash
cp k8s/mysql-secret.example.yaml k8s/mysql-secret.yaml   # then set real DB_USER / DB_PASSWORD
kubectl apply -f k8s/mysql-secret.yaml
kubectl get secret mysql-secret -n sample-app
```

## 3. Build and push the new images

```bash
export SWR=swr.pk-isb-1.sky47.com
export ORG=pk-beta-repo

docker build -t $SWR/$ORG/sample-backend:1.1.0 ./backend
docker build -t $SWR/$ORG/sample-frontend:1.1.0 ./frontend

docker push $SWR/$ORG/sample-backend:1.1.0
docker push $SWR/$ORG/sample-frontend:1.1.0
```

> On Apple Silicon / ARM: `docker buildx build --platform linux/amd64 -t $SWR/$ORG/sample-backend:1.1.0 --push ./backend`

## 4. Point the backend manifest at the new image and RDS

In `k8s/backend.yaml`:
1. Update the image tag: `swr.pk-isb-1.sky47.com/pk-beta-repo/sample-backend:1.0.0` → `:1.1.0`
2. Replace the `DB_HOST` placeholder with the RDS address from step 1:
   ```yaml
   - name: DB_HOST
     value: <rds-mysql-instance-address>
   ```
   (`DB_PORT` 3306 and `DB_NAME` sampleapp are already set; credentials come from `mysql-secret`.)

In `k8s/frontend.yaml`:
1. Update the image tag: `sample-frontend:1.0.0` → `:1.1.0`
2. Leave the ELB annotation untouched — the running frontend Service/ELB is reused.

## 5. Roll out

Apply the backend first (its new pods must be Ready before the new frontend starts proxying to it):

```bash
kubectl apply -f k8s/backend.yaml
kubectl rollout status deployment/backend -n sample-app
kubectl apply -f k8s/frontend.yaml
kubectl rollout status deployment/frontend -n sample-app
```

`kubectl apply` performs a rolling update: the old pod keeps serving until the new pod passes its readiness probe, so there is no downtime. The old backend pods (image 1.0.0, no DB) are replaced by new pods that connect to RDS.

## 6. Verify the new setup

```bash
kubectl get pods -n sample-app                          # all Running/Ready
kubectl logs deploy/backend -n sample-app               # Hikari pool started, no DB errors
kubectl get svc frontend -n sample-app                  # EXTERNAL-IP = same ELB as before

curl http://<EXTERNAL-IP>/api/health                    # {"status":"UP",...}
curl http://<EXTERNAL-IP>/api/info                      # "database":{"status":"UP","product":"MySQL","version":"5.7.44",...}
curl http://<EXTERNAL-IP>/api/products                  # seeded product list (JSON)
curl -X POST http://<EXTERNAL-IP>/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Test Product","price":12.50,"stock":3}'  # 201 — then refresh the UI and delete it
```

Then open `http://<EXTERNAL-IP>/` in a browser: the product catalog should show the seeded rows, and add/edit/delete should work end-to-end (UI → nginx → backend → RDS).

## 7. Roll back if needed

The old images are still in SWR, so a rollback is a tag change only:

```bash
kubectl set image deployment/backend backend=swr.pk-isb-1.sky47.com/pk-beta-repo/sample-backend:1.0.0 -n sample-app
kubectl set image deployment/frontend frontend=swr.pk-isb-1.sky47.com/pk-beta-repo/sample-frontend:1.0.0 -n sample-app
kubectl rollout status deployment/backend -n sample-app
kubectl rollout status deployment/frontend -n sample-app
```

or simply `kubectl rollout undo deployment/backend -n sample-app` (and same for frontend) to go back one revision. The RDS instance and `mysql-secret` can stay — the old backend just ignores them.

## Troubleshooting

| Problem | Fix |
|---|---|
| New backend pods `CrashLoopBackOff` with `Communications link failure` | RDS unreachable — check `DB_HOST`, that `mysql-secret` exists in `sample-app`, and that the RDS security group allows TCP 3306 from the cluster nodes |
| Backend starts but `/api/products` returns 500 | `products` table missing — run `dbscripts/001_create_products.sql` against `sampleapp`; confirm `DB_NAME` matches |
| `/api/info` shows `"database":{"status":"DOWN"}` | Backend up but DB connect fails — check credentials/security group, then backend logs |
| New frontend can't reach backend | Backend rollout must finish first — re-check `kubectl rollout status deployment/backend -n sample-app`; the `backend` Service name must stay `backend` |
| UI shows old version after rollout | Hard-refresh the browser (nginx serves the new static bundle; clear cache) |
