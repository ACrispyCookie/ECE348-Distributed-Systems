#SIMPLESCRIPT
BEQ $argc 0 #NoArgs
SET @result 1
SET @n $arg1
#LoopStart BEQ $n 0 #End
MUL @result $result $n
SUB @n $n 1
BRA #LoopStart
#End PRN "The factorial of" $arg1 "is" $result
RET
#NoArgs PRN "Error: Please provide a number."
RET