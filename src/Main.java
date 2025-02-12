import java.util.Arrays;

public class Main implements Observer {
    public static void main(String[] args) {
        try {
            BeliefStore beliefStore = new BeliefStore();
            String trFilePath = "tr_program4.txt"; 
            TRProgram program = TRParser.parse(trFilePath, beliefStore);

            // Agregar `Main` como observador
            Main observer = new Main();
            program.addObserver(observer);

            System.out.println("Iniciando el programa TR...");
            program.run();
            Thread.sleep(20000);

            System.out.println("\nEstado final de la BeliefStore:");
            beliefStore.dumpState();

            System.out.println("\nDeteniendo el programa TR...");
            //program.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDiscreteActionExecuted(String actionName, double[] parameters) {
        System.out.println("Observer: Se ejecuta la acción discreta: " + actionName + " con parámetros: " + Arrays.toString(parameters));
    }

    @Override
    public void onDurativeActionStarted(String actionName, double[] parameters) {
        System.out.println("Observer: Iniciando acción durativa: " + actionName + " con parámetros: " + Arrays.toString(parameters));
    }

    @Override
    public void onDurativeActionStopped(String actionName) {
        System.out.println("Observer: Finalizando acción durativa: " + actionName);
    }
}
