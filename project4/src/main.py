from parser import *
from state import *
from scheduler import *
from discovery import *
from network import *
import os
import time

class RuntimeEnvironment:
    def __init__(self):
        self.running = True
        self.load_balancing = False
        self.shutdown_warning = 0
        self.node_id = uuid.uuid4()
        self.scheduler = Scheduler(self)
        self.network = NetworkReceiver(self.node_id, self.scheduler)
        self.discovery = DiscoveryThread(self.node_id, self.scheduler, self.network)

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
                elif command == "shutdown":
                    self.cmd_shutdown(False)
                else:
                    print(f"[RUNTIME] Unknown command: '{command}'. Available commands: run, list, tuples, kill, migrate, shutdown")

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
        with open(filename, 'r') as file:
            source_code = file.read()
    
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
        tuples = self.scheduler.tuple_space.local.get_tuples()
        requests = self.scheduler.tuple_space.local.get_requests()
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
        
        migrated = self.scheduler.migrate_thread(tid, ip, port)
        if migrated:
            print(f"[RUNTIME] Migrating thread {migrated} to {ip}:{port}...")
        else:
            print(f"[RUNTIME] Error: Thread not found or node not found.")

    def cmd_shutdown(self, interrupt: bool):
        """Command: shutdown (Shuts down the environment peacefully or not)"""
        
        shutdown = False
        force = time.time() - self.shutdown_warning < 10
        self.shutdown_warning = time.time()
        if len(self.scheduler.get_threads()) == 0:
            shutdown = True
        else: # TODO check if last instance
            if force:
                shutdown = True
                print(("\n" if interrupt else "") + "[RUNTIME] Warning: You have forced a shutdown...")
            else:
                print(("\n" if interrupt else "") + "[RUNTIME] Warning: This is the last environment running. Retry to force shutdown.")

        if shutdown:
            print("[RUNTIME] Shutting down...")
            self.running = False
            self.scheduler.shutdown()
            self.network.stop()
            self.network.join()
            self.discovery.stop()
            self.discovery.join()

if __name__ == "__main__":
    env = RuntimeEnvironment()
    env.start()