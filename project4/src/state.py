import uuid
from dataclasses import dataclass, asdict
from typing import List, Dict, Optional
from enum import Enum

class InstructionType(str, Enum):
    # Variable Assignment
    SET = "SET"
    
    # Arithmetic Operations
    ADD = "ADD"
    SUB = "SUB"
    MUL = "MUL"
    DIV = "DIV"
    MOD = "MOD"
    
    # Conditional Branching
    BGT = "BGT"
    BGE = "BGE"
    BLT = "BLT"
    BLE = "BLE"
    BEQ = "BEQ"
    
    # Unconditional Branching
    BRA = "BRA"
    
    # Tuple Space Operations
    OUT = "OUT"
    RD = "RD"
    IN = "IN"
    
    # Utilities
    SLP = "SLP"
    PRN = "PRN"
    RET = "RET"

@dataclass
class Instruction:
    """Represents a single parsed line of SimpleScript."""
    tag: InstructionType
    args: List[str]
    label: Optional[str] = None

    def to_dict(self):
        return {"tag": self.tag, "args": self.args, "label": self.label}

    @classmethod
    def from_dict(cls, data):
        return cls(tag=InstructionType(data["tag"]), args=data["args"], label=data["label"])

@dataclass
class Program:
    """Represents a fully parsed SimpleScript program."""
    name: str
    instructions: List[Instruction]
    labels: Dict[str, int]

    def to_dict(self):
        return {
            "name": self.name,
            "instructions": [inst.to_dict() for inst in self.instructions],
            "labels": self.labels
        }

    @classmethod
    def from_dict(cls, data):
        return cls(
            name=data["name"],
            instructions=[Instruction.from_dict(i) for i in data["instructions"]],
            labels=data["labels"]
        )

@dataclass
class Wildcard:
    name: str

    def to_dict(self):
        return {
            "__type__": "wildcard",
            "name": self.name
        }

    @classmethod
    def from_dict(cls, data):
        return cls(name=data["name"])


class ThreadState:
    """Holds the execution state of a specific thread."""
    
    def __init__(self, program: Program, args: List[str]):
        self.thread_id: str = str(uuid.uuid4())
        self.program: Program = program
        
        self.program_counter: int = 0
        self.variables: Dict[str, str | int] = {}
        
        self.wakeup_timestamp: float = 0
        self.tuple_pattern: list = []
        self.tuple_in: bool = False
        self.is_terminated: bool = False

        self._initialize_arguments(args)

    def _initialize_arguments(self, args: List[str]) -> None:
        """
        Initializes the $arg<n> variables as required by SimpleScript specs.
        """
        self.variables["arg0"] = self.program.name
        self.variables["argc"] = len(args)
        
        for i, arg in enumerate(args):
            try:
                val = int(arg)
            except ValueError:
                val = arg
                
            self.variables[f"arg{i+1}"] = val

    def get_current_instruction(self) -> Optional[Instruction]:
        """Returns the instruction at the current PC"""
        if self.program_counter >= len(self.program.instructions):
            return None
        return self.program.instructions[self.program_counter]
    
    def to_dict(self):
        return {
            "thread_id": self.thread_id,
            "program": self.program.to_dict(),
            "program_counter": self.program_counter,
            "variables": self.variables,
            "wakeup_timestamp": self.wakeup_timestamp
        }

    @classmethod
    def from_dict(cls, data):
        # Create a dummy state to bypass the __init__ logic
        state = cls.__new__(cls)
        state.thread_id = data["thread_id"]
        state.program = Program.from_dict(data["program"])
        state.program_counter = data["program_counter"]
        state.variables = data["variables"]
        state.wakeup_timestamp = data["wakeup_timestamp"]
        state.tuple_pattern = []
        state.tuple_in = False
        state.is_terminated = False
        return state

@dataclass
class TupleRequest:
    thread_id: str
    owner: str
    completer: str
    pattern: list[str|int|Wildcard]
    is_destructive: bool

    def to_dict(self) -> dict:
        def encode_item(x):
            if isinstance(x, Wildcard):
                return x.to_dict()
            return x

        return {
            "thread_id": self.thread_id,
            "owner": self.owner,
            "completer": self.completer,
            "pattern": [encode_item(x) for x in self.pattern],
            "is_destructive": self.is_destructive,
        }

    @classmethod
    def from_dict(cls, data):
        def decode_item(x):
            if isinstance(x, dict) and x.get("__type__") == "wildcard":
                return Wildcard.from_dict(x)
            return x

        return cls(
            thread_id=data["thread_id"],
            owner=data["owner"],
            completer=data["completer"],
            pattern=[decode_item(x) for x in data["pattern"]],
            is_destructive=data["is_destructive"],
        )