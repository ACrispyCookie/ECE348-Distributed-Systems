import threading
import copy
from state import *
from typing import List, Tuple, Optional, Any, Callable
    
class TupleSpaceManager:
    """Local tuple storage and request queue. Internal use only."""
    def __init__(self):
        self.tuples: List[Tuple[Any, ...]] = []
        self.requests: List[TupleRequest] = []
        self.lock = threading.Lock()

    def out(self, tup: tuple, tid: str=None) -> List[TupleRequest]:
        """Place tuple, return matching requests (RD and one IN, if any)."""
        with self.lock:
            completed_reqs = []
            destructive_found = False
            
            for i in range(len(self.requests) - 1, -1, -1):
                req = self.requests[i]
                if (not tid or tid == req.thread_id) and self._match(tup, req.pattern):
                    if not req.is_destructive:
                        # It's an RD: keep looking
                        completed_reqs.append(self.requests.pop(i))
                    elif not destructive_found:
                        # It's an IN: flag that the tuple is now consumed
                        completed_reqs.append(self.requests.pop(i))
                        destructive_found = True
                
            if not tid and not destructive_found:
                self.tuples.append(tup)
            return completed_reqs

    def read(self, tid: str, pattern: list, destructive: bool, owner: str) -> tuple | TupleRequest:
        """Try to read locally. Create request if not found."""
        with self.lock:
            tup = self._take(pattern) if destructive else self._read(pattern)
            if tup:
                return tup
            
            req = TupleRequest(
                thread_id=tid,
                owner=owner,
                completer=None,
                pattern=pattern,
                is_destructive=destructive
            )
            self.requests.append(req)
            return req
            
    def add_requests(self, reqs: List[TupleRequest]):
        """Add foreign requests (from discovery/startup)."""
        with self.lock:
            self.requests.extend(reqs)

    def remove_request(self, tid: str):
        """Remove all requests from a thread (kill/migrate/shutdown)."""
        with self.lock:
            self.requests = [r for r in self.requests if r.thread_id != tid]
                
    def remove_all_requests(self):
        """Clear all pending requests (shutdown/transfer)."""
        with self.lock:
            self.requests.clear()
                
    def get_tuples(self):
        """Snapshot of tuples for diagnostics."""
        with self.lock:
            return list(self.tuples)

    def get_requests(self):
        """Snapshot of requests for diagnostics."""
        with self.lock:
            return list(self.requests)

    def get_local_requests(self, node_id) -> List[TupleRequest]:
        """Get unclaimed requests for discovery sharing."""
        claimed = []

        with self.lock:
            for req in self.requests:
                if req.owner is None:
                    req_copy = copy.deepcopy(req)
                    req_copy.owner = node_id
                    claimed.append(req_copy)
        return claimed
    
    def _read(self, pattern) -> Optional[Tuple]:
        for tup in self.tuples:
            if self._match(tup, pattern):
                return tup
        return None

    def _take(self, pattern) -> Optional[Tuple]:
        for i, tup in enumerate(self.tuples):
            if self._match(tup, pattern):
                return self.tuples.pop(i)
        return None

    def _match(self, tup: Tuple, pattern: List) -> bool:
        if len(tup) != len(pattern):
            return False
        for t_val, p_val in zip(tup, pattern):
            if isinstance(p_val, Wildcard):
                continue
            if t_val != p_val:
                return False
        return True

class DistributedTupleManager:
    """Protocol coordinator. Only public API for tuple-space access."""
    def __init__(self, scheduler_receive: Callable):
        self.local = TupleSpaceManager()
        self.scheduler_receive = scheduler_receive
        self.send_in = None
        self.send_out = None
        self.send_cancel = None

    def out(self, tup: tuple, completer: str=None, tid: str=None) -> None:
        """Place tuple. Matches local requests and notifies remote owners if locally completed."""
        reqs = self.local.out(tup, tid)
        if not reqs:
            return
        
        for req in reqs:
            if not req.owner:
                self.scheduler_receive(req.thread_id, tup)
                if completer:
                    self.send_cancel(req.thread_id, req.is_destructive, completer)
            elif not completer:
                self.send_out(req.thread_id, req.is_destructive, req.owner, tup)


    def read(self, tid: str, pattern: list, destructive: bool, owner: str=None) -> None:
        """Try to read locally. If tuple returned, complete local or remote request, else create request"""
        ret = self.local.read(tid, pattern, destructive, owner)
        if isinstance(ret, tuple) and owner:
            self.send_out(tid, destructive, owner, ret)
        elif isinstance(ret, tuple):
            self.scheduler_receive(tid, ret)
        elif not owner:
            self.send_in(ret)

    
    def add_foreign_requests(self, reqs: List[TupleRequest]):
        """Add requests from remote nodes (discovery/startup)."""
        self.local.add_requests(reqs)

    def remove_request(self, tid: str):
        """Remove requests for a thread (kill/migrate/shutdown of one thread)."""
        self.local.remove_request(tid)

    def remove_all_requests(self):
        """Clear all requests (shutdown, transfer to another node)."""
        self.local.remove_all_requests()

    def get_tuples(self) -> List[Tuple]:
        """Snapshot of tuples for CLI/monitoring."""
        return self.local.get_tuples()

    def get_requests(self) -> List[TupleRequest]:
        """Snapshot of requests for CLI/monitoring."""
        return self.local.get_requests()

    def get_local_requests(self, node_id: str) -> List[TupleRequest]:
        """Get requests that have no owner yet (for discovery sharing)."""
        return self.local.get_local_requests(node_id)

    def get_foreign_requests(self, node_id: str) -> List[TupleRequest]:
        """Get requests from a specific remote node (for cleanup on node failure)."""
        with self.local.lock:
            return [req for req in self.local.requests if req.owner == node_id]
