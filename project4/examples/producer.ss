#SIMPLESCRIPT Producer
PRN "Producer: Started..."
SLP 3

PRN "Producer: Storing irrelevant tuple..."
OUT "task" 42 "Calculate_Gravity" "Should_not_match"

SLP 3

PRN "Producer: Storing task tuple..."
OUT "task" 42 "Calculate_Gravity"

SLP 3

PRN "Producer: Storing shutdown tuple..."
OUT "shutdown"

PRN "Producer: Exiting."
RET