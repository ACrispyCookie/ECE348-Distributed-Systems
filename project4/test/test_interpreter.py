import pytest
from interpreter import Interpreter, Instruction
from scheduler import Scheduler
from network.state import ThreadState, Program, Wildcard
from tuple_space import DistributedTupleManager

@pytest.fixture
def blank_state():
    """Provides an empty thread state for each test."""
    dummy_program = Program(name="Test", instructions=[], labels={})
    return ThreadState(program=dummy_program, args=[])

@pytest.fixture
def sched():
    """Provides a dummy scheduler for each test."""
    sched = Scheduler(None)
    sched.tuple_space.send_in = lambda *args, **kwargs: None
    sched.tuple_space.send_cancel = lambda *args, **kwargs: None
    return sched

# --- ARITHMETIC TESTS ---

def test_math_add(blank_state):
    blank_state.variables["x"] = 10
    blank_state.variables["y"] = 5
    instr = Instruction(tag="ADD", args=["@sum", "$x", "$y"])
    
    Interpreter()._execute_instruction(instr, blank_state, DistributedTupleManager(None))
    
    assert blank_state.variables["sum"] == 15

def test_math_sub(blank_state):
    blank_state.variables["x"] = 10
    blank_state.variables["y"] = 5
    instr = Instruction(tag="SUB", args=["@sum", "$x", "$y"])
    
    Interpreter()._execute_instruction(instr, blank_state, DistributedTupleManager(None))
    
    assert blank_state.variables["sum"] == 5

def test_math_mul(blank_state):
    blank_state.variables["x"] = 10
    blank_state.variables["y"] = 5
    instr = Instruction(tag="MUL", args=["@sum", "$x", "$y"])
    
    Interpreter()._execute_instruction(instr, blank_state, DistributedTupleManager(None))
    
    assert blank_state.variables["sum"] == 50

def test_math_div(blank_state):
    blank_state.variables["x"] = 10
    blank_state.variables["y"] = 5
    instr = Instruction(tag="DIV", args=["@sum", "$x", "$y"])
    
    Interpreter()._execute_instruction(instr, blank_state, DistributedTupleManager(None))
    
    assert blank_state.variables["sum"] == 2

def test_math_mod(blank_state):
    blank_state.variables["x"] = 10
    blank_state.variables["y"] = 5
    instr = Instruction(tag="MOD", args=["@sum", "$x", "$y"])
    
    Interpreter()._execute_instruction(instr, blank_state, DistributedTupleManager(None))
    
    assert blank_state.variables["sum"] == 0

def test_math_division_by_zero(blank_state):
    blank_state.variables["x"] = 10
    blank_state.variables["y"] = 0
    instr = Instruction(tag="DIV", args=["@ans", "$x", "$y"])
    
    with pytest.raises(ZeroDivisionError):
        Interpreter()._execute_instruction(instr, blank_state, DistributedTupleManager(None))

def test_math_type_enforcement(blank_state):
    blank_state.variables["x"] = 10
    blank_state.variables["y"] = "Hello" # Cannot add a string
    instr = Instruction(tag="ADD", args=["@ans", "$x", "$y"])
    
    with pytest.raises(TypeError):
        Interpreter()._execute_instruction(instr, blank_state, DistributedTupleManager(None))

# --- BRANCHING TESTS ---

def test_branching_beq_true(blank_state):
    # Setup dummy labels
    blank_state.program.labels["#Target"] = 5 
    blank_state.program_counter = 0
    
    blank_state.variables["val"] = 42
    instr = Instruction(tag="BEQ", args=["$val", "42", "#Target"])
    
    Interpreter()._execute_instruction(instr, blank_state, DistributedTupleManager(None))
    
    # Because 42 == 42, PC should jump to 5
    assert blank_state.program_counter == 5

def test_branching_beq_false(blank_state):
    blank_state.program.labels["#Target"] = 5 
    blank_state.program_counter = 0
    
    blank_state.variables["val"] = 42
    instr = Instruction(tag="BEQ", args=["$val", "99", "#Target"])
    
    Interpreter()._execute_instruction(instr, blank_state, DistributedTupleManager(None))
    
    # Because 42 != 99, PC should increment by 1
    assert blank_state.program_counter == 1

def test_branch_to_missing_label(blank_state):
    instr = Instruction(tag="BRA", args=["#GhostLabel"])
    
    with pytest.raises(ValueError, match="Cannot branch to unknown label"):
        Interpreter()._execute_instruction(instr, blank_state, DistributedTupleManager(None))

# --- TUPLE SPACE TESTS ---

def test_tuple_space_out(blank_state, sched):
    ts = sched.tuple_space
    blank_state.variables["my_var"] = 99
    
    # OUT "test" $my_var
    instr = Instruction(tag="OUT", args=['"test"', "$my_var"])
    Interpreter()._execute_instruction(instr, blank_state, ts)
    
    # Verify the space holds the concrete evaluated values
    assert ("test", 99) in ts.local.tuples

def test_tuple_space_rd_blocks_when_empty(blank_state, sched):
    ts = sched.tuple_space
    # RD "test" $x
    instr = Instruction(tag="RD", args=['"test"', "@x"])
    Interpreter()._execute_instruction(instr, blank_state, ts)
    
    # Should flag the thread as blocked and register the pattern
    assert blank_state.tuple_pattern == ["test", Wildcard("x")]
    assert blank_state.tuple_in is False

def test_tuple_space_in_blocks_when_empty(blank_state, sched):
    ts = sched.tuple_space
    # IN "test" $x
    instr = Instruction(tag="IN", args=['"test"', "@x"])
    Interpreter()._execute_instruction(instr, blank_state, ts)
    
    # Should flag the thread as blocked and register the pattern
    assert blank_state.tuple_pattern == ["test", Wildcard("x")]
    assert blank_state.tuple_in is True

def test_tuple_space_in_consumes_and_binds(blank_state, sched):
    ts = sched.tuple_space
    ts.local.tuples.append(("task", 42, "calc"))
    sched.active_threads[blank_state.thread_id] = blank_state
    
    # IN "task" $id $action
    instr = Instruction(tag="IN", args=['"task"', "@id", "@action"])
    Interpreter()._execute_instruction(instr, blank_state, ts)
    
    # Thread should NOT be blocked
    assert blank_state.tuple_pattern == [] 
    
    # Variables should be bound correctly
    assert blank_state.variables["id"] == 42
    assert blank_state.variables["action"] == "calc"
    
    # Tuple should be REMOVED from the space
    assert len(ts.local.tuples) == 0

def test_tuple_space_rd_reads_but_keeps(blank_state, sched):
    ts = sched.tuple_space
    ts.local.tuples.append(("status", 200))
    sched.active_threads[blank_state.thread_id] = blank_state
    
    instr = Instruction(tag="RD", args=['"status"', "@code"])
    Interpreter()._execute_instruction(instr, blank_state, ts)
    
    assert blank_state.variables["code"] == 200
    assert len(ts.local.tuples) == 1


def test_tuple_space_pattern_rejects_unquoted_bareword(blank_state, sched):
    instr = Instruction(tag="RD", args=["payload", "@value"])

    with pytest.raises(ValueError, match="Cannot resolve tuple pattern value"):
        Interpreter()._execute_instruction(instr, blank_state, sched.tuple_space)

    assert blank_state.tuple_pattern == []
    assert sched.tuple_space.get_requests() == []

