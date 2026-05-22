import pytest
import time
from scheduler import Scheduler, ThreadStatus
from network.state import ThreadState, Program, TupleRequest, Wildcard

class DummyNetwork:
    def __init__(self):
        self.nodes = {}
        self.should_migrate = True
        self.shutdown_result = True
        self.sent_migrations = []
        self.cancelled_requests = []
        self.broadcast_loads = []
        self.least_loaded = None
        self.migration_notes = []

    def send_migrate(self, state, ip, port):
        self.sent_migrations.append((state, ip, port))
        return self.should_migrate

    def node_id_for_address(self, ip, port):
        for node_id, (_conn, addr) in self.nodes.items():
            if addr == (ip, port):
                return node_id
        return None

    def send_cancel(self, tid, destructive, completer, exclude_node=None):
        self.cancelled_requests.append((tid, destructive, completer, exclude_node))

    def broadcast_load(self, load):
        self.broadcast_loads.append(load)

    def least_loaded_node(self):
        return self.least_loaded

    def note_migrated_to_node(self, node_id, observed_load):
        self.migration_notes.append((node_id, observed_load))

    def shutdown(self):
        return self.shutdown_result

class DummyEnv:
    def __init__(self):
        self.running = True
        self.load_balancing = False
        self.network = DummyNetwork()

@pytest.fixture
def sched():
    env = DummyEnv()
    scheduler = Scheduler(env)
    return scheduler

@pytest.fixture
def dummy_state():
    prog = Program(name="test_prog", instructions=[], labels={})
    state = ThreadState(program=prog, args=[])
    state.thread_id = "test-uuid-123"
    return state

# --- GENERAL ---

def test_add_thread(sched, dummy_state):
    sched.add_thread(dummy_state)
    assert dummy_state.thread_id in sched.active_threads
    assert len(sched.get_threads(ThreadStatus.ACTIVE)) == 1

def test_kill_active_thread(sched, dummy_state):
    sched.add_thread(dummy_state)
    
    success = sched.kill_thread(dummy_state.thread_id)
    
    assert success is dummy_state.thread_id
    assert dummy_state.thread_id not in sched.active_threads
    assert dummy_state.is_terminated is True

def test_kill_sleeping_thread(sched, dummy_state):
    sched.sleeping_threads[dummy_state.thread_id] = dummy_state
    
    success = sched.kill_thread(dummy_state.thread_id)
    
    assert success is dummy_state.thread_id
    assert dummy_state.thread_id not in sched.sleeping_threads
    assert dummy_state.is_terminated is True

def test_kill_waiting_thread(sched, dummy_state):
    sched.sleeping_threads[dummy_state.thread_id] = dummy_state
    
    success = sched.kill_thread(dummy_state.thread_id)
    
    assert success is dummy_state.thread_id
    assert dummy_state.thread_id not in sched.waiting_threads
    assert dummy_state.is_terminated is True

# --- SLEEP RELATED ---

def test_wakeup_sleeping_moves_thread_when_time_expires(sched, dummy_state):
    # Set the wakeup time to 1 second in the past
    dummy_state.wakeup_timestamp = time.time() - 1
    sched.sleeping_threads[dummy_state.thread_id] = dummy_state
    
    sched._wakeup_sleeping()

    assert dummy_state.thread_id not in sched.sleeping_threads
    assert dummy_state.thread_id in sched.active_threads
    assert dummy_state.wakeup_timestamp == 0

def test_wakeup_sleeping_ignores_future_timers(sched, dummy_state):
    # Set the wakeup time to 10 seconds in the FUTURE
    dummy_state.wakeup_timestamp = time.time() + 10
    sched.sleeping_threads[dummy_state.thread_id] = dummy_state
    
    sched._wakeup_sleeping()
    
    assert dummy_state.thread_id in sched.sleeping_threads
    assert dummy_state.thread_id not in sched.active_threads
    assert dummy_state.wakeup_timestamp != 0

# --- MIGRATION SAFETY ---

def test_migrate_active_thread_failure_keeps_thread_local(sched, dummy_state):
    sched.env.network.should_migrate = False
    sched.active_threads[dummy_state.thread_id] = dummy_state

    result = sched.migrate_thread(dummy_state.thread_id, "127.0.0.1", 5000)

    assert result is None
    assert sched.active_threads[dummy_state.thread_id] is dummy_state
    assert sched.env.network.sent_migrations == [(dummy_state, "127.0.0.1", 5000)]


def test_migrate_sleeping_thread_failure_keeps_thread_local(sched, dummy_state):
    sched.env.network.should_migrate = False
    dummy_state.wakeup_timestamp = time.time() + 60
    sched.sleeping_threads[dummy_state.thread_id] = dummy_state

    result = sched.migrate_thread(dummy_state.thread_id, "127.0.0.1", 5000)

    assert result is None
    assert sched.sleeping_threads[dummy_state.thread_id] is dummy_state
    assert dummy_state.wakeup_timestamp != 0


def test_migrate_waiting_thread_failure_preserves_request_and_state(sched, dummy_state):
    sched.env.network.should_migrate = False
    dummy_state.program_counter = 3
    dummy_state.tuple_pattern = ["payload", Wildcard("x")]
    dummy_state.tuple_in = True
    sched.waiting_threads[dummy_state.thread_id] = dummy_state
    sched.tuple_space.local.requests.append(TupleRequest(
        thread_id=dummy_state.thread_id,
        owner=None,
        completer=None,
        pattern=dummy_state.tuple_pattern,
        is_destructive=True,
    ))

    result = sched.migrate_thread(dummy_state.thread_id, "127.0.0.1", 5000)

    assert result is None
    assert sched.waiting_threads[dummy_state.thread_id] is dummy_state
    assert dummy_state.program_counter == 3
    assert dummy_state.tuple_pattern == ["payload", Wildcard("x")]
    assert [req.thread_id for req in sched.tuple_space.local.requests] == [dummy_state.thread_id]
    assert sched.env.network.cancelled_requests == []


def test_migrate_waiting_thread_success_cancels_request_and_rewinds_for_retry(sched, dummy_state):
    dummy_state.program_counter = 3
    dummy_state.tuple_pattern = ["payload", Wildcard("x")]
    dummy_state.tuple_in = True
    sched.waiting_threads[dummy_state.thread_id] = dummy_state
    sched.tuple_space.local.requests.append(TupleRequest(
        thread_id=dummy_state.thread_id,
        owner=None,
        completer=None,
        pattern=dummy_state.tuple_pattern,
        is_destructive=True,
    ))

    result = sched.migrate_thread(dummy_state.thread_id, "127.0.0.1", 5000)

    assert result == dummy_state.thread_id
    assert dummy_state.thread_id not in sched.waiting_threads
    assert sched.tuple_space.local.requests == []
    assert sched.env.network.cancelled_requests == [(dummy_state.thread_id, True, None, None)]
    migrated_state = sched.env.network.sent_migrations[0][0]
    assert migrated_state is dummy_state
    assert migrated_state.program_counter == 2
    assert migrated_state.tuple_pattern == []


def test_scheduler_shutdown_failure_keeps_environment_running_and_reports_unsafe(sched, dummy_state):
    sched.env.network.nodes = {"peer-node": (object(), ("127.0.0.1", 5000))}
    sched.env.network.shutdown_result = False
    sched.active_threads[dummy_state.thread_id] = dummy_state
    sched.thread.join = lambda: None

    result = sched.shutdown(False)

    assert result == -1
    assert sched.env.running is True
    assert dummy_state.thread_id in sched.active_threads


def test_migrate_waiting_thread_cancel_excludes_destination_node(sched, dummy_state):
    sched.env.network.nodes = {
        "target-node": (object(), ("127.0.0.1", 5000)),
        "other-node": (object(), ("127.0.0.1", 5001)),
    }
    dummy_state.program_counter = 3
    dummy_state.tuple_pattern = ["payload", Wildcard("x")]
    dummy_state.tuple_in = True
    sched.waiting_threads[dummy_state.thread_id] = dummy_state
    sched.tuple_space.local.requests.append(TupleRequest(
        thread_id=dummy_state.thread_id,
        owner=None,
        completer=None,
        pattern=dummy_state.tuple_pattern,
        is_destructive=True,
    ))

    result = sched.migrate_thread(dummy_state.thread_id, "127.0.0.1", 5000)

    assert result == dummy_state.thread_id
    assert sched.env.network.cancelled_requests == [
        (dummy_state.thread_id, True, None, "target-node")
    ]


def test_load_returns_total_active_sleeping_and_waiting_threads(sched, dummy_state):
    sleeping = ThreadState(program=dummy_state.program, args=[])
    sleeping.thread_id = "sleeping-thread"
    waiting = ThreadState(program=dummy_state.program, args=[])
    waiting.thread_id = "waiting-thread"
    sched.active_threads[dummy_state.thread_id] = dummy_state
    sched.sleeping_threads[sleeping.thread_id] = sleeping
    sched.waiting_threads[waiting.thread_id] = waiting

    assert sched.load() == 3


def test_choose_thread_for_load_balance_prefers_active_then_sleeping_then_waiting(sched, dummy_state):
    sleeping = ThreadState(program=dummy_state.program, args=[])
    sleeping.thread_id = "sleeping-thread"
    waiting = ThreadState(program=dummy_state.program, args=[])
    waiting.thread_id = "waiting-thread"
    sched.active_threads[dummy_state.thread_id] = dummy_state
    sched.sleeping_threads[sleeping.thread_id] = sleeping
    sched.waiting_threads[waiting.thread_id] = waiting

    assert sched.choose_thread_for_load_balance() == dummy_state.thread_id

    sched.active_threads.clear()
    assert sched.choose_thread_for_load_balance() == sleeping.thread_id

    sched.sleeping_threads.clear()
    assert sched.choose_thread_for_load_balance() == waiting.thread_id


def test_load_balance_does_not_migrate_when_difference_is_less_than_two(sched, dummy_state):
    sched.env.load_balancing = True
    sched.env.network.least_loaded = ("peer-node", 1, ("127.0.0.1", 5000))
    sched.active_threads[dummy_state.thread_id] = dummy_state
    second = ThreadState(program=dummy_state.program, args=[])
    second.thread_id = "second-thread"
    sched.active_threads[second.thread_id] = second

    sched._maybe_balance_load()

    assert sched.env.network.broadcast_loads == [2]
    assert sched.env.network.sent_migrations == []


def test_load_balance_migrates_one_thread_to_least_loaded_peer_when_difference_is_at_least_two(sched, dummy_state):
    sched.env.load_balancing = True
    sched.env.network.least_loaded = ("peer-node", 1, ("127.0.0.1", 5000))
    sched.active_threads[dummy_state.thread_id] = dummy_state
    second = ThreadState(program=dummy_state.program, args=[])
    second.thread_id = "second-thread"
    sched.active_threads[second.thread_id] = second
    third = ThreadState(program=dummy_state.program, args=[])
    third.thread_id = "third-thread"
    sched.active_threads[third.thread_id] = third

    sched._maybe_balance_load()

    assert sched.env.network.broadcast_loads == [3]
    assert len(sched.env.network.sent_migrations) == 1
    migrated_state, ip, port = sched.env.network.sent_migrations[0]
    assert migrated_state.thread_id == dummy_state.thread_id
    assert (ip, port) == ("127.0.0.1", 5000)
    assert dummy_state.thread_id not in sched.active_threads
    assert sched.env.network.migration_notes == [("peer-node", 1)]

