package com.labmind.redisson.repeatexecutelimit.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

public class RepeatExecuteLimitKeyEvaluator {

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public List<String> resolveKeys(Method method, Object[] arguments, String[] keyExpressions) {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        if (keyExpressions == null || keyExpressions.length == 0) {
            throw new IllegalStateException("RepeatExecuteLimit.keys must not be empty.");
        }

        Object[] actualArguments = arguments == null ? new Object[0] : arguments;
        StandardEvaluationContext evaluationContext = createEvaluationContext(method, actualArguments);
        List<String> keys = new ArrayList<>(keyExpressions.length);
        for (String keyExpression : keyExpressions) {
            keys.add(resolveKey(keyExpression, evaluationContext));
        }
        return keys;
    }

    private StandardEvaluationContext createEvaluationContext(Method method, Object[] arguments) {
        StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
        evaluationContext.setVariable("args", arguments);
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            evaluationContext.setVariable("p" + index, argument);
            evaluationContext.setVariable("a" + index, argument);
            evaluationContext.setVariable("arg" + index, argument);
            if (parameterNames != null && index < parameterNames.length && StringUtils.hasText(parameterNames[index])) {
                evaluationContext.setVariable(parameterNames[index], argument);
            }
        }
        return evaluationContext;
    }

    private String resolveKey(String keyExpression, StandardEvaluationContext evaluationContext) {
        if (!StringUtils.hasText(keyExpression)) {
            throw new IllegalStateException("RepeatExecuteLimit.keys contains a blank expression.");
        }

        String normalizedExpression = keyExpression.trim();
        if (!normalizedExpression.startsWith("#")) {
            return normalizedExpression;
        }

        Object resolvedValue;
        try {
            resolvedValue = expressionParser.parseExpression(normalizedExpression).getValue(evaluationContext);
        }
        catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to resolve RepeatExecuteLimit key expression: " + normalizedExpression,
                    ex);
        }
        if (resolvedValue == null) {
            throw new IllegalStateException(
                    "RepeatExecuteLimit key expression resolved to null: " + normalizedExpression);
        }

        String resolvedKey = resolvedValue.toString().trim();
        if (!StringUtils.hasText(resolvedKey)) {
            throw new IllegalStateException(
                    "RepeatExecuteLimit key expression resolved to blank: " + normalizedExpression);
        }
        return resolvedKey;
    }
}
