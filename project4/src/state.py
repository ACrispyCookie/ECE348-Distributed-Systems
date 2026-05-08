import uuid
from dataclasses import dataclass
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

@dataclass
class Program:
    """Represents a fully parsed SimpleScript program."""
    name: str
    instructions: List[Instruction]
    labels: Dict[str, int]

@dataclass
class Wildcard:
    name: str


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
        self.variables["$arg0"] = self.program.name
        self.variables["$argc"] = len(args)
        
        for i, arg in enumerate(args):
            try:
                val = int(arg)
            except ValueError:
                val = arg
                
            self.variables[f"$arg{i+1}"] = val

    def get_current_instruction(self) -> Optional[Instruction]:
        """Returns the instruction at the current PC"""
        if self.program_counter >= len(self.program.instructions):
            return None
        return self.program.instructions[self.program_counter]