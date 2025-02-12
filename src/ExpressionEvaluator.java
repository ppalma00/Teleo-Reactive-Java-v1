import org.mvel2.MVEL;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ExpressionEvaluator {
	public static boolean evaluateLogicalExpression(String condition, BeliefStore beliefStore) {
	    try {
	        // Reemplazar `t1.end` por `t1_end` para compatibilidad con BeliefStore
	        condition = condition.replaceAll("\\b(\\w+)\\.end\\b", "$1_end");

	        // Crear el contexto con variables y hechos activos
	        Map<String, Object> context = new HashMap<>();
	        context.putAll(beliefStore.getAllIntVars());
	        context.putAll(beliefStore.getAllRealVars());

	        // Agregar hechos ACTIVOS sin parámetros como `true`
	        for (String fact : beliefStore.getActiveFactsNoParams()) {
	            context.put(fact, true);
	        }

	        // Agregar hechos ACTIVOS con parámetros
	        for (Map.Entry<String, List<List<Integer>>> entry : beliefStore.getActiveFacts().entrySet()) {
	            String factBase = entry.getKey();
	            for (List<Integer> params : entry.getValue()) {
	                String factWithParams = factBase + "(" + params.stream()
	                        .map(String::valueOf)
	                        .collect(Collectors.joining(",")) + ")";
	                context.put(factWithParams, true);
	            }
	        }

	        // **🔹 Reemplazar en la condición cualquier hecho no registrado por `false` 🔹**
	        Pattern factPattern = Pattern.compile("\\b(\\w+)\\(([^)]*)\\)"); // Captura hechos con parámetros como `uno(4)`
	        Matcher matcher = factPattern.matcher(condition);
	        StringBuffer processedCondition = new StringBuffer();

	        while (matcher.find()) {
	            String factName = matcher.group(1);  // Ejemplo: "uno"
	            String parameters = matcher.group(2); // Ejemplo: "4"

	            String fullFact = factName + "(" + parameters + ")";
	            boolean isActive = context.containsKey(fullFact) && (Boolean) context.get(fullFact);
	            matcher.appendReplacement(processedCondition, String.valueOf(isActive));
	        }

	        matcher.appendTail(processedCondition);
	        condition = processedCondition.toString();

	        // Incluir los temporizadores terminados en el contexto
	        for (String timer : beliefStore.getDeclaredTimers()) {
	            String timerEndFact = timer + "_end";
	            boolean isActive = beliefStore.isFactActive(timerEndFact);
	            context.put(timerEndFact, isActive); // True si expiró, False si aún no
	        }

	        // 🔍 Depuración: Imprimir el contexto antes de evaluar
	      //  System.out.println("🔍 Context before evaluation: " + context);
	      //  System.out.println("🔍 Processed Condition: " + condition);

	        // Evaluar la expresión con MVEL
	        Object result = MVEL.eval(condition, context);

	        // Retornar el resultado booleano
	        return result instanceof Boolean && (Boolean) result;
	    } catch (Exception e) {
	        System.err.println("❌ Error evaluating logical expression: " + condition);
	        e.printStackTrace();
	        return false;
	    }
	}

}
