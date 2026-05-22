# Cross-Machine Demo and Reliability Checklist

Goal: verify the project before the presentation on **different physical machines**, not only local tests.

This guide assumes the repository is copied to each machine at the same path or at least with the same project contents.

---

## 0. Before going to the lab/classroom

Run this on your development machine:

```bash
cd project4
src/venv/bin/python -m pytest -q
src/venv/bin/python -m compileall -q src test
```

Expected:

```text
all tests pass
compileall has no output/errors
```

Also verify the demo files parse:

```bash
PYTHONPATH=src src/venv/bin/python - <<'PY'
from pathlib import Path
from parser import parse_simplescript
for path in sorted(Path('demo').glob('*.ss')):
    assert parse_simplescript(path.read_text(), str(path)), path
    print('OK', path)
PY
```

---

## 1. Network prerequisites

All machines should be on the same LAN/Wi-Fi. The runtime uses:

- TCP: one randomly assigned listening port per runtime;
- UDP multicast discovery: `224.1.1.1:10100`.

Recommended firewall allowance during the demo:

- allow inbound TCP to the runtime port printed at startup;
- allow UDP port `10100` for multicast discovery.

On Linux, useful checks are:

```bash
ip addr
hostname -I
```

If automatic discovery fails, use the manual `connect <ip> <port>` command added for demo robustness.

---

## 2. Start one runtime per machine

On every machine:

```bash
cd project4
python3 src/main.py --load-balance
```

or, if using the bundled virtualenv:

```bash
src/venv/bin/python src/main.py --load-balance
```

Each runtime prints something like:

```text
[NETWORK] Running main socket on 192.168.1.23:41295.
[DISCOVERY] Discovering nodes on 224.1.1.1:10100 every 1.0 seconds.
```

Write down each machine's `<IP>:<PORT>`.

---

## 3. Verify node connectivity

### Automatic path

Wait 2-5 seconds. You should see logs like:

```text
[NETWORK] Discovered node on <IP>:<PORT>, sending NEW message
[NETWORK] NEW from <IP>:<PORT>
```

### Manual fallback path

If no discovery logs appear, manually connect each machine to one known peer.

Example: on Machine A, connect to Machine B:

```text
connect 192.168.1.24 41295
```

Expected:

```text
[RUNTIME] Connected to 192.168.1.24:41295
[NETWORK] NEW from 192.168.1.24:41295
```

For three machines, connect each machine to the others for the most reliable load-balancing demo:

```text
# On B
connect <A_IP> <A_PORT>
connect <C_IP> <C_PORT>

# On C
connect <A_IP> <A_PORT>
connect <B_IP> <B_PORT>
```

A simpler star topology where B and C both connect to A is usually enough for A-driven tuple/migration demos, but a full mesh makes the three-node load-balancing demo more predictable.

---

## 4. Cross-machine acceptance tests

Use these as your go/no-go checklist.

### Test A: local language/runtime sanity

On Machine A:

```text
run demo/01_language_math.ss
```

Pass if you see:

```text
DEMO1: final factorial=720
```

### Test B: distributed `IN`/`OUT`

On Machine A:

```text
run demo/02_tuple_consumer.ss
tuples
```

Pass if `tuples` shows a waiting request.

On Machine B:

```text
run demo/03_tuple_producer.ss
```

Pass if Machine A prints:

```text
DEMO2: consumed task id=101 payload=calculate-gravity origin=remote-node
```

### Test C: non-destructive `RD`

On Machine A:

```text
run demo/04_rd_reader.ss
```

On Machine B:

```text
run demo/05_rd_publisher.ss
```

Pass if Machine A prints:

```text
DEMO3: RD saw announcement=hello-from-remote-out
```

Then run:

```text
tuples
```

Pass if the announcement tuple is still visible somewhere or at least was not consumed by the reader thread.

### Test D: manual migration

On Machine A:

```text
run demo/06_migratable_worker.ss
list
```

Copy a unique thread-id prefix, then migrate it to Machine B:

```text
migrate <THREAD_ID_PREFIX> <B_IP> <B_PORT>
```

On Machine B:

```text
list
```

Pass if the same thread id is listed on Machine B and heartbeats continue.

### Test E: automatic load balancing

Start three runtimes if possible. On Machine A:

```text
run demo/07_balancer_sleeper.ss
run demo/07_balancer_sleeper.ss
run demo/07_balancer_sleeper.ss
run demo/07_balancer_sleeper.ss
run demo/07_balancer_sleeper.ss
run demo/07_balancer_sleeper.ss
```

Wait 5-10 seconds, then run on each machine:

```text
list
```

Pass if the sleepers are roughly evenly distributed:

- 2 machines: usually `3/3` or close;
- 3 machines: usually `2/2/2` or close.

### Test F: graceful shutdown migration

On Machine A:

```text
run demo/08_shutdown_worker.ss
shutdown
```

On Machine B:

```text
list
```

Pass if Machine A shuts down safely and Machine B owns the migrated worker.

---

## 5. Presentation script order

A good live order is:

1. Run `demo/01_language_math.ss` to show SimpleScript basics.
2. Start two nodes and show discovery / `connect` fallback.
3. Run consumer on A, producer on B to show distributed tuple space.
4. Run `RD` demo to explain destructive vs non-destructive reads.
5. Start migratable worker on A, migrate to B, show same thread id.
6. Start six balancer sleepers on A, show automatic distribution.
7. Run shutdown worker on A, call `shutdown`, show it continues on B.

This order builds from simple local semantics to the distributed features, matching the assignment stages.

---

## 6. If something fails during demo

### No discovery logs

Use manual connect:

```text
connect <peer_ip> <peer_port>
```

### `migrate` says node not found

The target node is not connected yet. Run manual `connect` first from one side, wait for `NEW`, then retry migration.

### Consumer never receives producer tuple

Check:

```text
tuples
list
```

Make sure the consumer is waiting and both nodes are connected. If needed, run producer again.

### Load balancing does not move immediately

Wait a few seconds and run `list` again. Load balancing is periodic and avoids useless oscillations.

### Shutdown refuses

If the message says no other nodes are available, connect another runtime first. A second `shutdown` within 10 seconds force-quits, but do not use that for the graceful-shutdown demo.

---

## 7. Minimum confident pre-demo checklist

- [ ] Full test suite passes on your laptop.
- [ ] Demo files parse.
- [ ] Two machines can run the runtime.
- [ ] At least one direction of `connect <ip> <port>` works.
- [ ] Cross-machine `IN`/`OUT` passes.
- [ ] Manual migration passes.
- [ ] Load balancing spreads sleepers.
- [ ] Graceful shutdown migrates a worker to another node.
