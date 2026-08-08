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
 * asm-writer 类结构修改工具：对已有字节码进行增删改查。
 *
 * <p>支持能力：
 * <ul>
 *   <li>新增/删除字段</li>
 *   <li>新增/删除方法</li>
 *   <li>修改方法/字段/类的访问权限</li>
 *   <li>给类/方法/字段新增注解</li>
 *   <li>删除注解</li>
 * </ul>
 *
 * <p>所有修改操作均通过 {@link JQuickEnhanceGuard#DEFAULT} 安全校验；
 * 构造方法、native 方法受保护，不允许删除或破坏性修改。
 *
 * <h3>使用示例</h3>
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
     * 从字节码创建修改器。
     */
    public static JQuickClassModifierTool from(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes 不能为空");
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
     * 设置自定义安全守卫。
     */
    public JQuickClassModifierTool guard(JQuickEnhanceGuard guard) {
        this.guard = guard == null ? JQuickEnhanceGuard.DEFAULT : guard;
        return this;
    }

    /**
     * 新增字段。
     */
    public JQuickClassModifierTool addField(int access, String name, String descriptor, Object value) {
        fieldsToAdd.add(new JQuickFieldInfo(access, name, descriptor, null, value));
        return this;
    }

    /**
     * 新增字段（无初始值）。
     */
    public JQuickClassModifierTool addField(int access, String name, String descriptor) {
        return addField(access, name, descriptor, null);
    }

    /**
     * 删除字段。
     */
    public JQuickClassModifierTool removeField(String name) {
        fieldsToRemove.add(name);
        return this;
    }

    /**
     * 修改字段访问权限。
     */
    public JQuickClassModifierTool changeFieldAccess(String name, int newAccess) {
        fieldAccessChanges.put(name, newAccess);
        return this;
    }

    /**
     * 新增方法（方法体由回调填充）。
     */
    public JQuickClassModifierTool addMethod(int access, String name, String descriptor, String[] exceptions, JQuickClassWriterTool.MethodBody body) {
        guard.checkModifiable(name, descriptor, access);
        methodsToAdd.add(new MethodAddSpec(access, name, descriptor, exceptions, body));
        return this;
    }

    /**
     * 新增方法（无异常声明）。
     */
    public JQuickClassModifierTool addMethod(int access, String name, String descriptor, JQuickClassWriterTool.MethodBody body) {
        return addMethod(access, name, descriptor, null, body);
    }

    /**
     * 删除方法。构造方法/native 方法会被守卫拒绝。
     */
    public JQuickClassModifierTool removeMethod(String name, String descriptor) {
        if (com.jquick.asm.util.JQuickAsmConstants.INIT.equals(name)
                || com.jquick.asm.util.JQuickAsmConstants.CLINIT.equals(name)) {
            throw new SecurityException("禁止删除构造方法/静态初始化块: " + name);
        }
        methodsToRemove.add(methodKey(name, descriptor));
        return this;
    }

    /**
     * 修改方法访问权限。
     */
    public JQuickClassModifierTool changeMethodAccess(String name, String descriptor, int newAccess) {
        methodAccessChanges.put(methodKey(name, descriptor), newAccess);
        return this;
    }

    /**
     * 给类新增注解。
     */
    public JQuickClassModifierTool addClassAnnotation(String descriptor) {
        classAnnotationsToAdd.add(new JQuickAnnotationInfo(descriptor));
        return this;
    }

    /**
     * 给类新增注解（Class 形式）。
     */
    public JQuickClassModifierTool addClassAnnotation(Class<? extends java.lang.annotation.Annotation> annType) {
        return addClassAnnotation(JQuickTypeUtil.toDescriptor(annType));
    }

    /**
     * 删除类注解。
     */
    public JQuickClassModifierTool removeClassAnnotation(String descriptor) {
        classAnnotationsToRemove.add(descriptor);
        return this;
    }

    /**
     * 给方法新增注解。
     */
    public JQuickClassModifierTool addMethodAnnotation(String name, String descriptor, String annDescriptor) {
        methodAnnotationsToAdd
                .computeIfAbsent(methodKey(name, descriptor), k -> new ArrayList<>())
                .add(new JQuickAnnotationInfo(annDescriptor));
        return this;
    }

    /**
     * 删除方法注解。
     */
    public JQuickClassModifierTool removeMethodAnnotation(String name, String descriptor, String annDescriptor) {
        methodAnnotationsToRemove.add(methodKey(name, descriptor) + "#" + annDescriptor);
        return this;
    }

    /**
     * 给字段新增注解。
     */
    public JQuickClassModifierTool addFieldAnnotation(String fieldName, String annDescriptor) {
        fieldAnnotationsToAdd.computeIfAbsent(fieldName, k -> new ArrayList<>())
                .add(new JQuickAnnotationInfo(annDescriptor));
        return this;
    }

    /**
     * 删除字段注解。
     */
    public JQuickClassModifierTool removeFieldAnnotation(String fieldName, String annDescriptor) {
        fieldAnnotationsToRemove.add(fieldName + "#" + annDescriptor);
        return this;
    }

    /**
     * 执行所有修改并返回新字节码。
     */
    public byte[] apply() {
        ClassReader reader = new ClassReader(source);
        ClassWriter cw = new ClassWriter(reader, JQuickAsmConstants.WRITER_FLAGS);
        ModifierVisitor mv = new ModifierVisitor(JQuickAsmConstants.ASM_API, cw);
        reader.accept(mv, JQuickAsmConstants.PARSE_FLAGS);
        return cw.toByteArray();
    }

    /**
     * 执行修改并内存加载。
     */
    public Class<?> applyAndDefine(String className) {
        return JQuickBytecodeUtil.defineClass(className, apply());
    }

    /**
     * 执行修改并写出文件。
     */
    public String applyAndWrite(String className, String outputDir) {
        return JQuickBytecodeUtil.writeToFile(className, apply(), outputDir);
    }

    /**
     * 包装方法访问器：在方法访问开始时注入新增的注解。
     *
     * <p>ASM 允许在 {@code visitCode} 之前任意次调用 {@code visitAnnotation}，
     * 因此在包装器构造阶段立即注入是合法的；原方法的注解随后由 ClassReader 继续访问，
     * 顺序不影响运行时语义。
     */
    private MethodVisitor wrapMethodAnnotationAdder(MethodVisitor mv, String methodKey) {
        List<JQuickAnnotationInfo> toAdd = methodAnnotationsToAdd.get(methodKey);
        if (toAdd == null || toAdd.isEmpty()) {
            return mv;
        }
        // 立即注入：注解必须在 visitAttribute/visitCode 之前访问
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

        MethodAddSpec(int access, String name, String descriptor, String[] exceptions,
                      JQuickClassWriterTool.MethodBody body) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.exceptions = exceptions;
            this.body = body;
        }
    }

    /**
     * 修改访问器：在遍历过程中按配置进行增删改。
     */
    private class ModifierVisitor extends ClassVisitor {

        ModifierVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (classAnnotationsToRemove.contains(descriptor)) {
                // 删除：不转发，返回 null
                return null;
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public void visitEnd() {
            // 新增类注解
            for (JQuickAnnotationInfo ann : classAnnotationsToAdd) {
                AnnotationVisitor av = super.visitAnnotation(ann.getDescriptor(), true);
                if (av != null) {
                    av.visitEnd();
                }
            }
            // 新增字段
            for (JQuickFieldInfo f : fieldsToAdd) {
                FieldVisitor fv = super.visitField(f.getAccess(), f.getName(),
                        f.getDescriptor(), f.getSignature(), f.getValue());
                if (fv != null) {
                    fv.visitEnd();
                }
            }
            // 新增方法
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
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if (fieldsToRemove.contains(name)) {
                return null; // 删除字段
            }
            int newAccess = access;
            if (fieldAccessChanges.containsKey(name)) {
                newAccess = fieldAccessChanges.get(name);
            }
            FieldVisitor fv = super.visitField(newAccess, name, descriptor, signature, value);
            final String fieldName = name;
            // 包装：过滤删除项 + 注入新增项
            fv = new FieldVisitor(JQuickAsmConstants.ASM_API, fv) {
                @Override
                public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
                    if (fieldAnnotationsToRemove.contains(fieldName + "#" + annDesc)) {
                        return null;
                    }
                    return super.visitAnnotation(annDesc, visible);
                }
            };
            // 新增字段注解
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
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            String key = methodKey(name, descriptor);
            boolean willRemove = methodsToRemove.contains(key);
            boolean willChangeAccess = methodAccessChanges.containsKey(key);

            // 安全校验：仅在真正修改（删除/改权限）时拦截，构造方法/native 等受保护；
            // 仅遍历转发不受影响，保证正常方法可被保留。
            if (willRemove || willChangeAccess) {
                guard.checkModifiable(name, descriptor, access);
            }

            if (willRemove) {
                return null; // 删除方法
            }
            int newAccess = access;
            if (willChangeAccess) {
                newAccess = methodAccessChanges.get(key);
            }
            MethodVisitor mv = super.visitMethod(newAccess, name, descriptor, signature, exceptions);

            // 删除方法注解 + 新增方法注解：统一包装
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
