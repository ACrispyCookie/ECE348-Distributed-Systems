from interpreter import Interpreter
from state import ThreadState
from tuple_space import TupleSpace
from enum import Enum
from typing import Dict
import threading
import time

class ThreadStatus(str, Enum):
    ACTIVE = "ACTIVE"
    SLEEPING = "SLEEPING"
    WAITING = "WAITING"

class Scheduler:
    def __init__(self, env):
        self.env = env
        self.interpreter = Interpreter()
        self.tuple_space = TupleSpace(self)

        self.thread = threading.Thread(target=self._scheduler, daemon=True, name="simplescript-scheduler")
        self.lock = threading.Condition()
        self.active_threads: dict[str, ThreadState] = {}
        self.sleeping_threads: dict[str, ThreadState] = {}
        self.waiting_threads: dict[str, ThreadState] = {}

    def start(self):
        self.thread.start()

    def notify(self):
        with self.lock:
            self.lock.notify_all()

    def shutdown(self):
        self.notify()
        self.thread.join()

    def add_thread(self, thread: ThreadState):
        with self.lock:
            self.active_threads[thread.thread_id] = thread
            self.lock.notify_all()

    def kill_thread(self, tid: str) -> bool:
        with self.lock:
            if tid in self.active_threads:
                self.active_threads.pop(tid).is_terminated = True
                return True
            elif tid in self.sleeping_threads:
                self.sleeping_threads.pop(tid).is_terminated = True
                self.lock.notify_all()
                return True
            elif tid in self.waiting_threads:
                self.waiting_threads.pop(tid).is_terminated = True
                return True
            
            return False
            
    def migrate_thread(self, tid: str) -> bool:
        with self.lock:
            if tid in self.get_threads():
                return True
            else:
                return False
            
    def get_threads(self, status: ThreadStatus | None = None) -> Dict[str, ThreadState]:
        with self.lock:
            if not status:
                return self.active_threads | self.sleeping_threads | self.waiting_threads
            elif status == ThreadStatus.ACTIVE:
                return self.active_threads
            elif status == ThreadStatus.SLEEPING:
                return self.sleeping_threads
            elif status == ThreadStatus.WAITING:
                return self.waiting_threads

    def check_tuple_and_wake(self, new_tuple: tuple) -> bool:
        for tid in list(self.waiting_threads.keys()):
            state = self.waiting_threads[tid]
            
            if self.tuple_space.match(new_tuple, state.tuple_pattern):
                self.interpreter.bind_tuple_variables(state, state.tuple_pattern, new_tuple)
                
                self.waiting_threads.pop(tid)
                state.tuple_pattern = []
                self.active_threads[tid] = state
                self.lock.notify_all()
                
                if state.tuple_in:
                    return True
        return False

    def _scheduler(self):
        while self.env.running:
            with self.lock:
                self._wakeup_sleeping()
                while not self.active_threads and self.env.running:
                    self._wait_for_sleeping()
                
                if not self.env.running:
                    break

                thread_ids = list(self.active_threads.keys())
                for tid in thread_ids:
                    state = self.active_threads[tid]

                    if state.is_terminated:
                        del self.active_threads[tid]
                        continue

                    success = self.interpreter.step(state, self.tuple_space)
                    if not success:
                        state.is_terminated = True
                    elif state.wakeup_timestamp != 0:
                        self.active_threads.pop(tid)
                        self.sleeping_threads[tid] = state
                    elif state.tuple_pattern:
                        self.active_threads.pop(tid)
                        self.waiting_threads[tid] = state

    def _wakeup_sleeping(self):
        now = time.time()
        ready_to_wake = [
            tid for tid, state in self.sleeping_threads.items() 
            if state.wakeup_timestamp != 0 and state.wakeup_timestamp <= now
        ]

        for tid in ready_to_wake:
            state = self.sleeping_threads.pop(tid)
            state.wakeup_timestamp = 0
            self.active_threads[tid] = state

    def _wait_for_sleeping(self):
        sleeping_threads = [s for s in self.sleeping_threads.values() if s.wakeup_timestamp != 0]

        if sleeping_threads:
            next_thread = min(sleeping_threads, key=lambda s: s.wakeup_timestamp)
            sleep_duration = next_thread.wakeup_timestamp - time.time()

            if sleep_duration > 0:
                self.lock.wait(timeout=sleep_duration)
            else:
                self._wakeup_sleeping()
        else:
            self.lock.wait()