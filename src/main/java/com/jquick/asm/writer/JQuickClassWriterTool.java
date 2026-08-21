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
 * asm‑writer class‑generation tool: dynamically construct and generate Class in memory.
 *
 * <p>Adopts fluent‑style API. There is no need to manually write {@link org.objectweb.asm.ClassVisitor},
 * to complete the full workflow: generate class from scratch → load as {@link Class} object / write out class file.
 *
 * <h3>Usage Example</h3>
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
 *     .build();                       // return bytecode array
 *
 * Class<?> clazz = JQuickClassWriterTool.define(bytes);   // load class in‑memory
 * JQuickClassWriterTool.writeToFile(bytes, "com.demo.Hello", "d:/out");  // write class to disk
 * }</pre>
 */
public final class JQuickClassWriterTool {

    private JQuickClassWriterTool() {
    }

    /**
     * Create class builder for dynamic class generation.
     *
     * @param className Fully qualified class name, e.g. {@code "com.demo.Hello"}
     */
    public static Builder builder(String className) {
        return new Builder(className);
    }

    /**
     * Load {@link Class} object from bytecode in memory.
     *
     * @param bytes     Bytecode
     * @param className Fully qualified class name
     */
    public static Class<?> define(byte[] bytes, String className) {
        return JQuickBytecodeUtil.defineClass(className, bytes);
    }

    /**
     * Write bytecode to local class file.
     *
     * @param bytes     Bytecode
     * @param className Fully qualified class name
     * @param outputDir Output directory path
     * @return Absolute file path
     */
    public static String writeToFile(byte[] bytes, String className, String outputDir) {
        return JQuickBytecodeUtil.writeToFile(className, bytes, outputDir);
    }

    /**
     * Format print bytecode.
     *
     * @param bytes Bytecode
     * @return Formatted string
     */
    public static String dump(byte[] bytes) {
        return JQuickBytecodeUtil.dump(bytes);
    }

    // forbid unused import warning warning
    @SuppressWarnings("unused")
    private static void unused() {
        JQuickAccessUtil.isPublic(0);
    }

    /**
     * Method body callback interface: caller writes method instructions here.
     */
    @FunctionalInterface
    public interface MethodBody {
        void write(MethodVisitor mv);
    }

    /**
     * Fluent class builder for dynamic class generation.
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
                throw new IllegalArgumentException("className cannot be empty or null");
            }
            this.className = className;
            this.internalName = JQuickTypeUtil.classNameToInternal(className);
        }

        /**
         * Set bytecode version, default is JDK8.
         */
        public Builder version(int version) {
            this.version = version;
            return this;
        }

        /**
         * Set access modifier.
         */
        public Builder access(int access) {
            this.access = access;
            return this;
        }

        /**
         * Set super class name (dot-separated fully qualified name).
         */
        public Builder extendSuper(String superClassName) {
            this.superName = JQuickTypeUtil.classNameToInternal(superClassName);
            return this;
        }

        /**
         * Implement interface (class form).
         */
        public Builder implementInterface(Class<?> itf) {
            this.interfaces.add(JQuickTypeUtil.toInternalName(itf));
            return this;
        }

        /**
         * Implement interface (fully qualified name form).
         */
        public Builder implementInterface(String interfaceName) {
            this.interfaces.add(JQuickTypeUtil.classNameToInternal(interfaceName));
            return this;
        }

        /**
         * Add field with initial value and signature.
         */
        public Builder addField(int access, String name, String descriptor,
                                String signature, Object value) {
            fields.add(new FieldSpec(access, name, descriptor, signature, value));
            return this;
        }

        /**
         * Add field without initial value and signature.
         */
        public Builder addField(int access, String name, String descriptor) {
            return addField(access, name, descriptor, null, null);
        }

        /**
         * Add field based on {@link JQuickFieldInfo}.
         */
        public Builder addField(JQuickFieldInfo field) {
            fields.add(new FieldSpec(field.getAccess(), field.getName(),
                    field.getDescriptor(), field.getSignature(), field.getValue()));
            return this;
        }

        /**
         * Add method.
         */
        public Builder addMethod(int access, String name, String descriptor, String signature, String[] exceptions, MethodBody body) {
            methods.add(new MethodSpec(access, name, descriptor, signature, exceptions, body));
            return this;
        }

        /**
         * Add method without signature and exceptions.
         */
        public Builder addMethod(int access, String name, String descriptor, MethodBody body) {
            return addMethod(access, name, descriptor, null, null, body);
        }

        /**
         * Add method based on {@link JQuickMethodInfo}.
         */
        public Builder addMethod(JQuickMethodInfo method, MethodBody body) {
            String[] exs = method.getExceptions().isEmpty() ? null
                    : method.getExceptions().toArray(new String[0]);
            methods.add(new MethodSpec(method.getAccess(), method.getName(), method.getDescriptor(), method.getSignature(), exs, body));
            return this;
        }

        /**
         * Add annotation to class.
         */
        public Builder addAnnotation(String descriptor) {
            annotations.add(new AnnotationSpec(descriptor));
            return this;
        }

        /**
         * Add annotation to class.
         */
        public Builder addAnnotation(Class<? extends java.lang.annotation.Annotation> annType) {
            annotations.add(new AnnotationSpec(JQuickTypeUtil.toDescriptor(annType)));
            return this;
        }

        /**
         * Build bytecode.
         *
         * @return Bytecode
         */
        public byte[] build() {
            ClassWriter cw = new ClassWriter(JQuickAsmConstants.WRITER_FLAGS);
            String[] itfs = interfaces.isEmpty() ? null : interfaces.toArray(new String[0]);
            cw.visit(version, access, internalName, null, superName, itfs);
            // Class annotations
            for (AnnotationSpec ann : annotations) {
                cw.visitAnnotation(ann.descriptor, true).visitEnd();
            }
            // Fields
            for (FieldSpec f : fields) {
                FieldVisitor fv = cw.visitField(f.access, f.name, f.descriptor, f.signature, f.value);
                fv.visitEnd();
            }
            // Methods
            for (MethodSpec m : methods) {
                MethodVisitor mv = cw.visitMethod(m.access, m.name, m.descriptor, m.signature, m.exceptions);
                if (m.body != null) {
                    m.body.write(mv);
                } else {
                    // abstract/native methods
                }
                mv.visitEnd();
            }

            cw.visitEnd();
            return cw.toByteArray();
        }

        /**
         * Build and define class in memory.
         *
         * @return Class
         */
        public Class<?> buildAndDefine() {
            return JQuickBytecodeUtil.defineClass(className, build());
        }

        /**
         * Build and write class file.
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

        MethodSpec(int access, String name, String descriptor, String signature, String[] exceptions, MethodBody body) {
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
