package com.jquick.asm.enhance;

import com.jquick.asm.core.JQuickBaseClassVisitor;
import com.jquick.asm.core.JQuickBaseMethodVisitor;
import com.jquick.asm.core.JQuickEnhanceGuard;
import com.jquick.asm.util.JQuickAsmConstants;
import com.jquick.asm.util.JQuickBytecodeUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * asm-enhance 方法字节码编辑器。
 *
 * <p>对外提供「方法头部插入」「方法尾部插入」「方法返回前插入逻辑」能力，
 * 屏蔽 {@link org.objectweb.asm.MethodVisitor} 的指令级处理。基于
 * {@link JQuickBaseMethodVisitor} 的 onMethodEnter/onMethodExit 钩子实现。
 *
 * <p>所有增强操作先经 {@link JQuickEnhanceGuard} 安全校验，构造方法/native/abstract
 * 方法自动跳过；可配置方法名黑/白名单与自定义匹配器。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * byte[] enhanced = JQuickMethodEnhancer.from(originalBytes)
 *     .match("doSomething")            // 仅增强指定方法
 *     .advice(JQuickMethodAdvice.builder()
 *         .onEnter(ctx -> {
 *             MethodVisitor mv = ctx.methodVisitor();
 *             mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
 *             mv.visitLdcInsn(">> enter " + ctx.name());
 *             mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
 *                     "(Ljava/lang/String;)V", false);
 *         })
 *         .build())
 *     .apply();
 * Class<?> clazz = JQuickBytecodeUtil.defineClass("com.demo.Foo", enhanced);
 * }</pre>
 */
public final class JQuickMethodEnhancer {

    private final byte[] source;

    private final List<Predicate<MethodMeta>> matchers = new ArrayList<>();

    private JQuickMethodAdvice advice = JQuickMethodAdvice.builder().build();

    private JQuickEnhanceGuard guard = JQuickEnhanceGuard.DEFAULT;

    private boolean matchAll = false;

    private JQuickMethodEnhancer(byte[] source) {
        this.source = source;
    }

    /**
     * 从字节码创建增强器。
     */
    public static JQuickMethodEnhancer from(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes 不能为空");
        }
        return new JQuickMethodEnhancer(bytes);
    }

    // 抑制未使用导入告警
    @SuppressWarnings("unused")
    private static void unused() {
        new HashSet<Set<?>>();
    }

    /**
     * 设置自定义安全守卫。
     */
    public JQuickMethodEnhancer guard(JQuickEnhanceGuard guard) {
        this.guard = guard == null ? JQuickEnhanceGuard.DEFAULT : guard;
        return this;
    }

    /**
     * 匹配所有可安全修改的方法（构造方法/native/abstract 自动排除）。
     */
    public JQuickMethodEnhancer matchAll() {
        this.matchAll = true;
        return this;
    }

    /**
     * 按方法名匹配（重载方法都会被增强）。
     */
    public JQuickMethodEnhancer match(String methodName) {
        matchers.add(m -> m.name.equals(methodName));
        return this;
    }

    /**
     * 按方法名+描述符精确匹配。
     */
    public JQuickMethodEnhancer match(String methodName, String descriptor) {
        matchers.add(m -> m.name.equals(methodName) && m.descriptor.equals(descriptor));
        return this;
    }

    /**
     * 自定义匹配谓词。
     */
    public JQuickMethodEnhancer match(Predicate<MethodMeta> predicate) {
        if (predicate != null) {
            matchers.add(predicate);
        }
        return this;
    }

    /**
     * 设置增强通知。
     */
    public JQuickMethodEnhancer advice(JQuickMethodAdvice advice) {
        this.advice = advice == null ? JQuickMethodAdvice.builder().build() : advice;
        return this;
    }

    /**
     * 执行增强并返回新字节码。
     */
    public byte[] apply() {
        ClassReader reader = new ClassReader(source);
        ClassWriter cw = new ClassWriter(reader, JQuickAsmConstants.WRITER_FLAGS);
        JQuickBaseClassVisitor cv = new JQuickBaseClassVisitor(JQuickAsmConstants.ASM_API, cw, null);
        cv.setMethodVisitorFactory((api, downstream, access, name, descriptor, signature,
                                    exceptions, classInfo) -> {
            String owner = classInfo == null ? null : classInfo.getInternalName();
            MethodMeta meta = new MethodMeta(access, name, descriptor);
            if (!shouldEnhance(meta)) {
                return downstream; // 不匹配：原样转发
            }
            // 安全校验：不满足则跳过增强（不抛异常，保证流程不中断）
            try {
                guard.checkModifiable(name, descriptor, access);
            } catch (SecurityException e) {
                // 跳过不可增强方法
                return downstream;
            }
            final String ownerInternalName = owner;
            return new JQuickBaseMethodVisitor(api, downstream, access, name, descriptor) {
                @Override
                protected void onMethodEnter() {
                    JQuickMethodContext ctx = new JQuickMethodContext(mv, access, name, descriptor, ownerInternalName);
                    advice.onEnter(ctx);
                }

                @Override
                protected void onMethodExit(int opcode) {
                    JQuickMethodContext ctx = new JQuickMethodContext(mv, access, name, descriptor, ownerInternalName);
                    advice.onExit(ctx, opcode);
                }
            };
        });

        reader.accept(cv, JQuickAsmConstants.PARSE_FLAGS);
        return cw.toByteArray();
    }

    /**
     * 执行增强并内存加载。
     */
    public Class<?> applyAndDefine(String className) {
        return JQuickBytecodeUtil.defineClass(className, apply());
    }

    /**
     * 执行增强并写出文件。
     */
    public String applyAndWrite(String className, String outputDir) {
        return JQuickBytecodeUtil.writeToFile(className, apply(), outputDir);
    }

    private boolean shouldEnhance(MethodMeta meta) {
        if (matchAll) {
            return true;
        }
        for (Predicate<MethodMeta> p : matchers) {
            if (p.test(meta)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 方法匹配元数据。
     */
    public static final class MethodMeta {
        public final int access;
        public final String name;
        public final String descriptor;

        public MethodMeta(int access, String name, String descriptor) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
        }
    }
}
