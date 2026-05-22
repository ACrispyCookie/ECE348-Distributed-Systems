import threading

from network.network import NetworkThread
from network.network_msgs import (
    Message,
    MessageType,
    LoadStatusMessage,
    ShutdownMessage,
    ShutdownPrepareAckMessage,
    ShutdownPrepareMessage,
    ThreadMigrateMessage,
    TupleIdMessage,
    TupleInMessage,
)
from network.state import Program, ThreadState, TupleRequest, Wildcard
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
        self.threads = {}
        self.migrated_threads = []
        self.migrate_result = "thread-1"

    def add_thread(self, state):
        self.added_threads.append(state)
        return self.add_thread_result

    def get_threads(self):
        return dict(self.threads)

    def migrate_thread(self, tid, ip, port):
        self.migrated_threads.append((tid, ip, port))
        return self.migrate_result


class DummyDiscovery:
    def __init__(self):
        self.shutdown_called = False
        self.join_called = False

    def shutdown(self):
        self.shutdown_called = True

    def join(self):
        self.join_called = True


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


def test_load_status_message_updates_peer_load():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.nodes = {"peer-node": (object(), ("127.0.0.1", 5000))}
    network.draining_nodes = set()
    network.node_loads = {}

    Message.handle_from_json(
        LoadStatusMessage(MessageType.LOAD_STATUS, "peer-node", 3).to_json().decode("utf-8"),
        network,
        ("127.0.0.1", 5000),
        None,
    )

    assert network.node_loads == {"peer-node": 3}


def test_broadcast_load_sends_status_to_available_nodes_only():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.node_id = "local-node"
    network.nodes = {
        "available-node": (object(), ("127.0.0.1", 5000)),
        "draining-node": (object(), ("127.0.0.1", 5001)),
    }
    network.draining_nodes = {"draining-node"}
    sent_messages = []
    network._send_message = lambda node_id, message: sent_messages.append((node_id, message)) or True

    network.broadcast_load(4)

    assert len(sent_messages) == 1
    assert sent_messages[0][0] == "available-node"
    assert isinstance(sent_messages[0][1], LoadStatusMessage)
    assert sent_messages[0][1].node_id == "local-node"
    assert sent_messages[0][1].load == 4


def test_least_loaded_node_ignores_unknown_loads_and_draining_nodes():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.nodes = {
        "busy-node": (object(), ("127.0.0.1", 5000)),
        "idle-node": (object(), ("127.0.0.1", 5001)),
        "draining-node": (object(), ("127.0.0.1", 5002)),
        "unknown-load-node": (object(), ("127.0.0.1", 5003)),
    }
    network.draining_nodes = {"draining-node"}
    network.node_loads = {
        "busy-node": 5,
        "idle-node": 1,
        "draining-node": 0,
    }

    assert network.least_loaded_node() == ("idle-node", 1, ("127.0.0.1", 5001))


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
    network.draining = False
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


def test_shutdown_prepare_marks_peer_draining_and_sends_ack():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.node_id = "local-node"
    network.draining_nodes = set()
    sent_messages = []
    network._send_message = lambda node_id, message: sent_messages.append((node_id, message)) or True

    ShutdownPrepareMessage(MessageType.SHUTDOWN_PREPARE, "peer-node").handle(network, None, None)

    assert network.draining_nodes == {"peer-node"}
    assert sent_messages[0][0] == "peer-node"
    assert isinstance(sent_messages[0][1], ShutdownPrepareAckMessage)
    assert sent_messages[0][1].node_id == "local-node"


def test_shutdown_prepare_ack_is_recorded_and_notifies_waiter():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.shutdown_prepare_condition = threading.Condition(network.lock)
    network.shutdown_prepare_acks = set()

    ShutdownPrepareAckMessage(MessageType.SHUTDOWN_PREPARE_ACK, "peer-node").handle(network, None, None)

    assert network.shutdown_prepare_acks == {"peer-node"}


def test_shutdown_broadcasts_prepare_and_waits_for_acks_before_final_shutdown():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.shutdown_prepare_condition = threading.Condition(network.lock)
    network.node_id = "local-node"
    network.nodes = {
        "target-node": (object(), ("127.0.0.1", 5000)),
        "other-node": (object(), ("127.0.0.1", 5001)),
    }
    network.draining = False
    network.draining_nodes = set()
    network.shutdown_prepare_acks = set()
    network.discovery = DummyDiscovery()
    network.running = True
    network.scheduler = DummyScheduler()
    migrated = []
    network.scheduler.get_threads = lambda: {"thread-1": make_thread_state()}
    network.scheduler.migrate_thread = lambda tid, ip, port: migrated.append((tid, ip, port)) or tid
    network.scheduler.tuple_space.out(("kept",))
    sent_messages = []

    def send_message(node_id, message):
        sent_messages.append((node_id, message.type))
        if message.type == MessageType.SHUTDOWN_PREPARE:
            network.mark_shutdown_prepare_ack(node_id)
        return True

    network._send_message = send_message

    assert network.shutdown() is True

    assert network.draining is True
    assert sent_messages[0:2] == [
        ("target-node", MessageType.SHUTDOWN_PREPARE),
        ("other-node", MessageType.SHUTDOWN_PREPARE),
    ]
    assert migrated == [("thread-1", "127.0.0.1", 5000)]
    assert sent_messages[-2:] == [
        ("target-node", MessageType.SHUTDOWN),
        ("other-node", MessageType.SHUTDOWN),
    ]


def test_shutdown_uses_non_draining_target_for_migrated_threads_and_tuples():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.shutdown_prepare_condition = threading.Condition(network.lock)
    network.node_id = "local-node"
    network.nodes = {
        "draining-node": (object(), ("127.0.0.1", 5000)),
        "target-node": (object(), ("127.0.0.1", 5001)),
    }
    network.draining = False
    network.draining_nodes = {"draining-node"}
    network.shutdown_prepare_acks = set()
    network.discovery = DummyDiscovery()
    network.running = True
    network.scheduler = DummyScheduler()
    migrated = []
    network.scheduler.get_threads = lambda: {"thread-1": make_thread_state()}
    network.scheduler.migrate_thread = lambda tid, ip, port: migrated.append((tid, ip, port)) or tid
    network.scheduler.tuple_space.out(("kept",))
    sent_messages = []

    def send_message(node_id, message):
        sent_messages.append((node_id, message))
        if message.type == MessageType.SHUTDOWN_PREPARE:
            network.mark_shutdown_prepare_ack(node_id)
        return True

    network._send_message = send_message

    assert network.shutdown() is True

    assert migrated == [("thread-1", "127.0.0.1", 5001)]
    final_shutdowns = [(node_id, msg) for node_id, msg in sent_messages if msg.type == MessageType.SHUTDOWN]
    assert final_shutdowns[0][0] == "draining-node"
    assert final_shutdowns[0][1].tuples == []
    assert final_shutdowns[1][0] == "target-node"
    assert final_shutdowns[1][1].tuples == [("kept",)]


def test_draining_node_ignores_new_remote_tuple_requests():
    network = NetworkThread.__new__(NetworkThread)
    network.draining = True
    network.scheduler = DummyScheduler()
    request = TupleRequest(
        thread_id="remote-thread",
        owner="remote-node",
        completer=None,
        pattern=["task", Wildcard("value")],
        is_destructive=True,
    )

    TupleInMessage(MessageType.IN, request).handle(network, None, None)

    assert network.scheduler.tuple_space.get_requests() == []


def test_draining_node_returns_incoming_migrations_to_source():
    network = NetworkThread.__new__(NetworkThread)
    network.draining = True
    network.scheduler = DummyScheduler()
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

    assert network.scheduler.added_threads == []
    returned_state, ip, port = returned_migrations[0]
    assert returned_state.thread_id == original_state.thread_id
    assert (ip, port) == ("10.0.0.2", 6000)


def test_message_json_dispatch_handles_shutdown_prepare_and_ack():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.shutdown_prepare_condition = threading.Condition(network.lock)
    network.node_id = "local-node"
    network.draining_nodes = set()
    network.shutdown_prepare_acks = set()
    sent_messages = []
    network._send_message = lambda node_id, message: sent_messages.append((node_id, message)) or True

    Message.handle_from_json(
        ShutdownPrepareMessage(MessageType.SHUTDOWN_PREPARE, "peer-node").to_json().decode("utf-8"),
        network,
        ("127.0.0.1", 5000),
        None,
    )
    Message.handle_from_json(
        ShutdownPrepareAckMessage(MessageType.SHUTDOWN_PREPARE_ACK, "peer-node").to_json().decode("utf-8"),
        network,
        ("127.0.0.1", 5000),
        None,
    )

    assert network.draining_nodes == {"peer-node"}
    assert isinstance(sent_messages[0][1], ShutdownPrepareAckMessage)
    assert network.shutdown_prepare_acks == {"peer-node"}


def test_shutdown_does_not_wait_for_nodes_that_failed_prepare_send():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.shutdown_prepare_condition = threading.Condition(network.lock)
    network.node_id = "local-node"
    network.nodes = {
        "live-node": (object(), ("127.0.0.1", 5000)),
        "dead-node": (object(), ("127.0.0.1", 5001)),
    }
    network.draining = False
    network.draining_nodes = set()
    network.shutdown_prepare_acks = set()
    network.discovery = DummyDiscovery()
    network.running = True
    network.scheduler = DummyScheduler()
    network.scheduler.threads = {"thread-1": make_thread_state()}
    network.close = lambda: setattr(network, "running", False)
    sent_messages = []

    def send_message(node_id, message):
        sent_messages.append((node_id, message.type))
        if node_id == "dead-node" and message.type == MessageType.SHUTDOWN_PREPARE:
            network.nodes.pop("dead-node")
            return False
        if message.type == MessageType.SHUTDOWN_PREPARE:
            network.mark_shutdown_prepare_ack(node_id)
        return True

    network._send_message = send_message

    assert network.shutdown() is True

    assert ("dead-node", MessageType.SHUTDOWN) not in sent_messages
    assert network.scheduler.migrated_threads == [("thread-1", "127.0.0.1", 5000)]


def test_shutdown_aborts_without_transferring_tuples_if_thread_migration_fails():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.shutdown_prepare_condition = threading.Condition(network.lock)
    network.node_id = "local-node"
    network.nodes = {
        "target-node": (object(), ("127.0.0.1", 5000)),
    }
    network.draining = False
    network.draining_nodes = set()
    network.shutdown_prepare_acks = set()
    network.discovery = DummyDiscovery()
    network.running = True
    network.scheduler = DummyScheduler()
    network.scheduler.threads = {"thread-1": make_thread_state()}
    network.scheduler.migrate_result = None
    network.scheduler.tuple_space.out(("must-stay",))
    network.close = lambda: setattr(network, "running", False)
    sent_messages = []

    def send_message(node_id, message):
        sent_messages.append((node_id, message.type))
        if message.type == MessageType.SHUTDOWN_PREPARE:
            network.mark_shutdown_prepare_ack(node_id)
        return True

    network._send_message = send_message

    assert network.shutdown() is False

    assert network.running is True
    assert network.draining is False
    assert network.scheduler.tuple_space.get_tuples() == [("must-stay",)]
    assert ("target-node", MessageType.SHUTDOWN) not in sent_messages


def test_shutdown_message_with_missing_tuple_payload_is_treated_as_empty_transfer():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.pending_requests = {}
    network.nodes = {"peer-node": (object(), ("127.0.0.1", 5000))}
    network.draining_nodes = {"peer-node"}
    network.shutdown_prepare_acks = {"peer-node"}
    network.scheduler = DummyScheduler()

    ShutdownMessage(MessageType.SHUTDOWN, "peer-node", None).handle(
        network,
        ("127.0.0.1", 5000),
        None,
    )

    assert "peer-node" not in network.nodes
    assert network.draining_nodes == set()
    assert network.shutdown_prepare_acks == set()


def test_receiving_migrated_thread_removes_stale_foreign_request_before_retry():
    network = NetworkThread.__new__(NetworkThread)
    network.draining = False
    network.scheduler = DummyScheduler()
    network.scheduler.tuple_space.add_foreign_requests([
        TupleRequest(
            thread_id="migrated-thread",
            owner="source-node",
            completer=None,
            pattern=["task", Wildcard("payload")],
            is_destructive=True,
        )
    ])
    original_state = make_thread_state()
    original_state.thread_id = "migrated-thread"
    original_state.program_counter = 2

    ThreadMigrateMessage(
        MessageType.MIGRATE,
        "source-node",
        original_state.to_dict(),
    ).handle(network, None, DummyConn())

    assert network.scheduler.added_threads[0].thread_id == "migrated-thread"
    assert network.scheduler.tuple_space.get_requests() == []


def test_send_cancel_can_skip_migration_destination_to_avoid_deleting_retried_request():
    network = NetworkThread.__new__(NetworkThread)
    network.lock = threading.RLock()
    network.nodes = {
        "target-node": (object(), ("127.0.0.1", 5000)),
        "other-node": (object(), ("127.0.0.1", 5001)),
    }
    sent_messages = []
    network._send_message = lambda node_id, message: sent_messages.append((node_id, message)) or True

    network.send_cancel("migrated-thread", True, None, exclude_node="target-node")

    assert [(node_id, msg.type, msg.thread_id) for node_id, msg in sent_messages] == [
        ("other-node", MessageType.IN_CANCEL, "migrated-thread")
    ]


def test_out_consume_ack_does_not_restore_reserved_tuple():
    network = NetworkThread.__new__(NetworkThread)
    network.pending_requests = {"thread-1": ("owner-node", ("task", 123))}
    network.scheduler = DummyScheduler()

    TupleIdMessage(MessageType.OUT_CONSUME, "thread-1").handle(network, None, None)

    assert network.pending_requests == {}
    assert network.scheduler.tuple_space.get_tuples() == []


def test_receiving_migrated_thread_recovers_pending_out_tuple_for_retry():
    network = NetworkThread.__new__(NetworkThread)
    network.draining = False
    network.pending_requests = {"migrated-thread": ("source-node", ("task", 123, "payload"))}
    network.scheduler = DummyScheduler()
    network.scheduler.tuple_space.add_foreign_requests([
        TupleRequest(
            thread_id="migrated-thread",
            owner="source-node",
            completer=None,
            pattern=["task", Wildcard("task_id"), Wildcard("payload")],
            is_destructive=True,
        )
    ])
    original_state = make_thread_state()
    original_state.thread_id = "migrated-thread"
    original_state.program_counter = 2

    ThreadMigrateMessage(
        MessageType.MIGRATE,
        "source-node",
        original_state.to_dict(),
    ).handle(network, None, DummyConn())

    assert network.pending_requests == {}
    assert network.scheduler.tuple_space.get_requests() == []
    assert network.scheduler.tuple_space.get_tuples() == [("task", 123, "payload")]
    assert network.scheduler.added_threads[0].thread_id == "migrated-thread"
