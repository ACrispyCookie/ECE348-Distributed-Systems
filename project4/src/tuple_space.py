import threading
import copy
from state import *
from typing import List, Tuple, Optional, Any, Callable

class TupleSpace:
    def __init__(self):
        self.tuples: List[Tuple[Any, ...]] = []

    def put(self, tup: Tuple[Any, ...]):
        self.tuples.append(tup)

    def rd(self, pattern: List[Any]) -> Optional[Tuple[Any, ...]]:
        """Reads a matching tuple without removing it. Returns None if no match."""
        for tup in self.tuples:
            if self.match(tup, pattern):
                return tup
        return None

    def take(self, pattern: List[Any]) -> Optional[Tuple[Any, ...]]:
        """Reads and removes a matching tuple. Returns None if no match."""
        for i, tup in enumerate(self.tuples):
            if self.match(tup, pattern):
                return self.tuples.pop(i)
        return None

    def match(self, tup: Tuple[Any, ...], pattern: List[Any]) -> bool:
        """
        Helper method to check if a tuple matches a given pattern.
        """
        if len(tup) != len(pattern):
            return False
            
        for t_val, p_val in zip(tup, pattern):
            if isinstance(p_val, Wildcard):
                continue
            
            if t_val != p_val:
                return False
                
        return True
    
class TupleSpaceManager:
    def __init__(self):
        self.space = TupleSpace()
        self.requests: List[TupleRequest] = []
        self.lock = threading.Lock()

    def out(self, tup: tuple, tid: str=None) -> Optional[TupleRequest]:
        with self.lock:
            complete = None
            for i, req in enumerate(self.requests):
                if (not tid or tid == req.thread_id) and self.space.match(tup, req.pattern):
                    complete = self.requests.pop(i)
                    break
            if not tid and (not complete or not complete.is_destructive):
                self.space.put(tup)
            return complete

    def read(self, tid: str, pattern: list, destructive: bool, owner: str) -> tuple | TupleRequest:
        with self.lock:
            tup = self.space.take(pattern) if destructive else self.space.rd(pattern)
            if not tup:
                req = TupleRequest(
                    thread_id=tid,
                    owner=owner,
                    completer=None,
                    pattern=pattern,
                    is_destructive=destructive
                )
                self.requests.append(req)
                return req
            else:
                return tup
            
    def add_requests(self, reqs: List[TupleRequest]):
        with self.lock:
            self.requests.extend(reqs)

    def remove_request(self, tid: str):
        with self.lock:
            for req in self.requests:
                if req.thread_id == tid:
                    self.requests.remove(req)
                    return
                
    def get_tuples(self):
        with self.lock:
            return list(self.space.tuples)

    def get_requests(self):
        with self.lock:
            return list(self.requests)

    def get_local_requests(self, node_id) -> List[TupleRequest]:
        claimed = []

        with self.lock:
            for req in self.requests:
                if req.owner is None:
                    req_copy = copy.deepcopy(req)
                    req_copy.owner = node_id
                    claimed.append(req_copy)
        return claimed

class DistributedTupleManager:
    def __init__(self, scheduler_receive: Callable):
        self.local = TupleSpaceManager()
        self.scheduler_receive = scheduler_receive
        self.send_in = None
        self.send_out = None
        self.send_cancel = None

    def out(self, tup: tuple, completer: str=None, tid: str=None) -> None:
        req = self.local.out(tup, tid)
        if not req:
            return
        
        if not req.owner:
            self.scheduler_receive(req.thread_id, tup)
            if completer:
                self.send_cancel(req.thread_id, completer)
        elif not completer:
            self.send_out(req.thread_id, req.owner, tup)


    def read(self, tid: str, pattern: list, destructive: bool, owner: str=None) -> None:
        ret = self.local.read(tid, pattern, destructive, owner)
        if isinstance(ret, tuple) and owner:
            self.send_out(tid, owner, ret)
        elif isinstance(ret, tuple):
            self.scheduler_receive(tid, ret)
        elif not owner:
            self.send_in(ret)
