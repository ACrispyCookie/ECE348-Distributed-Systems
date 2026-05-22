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

    def send_migrate(self, state, ip, port):
        self.sent_migrations.append((state, ip, port))
        return self.should_migrate

    def send_cancel(self, tid, destructive, completer):
        self.cancelled_requests.append((tid, destructive, completer))

    def shutdown(self):
        return self.shutdown_result

class DummyEnv:
    def __init__(self):
        self.running = True
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
    assert sched.env.network.cancelled_requests == [(dummy_state.thread_id, True, None)]
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

