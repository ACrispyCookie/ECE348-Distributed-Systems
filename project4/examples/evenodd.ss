#SIMPLESCRIPT
BEQ $argc 0 #NoArgs
MOD @rem $arg1 2
BEQ $rem 0 #IsEven
PRN $arg1 "is an ODD number."
RET
#IsEven PRN $arg1 "is an EVEN number."
RET
#NoArgs PRN "Usage: run evenodd <number>"
RET