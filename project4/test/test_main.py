import pytest
import main
from main import parse_args


class DummyScheduler:
    def __init__(self, env):
        self.env = env


class DummyNetwork:
    def __init__(self, scheduler, tcp_port=0):
        self.scheduler = scheduler
        self.tcp_port = tcp_port
        self.manual_connections = []

    def connect_to_address(self, ip, port):
        self.manual_connections.append((ip, port))
        return True


def test_parse_args_enables_load_balancing_flag():
    args = parse_args(["--load-balance"])

    assert args.load_balance is True


def test_parse_args_leaves_load_balancing_disabled_by_default():
    args = parse_args([])

    assert args.load_balance is False


def test_parse_args_accepts_tcp_port_flag():
    args = parse_args(["--port", "10036"])

    assert args.tcp_port == 10036


def test_parse_args_accepts_tcp_port_short_flag():
    args = parse_args(["-p", "10037"])

    assert args.tcp_port == 10037


def test_parse_args_defaults_to_ephemeral_tcp_port():
    args = parse_args([])

    assert args.tcp_port == 0


def test_parse_args_rejects_out_of_range_tcp_port():
    with pytest.raises(SystemExit):
        parse_args(["--port", "70000"])


def test_parse_args_rejects_negative_tcp_port():
    with pytest.raises(SystemExit):
        parse_args(["--port", "-1"])


def test_runtime_environment_passes_tcp_port_to_network(monkeypatch):
    monkeypatch.setattr(main, "Scheduler", DummyScheduler)
    monkeypatch.setattr(main, "NetworkThread", DummyNetwork)

    env = main.RuntimeEnvironment(tcp_port=10038)

    assert env.network.tcp_port == 10038


def test_runtime_environment_accepts_initial_load_balancing_flag(monkeypatch):
    monkeypatch.setattr(main, "Scheduler", DummyScheduler)
    monkeypatch.setattr(main, "NetworkThread", DummyNetwork)

    env = main.RuntimeEnvironment(load_balancing=True)

    assert env.load_balancing is True


def test_cmd_connect_establishes_manual_connection(monkeypatch, capsys):
    monkeypatch.setattr(main, "Scheduler", DummyScheduler)
    monkeypatch.setattr(main, "NetworkThread", DummyNetwork)
    env = main.RuntimeEnvironment()

    env.cmd_connect(["192.0.2.10", "41000"])

    assert env.network.manual_connections == [("192.0.2.10", 41000)]
    assert "Connected to 192.0.2.10:41000" in capsys.readouterr().out


def test_cmd_connect_rejects_invalid_port(monkeypatch, capsys):
    monkeypatch.setattr(main, "Scheduler", DummyScheduler)
    monkeypatch.setattr(main, "NetworkThread", DummyNetwork)
    env = main.RuntimeEnvironment()

    env.cmd_connect(["192.0.2.10", "not-a-port"])

    assert env.network.manual_connections == []
    assert "Specify a valid port" in capsys.readouterr().out
