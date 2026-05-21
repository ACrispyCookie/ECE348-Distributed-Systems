import threading

from network.network import NetworkThread
from network.network_msgs import MessageType, TupleIdMessage
from network.state import Program, ThreadState
from tuple_space import DistributedTupleManager


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


class DummyScheduler:
    def __init__(self):
        self.tuple_space = DistributedTupleManager(lambda *_: None)


def test_send_migrate_reports_message_send_failure():
    network = make_network_with_send_result(False)

    result = network.send_migrate(make_thread_state(), "127.0.0.1", 5000)

    assert result is False


def test_send_migrate_reports_message_send_success():
    network = make_network_with_send_result(True)

    result = network.send_migrate(make_thread_state(), "127.0.0.1", 5000)

    assert result is True


def test_cancel_pending_destructive_remote_in_restores_only_actual_tuple():
    network = NetworkThread.__new__(NetworkThread)
    network.pending_requests = {
        "thread-1": ("owner-node", ("payload", 99))
    }
    network.scheduler = DummyScheduler()

    TupleIdMessage(MessageType.IN_CANCEL, "thread-1").handle(network, None, None)

    assert network.pending_requests == {}
    assert network.scheduler.tuple_space.get_tuples() == [("payload", 99)]
