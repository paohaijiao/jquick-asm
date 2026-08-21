package com.jquick.asm.writer;

import com.jquick.asm.core.JQuickAnnotationInfo;
import com.jquick.asm.core.JQuickEnhanceGuard;
import com.jquick.asm.core.JQuickFieldInfo;
import com.jquick.asm.util.JQuickAccessUtil;
import com.jquick.asm.util.JQuickAsmConstants;
import com.jquick.asm.util.JQuickBytecodeUtil;
import com.jquick.asm.util.JQuickTypeUtil;
import org.objectweb.asm.*;

import java.util.*;

/**
 * asm‑writer class‑structure modification tool: perform create, read, update and delete operations on existing bytecode.
 *
 * <p>Supported capabilities:
 * <ul>
 *   <li>Add / remove fields</li>
 *   <li>Add / remove methods</li>
 *   <li>Modify access flags for classes, methods and fields</li>
 *   <li>Add annotations to classes, methods and fields</li>
 *   <li>Remove annotations</li>
 * </ul>
 *
 * <p>All modification operations pass security validation via {@link JQuickEnhanceGuard#DEFAULT};
 * constructors and native methods are protected against deletion or destructive modification.
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * byte[] modified = JQuickClassModifierTool.from(originalBytes)
 *     .addField(Opcodes.ACC_PRIVATE, "counter", "I")
 *     .removeMethod("oldMethod", "()V")
 *     .changeMethodAccess("doSomething", "(I)V", Opcodes.ACC_PUBLIC)
 *     .addMethodAnnotation("doSomething", "(I)V", "Lcom/demo/Trace;")
 *     .apply();
 * }</pre>
 */

public final class JQuickClassModifierTool {

    private final byte[] source;

    private final List<JQuickFieldInfo> fieldsToAdd = new ArrayList<>();

    private final Set<String> fieldsToRemove = new HashSet<>();

    private final Map<String, Integer> fieldAccessChanges = new LinkedHashMap<>();

    private final List<MethodAddSpec> methodsToAdd = new ArrayList<>();

    private final Set<String> methodsToRemove = new HashSet<>();

    private final Map<String, Integer> methodAccessChanges = new LinkedHashMap<>();

    private final Set<String> methodAnnotationsToRemove = new HashSet<>();

    private final Map<String, List<JQuickAnnotationInfo>> methodAnnotationsToAdd = new LinkedHashMap<>();

    private final Map<String, List<JQuickAnnotationInfo>> fieldAnnotationsToAdd = new LinkedHashMap<>();

    private final Set<String> fieldAnnotationsToRemove = new HashSet<>();

    private final List<JQuickAnnotationInfo> classAnnotationsToAdd = new ArrayList<>();

    private final Set<String> classAnnotationsToRemove = new HashSet<>();

    private JQuickEnhanceGuard guard = JQuickEnhanceGuard.DEFAULT;

    private JQuickClassModifierTool(byte[] source) {
        this.source = source;
    }

    /**
     * Create modifier tool from bytecode.
     *
     * @param bytes Bytecode array
     * @return Modifier tool instance
     */
    public static JQuickClassModifierTool from(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes cannot be null");
        }
        return new JQuickClassModifierTool(bytes);
    }

    private static String methodKey(String name, String descriptor) {
        return name + descriptor;
    }

    @SuppressWarnings("unused")
    private static void unused() {
        JQuickAccessUtil.isPublic(0);
    }

    /**
     * Set custom security guard.
     */
    public JQuickClassModifierTool guard(JQuickEnhanceGuard guard) {
        this.guard = guard == null ? JQuickEnhanceGuard.DEFAULT : guard;
        return this;
    }

    /**
     * Add field.
     */
    public JQuickClassModifierTool addField(int access, String name, String descriptor, Object value) {
        fieldsToAdd.add(new JQuickFieldInfo(access, name, descriptor, null, value));
        return this;
    }

    /**
     * Add field (no initial value).
     */
    public JQuickClassModifierTool addField(int access, String name, String descriptor) {
        return addField(access, name, descriptor, null);
    }

    /**
     * Remove field.
     */
    public JQuickClassModifierTool removeField(String name) {
        fieldsToRemove.add(name);
        return this;
    }

    /**
     * Modify field access flag.
     */
    public JQuickClassModifierTool changeFieldAccess(String name, int newAccess) {
        fieldAccessChanges.put(name, newAccess);
        return this;
    }

    /**
     * Add method.
     */
    public JQuickClassModifierTool addMethod(int access, String name, String descriptor, String[] exceptions, JQuickClassWriterTool.MethodBody body) {
        guard.checkModifiable(name, descriptor, access);
        methodsToAdd.add(new MethodAddSpec(access, name, descriptor, exceptions, body));
        return this;
    }

    /**
     * Add method (no exceptions).
     */
    public JQuickClassModifierTool addMethod(int access, String name, String descriptor, JQuickClassWriterTool.MethodBody body) {
        return addMethod(access, name, descriptor, null, body);
    }

    /**
     * Remove method.
     */
    public JQuickClassModifierTool removeMethod(String name, String descriptor) {
        if (com.jquick.asm.util.JQuickAsmConstants.INIT.equals(name)
                || com.jquick.asm.util.JQuickAsmConstants.CLINIT.equals(name)) {
            throw new SecurityException("Cannot delete constructor or static initializer: " + name);
        }
        methodsToRemove.add(methodKey(name, descriptor));
        return this;
    }

    /**
     * Modify method access flag.
     */
    public JQuickClassModifierTool changeMethodAccess(String name, String descriptor, int newAccess) {
        methodAccessChanges.put(methodKey(name, descriptor), newAccess);
        return this;
    }

    /**
     * Add class annotation.
     */
    public JQuickClassModifierTool addClassAnnotation(String descriptor) {
        classAnnotationsToAdd.add(new JQuickAnnotationInfo(descriptor));
        return this;
    }

    /**
     * Add class annotation.
     */
    public JQuickClassModifierTool addClassAnnotation(Class<? extends java.lang.annotation.Annotation> annType) {
        return addClassAnnotation(JQuickTypeUtil.toDescriptor(annType));
    }

    /**
     * Remove class annotation.
     */
    public JQuickClassModifierTool removeClassAnnotation(String descriptor) {
        classAnnotationsToRemove.add(descriptor);
        return this;
    }

    /**
     * Add method annotation.
     */
    public JQuickClassModifierTool addMethodAnnotation(String name, String descriptor, String annDescriptor) {
        methodAnnotationsToAdd
                .computeIfAbsent(methodKey(name, descriptor), k -> new ArrayList<>())
                .add(new JQuickAnnotationInfo(annDescriptor));
        return this;
    }

    /**
     * Remove method annotation.
     */
    public JQuickClassModifierTool removeMethodAnnotation(String name, String descriptor, String annDescriptor) {
        methodAnnotationsToRemove.add(methodKey(name, descriptor) + "#" + annDescriptor);
        return this;
    }

    /**
     * Add field annotation.
     */
    public JQuickClassModifierTool addFieldAnnotation(String fieldName, String annDescriptor) {
        fieldAnnotationsToAdd.computeIfAbsent(fieldName, k -> new ArrayList<>())
                .add(new JQuickAnnotationInfo(annDescriptor));
        return this;
    }

    /**
     * Remove field annotation.
     */
    public JQuickClassModifierTool removeFieldAnnotation(String fieldName, String annDescriptor) {
        fieldAnnotationsToRemove.add(fieldName + "#" + annDescriptor);
        return this;
    }

    /**
     * Apply all modifications and return new bytecode.
     */
    public byte[] apply() {
        ClassReader reader = new ClassReader(source);
        ClassWriter cw = new ClassWriter(reader, JQuickAsmConstants.WRITER_FLAGS);
        ModifierVisitor mv = new ModifierVisitor(JQuickAsmConstants.ASM_API, cw);
        reader.accept(mv, JQuickAsmConstants.PARSE_FLAGS);
        return cw.toByteArray();
    }

    /**
     * Apply all modifications and define class in memory.
     */
    public Class<?> applyAndDefine(String className) {
        return JQuickBytecodeUtil.defineClass(className, apply());
    }

    /**
     * Apply all modifications and write to file.
     */
    public String applyAndWrite(String className, String outputDir) {
        return JQuickBytecodeUtil.writeToFile(className, apply(), outputDir);
    }

    /**
     * Wrapped method visitor: injects additional annotations at the beginning of method visiting.
     *
     * <p>ASM permits multiple {@code visitAnnotation} invocations before {@code visitCode}.
     * Therefore injecting annotations immediately during wrapper construction is valid.
     * Annotations from the original method are visited subsequently by ClassReader;
     * visitation order does not affect runtime semantics.
     */
    private MethodVisitor wrapMethodAnnotationAdder(MethodVisitor mv, String methodKey) {
        List<JQuickAnnotationInfo> toAdd = methodAnnotationsToAdd.get(methodKey);
        if (toAdd == null || toAdd.isEmpty()) {
            return mv;
        }
        // Inject immediately: annotations must be visited before visitAttribute / visitCode
        for (JQuickAnnotationInfo ann : toAdd) {
            AnnotationVisitor av = mv.visitAnnotation(ann.getDescriptor(), true);
            if (av != null) {
                av.visitEnd();
            }
        }
        return mv;
    }

    private static class MethodAddSpec {

        final int access;

        final String name;

        final String descriptor;

        final String[] exceptions;

        final JQuickClassWriterTool.MethodBody body;

        MethodAddSpec(int access, String name, String descriptor, String[] exceptions, JQuickClassWriterTool.MethodBody body) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.exceptions = exceptions;
            this.body = body;
        }
    }

    /**
     * Modify class visitor: traverse class and apply modifications.
     */
    private class ModifierVisitor extends ClassVisitor {

        ModifierVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (classAnnotationsToRemove.contains(descriptor)) {
                // Remove annotation: do not forward, return null
                return null;
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public void visitEnd() {
            // Add class annotations
            for (JQuickAnnotationInfo ann : classAnnotationsToAdd) {
                AnnotationVisitor av = super.visitAnnotation(ann.getDescriptor(), true);
                if (av != null) {
                    av.visitEnd();
                }
            }
            // Add fields annotations
            for (JQuickFieldInfo f : fieldsToAdd) {
                FieldVisitor fv = super.visitField(f.getAccess(), f.getName(),
                        f.getDescriptor(), f.getSignature(), f.getValue());
                if (fv != null) {
                    fv.visitEnd();
                }
            }
            // Add methods annotations
            for (MethodAddSpec m : methodsToAdd) {
                MethodVisitor methodVisitor = super.visitMethod(
                        m.access, m.name, m.descriptor, null, m.exceptions);
                if (m.body != null) {
                    m.body.write(methodVisitor);
                }
                methodVisitor.visitEnd();
            }
            super.visitEnd();
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (fieldsToRemove.contains(name)) {
                return null; // Remove field: do not forward, return null
            }
            int newAccess = access;
            if (fieldAccessChanges.containsKey(name)) {
                newAccess = fieldAccessChanges.get(name);
            }
            FieldVisitor fv = super.visitField(newAccess, name, descriptor, signature, value);
            final String fieldName = name;
            // Wrap: filter out removal items + inject new annotations
            fv = new FieldVisitor(JQuickAsmConstants.ASM_API, fv) {
                @Override
                public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
                    if (fieldAnnotationsToRemove.contains(fieldName + "#" + annDesc)) {
                        return null;
                    }
                    return super.visitAnnotation(annDesc, visible);
                }
            };
            // Add field annotations
            List<JQuickAnnotationInfo> fieldAnns = fieldAnnotationsToAdd.get(name);
            if (fieldAnns != null) {
                for (JQuickAnnotationInfo ann : fieldAnns) {
                    AnnotationVisitor av = fv.visitAnnotation(ann.getDescriptor(), true);
                    if (av != null) {
                        av.visitEnd();
                    }
                }
            }
            return fv;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            String key = methodKey(name, descriptor);
            boolean willRemove = methodsToRemove.contains(key);
            boolean willChangeAccess = methodAccessChanges.containsKey(key);
            // Security check: interception only for actual modifications (deletion / access‑flag change).
            // Constructors, native methods and other protected members are guarded;
            // pure traversal and forwarding are unaffected to preserve normal methods.
            if (willRemove || willChangeAccess) {
                guard.checkModifiable(name, descriptor, access);
            }

            if (willRemove) {
                return null; // Remove method: do not forward, return null
            }
            int newAccess = access;
            if (willChangeAccess) {
                newAccess = methodAccessChanges.get(key);
            }
            MethodVisitor mv = super.visitMethod(newAccess, name, descriptor, signature, exceptions);
            // Wrap: filter out removal items + inject new annotations
            final String removePrefix = key + "#";
            MethodVisitor filtered = new MethodVisitor(JQuickAsmConstants.ASM_API, mv) {
                @Override
                public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
                    if (methodAnnotationsToRemove.contains(removePrefix + annDesc)) {
                        return null;
                    }
                    return super.visitAnnotation(annDesc, visible);
                }
            };
            return wrapMethodAnnotationAdder(filtered, key);
        }
    }
}
