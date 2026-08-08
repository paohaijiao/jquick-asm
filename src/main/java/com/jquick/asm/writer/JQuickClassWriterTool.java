package com.jquick.asm.writer;

import com.jquick.asm.core.JQuickFieldInfo;
import com.jquick.asm.core.JQuickMethodInfo;
import com.jquick.asm.util.JQuickAccessUtil;
import com.jquick.asm.util.JQuickAsmConstants;
import com.jquick.asm.util.JQuickBytecodeUtil;
import com.jquick.asm.util.JQuickTypeUtil;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * asm-writer 类生成工具：内存动态构建并生成 Class。
 *
 * <p>采用流式 API，无需手写 {@link org.objectweb.asm.ClassVisitor}，
 * 即可完成「从零生成类 → 加载为 Class 对象 / 写出 class 文件」全流程。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * byte[] bytes = JQuickClassWriterTool.builder("com.demo.Hello")
 *     .extendSuper("com.demo.Base")
 *     .implementInterface(Runnable.class)
 *     .addField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;")
 *     .addMethod(Opcodes.ACC_PUBLIC, "run", "()V", mv -> {
 *         mv.visitCode();
 *         mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
 *         mv.visitLdcInsn("hello");
 *         mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
 *                 "(Ljava/lang/String;)V", false);
 *         mv.visitInsn(Opcodes.RETURN);
 *         mv.visitMaxs(2, 1);
 *         mv.visitEnd();
 *     })
 *     .build();                       // 返回字节码
 *
 * Class<?> clazz = JQuickClassWriterTool.define(bytes);   // 内存加载
 * JQuickClassWriterTool.writeToFile(bytes, "com.demo.Hello", "d:/out");  // 写出文件
 * }</pre>
 */
public final class JQuickClassWriterTool {

    private JQuickClassWriterTool() {
    }

    /**
     * 创建类构建器。
     *
     * @param className 全限定类名，如 {@code "com.demo.Hello"}
     */
    public static Builder builder(String className) {
        return new Builder(className);
    }

    /**
     * 内存加载字节码为 {@link Class} 对象。
     *
     * @param bytes     字节码
     * @param className 全限定类名
     */
    public static Class<?> define(byte[] bytes, String className) {
        return JQuickBytecodeUtil.defineClass(className, bytes);
    }

    /**
     * 写出字节码到本地 class 文件。
     *
     * @param bytes     字节码
     * @param className 全限定类名
     * @param outputDir 输出根目录
     * @return 文件绝对路径
     */
    public static String writeToFile(byte[] bytes, String className, String outputDir) {
        return JQuickBytecodeUtil.writeToFile(className, bytes, outputDir);
    }

    /**
     * 格式化打印字节码。
     */
    public static String dump(byte[] bytes) {
        return JQuickBytecodeUtil.dump(bytes);
    }

    // 防止未使用导入告警
    @SuppressWarnings("unused")
    private static void unused() {
        JQuickAccessUtil.isPublic(0);
    }

    /**
     * 方法体回调接口：调用方在此写入方法指令。
     */
    @FunctionalInterface
    public interface MethodBody {
        void write(MethodVisitor mv);
    }

    /**
     * 流式类构建器。
     */
    public static final class Builder {

        private final String className;
        private final String internalName;
        private final List<String> interfaces = new ArrayList<>();
        private final List<FieldSpec> fields = new ArrayList<>();
        private final List<MethodSpec> methods = new ArrayList<>();
        private final List<AnnotationSpec> annotations = new ArrayList<>();
        private int version = JQuickAsmConstants.DEFAULT_CLASS_VERSION;
        private int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        private String superName = JQuickAsmConstants.OBJECT_INTERNAL_NAME;

        Builder(String className) {
            if (className == null || className.isEmpty()) {
                throw new IllegalArgumentException("className 不能为空");
            }
            this.className = className;
            this.internalName = JQuickTypeUtil.classNameToInternal(className);
        }

        /**
         * 设置字节码版本，默认 JDK8。
         */
        public Builder version(int version) {
            this.version = version;
            return this;
        }

        /**
         * 设置访问修饰符。
         */
        public Builder access(int access) {
            this.access = access;
            return this;
        }

        /**
         * 设置父类（点分隔全限定名）。
         */
        public Builder extendSuper(String superClassName) {
            this.superName = JQuickTypeUtil.classNameToInternal(superClassName);
            return this;
        }

        /**
         * 实现接口（Class 形式）。
         */
        public Builder implementInterface(Class<?> itf) {
            this.interfaces.add(JQuickTypeUtil.toInternalName(itf));
            return this;
        }

        /**
         * 实现接口（全限定名形式）。
         */
        public Builder implementInterface(String interfaceName) {
            this.interfaces.add(JQuickTypeUtil.classNameToInternal(interfaceName));
            return this;
        }

        /**
         * 新增字段（带初始值与签名）。
         */
        public Builder addField(int access, String name, String descriptor,
                                String signature, Object value) {
            fields.add(new FieldSpec(access, name, descriptor, signature, value));
            return this;
        }

        /**
         * 新增字段。
         */
        public Builder addField(int access, String name, String descriptor) {
            return addField(access, name, descriptor, null, null);
        }

        /**
         * 基于 {@link JQuickFieldInfo} 新增字段。
         */
        public Builder addField(JQuickFieldInfo field) {
            fields.add(new FieldSpec(field.getAccess(), field.getName(),
                    field.getDescriptor(), field.getSignature(), field.getValue()));
            return this;
        }

        /**
         * 新增方法，方法体由 {@code body} 回调填充。
         */
        public Builder addMethod(int access, String name, String descriptor,
                                 String signature, String[] exceptions, MethodBody body) {
            methods.add(new MethodSpec(access, name, descriptor, signature, exceptions, body));
            return this;
        }

        /**
         * 新增方法（无签名/异常）。
         */
        public Builder addMethod(int access, String name, String descriptor, MethodBody body) {
            return addMethod(access, name, descriptor, null, null, body);
        }

        /**
         * 基于 {@link JQuickMethodInfo} 新增方法（需提供方法体回调）。
         */
        public Builder addMethod(JQuickMethodInfo method, MethodBody body) {
            String[] exs = method.getExceptions().isEmpty() ? null
                    : method.getExceptions().toArray(new String[0]);
            methods.add(new MethodSpec(method.getAccess(), method.getName(),
                    method.getDescriptor(), method.getSignature(), exs, body));
            return this;
        }

        /**
         * 给类新增注解。
         */
        public Builder addAnnotation(String descriptor) {
            annotations.add(new AnnotationSpec(descriptor));
            return this;
        }

        /**
         * 给类新增注解（Class 形式）。
         */
        public Builder addAnnotation(Class<? extends java.lang.annotation.Annotation> annType) {
            annotations.add(new AnnotationSpec(JQuickTypeUtil.toDescriptor(annType)));
            return this;
        }

        /**
         * 构建字节码。
         *
         * @return 类字节码
         */
        public byte[] build() {
            ClassWriter cw = new ClassWriter(JQuickAsmConstants.WRITER_FLAGS);
            String[] itfs = interfaces.isEmpty() ? null : interfaces.toArray(new String[0]);
            cw.visit(version, access, internalName, null, superName, itfs);

            // 类注解
            for (AnnotationSpec ann : annotations) {
                cw.visitAnnotation(ann.descriptor, true).visitEnd();
            }

            // 字段
            for (FieldSpec f : fields) {
                FieldVisitor fv = cw.visitField(f.access, f.name, f.descriptor, f.signature, f.value);
                fv.visitEnd();
            }

            // 方法
            for (MethodSpec m : methods) {
                MethodVisitor mv = cw.visitMethod(m.access, m.name, m.descriptor, m.signature, m.exceptions);
                if (m.body != null) {
                    m.body.write(mv);
                } else {
                    // abstract/native 方法无方法体
                }
                mv.visitEnd();
            }

            cw.visitEnd();
            return cw.toByteArray();
        }

        /**
         * 构建并直接内存加载为 Class。
         */
        public Class<?> buildAndDefine() {
            return JQuickBytecodeUtil.defineClass(className, build());
        }

        /**
         * 构建并写出 class 文件。
         */
        public String buildAndWrite(String outputDir) {
            return JQuickBytecodeUtil.writeToFile(className, build(), outputDir);
        }
    }

    private static class FieldSpec {
        final int access;
        final String name;
        final String descriptor;
        final String signature;
        final Object value;

        FieldSpec(int access, String name, String descriptor, String signature, Object value) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.value = value;
        }
    }

    private static class MethodSpec {
        final int access;
        final String name;
        final String descriptor;
        final String signature;
        final String[] exceptions;
        final MethodBody body;

        MethodSpec(int access, String name, String descriptor, String signature,
                   String[] exceptions, MethodBody body) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = exceptions;
            this.body = body;
        }
    }

    private static class AnnotationSpec {
        final String descriptor;

        AnnotationSpec(String descriptor) {
            this.descriptor = descriptor;
        }
    }
}
