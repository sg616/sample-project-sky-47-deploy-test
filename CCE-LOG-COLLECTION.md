# CCE Log Collection with ICAgent — sky47 Cloud (LTS)

Configure collection of container stdout/stderr logs from the CCE cluster into LTS using ICAgent, via the LTS console's dedicated **CCE (Cloud Container Engine)** ingestion type.

Reference: *Log Tank Service (LTS) 2.5.0 User Guide* (`lts-usermanual.pdf`), sections 5.3.2 "Ingesting CCE Application Logs to LTS", 6.1 "Managing Host Groups", and 6.2.4 "Managing ICAgent in Container Scenarios".

## How it works

```
Container stdout/stderr → ICAgent (on every cluster node) → LTS log stream
```

LTS delivers the collection configuration to ICAgent through a **host group**; ICAgent then reports matching container logs to the selected log stream. No in-cluster resource (`LogConfig`) and no manual node file paths are needed — the CCE ingestion type understands namespaces/pods/containers natively.

## Prerequisites and constraints

- CCE **standard or Turbo** cluster (ICAgent cannot be installed in Autopilot/Agile clusters).
- Container engine: docker (storage driver must be **overlay2**) or containerd (requires ICAgent **5.12.130 or later**).
- **Output to AOM must be disabled** — otherwise ICAgent sends stdout to AOM only and nothing reaches LTS. It is disabled by default. To check/disable (manual §13.2 "Setting ICAgent Collection"): **LTS → Configuration Center → ICAgent Collection tab → Output to AOM** → select the CCE cluster → toggle **off** its *Apply to Cluster* switch (requires ICAgent 5.12.133+). If it was enabled, disable it *before* installing/upgrading ICAgent.
- Permissions for LTS and CCE in the sky47 account.

## 1. Install/upgrade ICAgent in the CCE cluster

1. Console → **Log Tank Service → Host Management → Hosts** → **CCE Cluster** tab.
2. Select your cluster. If ICAgent status is *Uninstalled* or outdated, click **Upgrade ICAgent** → OK. This installs/upgrades ICAgent on **all nodes** of the cluster (~1 minute).
3. Wait until ICAgent status shows **Running**.

Notes (manual §6.2.4):
- When a CCE cluster is created, ICAgent is normally installed by default with **Output to AOM disabled**. If Output to AOM was enabled at some point, disable it *before* upgrading ICAgent.
- Upgrading ICAgent auto-creates a log group and a **custom-identifier host group**, both named `k8s-log-{ClusterID}`. The ingestion wizard uses these.

## 2. Create the ingestion configuration

1. Console → **LTS → Log Ingestion → Ingestion Center** → click **CCE (Cloud Container Engine)**.
   (Equivalent: **Log Ingestion → Ingestion Management → Create → CCE**.)

2. **Step 1 — Select log stream.** Choose a collection mode:
   - **Fixed log stream** (simplest): logs go to auto-named streams in log group `k8s-log-{ClusterID}` — `stdout-{ClusterID}` for stdout/stderr, `containerfile-{ClusterID}` for container files, `event-{ClusterID}` for Kubernetes events. Select the cluster; the group is created automatically if missing.
   - **Custom log stream**: select the cluster, then a log group/stream of your own (e.g. group `sample-app-logs`, stream `sample-app-stdout` — create them inline if needed).

3. **Step 2 — Check dependencies.** The wizard verifies: ICAgent ≥ 5.12.130 installed, host group `k8s-log-{ClusterID}` exists, log group/stream exist. If anything fails, click **Auto Correct** to fix all items in one click, then **Check Again**.

4. **Step 3 — Select host group.** The host group `k8s-log-{ClusterID}` is pre-selected — keep it. (New nodes inherit the configuration automatically because it is a custom-identifier group.) Click **Next: Configurations**.

5. **Step 4 — Configure the collection.**
   - **Collection Configuration Name**: e.g. `sample-app-stdout`.
   - **Data Source**: **Container standard output**; enable **stdout** and **stderr** (choose whether stderr goes to the same file `stdout.log` or a separate `stderr.log`). Ensure **Output to AOM is off**.
   - **Kubernetes Matching Rules** → **Namespace Name Regular Expression**: `^sample-app$` — limits collection to this app's namespace. (Pod/container name regexes and label white/blacklists are also available; leave empty to collect all.)
   - **Structuring Parsing**: leave disabled for raw keyword search, or enable and pick a rule (Single-Line Full-Text, JSON, Delimiter, Regular, Combined) for field-based search.
   - **Log Format**: Single-line; **Log Time**: System time (defaults, recommended by the manual).

6. **Step 5 — Index settings.** Click **Auto Configure** (generates fields like `hostIP`, `hostName`, `pathFile`) or **Skip and Submit** — indexing can be added later but only applies to newly ingested logs.

7. **Step 6 — Done.** The configuration appears under **Log Ingestion → Ingestion Management**, where you can Modify, Copy, Disable (toggle), or Delete it. Deleting/disabling stops collection — use with caution.

## 3. Verify

1. Generate traffic: open the app and click Health / Info / Echo a few times.
2. Confirm logs exist at the source: `kubectl logs deploy/backend -n sample-app | tail`.
3. Console → **LTS → Log Management** → your log group → stream (`stdout-{ClusterID}` or `sample-app-stdout`) — nginx access lines and Spring Boot lines should appear within ~1 minute. Use keywords such as `Spring` or `GET /api/health`.
4. Optional: configure structuring/indexing for field search (§7 of the manual), or **Log Transfer** to OBS for long-term retention (§11).

## Troubleshooting

| Problem | Fix |
|---|---|
| ICAgent status not **Running** on the CCE Cluster tab | *Offline* = wrong AK/SK — reinstall; *Upgrade failed* — retry **Upgrade ICAgent**; *Faulty* — contact support (manual §6.2.4, Table 6-9) |
| No logs in the stream but `kubectl logs` works | Output to AOM is still enabled (must be disabled); dependency check skipped — rerun the wizard's **Auto Correct**; namespace regex doesn't match `sample-app` |
| Container file logs not collected | docker storage driver is not overlay2 (`docker info \| grep "Storage Driver"`); a CCE workload mount path overrides collection paths; files older than 12 h (new) / 2 h (already collected) are skipped |
| Logs truncated at 500 KB | Enable **Split Logs** in the "Other" settings (500–1,024 KB) |
| Search finds nothing by field | Indexing was skipped or changed after ingestion — index settings apply to new logs only |
