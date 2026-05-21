from typing import List, Optional
from network.state import Instruction, InstructionType, Program
import shlex

_FIXED_ARITY = {
    "SET": 2,
    "ADD": 3,
    "SUB": 3,
    "MUL": 3,
    "DIV": 3,
    "MOD": 3,
    "BGT": 3,
    "BGE": 3,
    "BLT": 3,
    "BLE": 3,
    "BEQ": 3,
    "BRA": 1,
    "SLP": 1,
    "RET": 0,
}

_MIN_ARITY = {
    "OUT": 1,
    "RD": 1,
    "IN": 1,
}


def _validate_instruction_arity(tag: str, arg_count: int) -> Optional[str]:
    if tag in _FIXED_ARITY:
        expected = _FIXED_ARITY[tag]
        if arg_count != expected:
            return f"Instruction '{tag}' expects {expected} argument(s), got {arg_count}."
    elif tag in _MIN_ARITY:
        minimum = _MIN_ARITY[tag]
        if arg_count < minimum:
            return f"Instruction '{tag}' expects at least {minimum} argument(s), got {arg_count}."
    return None

def parse_simplescript(source_code: str, program_name: str) -> Optional[Program]:
    """
    Parses a SimpleScript source string into a Program object.
    """
    instructions: List[Instruction] = []
    labels = {}
    lines = source_code.strip().split('\n')
    
    if not lines:
        print(f"Syntax error in {program_name}: File is empty.")
        return None
        
    first_line_tokens = lines[0].strip().split()
    if not first_line_tokens or first_line_tokens[0] != "#SIMPLESCRIPT":
        print(f"Syntax error in {program_name} on line 1: Program must start with '#SIMPLESCRIPT'.")
        return None
    
    valid_tags = {instr.value for instr in InstructionType}

    for line_number, line in enumerate(lines[1:], start=2):
        clean_line = line.strip()
    
        if not clean_line or clean_line.startswith('%'):
            continue
            
        try:
            tokens = shlex.split(clean_line, posix=False)
        except ValueError as e:
            print(f"Syntax error in {program_name} on line {line_number}: {e}")
            return None
        label = None
        tag_index = 0

        if tokens[0].startswith('#'):
            label = tokens[0]
            tag_index = 1
            
            if len(tokens) < 2:
                print(f"Syntax error in {program_name} on line {line_number}: Label '{label}' is missing an instruction.")
                return None

        tag = tokens[tag_index]
        args = tokens[tag_index + 1:]
        if tag not in valid_tags:
            print(f"Syntax error in {program_name} on line {line_number}: Unknown instruction '{tag}'.")
            return None

        arity_error = _validate_instruction_arity(tag, len(args))
        if arity_error:
            print(f"Syntax error in {program_name} on line {line_number}: {arity_error}")
            return None
        
        if label:
            if label in labels:
                print(f"Syntax error in {program_name} on line {line_number}: Duplicate label '{label}'.")
                return None
            labels[label] = len(instructions)
        
        instruction = Instruction(tag=tag, args=args, label=label)
        instructions.append(instruction)

    return Program(name=program_name, instructions=instructions, labels=labels)