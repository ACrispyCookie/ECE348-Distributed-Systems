#SIMPLESCRIPT
PRN "DEMO2: consumer waiting for distributed tuple"
IN "demo-task" @task_id @payload @origin
PRN "DEMO2: consumed task id=" $task_id " payload=" $payload " origin=" $origin
RET
