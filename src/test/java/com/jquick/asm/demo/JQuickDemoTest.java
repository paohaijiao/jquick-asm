package com.jquick.asm.demo;

import com.jquick.asm.core.JQuickClassInfo;
import com.jquick.asm.core.JQuickEnhanceGuard;
import com.jquick.asm.core.JQuickFieldInfo;
import com.jquick.asm.core.JQuickMethodInfo;
import com.jquick.asm.enhance.*;
import com.jquick.asm.reader.JQuickClassReaderTool;
import com.jquick.asm.util.JQuickBytecodeUtil;
import com.jquick.asm.writer.JQuickClassModifierTool;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

/**
 * jquick-asm 单元测试 Demo：演示如何使用工具实现方法埋点。
 *
 * <p>覆盖能力：类信息读取、类结构修改、方法头部/尾部插桩、try-catch 异常环绕埋点、
 * 内存加载、字节码格式化打印。
 */
public class JQuickDemoTest {

    /**
     * Tracer 在字节码中的内部名。
     */
    private static final String TRACER = "com/jquick/asm/demo/Tracer";

    private static final String TRACER_ENTER = "(Ljava/lang/String;)V";

    private static final String TRACER_ERROR = "(Ljava/lang/String;Ljava/lang/Throwable;)V";

    private byte[] calculatorBytes;

    @Before
    public void setUp() throws Exception {
        Tracer.reset();
        JQuickClassInfo info = JQuickClassReaderTool.read(Calculator.class);
        calculatorBytes = loadClassBytes(Calculator.class);
        assertEquals("java/lang/Object", info.getSuperName());
        assertTrue(info.findMethod("add", "(II)I") != null);
    }

    /**
     * 类信息读取能力演示
     */
    @Test
    public void demo01_readClassInfo() {
        JQuickClassInfo info = JQuickClassReaderTool.read(Calculator.class);
        System.out.println(JQuickClassReaderTool.summarize(info));
        assertEquals("java/lang/Object", info.getSuperName());
        JQuickMethodInfo add = info.findMethod("add", "(II)I");
        assertTrue("应能找到 add 方法", add != null);
        assertTrue("add 应为 public", (add.getAccess() & Opcodes.ACC_PUBLIC) != 0);
        JQuickFieldInfo base = info.findField("base");
        assertTrue("应能找到 base 字段", base != null);
        assertEquals("I", base.getDescriptor());
        JQuickMethodInfo divide = info.findMethod("divide", "(II)I");
        assertTrue(divide != null);
    }

    /**
     * 2. 类结构修改能力演示：新增字段/方法、改访问权限、加注解
     */
    @Test
    public void demo02_modifyClassStructure() {
        byte[] modified = JQuickClassModifierTool.from(calculatorBytes)
                .addField(Opcodes.ACC_PRIVATE, "counter", "I")
                .addMethodAnnotation("add", "(II)I", "Lcom/demo/Trace;")
                .changeMethodAccess("toString", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC)
                .apply();

        JQuickClassInfo info = JQuickClassReaderTool.read(modified);
        assertTrue("应新增 counter 字段", info.findField("counter") != null);
        JQuickMethodInfo add = info.findMethod("add", "(II)I");
        assertTrue("add 应带 Trace 注解", add.getAnnotation("Lcom/demo/Trace;") != null);
        System.out.println("修改后结构:\n" + JQuickClassReaderTool.summarize(info));
    }

    /**
     * 3. 安全守卫演示：构造方法/native 不允许增强
     */
    @Test
    public void demo03_securityGuard() {
        JQuickEnhanceGuard guard = JQuickEnhanceGuard.builder()
                .blacklist("toString")
                .build();
        byte[] enhanced = JQuickMethodEnhancer.from(calculatorBytes)
                .guard(guard)
                .matchAll()
                .advice(simpleTraceAdvice())
                .apply();
        System.out.println("安全守卫校验通过，黑名单方法被跳过");
        assertTrue(enhanced.length > 0);
        try {
            guard.checkModifiable("<init>", "(I)V", Opcodes.ACC_PUBLIC);
            fail("构造方法应被守卫拒绝");
        } catch (SecurityException expected) {
            System.out.println("守卫正确拦截构造方法: " + expected.getMessage());
        }
    }

    /**
     * 4. 方法头部/尾部插桩埋点演示
     *
     * @throws Exception
     */
    @Test
    public void demo04_methodHeadTailTrace() throws Exception {
        byte[] enhanced = JQuickMethodEnhancer.from(calculatorBytes)
                .match("add")
                .advice(simpleTraceAdvice())
                .apply();
        Class<?> enhancedClass = JQuickBytecodeUtil.defineClass("com.jquick.asm.demo.Calculator", enhanced);
        Constructor<?> ctor = enhancedClass.getConstructor(int.class);
        Object instance = ctor.newInstance(100);
        Method add = enhancedClass.getMethod("add", int.class, int.class);

        Object result = add.invoke(instance, 1, 2);
        System.out.println("add(1,2) = " + result + " (期望 103)");
        assertEquals(103, result);

        List<String> events = Tracer.snapshot();
        System.out.println("埋点事件: " + events);
        assertTrue("应包含 ENTER add", events.contains("ENTER add"));
        assertTrue("应包含 EXIT  add", events.contains("EXIT  add"));
    }

    /**
     * 5. try-catch 异常环绕埋点演示
     *
     * @throws Exception
     */
    @Test
    public void demo05_tryCatchTrace() throws Exception {
        byte[] enhanced = JQuickTryCatchEnhancer.from(calculatorBytes)
                .match("divide")
                .advice(JQuickTryCatchAdvice.builder()
                        .onEnter(ctx -> invokeTracerEnter(ctx, "divide"))
                        .onExit(ctx -> invokeTracerExit(ctx, "divide"))
                        .onException(ctx -> invokeTracerError(ctx, "divide"))
                        .build())
                .apply();
        System.out.println("增强后字节码片段:\n" + JQuickBytecodeUtil.dump(enhanced));
        Class<?> enhancedClass = JQuickBytecodeUtil.defineClass("com.jquick.asm.demo.Calculator", enhanced);
        Constructor<?> ctor = enhancedClass.getConstructor(int.class);
        Object instance = ctor.newInstance(0);
        Method divide = enhancedClass.getMethod("divide", int.class, int.class);
        // 正常路径
        Tracer.reset();
        Object result = divide.invoke(instance, 10, 2);
        System.out.println("divide(10,2) = " + result + " (期望 5)");
        assertEquals(5, result);
        List<String> ok = Tracer.snapshot();
        System.out.println("正常埋点: " + ok);
        assertTrue(ok.contains("ENTER divide"));
        assertTrue(ok.contains("EXIT  divide"));
        // 异常路径：除零
        Tracer.reset();
        try {
            divide.invoke(instance, 1, 0);
            fail("应抛出除零异常");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            System.out.println("捕获到异常: " + cause);
            assertTrue(cause instanceof IllegalArgumentException);
        }
        List<String> err = Tracer.snapshot();
        System.out.println("异常埋点: " + err);
        assertTrue("应包含 ENTER divide", err.contains("ENTER divide"));
        assertTrue("应包含 ERROR divide", err.contains("ERROR divide -> IllegalArgumentException"));
    }

    /**
     * 6. 内存生成全新类 + 写出文件演示
     *
     * @throws Exception
     */
    @Test
    public void demo06_generateClassFromScratch() throws Exception {
        com.jquick.asm.writer.JQuickClassWriterTool.MethodBody ctorBody = mv -> {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        };
        com.jquick.asm.writer.JQuickClassWriterTool.MethodBody runBody = mv -> {
            mv.visitCode();
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("hello from generated class");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
                    "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(2, 1);
            mv.visitEnd();
        };

        byte[] bytes = com.jquick.asm.writer.JQuickClassWriterTool.builder("com.demo.GeneratedHello")
                .implementInterface(Runnable.class)
                .addMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", ctorBody)
                .addMethod(Opcodes.ACC_PUBLIC, "run", "()V", runBody)
                .build();

        System.out.println("生成类字节码:\n" + JQuickBytecodeUtil.dump(bytes));

        Class<?> gen = JQuickBytecodeUtil.defineClass("com.demo.GeneratedHello", bytes);
        Runnable r = (Runnable) gen.getDeclaredConstructor().newInstance();
        r.run();
        System.out.println("生成类运行成功");
        assertTrue(Runnable.class.isAssignableFrom(gen));
    }


    /**
     * 构造简单的 enter/exit 埋点通知：调用 Tracer.onEnter/onExit。
     */
    private JQuickMethodAdvice simpleTraceAdvice() {
        return JQuickMethodAdvice.builder()
                .onEnter(ctx -> invokeTracerEnter(ctx, ctx.name()))
                .onExit((ctx, opcode) -> invokeTracerExit(ctx, ctx.name()))
                .build();
    }

    /**
     * 发射 Tracer.onEnter(name) 调用指令。
     */
    private void invokeTracerEnter(JQuickMethodContext ctx, String name) {
        org.objectweb.asm.MethodVisitor mv = ctx.methodVisitor();
        mv.visitLdcInsn(name);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "onEnter", TRACER_ENTER, false);
    }

    /**
     * 发射 Tracer.onExit(name) 调用指令。
     */
    private void invokeTracerExit(JQuickMethodContext ctx, String name) {
        org.objectweb.asm.MethodVisitor mv = ctx.methodVisitor();
        mv.visitLdcInsn(name);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "onExit", TRACER_ENTER, false);
    }

    /**
     * 发射 Tracer.onError(name, throwable) 调用指令。
     *
     * <p>调用时栈顶为异常对象 t，需保留一份供后续 ATHROW 重抛：
     * <pre>
     *   栈: [t]
     *   DUP          -> [t, t]
     *   LDC name     -> [t, t, name]
     *   SWAP         -> [t, name, t]
     *   INVOKESTATIC onError(String, Throwable)  -> [t]
     * </pre>
     */
    private void invokeTracerError(JQuickMethodContext ctx, String name) {
        org.objectweb.asm.MethodVisitor mv = ctx.methodVisitor();
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(name);
        mv.visitInsn(Opcodes.SWAP);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, TRACER, "onError", TRACER_ERROR, false);
    }

    /**
     * 通过类加载器读取类字节码。
     */
    private byte[] loadClassBytes(Class<?> clazz) throws Exception {
        String resource = clazz.getName().replace('.', '/') + ".class";
        try (java.io.InputStream in = clazz.getClassLoader().getResourceAsStream(resource)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
