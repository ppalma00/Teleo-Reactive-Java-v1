import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class BeliefStore {
    private final Map<String, Integer> intVars = new HashMap<>();
    private final Map<String, Double> realVars = new HashMap<>();
    private final Map<String, List<List<Integer>>> activeFacts = new HashMap<>();
    private final Set<String> activeFactsNoParams = new HashSet<>();  // Hechos sin parámetros
    private final Set<String> declaredFacts = new HashSet<>();
    private final Set<String> declaredTimers = new HashSet<>();
    private final Set<String> declaredDurativeActions = new HashSet<>();
    private final Map<String, Long> timers = new HashMap<>();

    // ------------------------ Manejo de Variables ------------------------
    public void declareTimer(String timer) {
        declaredTimers.add(timer);
        declaredFacts.add(timer + "_end");  // Registrar `t1_end` en lugar de `t1.end`
    }


    public void addIntVar(String varName, int initialValue) {
        intVars.put(varName, initialValue);
    }

    public void setIntVar(String varName, int value) {
        if (intVars.containsKey(varName)) {
            intVars.put(varName, value);
        }
    }

    public boolean isIntVar(String varName) {
        return intVars.containsKey(varName);
    }

    public Map<String, Integer> getAllIntVars() {
        return new HashMap<>(intVars);
    }

    public void addRealVar(String varName, double initialValue) {
        realVars.put(varName, initialValue);
    }

    public void setRealVar(String varName, double value) {
        if (realVars.containsKey(varName)) {
            realVars.put(varName, value);
        }
    }

    public boolean isRealVar(String varName) {
        return realVars.containsKey(varName);
    }

    public Map<String, Double> getAllRealVars() {
        return new HashMap<>(realVars);
    }

    // ------------------------ Manejo de Hechos ------------------------
    public void declareFact(String fact) {
        if (fact.contains("(")) {
            String factBase = fact.split("\\(")[0]; // Extrae la base del hecho
            declaredFacts.add(factBase);
        } else {
            declaredFacts.add(fact);
        }
        System.out.println("📌 Hecho declarado: " + fact);
    }


    public void addFact(String baseFactName, Integer... parameters) {
        // Verificar si el hecho base está declarado
        if (!declaredFacts.contains(baseFactName)) {
            System.err.println("⚠️ Intento de activar un hecho no declarado: " + baseFactName);
            return;
        }

        if (parameters.length == 0) {
            activeFactsNoParams.add(baseFactName);
            System.out.println("✅ Hecho ACTIVADO sin parámetros: " + baseFactName);
        } else {
            activeFacts.computeIfAbsent(baseFactName, k -> new ArrayList<>()).add(Arrays.asList(parameters));
            System.out.println("✅ Hecho ACTIVADO con parámetros: " + baseFactName + Arrays.toString(parameters));
        }
    }


/*
    public void removeFact(String fact) {
        if (fact.contains("(")) {
            String factBase = fact.split("\\(")[0];
            if (activeFacts.containsKey(factBase)) {
                activeFacts.get(factBase).removeIf(params -> params.toString().equals(fact));
                if (activeFacts.get(factBase).isEmpty()) {
                    activeFacts.remove(factBase);
                }
            }
        } else {
            activeFactsNoParams.remove(fact);
        }
        System.out.println("🗑️ Hecho eliminado: " + fact);
    }

*/
    public void removeFact(String factPattern) {
        if (factPattern.contains("(")) {
            // Caso: Hecho con parámetros
            String factBase = factPattern.split("\\(")[0];
            String paramPattern = factPattern.replaceAll(".*\\(|\\)", ""); // Extrae parámetros

            if (activeFacts.containsKey(factBase)) {
                List<List<Integer>> instances = activeFacts.get(factBase);
                boolean removed = instances.removeIf(params -> 
                    params.toString().replace("[", "").replace("]", "").equals(paramPattern));

                if (removed) {
                    System.out.println("🗑️ Hecho eliminado con parámetros: " + factPattern);
                }
                if (instances.isEmpty()) {
                    activeFacts.remove(factBase);
                }
            }
        } else {
            // Caso: Hecho sin parámetros
            if (activeFactsNoParams.remove(factPattern)) {
                System.out.println("🗑️ Hecho eliminado sin parámetros: " + factPattern);
            }
        }
    }

    public boolean isFactActive(String factPattern) {
        if (factPattern.contains("(")) {
            String factBase = factPattern.split("\\(")[0];
            Pattern pattern = Pattern.compile(factPattern.replace("_", "\\\\d+"));
            
            return activeFacts.containsKey(factBase) &&
                   activeFacts.get(factBase).stream().anyMatch(params -> pattern.matcher(params.toString()).matches());
        } else {
            return activeFactsNoParams.contains(factPattern);
        }
    }

    public Set<String> getDeclaredFacts() {
        return new HashSet<>(declaredFacts);
    }

    public Set<String> getActiveFactsNoParams() {
        return new HashSet<>(activeFactsNoParams);
    }

    public Map<String, List<List<Integer>>> getActiveFacts() {
        return activeFacts;
    }

    public void declareDurativeAction(String action) {
        declaredDurativeActions.add(action);
    }

    public boolean isDurativeAction(String action) {
        return declaredDurativeActions.contains(action);
    }

    public Set<String> getDeclaredDurativeActions() {
        return new HashSet<>(declaredDurativeActions);
    }

    // ------------------------ Manejo de Temporizadores ------------------------
    public void startTimer(String timerId, int durationSeconds) {
        if (!declaredTimers.contains(timerId)) {
            System.err.println("⚠️ Intento de iniciar un temporizador no declarado: " + timerId);
            return;
        }
        timers.put(timerId, System.currentTimeMillis() + (durationSeconds * 1000));
        removeFact(timerId + "_end");  // Quitar `t1_end` al iniciar el temporizador
        System.out.println("⏳ Temporizador INICIADO: " + timerId + " por " + durationSeconds + " segundos");
    }

    public boolean isTimerExpired(String timerId) {
        if (!timers.containsKey(timerId)) {
            return false; // Si el temporizador no existe, no hacer nada
        }

        boolean expired = System.currentTimeMillis() >= timers.get(timerId);
        String timerEndFact = timerId + "_end";

        if (expired) {
            // Asegurar que `t1_end` sigue declarado, y si no lo está, declararlo
            if (!declaredFacts.contains(timerEndFact)) {
                declaredFacts.add(timerEndFact);
            }

            // Activar `t1_end` si no está activo
            if (!isFactActive(timerEndFact)) {
                addFact(timerEndFact);
                System.out.println("✅ Temporizador EXPIRADO: " + timerEndFact + " ACTIVADO");
            }

            // Eliminar el temporizador para evitar que se vuelva a procesar en el siguiente ciclo
            timers.remove(timerId);
            System.out.println("🛑 Temporizador eliminado completamente: " + timerId);
        }

        return expired;
    }


    public Set<String> getDeclaredIntVars() {
        return intVars.keySet();
    }



    
    public void removeTimer(String timerId) {
        timers.remove(timerId);
        addFact(timerId + "_end");
        System.out.println("🛑 Temporizador detenido: " + timerId);
    }

    public Set<String> getDeclaredTimers() {
        return new HashSet<>(declaredTimers);
    }

    public Map<String, Long> getAllTimers() {
        return new HashMap<>(timers);
    }

    // ------------------------ Métodos de Depuración ------------------------
    public void dumpState() {
        System.out.println("\n🔹 Estado actual de BeliefStore:");
        System.out.println("   Hechos ACTIVOS sin parámetros: " + activeFactsNoParams);

        System.out.print("   Hechos ACTIVOS con parámetros: {");
        for (Map.Entry<String, List<List<Integer>>> entry : activeFacts.entrySet()) {
            System.out.print(entry.getKey() + "=" + entry.getValue() + ", ");
        }
        System.out.println("}");

        System.out.println("   Variables enteras: " + intVars);
        System.out.println("   Variables reales: " + realVars + "\n");
    }

    public void printTimers() {
        System.out.println("🔹 Temporizadores actuales en BeliefStore: " + timers);
    }
}
