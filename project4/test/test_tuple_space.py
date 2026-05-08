import pytest
from tuple_space import TupleSpace
from state import Wildcard

@pytest.fixture
def ts():
    return TupleSpace()

# --- MATCH FUNCTION ---
def test_match_exact_values(ts):
    tup = ("task", 42, "calculate")
    pattern = ["task", 42, "calculate"]
    assert ts.match(tup, pattern) is True

def test_match_length_mismatch(ts):
    tup = ("task", 42)
    # Pattern is longer
    assert ts.match(tup, ["task", 42, "calculate"]) is False
    # Pattern is shorter
    assert ts.match(tup, ["task"]) is False

def test_match_value_mismatch(ts):
    tup = ("task", 42)
    assert ts.match(tup, ["task", 99]) is False
    assert ts.match(tup, ["shutdown", 42]) is False

def test_match_type_mismatch(ts):
    tup = ("task", 42)
    # The integer 42 should NOT match the string "42"
    assert ts.match(tup, ["task", "42"]) is False

def test_match_single_wildcard(ts):
    tup = ("task", 42, "calculate")
    pattern = ["task", Wildcard("$id"), Wildcard("calculate")]
    assert ts.match(tup, pattern) is True

def test_match_multiple_wildcards(ts):
    tup = ("task", 42, "calculate")
    pattern = [Wildcard("$type"), Wildcard("$id"), Wildcard("$action")]
    assert ts.match(tup, pattern) is True

def test_match_no_wildcards_just_variables(ts):
    # Ensure that if the tuple itself literally contains a string starting with '$', 
    # it still matches an exact pattern correctly.
    tup = ("$literal_string", 42)
    pattern = ["$literal_string", 42]
    assert ts.match(tup, pattern) is True

# --- Operations ---
def test_out_tuple_adds_to_space(ts):
    assert len(ts.tuples) == 0
    ts.out_tuple(("task", 1))
    ts.out_tuple(("task", 2))
    assert len(ts.tuples) == 2
    assert ts.tuples[0] == ("task", 1)

def test_rd_tuple_finds_match_and_keeps_it(ts):
    ts.out_tuple(("task", 42))
    
    # Read with a wildcard
    result = ts.rd_tuple(["task", Wildcard("$id")])
    
    assert result == ("task", 42)
    # Verify it was NOT removed
    assert len(ts.tuples) == 1

def test_rd_tuple_returns_none_if_no_match(ts):
    ts.out_tuple(("task", 42))
    result = ts.rd_tuple(["shutdown"])
    assert result is None

def test_in_tuple_finds_match_and_removes_it(ts):
    ts.out_tuple(("task", 42))
    ts.out_tuple(("other", 99))
    
    # Read and remove with a wildcard
    result = ts.in_tuple(["task", Wildcard("$id")])
    
    assert result == ("task", 42)
    # Verify it WAS removed
    assert len(ts.tuples) == 1
    # Verify the other tuple is still safe
    assert ts.tuples[0] == ("other", 99)

def test_in_tuple_returns_none_if_no_match(ts):
    ts.out_tuple(("task", 42))
    result = ts.in_tuple(["shutdown"])
    
    assert result is None
    # Verify nothing was accidentally removed
    assert len(ts.tuples) == 1

# --- EDGE CASES ---

def test_in_tuple_only_removes_one_match(ts):
    # Add two identical tuples to the space
    ts.out_tuple(("task", 42))
    ts.out_tuple(("task", 42))
    
    assert len(ts.tuples) == 2
    
    # IN should only consume the FIRST one it finds
    result = ts.in_tuple(["task", 42])
    
    assert result == ("task", 42)
    assert len(ts.tuples) == 1  # The duplicate should still be there

def test_rd_and_in_order_preservation(ts):
    # Ensure tuples are evaluated in the order they were added (FIFO-ish)
    ts.out_tuple(("task", 1))
    ts.out_tuple(("task", 2))
    ts.out_tuple(("task", 3))
    
    # A generic wildcard should grab the oldest one first
    result = ts.in_tuple(["task", Wildcard("$id")])
    assert result == ("task", 1)
    
    result2 = ts.in_tuple(["task", Wildcard("$id")])
    assert result2 == ("task", 2)