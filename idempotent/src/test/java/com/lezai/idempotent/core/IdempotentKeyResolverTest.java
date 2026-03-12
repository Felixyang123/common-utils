package com.lezai.idempotent.core;

import com.lezai.idempotent.annotation.Idempotent;
import com.lezai.idempotent.generator.DefaultKeyGenerator;
import com.lezai.idempotent.generator.IdempotentKeyGenerator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * IdempotentKeyResolver 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("幂等键解析器测试")
class IdempotentKeyResolverTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private Method method;

    private IdempotentKeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        keyResolver = new IdempotentKeyResolver(applicationContext);
    }

    @Test
    @DisplayName("使用默认键生成器 - 空 key 表达式")
    void testResolveWithEmptyKeyExpression() {
        Idempotent idempotent = createIdempotentMock("", "default:", DefaultKeyGenerator.class);
        setupJoinPointMock(new Object[]{"v1", "v2"});

        when(applicationContext.getBean(DefaultKeyGenerator.class)).thenReturn(new DefaultKeyGenerator());

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertNotNull(result);
        assertTrue(result.startsWith("default:"));
        assertTrue(result.contains("TestClass.testMethod."));
    }

    @Test
    @DisplayName("使用自定义前缀")
    void testResolveWithCustomPrefix() {
        Idempotent idempotent = createIdempotentMock("", "custom_prefix:", DefaultKeyGenerator.class);
        setupJoinPointMock(new Object[]{"v1", "v2"});

        when(applicationContext.getBean(DefaultKeyGenerator.class)).thenReturn(new DefaultKeyGenerator());

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertNotNull(result);
        assertTrue(result.startsWith("custom_prefix:"));
    }

    @Test
    @DisplayName("SpEL 表达式 - 简单参数引用")
    void testResolveSpelSimpleParameter() {
        Idempotent idempotent = createIdempotentMock("#userId", "user:", DefaultKeyGenerator.class);
        setupSpelJoinPointMock(new Object[]{"user123"}, new String[]{"userId"});

        when(applicationContext.getBean(DefaultKeyGenerator.class)).thenReturn(new DefaultKeyGenerator());

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertTrue(result.startsWith("user:user123"));
    }

    @Test
    @DisplayName("SpEL 表达式 - 多个参数组合")
    void testResolveSpelMultipleParameters() {
        Idempotent idempotent = createIdempotentMock("#orderId + ':' + #userId", "order:", DefaultKeyGenerator.class);
        setupSpelJoinPointMock(new Object[]{"user123", "orderId123"}, new String[]{"userId", "orderId"});

        when(applicationContext.getBean(DefaultKeyGenerator.class)).thenReturn(new DefaultKeyGenerator());

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertTrue(result.startsWith("order:orderId123:user123"));
    }

    @Test
    @DisplayName("SpEL 表达式 - 对象属性访问")
    void testResolveSpelObjectProperty() throws NoSuchMethodException {
        Idempotent idempotent = createIdempotentMock("#request.id", "req:", DefaultKeyGenerator.class);

        TestRequest request = new TestRequest("req123", "test data");
        setupSpelJoinPointMock(new Object[]{request}, new String[]{"request"});

        when(applicationContext.getBean(DefaultKeyGenerator.class)).thenReturn(new DefaultKeyGenerator());

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertTrue(result.startsWith("req:req123"));
    }

    @Test
    @DisplayName("SpEL 表达式 - 字符串拼接")
    void testResolveSpelStringConcatenation() {
        Idempotent idempotent = createIdempotentMock("'prefix:'+#productId", "product:", DefaultKeyGenerator.class);
        setupSpelJoinPointMock(new Object[]{123}, new String[]{"productId"});

        when(applicationContext.getBean(DefaultKeyGenerator.class)).thenReturn(new DefaultKeyGenerator());

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertTrue(result.startsWith("product:prefix:123"));
    }

    @Test
    @DisplayName("非 SpEL 表达式 - 直接返回字符串")
    void testResolveNonSpelExpression() {
        Idempotent idempotent = createIdempotentMock("static_key", "static:", DefaultKeyGenerator.class);
        setupJoinPointMock(new Object[]{});

        when(applicationContext.getBean(DefaultKeyGenerator.class)).thenReturn(new DefaultKeyGenerator());

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertTrue(result.startsWith("static:static_key"));
    }

    @Test
    @DisplayName("使用自定义 KeyGenerator")
    void testResolveWithCustomKeyGenerator() {
        Idempotent idempotent = createIdempotentMock("", "custom:", CustomKeyGenerator.class);
        setupJoinPointMock(new Object[]{});

        when(applicationContext.getBean(CustomKeyGenerator.class)).thenReturn(new CustomKeyGenerator());

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertNotNull(result);
        assertTrue(result.startsWith("custom:"));
        assertTrue(result.contains("custom_key_from_generator"));
    }

    @Test
    @DisplayName("SpEL + 自定义 KeyGenerator 组合")
    void testResolveSpelWithCustomKeyGenerator() {
        Idempotent idempotent = createIdempotentMock("#userId", "combined:", CustomKeyGenerator.class);
        setupSpelCustomJoinPointMock(new Object[]{"user999"}, new String[]{"userId"});

        CustomKeyGenerator customGenerator = new CustomKeyGenerator();
        when(applicationContext.getBean(CustomKeyGenerator.class)).thenReturn(customGenerator);

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertNotNull(result);
        assertTrue(result.startsWith("combined:"));
        assertTrue(result.contains("user999"));
        assertEquals("combined:user999custom_key_from_generator", result);
    }

    @Test
    @DisplayName("null 参数数组 - 默认 key 生成")
    void testResolveNullArgs() {
        Idempotent idempotent = createIdempotentMock("", "nullargs:", DefaultKeyGenerator.class);
        setupJoinPointMock(null);

        when(applicationContext.getBean(DefaultKeyGenerator.class)).thenReturn(new DefaultKeyGenerator());

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertNotNull(result);
        assertEquals("nullargs:TestClass.testMethod.noargsTestClass.testMethod.noargs", result);
    }

    @Test
    @DisplayName("默认前缀为空时使用注解默认值")
    void testResolveDefaultPrefix() {
        Idempotent idempotent = createIdempotentMockWithoutPrefix();
        setupSpelJoinPointMock(new Object[]{"test123"}, new String[]{"id"});
        when(applicationContext.getBean(DefaultKeyGenerator.class)).thenReturn(new DefaultKeyGenerator());

        String result = keyResolver.resolve(joinPoint, idempotent);

        assertNotNull(result);
        assertTrue(result.startsWith("idempotent:test123"));
    }

    // ==================== 辅助方法 ====================

    private void setupJoinPointMock(Object[] args) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);

        when(method.getDeclaringClass()).thenAnswer(invocation -> TestClass.class);
        when(method.getName()).thenReturn("testMethod");
    }

    private void setupSpelCustomJoinPointMock(Object[] args, String[] paramNames) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(methodSignature.getParameterNames()).thenReturn(paramNames);
    }

    private void setupSpelJoinPointMock(Object[] args, String[] paramNames) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(paramNames);
        when(joinPoint.getArgs()).thenReturn(args);

        when(method.getDeclaringClass()).thenAnswer(invocation -> TestClass.class);
        when(method.getName()).thenReturn("testMethod");
    }

    private Idempotent createIdempotentMock(String key, String prefix,
                                            Class<? extends IdempotentKeyGenerator> generatorClass) {
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn(key);
        when(idempotent.prefix()).thenReturn(prefix);
        doReturn(generatorClass).when(idempotent).keyGenerator();
        return idempotent;
    }

    private Idempotent createIdempotentMockWithoutPrefix() {
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("#id");
        when(idempotent.prefix()).thenReturn("idempotent:");
        doReturn(DefaultKeyGenerator.class).when(idempotent).keyGenerator();
        return idempotent;
    }

    // ==================== 测试用的内部类 ====================

    public static class CustomKeyGenerator implements IdempotentKeyGenerator {
        @Override
        public String generate(ProceedingJoinPoint joinPoint) {
            return "custom_key_from_generator";
        }
    }

    public record TestRequest(String id, String name) {
    }

    public static class TestClass {
    }

}
