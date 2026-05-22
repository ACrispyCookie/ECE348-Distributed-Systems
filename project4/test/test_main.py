import main
from main import parse_args


class DummyScheduler:
    def __init__(self, env):
        self.env = env


class DummyNetwork:
    def __init__(self, scheduler):
        self.scheduler = scheduler


def test_parse_args_enables_load_balancing_flag():
    args = parse_args(["--load-balance"])

    assert args.load_balance is True


def test_parse_args_leaves_load_balancing_disabled_by_default():
    args = parse_args([])

    assert args.load_balance is False


def test_runtime_environment_accepts_initial_load_balancing_flag(monkeypatch):
    monkeypatch.setattr(main, "Scheduler", DummyScheduler)
    monkeypatch.setattr(main, "NetworkThread", DummyNetwork)

    env = main.RuntimeEnvironment(load_balancing=True)

    assert env.load_balancing is True
