import pytest
import time
from unittest.mock import MagicMock
from scheduler import Scheduler, ThreadStatus
from state import ThreadState, Program, Wildcard
from tuple_space import TupleSpace

class DummyEnv:
    def __init__(self):
        self.running = True

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

