import pytest
import time
from main import RuntimeEnvironment
from parser import parse_simplescript
from network.state import ThreadState
from scheduler import ThreadStatus


def wait_until(predicate, timeout=3.0, interval=0.01, description="condition"):
    start = time.time()
    while time.time() - start <= timeout:
        if predicate():
            return True
        time.sleep(interval)
    raise TimeoutError(f"Timed out waiting for {description}")


def connect_networks(left, right):
    left.network.establish_connection(right.network.node_id, right.network.node_addr)
    wait_until(
        lambda: right.network.node_id in left.network.nodes and left.network.node_id in right.network.nodes,
        timeout=3.0,
        description="two runtime environments to connect",
    )


def stop_environment(environment):
    if environment.running:
        environment.scheduler.shutdown(True)
    environment.network.join(timeout=2.0)


def disable_discovery(environment):
    environment.network.discovery.shutdown()
    environment.network.discovery.join(timeout=2.0)

# --- HELPERS ---

@pytest.fixture
def env():
    environment = RuntimeEnvironment()
    environment.scheduler.start()
    
    yield environment
    
    environment.running = False
    environment.scheduler.shutdown(True)

def wait_for_completion(scheduler, timeout=2.0):
    start = time.time()
    while True:
        if len(scheduler.get_threads()) == 0:
            return True
        if time.time() - start > timeout:
            raise TimeoutError("Test timed out waiting for threads to finish.")
        time.sleep(0.01) # Check every 10ms

def test_e2e_single_script_execution(env, capsys):
    source = """
    #SIMPLESCRIPT
    SET @count 3
    #LoopStart PRN $count
    SUB @count $count 1
    BGT $count 0 #LoopStart
    PRN "Done"
    """
    
    program = parse_simplescript(source, "countdown")
    state = ThreadState(program, args=[])
    env.scheduler.add_thread(state)
    
    wait_for_completion(env.scheduler)
    
    captured = capsys.readouterr()
    
    assert "3" in captured.out
    assert "2" in captured.out
    assert "1" in captured.out
    assert "Done" in captured.out

def test_e2e_producer_consumer_tuple_space(env, capsys):
    # Consumer: Blocks immediately waiting for data
    consumer_source = """
    #SIMPLESCRIPT
    PRN "Waiting for data..."
    IN "payload" @data
    PRN "Received:" $data
    """
    
    # Producer: Sleeps for 1s, then outputs data
    producer_source = """
    #SIMPLESCRIPT
    SLP 1
    OUT "payload" 99
    PRN "Data sent!"
    """
    
    consumer_prog = parse_simplescript(consumer_source, "consumer")
    producer_prog = parse_simplescript(producer_source, "producer")
    
    consumer_state = ThreadState(consumer_prog, args=[])
    producer_state = ThreadState(producer_prog, args=[])
    
    env.scheduler.add_thread(consumer_state)
    env.scheduler.add_thread(producer_state)
    
    wait_for_completion(env.scheduler)
    
    captured = capsys.readouterr()
    output = captured.out
    
    assert "Waiting for data..." in output
    assert "Data sent!" in output
    assert "Received:99" in output
    assert len(env.scheduler.tuple_space.local.tuples) == 0

def test_e2e_deadlock_remains_in_waiting_queue(env):
    source = """
    #SIMPLESCRIPT
    IN "never_gonna_happen" @x
    """
    
    program = parse_simplescript(source, "doomed")
    state = ThreadState(program, args=[])
    env.scheduler.add_thread(state)
    
    # Wait for completion
    with pytest.raises(TimeoutError):
        wait_for_completion(env.scheduler, timeout=1)
        
    from scheduler import ThreadStatus
    waiting_threads = env.scheduler.get_threads(ThreadStatus.WAITING)
    
    assert len(waiting_threads) == 1
    assert list(waiting_threads.values())[0].program.name == "doomed"


def test_e2e_two_nodes_shutdown_migrates_waiting_consumer_and_recreates_request(capsys):
    node_a = RuntimeEnvironment()
    node_b = RuntimeEnvironment()
    disable_discovery(node_a)
    disable_discovery(node_b)
    node_a.scheduler.start()
    node_b.scheduler.start()
    try:
        connect_networks(node_a, node_b)
        consumer_source = """
        #SIMPLESCRIPT
        IN "payload" @data
        PRN "Received:" $data
        """
        producer_source = """
        #SIMPLESCRIPT
        OUT "payload" 42
        PRN "Produced"
        """
        consumer = ThreadState(parse_simplescript(consumer_source, "migrating-consumer"), args=[])
        node_a.scheduler.add_thread(consumer)
        wait_until(
            lambda: consumer.thread_id in node_a.scheduler.get_threads(ThreadStatus.WAITING),
            description="consumer to block on node A",
        )

        result = node_a.scheduler.shutdown(False)
        assert result == 0
        node_a.network.join(timeout=2.0)

        wait_until(
            lambda: consumer.thread_id in node_b.scheduler.get_threads(ThreadStatus.WAITING),
            description="migrated consumer to recreate IN request on node B",
        )
        requests = node_b.scheduler.tuple_space.get_requests()
        assert len([req for req in requests if req.thread_id == consumer.thread_id and req.owner is None]) == 1

        producer = ThreadState(parse_simplescript(producer_source, "producer"), args=[])
        node_b.scheduler.add_thread(producer)
        wait_for_completion(node_b.scheduler, timeout=3.0)

        output = capsys.readouterr().out
        assert "Produced" in output
        assert "Received:42" in output
    finally:
        stop_environment(node_a)
        stop_environment(node_b)


def test_e2e_two_nodes_migration_preserves_tuple_produced_during_handoff(capsys):
    node_a = RuntimeEnvironment()
    node_b = RuntimeEnvironment()
    disable_discovery(node_a)
    disable_discovery(node_b)
    node_a.scheduler.start()
    node_b.scheduler.start()
    try:
        connect_networks(node_a, node_b)
        consumer_source = """
        #SIMPLESCRIPT
        IN "payload" @data
        PRN "Received:" $data
        """
        consumer = ThreadState(parse_simplescript(consumer_source, "migrating-consumer"), args=[])
        node_a.scheduler.add_thread(consumer)
        wait_until(
            lambda: consumer.thread_id in node_a.scheduler.get_threads(ThreadStatus.WAITING),
            description="consumer to block on node A",
        )

        node_b.scheduler.tuple_space.out(("payload", 77))
        wait_until(
            lambda: consumer.thread_id in node_b.network.pending_requests,
            description="node B to reserve tuple for node A's request",
        )

        result = node_a.scheduler.shutdown(False)
        assert result == 0
        node_a.network.join(timeout=2.0)

        captured_output = []
        wait_until(
            lambda: captured_output.append(capsys.readouterr().out) or "Received:77" in "".join(captured_output),
            timeout=3.0,
            description="migrated consumer to consume recovered tuple and print result",
        )
        output = "".join(captured_output) + capsys.readouterr().out
        wait_until(
            lambda: len(node_b.scheduler.get_threads()) == 0,
            timeout=3.0,
            description="migrated consumer to finish",
        )
        assert node_b.network.pending_requests == {}
        assert node_b.scheduler.tuple_space.get_tuples() == []
        assert node_b.scheduler.tuple_space.get_requests() == []
        assert "Received:77" in output
    finally:
        stop_environment(node_a)
        stop_environment(node_b)


def test_e2e_late_joining_node_receives_existing_foreign_request_and_can_complete_it(capsys):
    node_a = RuntimeEnvironment()
    node_b = RuntimeEnvironment()
    disable_discovery(node_a)
    disable_discovery(node_b)
    node_a.scheduler.start()
    node_b.scheduler.start()
    try:
        consumer_source = """
        #SIMPLESCRIPT
        IN "payload" @data
        PRN "Received:" $data
        """
        consumer = ThreadState(parse_simplescript(consumer_source, "consumer-before-peer"), args=[])
        node_a.scheduler.add_thread(consumer)
        wait_until(
            lambda: consumer.thread_id in node_a.scheduler.get_threads(ThreadStatus.WAITING),
            description="consumer to block before peer joins",
        )

        connect_networks(node_a, node_b)
        wait_until(
            lambda: any(
                req.thread_id == consumer.thread_id and req.owner == node_a.network.node_id
                for req in node_b.scheduler.tuple_space.get_requests()
            ),
            description="late-joining node to receive existing foreign request",
        )

        node_b.scheduler.tuple_space.out(("payload", 314))
        wait_until(
            lambda: len(node_a.scheduler.get_threads()) == 0,
            timeout=3.0,
            description="original consumer to complete from late peer OUT",
        )
        wait_until(
            lambda: node_b.network.pending_requests == {},
            timeout=3.0,
            description="late peer pending OUT to be consumed",
        )
        assert "Received:314" in capsys.readouterr().out
    finally:
        stop_environment(node_a)
        stop_environment(node_b)


def test_e2e_shutdown_transfers_stored_tuples_to_surviving_node(capsys):
    node_a = RuntimeEnvironment()
    node_b = RuntimeEnvironment()
    disable_discovery(node_a)
    disable_discovery(node_b)
    node_a.scheduler.start()
    node_b.scheduler.start()
    try:
        connect_networks(node_a, node_b)
        node_a.scheduler.tuple_space.out(("payload", 2718))
        wait_until(
            lambda: node_a.scheduler.tuple_space.get_tuples() == [("payload", 2718)],
            description="tuple to be stored on shutting-down node",
        )

        result = node_a.scheduler.shutdown(False)
        assert result == 0
        node_a.network.join(timeout=2.0)
        wait_until(
            lambda: node_b.scheduler.tuple_space.get_tuples() == [("payload", 2718)],
            description="surviving node to receive transferred tuple",
        )

        consumer_source = """
        #SIMPLESCRIPT
        IN "payload" @data
        PRN "Received:" $data
        """
        consumer = ThreadState(parse_simplescript(consumer_source, "post-transfer-consumer"), args=[])
        node_b.scheduler.add_thread(consumer)
        wait_for_completion(node_b.scheduler, timeout=3.0)
        assert node_b.scheduler.tuple_space.get_tuples() == []
        assert "Received:2718" in capsys.readouterr().out
    finally:
        stop_environment(node_a)
        stop_environment(node_b)
