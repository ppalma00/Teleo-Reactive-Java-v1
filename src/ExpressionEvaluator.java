import org.mvel2.MVEL;
import java.util.Map;
import java.util.HashMap;

public class ExpressionEvaluator {
    public static boolean evaluateLogicalExpression(String condition, BeliefStore beliefStore) {
        try {
            // Convertir `t1.end` en `t1_end` dentro de la condición antes de evaluarla
            condition = condition.replaceAll("\\b(\\w+)\\.end\\b", "$1_end");

            // Construir el contexto con variables y hechos activos
            Map<String, Object> context = new HashMap<>();

            // Agregar variables enteras y reales
            context.putAll(beliefStore.getAllIntVars());
            context.putAll(beliefStore.getAllRealVars());

            // Agregar hechos ACTIVOS sin parámetros como `true`
            for (String fact : beliefStore.getActiveFactsNoParams()) {
                context.put(fact, true);
            }

            // Agregar hechos ACTIVOS con parámetros como `true`
            for (String fact : beliefStore.getActiveFacts().keySet()) {
                context.put(fact, true);
            }

            // Verificar que `t1_end` está en el contexto antes de evaluar
            if (!context.containsKey("t1_end")) {
                boolean isActive = beliefStore.isFactActive("t1_end");
                context.put("t1_end", isActive);
            }

            // Evaluar la expresión con el contexto
            Object result = MVEL.eval(condition, context);

            // Retornar el resultado booleano
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            System.err.println("❌ Error al evaluar la expresión lógica: " + condition);
            e.printStackTrace();
            return false;
        }
    }
}
