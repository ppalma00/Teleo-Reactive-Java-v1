import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.mvel2.MVEL;

public class TRParser {
    private static final ExpressionEvaluator evaluator = new ExpressionEvaluator(); // ✅ Instancia de `ExpressionEvaluator`

    public static TRProgram parse(String filePath, BeliefStore beliefStore) throws IOException {
        TRProgram program = new TRProgram(beliefStore);
        boolean insideTRSection = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("FACTS:")) {
                    parseFacts(line, beliefStore);
                } else if (line.startsWith("VARSINT:")) {
                    parseIntVars(line, beliefStore);
                } else if (line.startsWith("VARSREAL:")) {
                    parseRealVars(line, beliefStore);
                } else if (line.startsWith("DISCRETE:")) {
                    parseDiscreteActions(line);
                } else if (line.startsWith("DURATIVE:")) {
                    parseDurativeActions(line, beliefStore);
                } else if (line.startsWith("TIMERS:")) {
                    parseTimers(line, beliefStore);
                } else if (line.startsWith("INIT:")) {
                    parseInit(line, beliefStore);
                } else if (line.startsWith("TR:")) {
                    insideTRSection = true;
                } else if (insideTRSection) {
                    parseRule(line, program, beliefStore);
                }
            }
        }

        return program;
    }

    private static void parseFacts(String line, BeliefStore beliefStore) {
        String[] facts = line.substring(6).trim().split(",");
        for (String fact : facts) {
            fact = fact.trim();
            if (!fact.isEmpty()) {
                beliefStore.declareFact(fact);
            }
        }
    }

    private static void parseIntVars(String line, BeliefStore beliefStore) {
        String[] vars = line.substring(8).trim().split(",");
        for (String var : vars) {
            var = var.trim();
            if (!var.isEmpty()) {
                beliefStore.addIntVar(var, 0);
            }
        }
    }

    private static void parseRealVars(String line, BeliefStore beliefStore) {
        String[] vars = line.substring(9).trim().split(",");
        for (String var : vars) {
            var = var.trim();
            if (!var.isEmpty()) {
                beliefStore.addRealVar(var, 0.0);
            }
        }
    }

    private static void parseDiscreteActions(String line) {
        String[] actions = line.substring(9).split(",");
        for (String action : actions) {
            action = action.trim();
        }
    }

    private static void parseDurativeActions(String line, BeliefStore beliefStore) {
        String[] actions = line.substring(9).split(",");
        for (String action : actions) {
            action = action.trim();
            if (!action.isEmpty()) {
                beliefStore.declareDurativeAction(action);
            }
        }
    }

    private static void parseTimers(String line, BeliefStore beliefStore) {
        String[] timers = line.substring(7).trim().split(",");
        for (String timer : timers) {
            timer = timer.trim();
            if (!timer.isEmpty()) {
                beliefStore.declareTimer(timer);
            }
        }
    }

    private static void parseInit(String line, BeliefStore beliefStore) {
        String[] initializations = line.substring(5).trim().split(",");
        for (String init : initializations) {
            init = init.trim();
            if (!init.contains(":=")) {
                beliefStore.addFact(init);
            } else {
                String[] parts = init.split(":=");
                String varName = parts[0].trim();
                String value = parts[1].trim();
                try {
                    if (beliefStore.isIntVar(varName)) {
                        beliefStore.setIntVar(varName, Integer.parseInt(value));
                    } else if (beliefStore.isRealVar(varName)) {
                        beliefStore.setRealVar(varName, Double.parseDouble(value));
                    }
                } catch (NumberFormatException e) {
                    System.err.println("⚠️ Error en formato de inicialización: " + init);
                }
            }
        }
    }

    private static void parseRule(String line, TRProgram program, BeliefStore beliefStore) {
        if (line.isEmpty()) return;

        String[] parts = line.split("->");
        if (parts.length < 2) {
            System.err.println("❌ Error en la sintaxis de la regla: " + line);
            return;
        }

        String conditionStr = parts[0].trim();
        String actionsAndUpdates = parts[1].trim();
        String[] actionParts = actionsAndUpdates.split("\\+\\+");

        // 🔹 **Corrección: Reemplazar `True` por `true` para MVEL**
        conditionStr = conditionStr.replace("True", "true");

        // 🔹 **Corrección: Evaluador de Expresiones ya no es estático**
        final String finalConditionStr = conditionStr;
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        Predicate<BeliefStore> condition = beliefStoreState -> evaluator.evaluateLogicalExpression(finalConditionStr, beliefStoreState);

        List<String> discreteActions = new ArrayList<>();
        List<String> durativeActions = new ArrayList<>();

        if (!actionParts[0].isEmpty()) {
            String[] actions = actionParts[0].split(",");
            for (String action : actions) {
                action = action.trim();
                if (!action.isEmpty()) {
                    if (beliefStore.isDurativeAction(action)) {
                        durativeActions.add(action);
                    } else {
                        discreteActions.add(action);
                    }
                }
            }
        }

        Runnable beliefStoreUpdates = null;
        if (actionParts.length > 1 && !actionParts[1].isEmpty()) {
            String updates = actionParts[1].trim();
            beliefStoreUpdates = () -> applyUpdates(updates, beliefStore);
        }

        TRRule rule = new TRRule(condition, finalConditionStr, discreteActions, durativeActions, beliefStoreUpdates);
        program.addRule(rule);
    }
    private static void applyUpdates(String updates, BeliefStore beliefStore) {
        String[] updateParts = updates.split(",");
        List<Runnable> remembers = new ArrayList<>();
        List<Runnable> assignments = new ArrayList<>(); // Para guardar las asignaciones y ejecutarlas después

        for (String update : updateParts) {
            update = update.trim();

            if (update.startsWith("forget(")) {
                String fact = update.substring(7, update.length() - 1).trim();
                fact = fact.replace(".end", "_end");
                beliefStore.removeFact(fact);
                System.out.println("🗑️ Fact removed: " + fact);
            } else if (update.startsWith("remember(")) {
                String factWithParams = update.substring(9, update.length() - 1).trim();

                String baseFactName = factWithParams.contains("(") ? factWithParams.substring(0, factWithParams.indexOf("(")) : factWithParams;

                Integer[] parameters = new Integer[0];
                if (factWithParams.contains("(") && factWithParams.contains(")")) {
                    String paramStr = factWithParams.substring(factWithParams.indexOf("(") + 1, factWithParams.indexOf(")"));
                    String[] paramArray = paramStr.split(",");

                    if (!paramStr.isEmpty()) {
                        parameters = Arrays.stream(paramArray)
                                .map(String::trim)
                                .map(Integer::parseInt)
                                .toArray(Integer[]::new);
                    }
                }

                beliefStore.addFact(baseFactName, parameters);
            } else if (update.contains(":=")) {
                String[] parts = update.split(":=");
                if (parts.length == 2) {
                    String varName = parts[0].trim();
                    String expression = parts[1].trim();

                    assignments.add(() -> {
                        try {
                            Map<String, Object> context = new HashMap<>();
                            context.putAll(beliefStore.getAllIntVars());
                            context.putAll(beliefStore.getAllRealVars());

                            for (String var : beliefStore.getAllIntVars().keySet()) {
                                context.putIfAbsent(var, 0);
                            }
                            for (String var : beliefStore.getAllRealVars().keySet()) {
                                context.putIfAbsent(var, 0.0);
                            }

                            Object result = MVEL.eval(expression, context);

                            if (beliefStore.isIntVar(varName)) {
                                if (result instanceof Integer) {
                                    beliefStore.setIntVar(varName, (Integer) result);
                                } else if (result instanceof Double) {
                                    beliefStore.setIntVar(varName, ((Double) result).intValue()); // Trunca el double a entero
                                } else {
                                    System.err.println("⚠️ Error: Invalid expression for integer variable: " + expression);
                                }
                            } else if (beliefStore.isRealVar(varName)) {
                                if (result instanceof Number) {
                                    beliefStore.setRealVar(varName, ((Number) result).doubleValue());
                                } else {
                                    System.err.println("⚠️ Error: Invalid expression for real variable: " + expression);
                                }
                            } else {
                                System.err.println("⚠️ Error: Undeclared variable: " + varName);
                            }

                            System.out.println("🔄 Variable updated: " + varName + " = " + result);
                        } catch (Exception e) {
                            System.err.println("⚠️ Error evaluating expression: " + expression);
                            e.printStackTrace();
                        }
                    });
                }
            }
        }

        for (Runnable remember : remembers) {
            remember.run();
        }

        for (Runnable assignment : assignments) {
            assignment.run();
        }
    }

}
