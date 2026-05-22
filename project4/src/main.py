from parser import *
from scheduler import *
from network.state import *
from network.network import NetworkThread
import argparse
import os
import time


def tcp_port(value):
    port = int(value)
    if port < 0 or port > 65535:
        raise argparse.ArgumentTypeError("TCP port must be between 0 and 65535")
    return port


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="SimpleScript distributed runtime environment")
    parser.add_argument(
        "--load-balance",
        "--balance",
        action="store_true",
        dest="load_balance",
        help="start the runtime with automatic load balancing enabled",
    )
    parser.add_argument(
        "-p",
        "--port",
        type=tcp_port,
        default=0,
        dest="tcp_port",
        help="TCP port for this runtime environment to listen on (default: 0, choose an ephemeral port)",
    )
    return parser.parse_args(argv)


class RuntimeEnvironment:
    def __init__(self, load_balancing: bool = False, tcp_port: int = 0):
        self.running = True
        self.load_balancing = load_balancing
        self.shutdown_warning = 0
        self.scheduler = Scheduler(self)
        self.network = NetworkThread(self.scheduler, tcp_port=tcp_port)

    def start(self):
        """Starts the environment: CLI listener and thread scheduler"""
        print("[RUNTIME] Starting SimpleScript runtime environment...")
        self.scheduler.start()
        self.command_loop()

    def command_loop(self):
        """Main loop listening for user commands."""
        while self.running:
            try:
                user_input = input("> ").strip()
                if not user_input:
                    continue
                
                parts = user_input.split()
                command = parts[0].lower()
                args = parts[1:]

                if command == "run":
                    self.cmd_run(args)
                elif command == "list":
                    self.cmd_list()
                elif command == "tuples":
                    self.cmd_tuples()
                elif command == "kill":
                    self.cmd_kill(args)
                elif command == "migrate":
                    self.cmd_migrate(args)
                elif command == "connect":
                    self.cmd_connect(args)
                elif command == "balance":
                    self.cmd_balance(args)
                elif command == "shutdown":
                    self.cmd_shutdown(False)
                else:
                    print(f"[RUNTIME] Unknown command: '{command}'. Available commands: run, list, tuples, kill, migrate, connect, balance, shutdown")

            except KeyboardInterrupt:
                self.cmd_shutdown(True)

    def cmd_run(self, args):
        """Command: run <progname> <arg1> ... <argN> (Starts new thread)"""
        if not args:
            print("[RUNTIME] Usage: run <progname> [args...]")
            return

        progname = args[0]
        script_args = args[1:]
        filename = f"{progname}"
        
        if not os.path.exists(filename):
            print(f"[RUNTIME] Error: Could not find script file '{filename}'")
            return
        
        try:
            with open(filename, 'r') as file:
                source_code = file.read()
        except OSError:
            print(f"[RUNTIME] Error: Could not open the script file '{filename}'")
            return
    
        program = parse_simplescript(source_code, progname)
        if not program:
            print(f"[RUNTIME] Error: Could not parse program '{progname}'")
            return

        new_thread = ThreadState(program=program, args=script_args)
        print(f"[RUNTIME] Starting thread {new_thread.thread_id} for program '{progname}'")
        self.scheduler.add_thread(new_thread)

    def cmd_list(self):
        """Command: list (prints id and program name of each thread)"""
        all_threads = []
        for tid, state in self.scheduler.get_threads(ThreadStatus.ACTIVE).items():
            all_threads.append((tid, state, "Running"))
            
        for tid, state in self.scheduler.get_threads(ThreadStatus.SLEEPING).items():
            all_threads.append((tid, state, "Sleeping"))
            
        for tid, state in self.scheduler.get_threads(ThreadStatus.WAITING).items():
            all_threads.append((tid, state, "Waiting"))

        if not all_threads:
            print("[RUNTIME] No threads started.")
            return

        print(f"{'THREAD ID':<38} | {'PROGRAM NAME':<20} | {'STATUS'}")
        print("-" * 75)
        for tid, state, status in all_threads:
            prog_name = getattr(state.program, 'name', 'Unknown')
            print(f"{tid:<38} | {prog_name:<20} | {status}")

    def cmd_tuples(self):
        """Command: tuples (prints all tuple requests and stored tuples)"""
        tuples = self.scheduler.tuple_space.get_tuples()
        requests = self.scheduler.tuple_space.get_requests()
        if not tuples and not requests:
            print("[RUNTIME] No tuples or requests")

        if tuples:
            print("-" * 42 + " TUPLE SPACE " + "-" * 42)
            for tup in tuples:
                print(f"{str(tup):<85}")

            if requests:
                print("\n")

        if not requests:
            return

        all_requests = []
        for req in requests:
            status = (
                req.owner
                if req.owner is not None
                else "Local"
            )

            all_requests.append((
                req.thread_id,
                req.pattern,
                "True" if req.is_destructive else "False",
                status
            ))

        print("-" * 40 + " THREAD REQUESTS " + "-" * 40)
        print(f"{'THREAD ID':<38} | {'IN':<5} | {'OWNER':<38} | {'PATTERN'}")
        for thread_id, pattern, destr, status in all_requests:
            print(f"{thread_id:<38} | {destr:<5} | {status:<38} | {pattern}")

    def cmd_kill(self, args):
        """Command: kill <thread id> (Kills a running thread)"""
        if not args:
            print("[RUNTIME] Usage: kill <thread id>")
            return
        
        tid = args[0]
        killed = self.scheduler.kill_thread(tid)
        if killed:
            print(f"[RUNTIME] Thread {killed} killed.")
        else:
            print(f"[RUNTIME] Error: Thread {tid} not found. Try specifying bigger part of the ID.")

    def cmd_migrate(self, args):
        """Command: migrate <thread id> <IP address> <port number> (Migrates a thread to another environment)"""
        if not args or len(args) != 3:
            print("[RUNTIME] Usage: migrate <thread id> <IP address> <port number>")
            return
            
        tid = args[0]
        ip = args[1]
        try:
            port = int(args[2])
        except ValueError:
            print("[RUNTIME] Specify a valid port!")
            return
        
        migrated = self.scheduler.migrate_thread_ip(tid, ip, port)
        if migrated:
            print(f"[RUNTIME] Migrating thread {migrated} to {ip}:{port}...")
        else:
            print(f"[RUNTIME] Error: Thread not found or node not found.")

    def cmd_connect(self, args):
        """Command: connect <IP address> <port number> (Manually connects to another environment)."""
        if len(args) != 2:
            print("[RUNTIME] Usage: connect <IP address> <port number>")
            return

        ip = args[0]
        try:
            port = int(args[1])
        except ValueError:
            print("[RUNTIME] Specify a valid port!")
            return

        connected = self.network.connect_to_address(ip, port)
        if connected:
            print(f"[RUNTIME] Connected to {ip}:{port}")
        else:
            print(f"[RUNTIME] Error: Could not connect to {ip}:{port}")

    def cmd_balance(self, args):
        """Command: balance <on|off> (Enables or disables automatic load balancing)"""
        if len(args) != 1 or args[0] not in ("on", "off"):
            print("[RUNTIME] Usage: balance <on|off>")
            return

        self.load_balancing = args[0] == "on"
        state = "enabled" if self.load_balancing else "disabled"
        print(f"[RUNTIME] Load balancing {state}.")

    def cmd_shutdown(self, interrupt: bool = False):
        """Command: shutdown (Shuts down the environment peacefully or forcefully)"""
        prefix = "\n" if interrupt else ""
        
        current_time = time.time()
        force = False
        if current_time - self.shutdown_warning <= 10.0:
            force = True

        result = self.scheduler.shutdown(force)

        if result == 0:
            print(f"{prefix}[RUNTIME] Threads migrated safely (or none active). Shutting down...")
            self.network.join()
        elif result == 1:
            print(f"{prefix}[RUNTIME] WARNING: Forced shutdown initiated! Unmigrated threads lost.")
            self.network.join()
        elif result == -1:
            self.shutdown_warning = current_time
            print(f"{prefix}[RUNTIME] Cannot shutdown safely: No other nodes available to migrate threads.")
            print(f"[RUNTIME] Issue the shutdown command again within 10 seconds to force quit.")
            return

if __name__ == "__main__":
    args = parse_args()
    env = RuntimeEnvironment(load_balancing=args.load_balance, tcp_port=args.tcp_port)
    env.start()
