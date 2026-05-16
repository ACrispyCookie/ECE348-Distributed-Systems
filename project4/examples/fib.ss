#SIMPLESCRIPT Fibonacci
BEQ $argc 0 #NoArgs
SET @a 0
SET @b 1
SET @count 0
SET @max $arg1
PRN "Generating" $max "Fibonacci numbers:"
#LoopStart BGE $count $max #End
PRN $a
SET @temp $a
ADD @a $a $b
SET @b $temp
ADD @count $count 1
BRA #LoopStart
#End RET
#NoArgs PRN "Usage: run fibonacci <count>"
RET