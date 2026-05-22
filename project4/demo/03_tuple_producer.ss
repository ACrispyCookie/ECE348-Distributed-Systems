#SIMPLESCRIPT
PRN "DEMO2: producer sleeping before OUT"
SLP 2
OUT "demo-task" 101 "calculate-gravity" "remote-node"
PRN "DEMO2: producer emitted tuple"
RET
