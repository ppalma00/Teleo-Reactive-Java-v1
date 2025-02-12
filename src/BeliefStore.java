import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class BeliefStore {
    private final Map<String, Integer> intVars = new HashMap<>();
    private final Map<String, Double> realVars = new HashMap<>();
    private final Map<String, List<List<Integer>>> activeFacts = new HashMap<>();
    private final Set<String> activeFactsNoParams = new HashSet<>();
    private final Set<String> declaredFacts = new HashSet<>();
    private final Set<String> declaredTimers = new HashSet<>();
    private final Set<String> declaredDurativeActions = new HashSet<>();
    private final Map<String, Long> timers = new HashMap<>();
    private final Map<String, Long> pausedTimers = new HashMap<>();

    // ------------------------ Manejo de Variables ------------------------
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
            String factBase = fact.split("\\(")[0];
            declaredFacts.add(factBase);
        } else {
            declaredFacts.add(fact);
        }
        System.out.println("📌 Declared fact: " + fact);
    }

    public void addFact(String baseFactName, Integer... parameters) {
        if (!declaredFacts.contains(baseFactName)) {
            System.err.println("⚠️ Attempt to activate an undeclared fact: " + baseFactName);
            return;
        }

        if (parameters.length == 0) {
            activeFactsNoParams.add(baseFactName);
            System.out.println("✅ Activated fact without parameters: " + baseFactName);
        } else {
            List<Integer> paramList = Arrays.asList(parameters);
            List<List<Integer>> existingInstances = activeFacts.computeIfAbsent(baseFactName, k -> new ArrayList<>());

            // Ensure we don't add duplicates
            if (!existingInstances.contains(paramList)) {
                existingInstances.add(paramList);
                System.out.println("✅ Activated fact with parameters: " + baseFactName + paramList);
            }
        }
    }


    public void removeFact(String factPattern) {
        if (factPattern.contains("(")) {
            String factBase = factPattern.split("\\(")[0];
            String paramPattern = factPattern.replaceAll(".*\\(|\\)", ""); // Extract parameters

            if (activeFacts.containsKey(factBase)) {
                List<List<Integer>> instances = activeFacts.get(factBase);
                boolean removed = instances.removeIf(params -> 
                    params.toString().replace("[", "").replace("]", "").equals(paramPattern));

                if (removed) {
                    System.out.println("🗑️ Removed fact with parameters: " + factPattern);
                }
                if (instances.isEmpty()) {
                    activeFacts.remove(factBase);
                }
            }
        } else {
            if (activeFactsNoParams.remove(factPattern)) {
                System.out.println("🗑️ Removed fact without parameters: " + factPattern);
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
    public void declareTimer(String timer) {
        declaredTimers.add(timer);
        declaredFacts.add(timer + "_end");  // Registrar `t1_end` como hecho en lugar de `t1.end`
    }

    public void startTimer(String timerId, int durationSeconds) {
        if (!declaredTimers.contains(timerId)) {
            System.err.println("⚠️ Attempt to start an undeclared timer: " + timerId);
            return;
        }
        timers.put(timerId, System.currentTimeMillis() + (durationSeconds * 1000));
        removeFact(timerId + "_end");
        System.out.println("⏳ Timer started: " + timerId + " for " + durationSeconds + " seconds");
    }

    public void stopTimer(String timerId) {
        if (!timers.containsKey(timerId) && !pausedTimers.containsKey(timerId)) {
            System.err.println("⚠️ Attempt to stop an undeclared or already removed timer: " + timerId);
            return;
        }
        timers.remove(timerId);
        pausedTimers.remove(timerId);
        addFact(timerId + "_end");
        System.out.println("🛑 Timer stopped: " + timerId);
    }

    public void pauseTimer(String timerId) {
        if (!timers.containsKey(timerId)) {
            System.err.println("⚠️ Attempt to pause an undeclared timer: " + timerId);
            return;
        }

        long remainingTime = timers.get(timerId) - System.currentTimeMillis();
        if (remainingTime > 0) {
            pausedTimers.put(timerId, remainingTime);
            timers.remove(timerId);
            System.out.println("⏸️ Timer paused: " + timerId + ", remaining time: " + remainingTime + " ms");
        }
    }

    public void continueTimer(String timerId) {
        if (pausedTimers.containsKey(timerId)) {
            long remainingTime = pausedTimers.remove(timerId);
            long resumeTime = System.currentTimeMillis() + remainingTime;

            timers.put(timerId, resumeTime);
            System.out.println("▶️ Timer resumed: " + timerId + ", new expiration in " + remainingTime + " ms.");
        } else {
            System.err.println("⚠️ Attempted to resume a non-paused timer: " + timerId);
        }
    }

    public boolean isTimerExpired(String timerId) {
        if (!timers.containsKey(timerId)) {
            return false;
        }

        boolean expired = System.currentTimeMillis() >= timers.get(timerId);
        String timerEndFact = timerId + "_end";

        if (expired) {
            if (!isFactActive(timerEndFact)) {
                addFact(timerEndFact);
                System.out.println("✅ Timer expired: " + timerEndFact + " activated");
            }
            timers.remove(timerId);
            System.out.println("🛑 Timer fully removed: " + timerId);
        }

        return expired;
    }

    public Set<String> getDeclaredTimers() {
        return new HashSet<>(declaredTimers);
    }

    public Map<String, Long> getAllTimers() {
        return new HashMap<>(timers);
    }

    public void dumpState() {
        System.out.println("\n🔹 Current BeliefStore state:");
        System.out.println("   Active facts without parameters: " + activeFactsNoParams);
        System.out.print("   Active facts with parameters: {");
        for (Map.Entry<String, List<List<Integer>>> entry : activeFacts.entrySet()) {
            System.out.print(entry.getKey() + "=" + entry.getValue() + ", ");
        }
        System.out.println("}");
        System.out.println("   Integer variables: " + intVars);
        System.out.println("   Real variables: " + realVars + "\n");
    }

}
