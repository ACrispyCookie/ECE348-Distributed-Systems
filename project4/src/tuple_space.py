import threading
from state import *
from typing import List, Tuple, Optional, Any

class TupleSpace:
    def __init__(self, scheduler=None):
        self.tuples: List[Tuple[Any, ...]] = []
        self.lock = threading.Lock()
        self.scheduler = scheduler

    def out_tuple(self, tup: Tuple[Any, ...]) -> None:
        if self.scheduler:
            with self.lock:
                remove = self.scheduler.check_tuple_and_wake(tup)
                if not remove:
                    self.tuples.append(tup)
        else:
            self.tuples.append(tup)

    def rd_tuple(self, pattern: List[Any]) -> Optional[Tuple[Any, ...]]:
        """Reads a matching tuple without removing it. Returns None if no match."""
        with self.lock:
            for tup in self.tuples:
                if self.match(tup, pattern):
                    return tup
            return None

    def in_tuple(self, pattern: List[Any]) -> Optional[Tuple[Any, ...]]:
        """Reads and removes a matching tuple. Returns None if no match."""
        with self.lock:
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
    
    