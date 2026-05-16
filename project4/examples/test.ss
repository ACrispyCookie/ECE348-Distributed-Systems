#SIMPLESCRIPT
    SET @count 3
    #LoopStart PRN $count
    SUB @count $count 1
    BGT $count 0 #LoopStart
    PRN "Done"