1)
multiply.txt 
------------
* Takes as arguments two integers <a> and <b>
* Calculates the product via addition with an artificial delay (sleep) between each step
* Assumes <a> is >= 0, else terminates

Example:
run multiply.txt 5 15

2) 
producer.txt
----------
* Takes as arguments a string <tag>, an integer <cnt>, and an integer <wait> 
* Sleeps <wait> seconds and outputs tuple [<tag>,<cnt>] to the tuple space
* Decrements <cnt> and repeats the above until <cnt> becomes 0  

consumer.txt
------------
* Takes as arguments a string <tag>, an integer <cnt>, and an integer <wait>
* Sleeps <wait> seconds and removes from the tuple space a tuple [<tag>,<int>] (note that the second field is a free variable) 
* Decrements <cnt> and repeats the above until <cnt> becomes 0 

Example for 5 iterations:
run producer.txt TAG 5 10
run consumer.txt TAG 5 20

Example with mixed producers/receivers (note the different tags):
run producer.txt TAG1 5 0
run consumer.txt TAG2 3 0
run consumer.txt TAG1 5 10
run producer.txt TAG2 3 20
