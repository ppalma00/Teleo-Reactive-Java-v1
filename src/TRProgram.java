import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TRProgram {
    private final BeliefStore beliefStore;
    private final List<TRRule> rules = new ArrayList<>();
    private final Map<String, Boolean> activeDurativeActions = new HashMap<>();
    private final List<Observer> observers = new ArrayList<>();
    private boolean running = true;
    private TRRule lastExecutedRule = null;
    private final Set<String> executedDiscreteActions = new HashSet<>();

    public TRProgram(BeliefStore beliefStore) {
        this.beliefStore = beliefStore;
    }

    public void addRule(TRRule rule) {
        rules.add(rule);
    }

    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    private void notifyObservers(String actionName, Double[] parameters) {
        double[] primitiveParams = Arrays.stream(parameters).mapToDouble(Double::doubleValue).toArray();
        
        for (Observer observer : observers) {
            observer.onDiscreteActionExecuted(actionName, primitiveParams);
        }
    }

    private void notifyDurativeActionStarted(String actionName, double[] parameters) {
        for (Observer observer : observers) {
            observer.onDurativeActionStarted(actionName, parameters);
        }
    }

    private void notifyDurativeActionStopped(String actionName) {
        for (Observer observer : observers) {
            observer.onDurativeActionStopped(actionName);
        }
    }
    public TRRule findHighestPriorityRule() {
        beliefStore.dumpState();
        for (TRRule rule : rules) {
            if (rule.evaluateCondition(beliefStore)) {
                return rule;
            }
        }
        return null;
    }
    public void run() {
        while (running) {
            TRRule activeRule = findHighestPriorityRule();

            if (lastExecutedRule != null && lastExecutedRule != activeRule) {
                stopDurativeActionsOfRule(lastExecutedRule);
            }

            // Ejecutar acciones ANTES de evaluar la expiración del temporizador
            if (lastExecutedRule == null || lastExecutedRule != activeRule) {
                executeRule(activeRule);
                lastExecutedRule = activeRule;
            }

            // Verificar expiración de temporizadores después de ejecutar acciones
            for (String timerId : beliefStore.getDeclaredTimers()) {
                beliefStore.isTimerExpired(timerId);
            }

            try {
                Thread.sleep(100); // Control del ciclo de ejecución
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    private void executeRule(TRRule rule) {
        System.out.println("🔄 Ejecutando regla con condición: " + rule.getConditionText());

        boolean isFirstActivation = (lastExecutedRule == null || lastExecutedRule != rule);
        boolean hasActions = !rule.getDiscreteActions().isEmpty() || !rule.getDurativeActions().isEmpty();

        // Si la regla tiene acciones (discretas o durativas), ejecutarlas solo al ganar el control
        if (isFirstActivation && hasActions) {
            for (String action : rule.getDiscreteActions()) {
                Double[] parameters = extractParameters(action);

                if (isTimerCommand(action)) {
                    executeTimerCommand(action, parameters);  // Corrected call
                } else {
                    executeDiscreteAction(action);
                    notifyObservers(action, parameters);
                }
            }
        }
        // Aplicar actualizaciones (tras '++') si la regla gana el control y tiene actualizaciones o si no tiene ninguna acción
        if (isFirstActivation && (rule.hasUpdates())) {
            rule.applyUpdates();
        }

        // Manejo de acciones durativas
        for (String action : rule.getDurativeActions()) {
            if (!activeDurativeActions.containsKey(action)) {
                Double[] parameters = extractParameters(action);
                double[] primitiveParams = Arrays.stream(parameters)
                                                 .mapToDouble(Double::doubleValue)
                                                 .toArray();
                startDurativeAction(action);
                activeDurativeActions.put(action, true);
                notifyDurativeActionStarted(action, primitiveParams);
            }
        }

        lastExecutedRule = rule;
    }

    private void executeTimerCommand(String action, Double[] parameters) {
        String[] parts = action.split("\\.");
        if (parts.length < 2) {
            System.err.println("⚠️ Malformed timer command: " + action);
            return;
        }

        String timerId = parts[0];  // Extracts `t1`
        String commandWithParams = parts[1];  // Extracts `start(1)`, `pause()`, etc.

        // Extract the command (start, stop, etc.)
        Matcher matcher = Pattern.compile("([a-zA-Z]+)\\(([^)]*)\\)").matcher(commandWithParams);
        String command = commandWithParams.split("\\(")[0];  // Extracts command without parameters

        // Debugging: Show correct extracted command
        System.out.println("🛠 Extracted timer command: " + command + " for timer: " + timerId);

        switch (command) {
            case "start":
                if (parameters.length > 0) {
                    beliefStore.startTimer(timerId, parameters[0].intValue());
                } else {
                    System.err.println("⚠️ `start` requires a duration (seconds).");
                }
                break;
            case "stop":
                beliefStore.stopTimer(timerId);
                break;
            case "pause":
                beliefStore.pauseTimer(timerId);
                break;
            case "continue":
                beliefStore.continueTimer(timerId);
                break;
            default:
                System.err.println("⚠️ Unknown timer action: " + command);
        }
    }
    private boolean isTimerCommand(String action) {
        return action.matches(".*\\.start\\(\\d+(\\.\\d+)?\\)") ||  // Matches `t1.start(1)`, `t1.start(1.5)`
               action.matches(".*\\.stop\\(\\)") || 
               action.matches(".*\\.pause\\(\\)") || 
               action.matches(".*\\.continue\\(\\)");
    }
    private void executeDiscreteAction(String action) {
        System.out.println("⏩ Se ejecuta la acción discreta: " + action);
    }

    private void startDurativeAction(String action) {
        System.out.println("⏳ Acción durativa INICIADA: " + action);
    }

    private void stopDurativeActionsOfRule(TRRule rule) {
        if (rule == null) return;

        for (String action : rule.getDurativeActions()) {
            if (activeDurativeActions.containsKey(action)) {
                activeDurativeActions.remove(action);
                System.out.println("✅ Acción durativa FINALIZADA: " + action);
                notifyDurativeActionStopped(action);
            }
        }
    }

    private Double[] extractParameters(String action) {
        int startIndex = action.indexOf("(");
        int endIndex = action.indexOf(")");

        if (startIndex != -1 && endIndex != -1) {
            String params = action.substring(startIndex + 1, endIndex);
            String[] paramArray = params.split(",");

            List<Double> paramList = new ArrayList<>();
            for (String param : paramArray) {
                param = param.trim();
                if (!param.isEmpty()) {
                    try {
                        paramList.add(Double.parseDouble(param));
                    } catch (NumberFormatException e) {
                        System.err.println("⚠️ Error al convertir el parámetro a número: " + param);
                    }
                }
            }

            return paramList.toArray(new Double[0]);
        }
        return new Double[0];
    }
    public void shutdown() {
        running = false;
        stopAllDurativeActions();
        System.out.println("🚨 TRProgram detenido.");
    }

    private void stopAllDurativeActions() {
        for (String action : activeDurativeActions.keySet()) {
            System.out.println("✅ Acción durativa FINALIZADA: " + action);
            notifyDurativeActionStopped(action);
        }
        activeDurativeActions.clear();
    }
}
