import pytest
from parser import parse_simplescript, Instruction

def test_parse_empty_and_comments():
    source = """
    #SIMPLESCRIPT
    
    % This is a comment
          % Another comment with spaces
    """
    program = parse_simplescript(source, "TestProg")
    assert len(program.instructions) == 0

def test_parse_basic_instructions():
    source = """
    #SIMPLESCRIPT
    SET @x 10
    PRN "Hello World"
    """
    program = parse_simplescript(source, "TestProg")
    
    assert len(program.instructions) == 2
    
    assert program.instructions[0].tag == "SET"
    assert program.instructions[0].args == ["@x", "10"]
    
    assert program.instructions[1].tag == "PRN"
    assert program.instructions[1].args == ['"Hello World"']

def test_parse_labels():
    source = """
    #SIMPLESCRIPT
    SET @count 0
    #LoopStart ADD @count $count 1
    BRA #LoopStart
    """
    program = parse_simplescript(source, "TestProg")
    
    assert len(program.instructions) == 3
    assert "#LoopStart" in program.labels
    assert program.labels["#LoopStart"] == 1
    assert program.instructions[1].tag == "ADD"