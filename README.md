# Teleo-Reactive-Java-v1
This Java program executes a Teleo-Reactive program with a very simple syntax as shown in the example. From main, it emulates the existence of an observer that receives notifications from the Teleo-Reactive program so that it knows when a durative action starts, when it ends, and when a discrete action is executed. 
// The different sections of the TR program are being declared.
// It is possible to declare facts with parameters.
// The ending of a Timer(secs) activates the ".end" fact in the BeliefStore
// Actions are optional. There can be many actions in a rule.
// Operations are indicated after the symbol '++' and are also optional. Remember and forget are operations on the list of actived facts of the BeliefStore. The fact 't1_end' is added when the timer expires. User must remove it from the BeliefStore manually. It is a warning to stop, continue or pause a timer not started.

FACTS: uno(INT,INT); dos
VARSINT: x; y
VARSREAL: z
DISCRETE: abrir(INT,INT); cerrar(REAL)
DURATIVE: alarma()
TIMERS: t1
INIT: x:=0; y:=88; z:=4.56

TR:

y==2 -> alarma() ++ x:=0; y:=0; forget(dos)
x==4 -> cerrar(2) ++ y:=2
x==3 && dos -> abrir(2,2) ++ x:=4; forget(uno(_,_))
t1.end -> ++forget(t1.end); x:=3
x==2 && uno(_,_)-> abrir(3,1)  ++ y:=5 
True -> abrir(2,4); alarma(); t1.start(1); ++ x:=2; remember(uno(4,2)); remember(uno(7,1)); remember(dos)

