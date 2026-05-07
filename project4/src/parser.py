from typing import List, Optional
from state import Instruction, InstructionType, Program

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
    
        if not clean_line:
            continue
            
        tokens = clean_line.split()
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
        
        if label:
            if label in labels:
                print(f"Syntax error in {program_name} on line {line_number}: Duplicate label '{label}'.")
                return None
            labels[label] = len(instructions)
        
        instruction = Instruction(tag=tag, args=args, label=label)
        instructions.append(instruction)

    return Program(name=program_name, instructions=instructions, labels=labels)