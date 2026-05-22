#SIMPLESCRIPT
PRN "DEMO6: shutdown worker started; I should survive graceful shutdown by migrating"
SET @count 0
#Loop ADD @count $count 1
PRN "DEMO6: shutdown heartbeat=" $count
SLP 3
BLT $count 20 #Loop
PRN "DEMO6: shutdown worker finished"
RET
