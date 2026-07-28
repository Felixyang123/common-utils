package com.lezai.idempotent.generator;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.DigestUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 默认幂等键生成器
 * 规则：类名.方法名.参数hash
 */
public class DefaultKeyGenerator implements IdempotentKeyGenerator {
    
    @Override
    public String generate(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        String paramHash = generateParamHash(joinPoint.getArgs());
        
        return String.format("%s.%s.%s", className, methodName, paramHash);
    }
    
    private String generateParamHash(Object[] args) {
        if (args == null || args.length == 0) {
            return "noargs";
        }
        String argsStr = Arrays.deepToString(args);
        return DigestUtils.md5DigestAsHex(argsStr.getBytes(StandardCharsets.UTF_8));
    }
}
