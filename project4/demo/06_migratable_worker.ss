#SIMPLESCRIPT
PRN "DEMO4: migratable worker started; migrate me while I print heartbeats"
SET @count 0
#Loop ADD @count $count 1
PRN "DEMO4: migratable heartbeat=" $count
SLP 2
BLT $count 30 #Loop
PRN "DEMO4: migratable worker finished"
RET
