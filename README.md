# Teleo-Reactive-Java-v1
This Java program executes a Teleo-Reactive program with a very simple syntax as shown in the example. From main, it emulates the existence of an observer that receives notifications from the Teleo-Reactive program so that it knows when a durative action starts, when it ends, and when a discrete action is executed. 
// The different sections of the TR program are being declared.
// It is possible to declare facts with parameters.
// The ending of a Timer(secs) activates the ".end" fact in the BeliefStore
// Actions are optional. There can be many actions in a rule.
// Operations are indicated after the symbol '++' and are also optional. Remember and forget are operations on the list of actived facts of the BeliefStore.

FACTS: uno(INT), dos
VARSINT: x, y
VARSREAL: z
DISCRETE: abrir(INT), cerrar(REAL)
DURATIVE: alarma()
TIMERS: t1
INIT: x:=0, y:=88, z:=4.56

TR:

y==2 -> alarma() ++ x:=0, y:=0, forget(dos)
x==3 -> cerrar(1.3) ++ y:=2
x==2 -> abrir(2) ++ x:=3, forget(uno(4))
t1.end -> ++forget(t1.end), x:=2
x==1 -> t1.pause()
True -> abrir(1), t1.start(5) ++x:=1, remember(uno(4)), remember(dos)

