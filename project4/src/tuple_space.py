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

    def out(self, tup: tuple) -> Optional[TupleRequest]:
        with self.lock:
            for i, req in enumerate(self.requests):
                if self.space.match(tup, req.pattern):
                    return self.requests.pop(i)
            self.space.put(tup)
            return None

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

    def out(self, tup: tuple, completer: str=None) -> None:
        req = self.local.out(tup)
        if not req:
            return
        
        if not req.owner:
            self.scheduler_receive(req.thread_id, tup)
            if completer:
                print("Cancel request")
        elif not completer:
            print("Complete network request")


    def read(self, tid: str, pattern: list, destructive: bool, owner: str=None) -> None:
        ret = self.local.read(tid, pattern, destructive, owner)
        if isinstance(ret, tuple) and owner:
            print("Complete network request")
        elif isinstance(ret, tuple):
            self.scheduler_receive(tid, ret)
