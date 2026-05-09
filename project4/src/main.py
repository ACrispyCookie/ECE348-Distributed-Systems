from parser import *
from state import *
from scheduler import *
from network import *
import os
import time

class RuntimeEnvironment:
    def __init__(self):
        self.running = True
        self.load_balancing = False
        self.shutdown_warning = 0
        self.node_id = uuid.uuid4()
        self.discovery = DiscoveryThread(self.node_id)
        self.scheduler = Scheduler(self)

    def start(self):
        """Starts the environment: CLI listener and thread scheduler"""
        print("Starting SimpleScript runtime environment...")
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
                elif command == "kill":
                    self.cmd_kill(args)
                elif command == "migrate":
                    self.cmd_migrate(args)
                elif command == "shutdown":
                    self.cmd_shutdown()
                else:
                    print(f"Unknown command: '{command}'. Available commands: run, list, kill, migrate, shutdown")

            except KeyboardInterrupt:
                self.cmd_shutdown()

    def cmd_run(self, args):
        """Command: run <progname> <arg1> ... <argN> (Starts new thread)"""
        if not args:
            print("Usage: run <progname> [args...]")
            return

        progname = args[0]
        script_args = args[1:]
        filename = f"{progname}"
        
        if not os.path.exists(filename):
            print(f"Error: Could not find script file '{filename}'")
            return
        with open(filename, 'r') as file:
            source_code = file.read()
    
        program = parse_simplescript(source_code, progname)
        if not program:
            print(f"Error: Could not parse program '{progname}'")
            return

        new_thread = ThreadState(program=program, args=script_args)
        print(f"Starting thread {new_thread.thread_id} for program '{progname}'")
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
            print("No threads started.")
            return

        print(f"{'THREAD ID':<38} | {'PROGRAM NAME':<20} | {'STATUS'}")
        print("-" * 75)
        for tid, state, status in all_threads:
            prog_name = getattr(state.program, 'name', 'Unknown')
            print(f"{tid:<38} | {prog_name:<20} | {status}")

    def cmd_kill(self, args):
        """Command: kill <thread id> (Kills a running thread)"""
        if not args:
            print("Usage: kill <thread id>")
            return
        
        tid = args[0]
        killed = self.scheduler.kill_thread(tid)
        if killed:
            print(f"Thread {tid} killed.")
        else:
            print(f"Error: Thread {tid} not found.")

    def cmd_migrate(self, args):
        """Command: migrate <thread id> <IP address> <port number> (Migrates a thread to another environment)"""
        if not args or len(args) != 3:
            print("Usage: migrate <thread id> <IP address> <port number>")
            return
            
        tid = args[0]
        migrated = self.scheduler.migrate_thread(tid)
        if migrated:
            print("Not implemented yet.")
        else:
            print(f"Error: Thread {tid} not found.")

    def cmd_shutdown(self):
        """Command: shutdown (Shuts down the environment peacefully or not)"""
        
        shutdown = False
        force = time.time() - self.shutdown_warning < 10
        self.shutdown_warning = time.time()
        if len(self.scheduler.get_threads()) == 0:
            shutdown = True
        else: # TODO check if last instance
            if force:
                shutdown = True
                print("Warning: You have forced a shutdown...")
            else:
                print("Warning: This is the last environment running. Retry to force shutdown.")

        if shutdown:
            print("Shutting down...")
            self.running = False
            self.scheduler.shutdown()
            self.discovery.stop()
            self.discovery.join()

if __name__ == "__main__":
    env = RuntimeEnvironment()
    env.start()