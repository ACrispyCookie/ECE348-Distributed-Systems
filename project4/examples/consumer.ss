#SIMPLESCRIPT Consumer
PRN "Consumer: Started. Waiting for a task..."

IN "task" @task_id @payload
PRN "Consumer: Got task ID " $task_id " with data: " $payload

PRN "Consumer: Waiting for the shutdown signal..."
RD "shutdown"

PRN "Consumer: Shutdown signal received. Exiting."
RET