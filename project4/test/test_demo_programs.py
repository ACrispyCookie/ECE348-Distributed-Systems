from pathlib import Path

from parser import parse_simplescript


def test_all_demo_programs_parse_successfully():
    demo_dir = Path(__file__).resolve().parents[1] / "demo"
    demo_files = sorted(demo_dir.glob("*.ss"))

    assert demo_files, "Expected demo/*.ss programs to exist"
    for demo_file in demo_files:
        program = parse_simplescript(demo_file.read_text(), str(demo_file))
        assert program is not None, f"{demo_file} should parse successfully"
        assert program.instructions, f"{demo_file} should contain at least one instruction"
