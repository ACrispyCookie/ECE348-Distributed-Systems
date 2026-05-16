#SIMPLESCRIPT Producer
PRN "Producer: Started..."
SET @COUNT 0

#IRRELEVANT_LOOP PRN "Producer: Producing irrelevant tuple " $COUNT "..."
OUT "task" 42 "Calculate_Gravity" "Should_not_match" $COUNT
SLP 3
ADD @COUNT $COUNT 1
BLT $COUNT 5 #IRRELEVANT_LOOP

SET @COUNT 42
PRN "Producer: Starting infinite task production... (Every 10s)"
#TASK_LOOP PRN "Producer: Producing task " $COUNT "..."
OUT "task" $COUNT "Calculate_Gravity"
ADD @COUNT $COUNT 1
SLP 10

BRA #TASK_LOOP