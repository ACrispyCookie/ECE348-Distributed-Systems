from parser import *
from state import *
from scheduler import *
import uuid
import os
import threading

class RuntimeEnvironment:
    def __init__(self):
        self.threads: dict[str, ThreadState] = {}
        self.lock = threading.Lock()
        self.running = True
        self.load_balancing = False
        self.scheduler_thread = threading.Thread(target=scheduler, daemon=True, name="simplescript-scheduler", args=[self])

    def start(self):
        """Starts the environment: CLI listener and thread scheduler"""
        print("Starting SimpleScript runtime environment...")
        self.scheduler_thread.start()
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
                break

    def cmd_run(self, args):
        """Command: run <progname> <arg1> ... <argN>"""
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
        with self.lock:
            self.threads[new_thread.thread_id] = new_thread

    def cmd_list(self):
        """Command: list (prints id and program name of each thread)"""
        with self.lock:
            if not self.threads:
                print("No threads.")
                return
                
            print(f"{'THREAD ID':<40} | {'PROGRAM NAME'}")
            print("-" * 60)
            for tid, state in self.threads.items():
                prog_name = getattr(state.program, 'name', 'Unknown')
                print(f"{tid:<40} | {prog_name}")

    def cmd_kill(self, args):
        """Command: kill <thread id>"""
        if not args:
            print("Usage: kill <thread id>")
            return
            
        tid_to_kill = args[0]
        with self.lock:
            if tid_to_kill in self.threads:
                self.threads[tid_to_kill].is_terminated = True
                print(f"Thread {tid_to_kill} terminated.")
            else:
                print(f"Error: Thread {tid_to_kill} not found.")

if __name__ == "__main__":
    env = RuntimeEnvironment()
    env.start()