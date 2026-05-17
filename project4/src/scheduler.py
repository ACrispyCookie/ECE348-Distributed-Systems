from interpreter import Interpreter
from network.state import ThreadState
from tuple_space import DistributedTupleManager
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
        self.tuple_space = DistributedTupleManager(self._on_tuple_received)

        self.thread = threading.Thread(target=self._scheduler, daemon=True, name="simplescript-scheduler")
        self.lock = threading.Condition(threading.RLock())
        self.active_threads: dict[str, ThreadState] = {}
        self.sleeping_threads: dict[str, ThreadState] = {}
        self.waiting_threads: dict[str, ThreadState] = {}

    def start(self):
        self.thread.start()

    def notify(self):
        with self.lock:
            self.lock.notify_all()

    def shutdown(self, force: bool):
        with self.lock:
            result = 0
            if not force and len(self.get_threads()) != 0 and len(self.env.network.nodes) == 0:
                return -1
            
            if len(self.get_threads()) != 0 and len(self.env.network.nodes) == 0:
                result = 1
            
            self.env.running = False
            self.env.network.shutdown()

        self.notify()
        self.thread.join()
        return result

    def add_thread(self, thread: ThreadState) -> bool:
        with self.lock:
            if not self.env.running:
                return False
            
            if thread.wakeup_timestamp == 0:
                self.active_threads[thread.thread_id] = thread
            else:
                self.sleeping_threads[thread.thread_id] = thread
            self.lock.notify_all()
            return True

    def kill_thread(self, tid: str) -> bool:
        with self.lock:
            real_tid, thread = self._resolve_tid(tid)
            if thread is None:
                return None
            
            if real_tid in self.active_threads:
                self.active_threads.pop(real_tid).is_terminated = True
                return real_tid
            elif real_tid in self.sleeping_threads:
                self.sleeping_threads.pop(real_tid).is_terminated = True
                self.lock.notify_all()
                return real_tid
            elif real_tid in self.waiting_threads:
                self.tuple_space.remove_request(real_tid)
                self.env.network.send_cancel(real_tid, self.waiting_threads[real_tid].tuple_in, self.env.network.node_id)
                self.waiting_threads.pop(real_tid).is_terminated = True
                return real_tid
            
            return None
            
    def migrate_thread_ip(self, tid: str, target_ip: str, target_port: int) -> bool:
        with self.lock:
            real_tid, thread = self._resolve_tid(tid)
            if thread is None:
                return None
            
            return self.migrate_thread(real_tid, target_ip, target_port)
            
    def migrate_thread(self, tid: str, target_ip: str, target_port: int) -> bool:
        with self.lock:
            if tid in self.active_threads:
                state = self.active_threads.pop(tid)
            elif tid in self.sleeping_threads:
                state = self.sleeping_threads.pop(tid)
            elif tid in self.waiting_threads:
                state = self.waiting_threads.pop(tid)
                self.tuple_space.remove_request(tid)
                self.env.network.send_cancel(tid, state.tuple_in, self.env.network.node_id)
                state.tuple_pattern = []
                state.program_counter -= 1

            if self.env.network.send_migrate(state, target_ip, target_port):
                return tid
            return None

            
    def get_threads(self, status: ThreadStatus | None = None) -> Dict[str, ThreadState]:
        with self.lock:
            if not status:
                return dict(self.active_threads | self.sleeping_threads | self.waiting_threads)
            elif status == ThreadStatus.ACTIVE:
                return dict(self.active_threads)
            elif status == ThreadStatus.SLEEPING:
                return dict(self.sleeping_threads)
            elif status == ThreadStatus.WAITING:
                return dict(self.waiting_threads)
            
    def _resolve_tid(self, tid_prefix: str):
        matches = []

        for tid, thread in (
            list(self.active_threads.items())
            + list(self.sleeping_threads.items())
            + list(self.waiting_threads.items())
        ):
            if tid.startswith(tid_prefix):
                matches.append((tid, thread))

        if len(matches) == 0:
            return None, None

        if len(matches) > 1:
            return None, None  # ambiguous

        return matches[0]

    def _on_tuple_received(self, tid: str, new_tuple: tuple):
        with self.lock:
            if tid not in self.waiting_threads.keys() and tid not in self.active_threads.keys():
                raise NameError("Something went terribly wrong!")
            
            if tid in self.active_threads:
                state = self.active_threads[tid]
                self.interpreter.bind_tuple_variables(state, state.tuple_pattern, new_tuple)
                state.tuple_pattern = []
                return
            
            state = self.waiting_threads[tid]
            self.interpreter.bind_tuple_variables(state, state.tuple_pattern, new_tuple)
            state.tuple_pattern = []
            self.waiting_threads.pop(tid)
            self.active_threads[tid] = state
            self.lock.notify_all()

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