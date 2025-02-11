import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.mvel2.MVEL;

public class TRParser {
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

        conditionStr = conditionStr.replace("True", "true");

        // Solución: usar una variable final o efectivamente final
        final String finalConditionStr = conditionStr;
        Predicate<BeliefStore> condition = beliefStoreState -> 
            ExpressionEvaluator.evaluateLogicalExpression(finalConditionStr, beliefStoreState);

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

        TRRule rule = new TRRule(condition, conditionStr, discreteActions, durativeActions, beliefStoreUpdates);
        program.addRule(rule);
    }


    private static void applyUpdates(String updates, BeliefStore beliefStore) {
        String[] updateParts = updates.split(",");
        List<Runnable> assignments = new ArrayList<>();  // Almacenar asignaciones para ejecutarlas después
        List<Runnable> remembers = new ArrayList<>();    // Almacenar remember() para ejecutarlas después

        // Ejecutar primero las eliminaciones (forget)
        for (String update : updateParts) {
            update = update.trim();

            if (update.startsWith("forget(")) {
                String fact = update.substring(7, update.length() - 1).trim();
                fact = fact.replace(".end", "_end"); // Convertir `t1.end` a `t1_end`
                
                beliefStore.removeFact(fact);
                System.out.println("🗑️ Hecho eliminado completamente: " + fact);
            }
        }

        // Luego ejecutar remember() y asignaciones
        for (String update : updateParts) {
            update = update.trim();

            if (update.startsWith("remember(")) {
                String fact = update.substring(9, update.length() - 1).trim();
                String baseFactName = fact.split("\\(")[0];

                if (fact.contains("(") && fact.contains(")")) {
                    String[] params = fact.replaceAll(".*\\(|\\)", "").split(",");

                    if (params.length > 0 && !params[0].isEmpty()) {
                        try {
                            Integer[] parsedParams = Arrays.stream(params)
                                .map(p -> p.equals("_") ? null : Integer.parseInt(p)) // Soporte para "_"
                                .toArray(Integer[]::new);

                            remembers.add(() -> beliefStore.addFact(baseFactName, parsedParams));
                        } catch (NumberFormatException e) {
                            System.err.println("⚠️ Error: Parámetro no numérico en el hecho '" + fact + "'");
                        }
                    } else {
                        System.err.println("⚠️ Error en `remember`: Se esperaba un hecho con parámetros.");
                    }
                } else {
                    remembers.add(() -> beliefStore.addFact(baseFactName));
                }
            } else if (update.contains(":=")) {
                String[] parts = update.split(":=");
                if (parts.length == 2) {
                    String varName = parts[0].trim();
                    String expression = parts[1].trim();

                    assignments.add(() -> {
                        try {
                            // Evaluar la expresión aritmética con MVEL
                            Map<String, Object> context = new HashMap<>();

                            // Registrar todas las variables enteras y reales con valores actuales
                            context.putAll(beliefStore.getAllIntVars());
                            context.putAll(beliefStore.getAllRealVars());

                            // Asignar valor 0 a variables no inicializadas
                            for (String var : beliefStore.getAllIntVars().keySet()) {
                                context.putIfAbsent(var, 0);
                            }
                            for (String var : beliefStore.getAllRealVars().keySet()) {
                                context.putIfAbsent(var, 0.0);
                            }

                            // Evaluar expresión usando MVEL
                            Object result = MVEL.eval(expression, context);

                            if (beliefStore.isIntVar(varName)) {
                                if (result instanceof Integer) {
                                    beliefStore.setIntVar(varName, (Integer) result);
                                } else if (result instanceof Double) {
                                    beliefStore.setIntVar(varName, ((Double) result).intValue());
                                } else {
                                    System.err.println("⚠️ Error: Expresión inválida para variable entera: " + expression);
                                }
                            } else if (beliefStore.isRealVar(varName)) {
                                if (result instanceof Number) {
                                    beliefStore.setRealVar(varName, ((Number) result).doubleValue());
                                } else {
                                    System.err.println("⚠️ Error: Expresión inválida para variable real: " + expression);
                                }
                            } else {
                                System.err.println("⚠️ Error: Variable no declarada: " + varName);
                            }

                            System.out.println("🔄 Variable actualizada: " + varName + " = " + result);
                        } catch (Exception e) {
                            System.err.println("⚠️ Error evaluando la expresión: " + expression);
                            e.printStackTrace();
                        }
                    });
                }
            }
        }

        // Ejecutar remember()
        for (Runnable remember : remembers) {
            remember.run();
        }

        // Ejecutar asignaciones después
        for (Runnable assignment : assignments) {
            assignment.run();
        }
    }




}
