#SIMPLESCRIPT
BEQ $argc 1 #Start
PRN "Usage: sleep.ss <seconds>"
RET
#Start SET $count 0
#LoopStart ADD $count $count 1
PRN $count
SLP 1
BLT $count $arg1 #LoopStart
PRN "Done!"
RET