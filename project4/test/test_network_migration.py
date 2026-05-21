import threading

from network.network import NetworkThread
from network.network_msgs import MessageType, ThreadMigrateMessage, TupleIdMessage
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
        self.add_thread_result = True
        self.added_threads = []

    def add_thread(self, state):
        self.added_threads.append(state)
        return self.add_thread_result


class DummyConn:
    def getpeername(self):
        return "127.0.0.1", 5000


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


def test_thread_migrate_failed_add_returns_thread_to_source_node():
    network = NetworkThread.__new__(NetworkThread)
    network.scheduler = DummyScheduler()
    network.scheduler.add_thread_result = False
    network.nodes = {
        "source-node": (object(), ("10.0.0.2", 6000))
    }
    returned_migrations = []
    network.send_migrate = lambda state, ip, port: returned_migrations.append((state, ip, port)) or True
    original_state = make_thread_state()

    ThreadMigrateMessage(
        MessageType.MIGRATE,
        "source-node",
        original_state.to_dict(),
    ).handle(network, None, DummyConn())

    returned_state, ip, port = returned_migrations[0]
    assert returned_state.thread_id == original_state.thread_id
    assert (ip, port) == ("10.0.0.2", 6000)
