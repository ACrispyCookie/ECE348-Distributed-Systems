from state import ThreadState
from state import InstructionType
import time

class Interpreter:
    def step(self, state: ThreadState) -> bool:
        """
        Executes a single instruction for the given ThreadState.
        Advances the program counter.
        """

        instruction = state.get_current_instruction()
        if not instruction:
            state.is_terminated = True
            return False
        
        try:
            self._execute_instruction(instruction, state)

            if not self._is_branch_instruction(instruction.tag):
                state.program_counter += 1
            return True
        except Exception as e:
            print(f"\n[Runtime Error - Thread {state.thread_id}] Line {state.program_counter + 1}: {e}")
            state.is_terminated = True
            return False

    def _execute_instruction(self, instr, state) -> None:
        tag = instr.tag
        args = instr.args
        
        # --- VARIABLE ASSIGNMENT ---
        if tag == "SET":
            state.variables[args[0]] = self._get_value(args[1], state)
            
        # --- ARITHMETIC ---
        elif tag in ("ADD", "SUB", "MUL", "DIV", "MOD"):
            var_name = args[0]
            val1 = self._get_value(args[1], state)
            val2 = self._get_value(args[2], state)
            
            # Ensure we are doing math on integers, not strings
            if not isinstance(val1, int) or not isinstance(val2, int):
                raise TypeError(f"Arithmetic requires integers. Got {type(val1).__name__} and {type(val2).__name__}")

            if tag == "ADD":
                state.variables[var_name] = val1 + val2
            elif tag == "SUB":
                state.variables[var_name] = val1 - val2
            elif tag == "MUL":
                state.variables[var_name] = val1 * val2
            elif tag == "DIV":
                if val2 == 0: raise ZeroDivisionError("Division by zero")
                state.variables[var_name] = val1 // val2
            elif tag == "MOD":
                if val2 == 0: raise ZeroDivisionError("Modulo by zero")
                state.variables[var_name] = val1 % val2

        # --- CONDITIONAL BRANCHING ---
        elif tag in ("BGT", "BGE", "BLT", "BLE", "BEQ"):
            val1 = self._get_value(args[0], state)
            val2 = self._get_value(args[1], state)
            label = args[2]
            
            condition_met = False
            if tag == "BGT": condition_met = (val1 > val2)
            elif tag == "BGE": condition_met = (val1 >= val2)
            elif tag == "BLT": condition_met = (val1 < val2)
            elif tag == "BLE": condition_met = (val1 <= val2)
            elif tag == "BEQ": condition_met = (val1 == val2)
            
            if condition_met:
                self._jump_to_label(label, state)
            else:
                state.program_counter += 1

        # --- UNCONDITIONAL BRANCHING ---
        elif tag == "BRA":
            label = args[0]
            self._jump_to_label(label, state)

        # --- UTILITY ---
        elif tag == "PRN":
            values = [str(self._get_value(arg, state)) for arg in args]
            print(f"[Thread {state.thread_id}] {' '.join(values)}")
            
        elif tag == "SLP":
            seconds = self._get_value(args[0], state)
            state.wakeup_timestamp = time.time() + seconds
            state.is_blocked = True
            
        elif tag == "RET":
            state.is_terminated = True
            
        # --- TUPLE SPACE ---
        elif tag in ("OUT", "RD", "IN"):
            # Stage 1: treated as no-ops per assignment instructions
            pass
            
        else:
            raise ValueError(f"Unsupported instruction tag: {tag}")

    def _get_value(self, arg: str, state):
        """
        Parses an argument. It can be an integer, a string literal, or a variable name.
        """
        if arg.startswith('$'):
            if arg not in state.variables:
                raise NameError(f"Undeclared variable '{arg}' referenced.")
            return state.variables[arg]

        if arg.startswith('"'):
            if not arg.endswith('"'):
                raise NameError(f'Expected " after {arg}.')
            return arg[1:-1]
            
        try:
            return int(arg)
        except ValueError:
            pass
        
    def _jump_to_label(self, label: str, state) -> None:
        """Sets the program counter to the index of the requested label."""
        if label not in state.program.labels:
            raise ValueError(f"Cannot branch to unknown label '{label}'")
        state.program_counter = state.program.labels[label]

    def _is_branch_instruction(self, tag: str) -> bool:
        """Helper to determine if an instruction manipulates the PC itself."""
        return tag in ("BGT", "BGE", "BLT", "BLE", "BEQ", "BRA", "RET")