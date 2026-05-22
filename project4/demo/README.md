# SimpleScript Distributed Runtime Demo

This directory contains a presentation-ready demo suite for Assignment 4.

Run the runtime from the project root so `run demo/<file>.ss` works:

```bash
python3 src/main.py --load-balance
```

If you are using the bundled virtualenv:

```bash
src/venv/bin/python src/main.py --load-balance
```

## Demo programs

| File | Feature shown |
| --- | --- |
| `01_language_math.ss` | parser, arithmetic, branches, loop, `PRN`, `RET` |
| `02_tuple_consumer.ss` | blocking `IN`, tuple wildcard variables, distributed tuple receive |
| `03_tuple_producer.ss` | delayed `OUT`, remote producer |
| `04_rd_reader.ss` | blocking non-destructive `RD` |
| `05_rd_publisher.ss` | reusable tuple via `OUT` for `RD` |
| `06_migratable_worker.ss` | live thread migration with same thread id continuing execution |
| `07_balancer_sleeper.ss` | automatic load balancing of sleeping threads |
| `08_shutdown_worker.ss` | graceful shutdown migration of running/sleeping thread state |

## Quick local smoke demo

Open two terminals from the project root.

Terminal A:

```text
python3 src/main.py --load-balance
```

Terminal B:

```text
python3 src/main.py --load-balance
```

Wait for discovery logs like:

```text
[NETWORK] Discovered node ...
[NETWORK] NEW from ...
```

Then use the demos below.

### 1. Language + scheduling

On Terminal A:

```text
run demo/01_language_math.ss
run demo/06_migratable_worker.ss
list
```

Expected: factorial output and a long-running heartbeat thread in `list`.

### 2. Distributed tuple space

On Terminal A:

```text
run demo/02_tuple_consumer.ss
tuples
```

Expected: consumer blocks; `tuples` shows a local request for `('demo-task', @task_id, @payload, @origin)`.

On Terminal B:

```text
run demo/03_tuple_producer.ss
```

Expected: Terminal A prints that it consumed the task produced on Terminal B.

### 3. `RD` keeps tuple available

On Terminal A:

```text
run demo/04_rd_reader.ss
```

On Terminal B:

```text
run demo/05_rd_publisher.ss
```

After the reader prints the announcement, run on either node:

```text
tuples
```

Expected: the `("announcement", "hello-from-remote-out")` tuple can remain available because `RD` is non-destructive.

### 4. Manual migration

On Terminal A:

```text
run demo/06_migratable_worker.ss
list
```

Copy a unique prefix of the thread id. Copy Terminal B's IP and port from its startup line:

```text
[NETWORK] Running main socket on <B_IP>:<B_PORT>.
```

On Terminal A:

```text
migrate <THREAD_ID_PREFIX> <B_IP> <B_PORT>
```

On Terminal B:

```text
list
```

Expected: the same thread id appears on B and heartbeats continue there.

### 5. Automatic load balancing

Use three terminals if possible. Start all with `--load-balance`, then on Terminal A:

```text
run demo/07_balancer_sleeper.ss
run demo/07_balancer_sleeper.ss
run demo/07_balancer_sleeper.ss
run demo/07_balancer_sleeper.ss
run demo/07_balancer_sleeper.ss
run demo/07_balancer_sleeper.ss
```

Wait a few seconds, then run on all terminals:

```text
list
```

Expected: sleepers spread across the available environments. The split should be roughly even, e.g. `2/2/2` for three nodes or `3/3` for two nodes.

### 6. Graceful shutdown migration

On Terminal A:

```text
run demo/08_shutdown_worker.ss
list
shutdown
```

On Terminal B:

```text
list
```

Expected: Terminal A exits safely, and the shutdown worker appears/continues on Terminal B.

If Terminal A is the only node, `shutdown` should refuse once and print that no other nodes are available. Running `shutdown` again within 10 seconds force-quits.
