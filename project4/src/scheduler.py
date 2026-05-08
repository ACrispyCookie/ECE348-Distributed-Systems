from main import RuntimeEnvironment
from interpreter import Interpreter
import time

def scheduler(env: RuntimeEnvironment):
    interpreter = Interpreter()
    while env.running:
        with env.lock:
            wakeup_sleeping(env)
            while not env.active_threads and env.running:
                wait_for_sleeping(env)
            
            if not env.running:
                break

            thread_ids = list(env.active_threads.keys())
            for tid in thread_ids:
                state = env.active_threads[tid]

                if state.is_terminated:
                    del env.active_threads[tid]
                    continue

                success = interpreter.step(state)
                if not success:
                    state.is_terminated = True
                elif state.is_blocked:
                    env.active_threads.pop(tid)
                    env.blocked_threads[tid] = state

def wakeup_sleeping(env: RuntimeEnvironment):
    now = time.time()
    ready_to_wake = [
        tid for tid, state in env.blocked_threads.items() 
        if state.wakeup_timestamp != 0 and state.wakeup_timestamp <= now
    ]

    for tid in ready_to_wake:
        state = env.blocked_threads.pop(tid)
        state.wakeup_timestamp = 0
        state.is_blocked = False
        env.active_threads[tid] = state

def wait_for_sleeping(env: RuntimeEnvironment):
    sleeping_threads = [s for s in env.blocked_threads.values() if s.wakeup_timestamp != 0]

    if sleeping_threads:
        next_thread = min(sleeping_threads, key=lambda s: s.wakeup_timestamp)
        sleep_duration = next_thread.wakeup_timestamp - time.time()

        if sleep_duration > 0:
            env.lock.wait(timeout=sleep_duration)
        else:
            wakeup_sleeping(env)
    else:
        env.lock.wait()