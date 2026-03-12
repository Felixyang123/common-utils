package com.lezai.idempotent.core;

import com.lezai.idempotent.annotation.Idempotent;
import com.lezai.idempotent.generator.IdempotentKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 幂等键解析器
 * 支持 SpEL 表达式和自定义生成器
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotentKeyResolver {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ApplicationContext applicationContext;

    /**
     * 解析幂等键
     */
    public String resolve(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        String prefix = idempotent.prefix();
        String keyExpression = idempotent.key();
        String parsedKey;

        // 如果 key 不为空，使用 SpEL 解析
        if (StringUtils.hasText(keyExpression)) {
            parsedKey = parseSpelExpression(joinPoint, keyExpression);
        } else {
            parsedKey = buildDefaultKey(joinPoint);
        }

        // 使用自定义生成器
        Class<? extends IdempotentKeyGenerator> generatorClass = idempotent.keyGenerator();
        if (generatorClass != null) {
            IdempotentKeyGenerator generator = applicationContext.getBean(generatorClass);
            String generatedKey = generator.generate(joinPoint);
            parsedKey += generatedKey;
        }

        // 默认生成规则
        return prefix + parsedKey;
    }

    /**
     * 解析 SpEL 表达式
     */
    private String parseSpelExpression(ProceedingJoinPoint joinPoint, String expression) {
        if (!expression.contains("#")) {
            return expression;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();

        String[] paramNames = signature.getParameterNames();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        return parser.parseExpression(expression).getValue(context, String.class);
    }

    /**
     * 构建默认的幂等键
     */
    private String buildDefaultKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        String paramHash = generateParamHash(joinPoint.getArgs());

        return String.format("%s.%s.%s", className, methodName, paramHash);
    }

    /**
     * 生成参数 hash
     */
    private String generateParamHash(Object[] args) {
        if (args == null || args.length == 0) {
            return "noargs";
        }
        String argsStr = Arrays.toString(args);
        return DigestUtils.md5DigestAsHex(argsStr.getBytes(StandardCharsets.UTF_8)).substring(0, 8);
    }
}
