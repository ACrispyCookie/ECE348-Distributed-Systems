from main import RuntimeEnvironment
from interpreter import Interpreter
import time

def scheduler(env: RuntimeEnvironment):
    interpreter = Interpreter()
    while env.running:
        with env.lock:
            thread_ids = list(env.threads.keys())

            for tid in thread_ids:
                state = env.threads[tid]

                if state.is_terminated:
                    del env.threads[tid]
                    continue

                if state.is_blocked:
                    if state.wakeup_timestamp != 0 and state.wakeup_timestamp < time.time():
                        state.is_blocked = False
                        state.wakeup_timestamp = 0
                    else:
                        continue

                success = interpreter.step(state)
                if not success:
                    state.is_terminated = True



                