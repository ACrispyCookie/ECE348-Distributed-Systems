import pytest
import time
from main import RuntimeEnvironment
from parser import parse_simplescript
from state import ThreadState

# --- HELPERS ---

@pytest.fixture
def env():
    environment = RuntimeEnvironment()
    environment.scheduler.start()
    
    yield environment
    
    environment.running = False
    environment.scheduler.shutdown()

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