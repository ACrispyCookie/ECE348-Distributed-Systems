#SIMPLESCRIPT
PRN "DEMO3: publisher sleeping before reusable OUT"
SLP 2
OUT "announcement" "hello-from-remote-out"
PRN "DEMO3: publisher emitted reusable announcement"
RET
