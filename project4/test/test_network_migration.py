import threading

from network.network import NetworkThread
from network.state import Program, ThreadState


def make_network_with_send_result(send_result):
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.node_id = "local-node"
    network.nodes = {
        "target-node": (object(), ("127.0.0.1", 5000))
    }
    network._send_message = lambda node_id, message: send_result
    return network


def make_thread_state():
    return ThreadState(Program(name="prog", instructions=[], labels={}), args=[])


def test_send_migrate_reports_message_send_failure():
    network = make_network_with_send_result(False)

    result = network.send_migrate(make_thread_state(), "127.0.0.1", 5000)

    assert result is False


def test_send_migrate_reports_message_send_success():
    network = make_network_with_send_result(True)

    result = network.send_migrate(make_thread_state(), "127.0.0.1", 5000)

    assert result is True
