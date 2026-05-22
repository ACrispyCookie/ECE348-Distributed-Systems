#SIMPLESCRIPT
PRN "DEMO1: SimpleScript arithmetic/branches starting"
SET @n 6
SET @fact 1
#Loop MUL @fact $fact $n
PRN "DEMO1: n=" $n " fact=" $fact
SUB @n $n 1
BGT $n 1 #Loop
PRN "DEMO1: final factorial=" $fact
RET
