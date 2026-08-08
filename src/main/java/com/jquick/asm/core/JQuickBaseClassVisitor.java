package com.jquick.asm.core;

import com.jquick.asm.util.JQuickAsmConstants;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

/**
 * asm-core 基础 ClassVisitor 封装。
 *
 * <p>承担两类职责：
 * <ol>
 *   <li>读取模式：遍历类结构并填充 {@link JQuickClassInfo}（由 asm-reader 使用）。</li>
 *   <li>增强模式：转发所有事件给下游 {@code ClassVisitor}，同时为方法访问提供
 *       统一的 {@link JQuickBaseMethodVisitor} 工厂钩子（由 asm-enhance 使用）。</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * JQuickClassInfo info = new JQuickClassInfo();
 * ClassVisitor cv = new JQuickBaseClassVisitor(Opcodes.ASM9, null, info);
 * reader.accept(cv, JQuickAsmConstants.PARSE_FLAGS);
 * // info 已填充完毕
 * }</pre>
 */
public class JQuickBaseClassVisitor extends ClassVisitor {

    /**
     * 解析结果容器（读取模式使用，增强模式可为 null）
     */
    protected final JQuickClassInfo classInfo;

    /**
     * 方法访问器工厂：返回 null 表示使用默认 JQuickBaseMethodVisitor
     */
    protected MethodVisitorFactory methodVisitorFactory;

    public JQuickBaseClassVisitor(int api, ClassVisitor cv, JQuickClassInfo classInfo) {
        super(api, cv);
        this.classInfo = classInfo;
    }

    public JQuickBaseClassVisitor(JQuickClassInfo classInfo) {
        this(JQuickAsmConstants.ASM_API, null, classInfo);
    }

    /**
     * 设置方法访问器工厂（增强模式使用）。
     *
     * @param factory 工厂，传入 null 表示不定制方法访问器
     */
    public void setMethodVisitorFactory(MethodVisitorFactory factory) {
        this.methodVisitorFactory = factory;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (classInfo != null) {
            classInfo.setVersion(version);
            classInfo.setAccess(access);
            classInfo.setInternalName(name);
            classInfo.setSuperName(superName);
            classInfo.setSignature(signature);
            if (interfaces != null) {
                for (String itf : interfaces) {
                    classInfo.addInterface(itf);
                }
            }
        }
        if (cv != null) {
            cv.visit(version, access, name, signature, superName, interfaces);
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (classInfo != null) {
            JQuickAnnotationInfo ann = new JQuickAnnotationInfo(descriptor);
            classInfo.addAnnotation(ann);
            return new AnnotationCollector(JQuickAsmConstants.ASM_API, cv != null ? cv.visitAnnotation(descriptor, visible) : null, ann);
        }
        return cv != null ? cv.visitAnnotation(descriptor, visible) : null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                   String signature, Object value) {
        if (classInfo != null) {
            JQuickFieldInfo field = new JQuickFieldInfo(access, name, descriptor, signature, value);
            classInfo.addField(field);
            return new FieldInfoCollector(JQuickAsmConstants.ASM_API,
                    cv != null ? cv.visitField(access, name, descriptor, signature, value) : null, field);
        }
        return cv != null ? cv.visitField(access, name, descriptor, signature, value) : null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        if (classInfo != null) {
            JQuickMethodInfo method = new JQuickMethodInfo(access, name, descriptor, signature);
            if (exceptions != null) {
                for (String ex : exceptions) {
                    method.addException(ex);
                }
            }
            classInfo.addMethod(method);
        }
        MethodVisitor downstream = cv != null ? cv.visitMethod(access, name, descriptor, signature, exceptions) : null;
        if (methodVisitorFactory != null) { // 增强模式：通过工厂定制方法访问器
            return methodVisitorFactory.create(
                    JQuickAsmConstants.ASM_API, downstream, access, name, descriptor,
                    signature, exceptions, classInfo);
        }
        if (classInfo != null) {// 读取模式：用 JQuickBaseMethodVisitor 收集参数名/注解（不转发指令）
            JQuickMethodInfo last = classInfo.getMethods().get(classInfo.getMethods().size() - 1);
            return new MethodMetaCollector(JQuickAsmConstants.ASM_API, downstream, last);
        }
        return downstream;
    }

    @Override
    public void visitEnd() {
        if (cv != null) {
            cv.visitEnd();
        }
    }

    @Override
    public org.objectweb.asm.ModuleVisitor visitModule(String name, int access, String version) {
        return cv != null ? cv.visitModule(name, access, version) : null;
    }

    @Override
    public void visitNestHost(String nestHost) {
        if (cv != null) {
            cv.visitNestHost(nestHost);
        }
    }

    @Override
    public void visitNestMember(String nestMember) {
        if (cv != null) {
            cv.visitNestMember(nestMember);
        }
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        if (cv != null) {
            cv.visitPermittedSubclass(permittedSubclass);
        }
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (cv != null) {
            cv.visitInnerClass(name, outerName, innerName, access);
        }
    }

    @Override
    public void visitOuterClass(String owner, String name, String descriptor) {
        if (cv != null) {
            cv.visitOuterClass(owner, name, descriptor);
        }
    }

    @Override
    public void visitAttribute(org.objectweb.asm.Attribute attribute) {
        if (cv != null) {
            cv.visitAttribute(attribute);
        }
    }

    @Override
    public void visitSource(String source, String debug) {
        if (cv != null) {
            cv.visitSource(source, debug);
        }
    }

    /**
     * 方法访问器工厂接口：增强模块通过它注入字节码改写逻辑。
     */
    public interface MethodVisitorFactory {
        /**
         * 创建方法访问器。
         *
         * @param api        ASM API 版本
         * @param downstream 下游 MethodVisitor（通常来自 ClassWriter）
         * @param access     方法访问修饰符
         * @param name       方法名
         * @param descriptor 方法描述符
         * @param signature  方法泛型签名
         * @param exceptions 方法抛出异常
         * @param classInfo  所属类信息
         */
        MethodVisitor create(int api, MethodVisitor downstream, int access, String name, String descriptor, String signature, String[] exceptions, JQuickClassInfo classInfo);
    }

    /**
     * 注解收集器：把注解属性写入 {@link JQuickAnnotationInfo}，同时转发到下游。
     */
    static class AnnotationCollector extends AnnotationVisitor {

        private final JQuickAnnotationInfo annotation;

        AnnotationCollector(int api, AnnotationVisitor downstream, JQuickAnnotationInfo annotation) {
            super(api, downstream);
            this.annotation = annotation;
        }

        @Override
        public void visit(String name, Object value) {
            annotation.setAttribute(name, value);
            super.visit(name, value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            JQuickAnnotationInfo nested = new JQuickAnnotationInfo(descriptor);// 嵌套注解：记录描述符占位
            annotation.setAttribute(name, nested);
            return new AnnotationCollector(api, super.visitAnnotation(name, descriptor), nested);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            annotation.setAttribute(name, value);
            super.visitEnum(name, descriptor, value);
        }
    }

    /**
     * 字段信息收集器：收集字段注解。
     */
    static class FieldInfoCollector extends FieldVisitor {

        private final JQuickFieldInfo field;

        FieldInfoCollector(int api, FieldVisitor downstream, JQuickFieldInfo field) {
            super(api, downstream);
            this.field = field;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            JQuickAnnotationInfo ann = new JQuickAnnotationInfo(descriptor);
            field.addAnnotation(ann);
            return new AnnotationCollector(JQuickAsmConstants.ASM_API, super.visitAnnotation(descriptor, visible), ann);
        }
    }

    /**
     * 方法元信息收集器：在读取模式下收集方法注解与参数名，不处理指令。
     */
    static class MethodMetaCollector extends MethodVisitor {

        private final JQuickMethodInfo method;

        private int paramIndex;

        MethodMetaCollector(int api, MethodVisitor downstream, JQuickMethodInfo method) {
            super(api, downstream);
            this.method = method;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            JQuickAnnotationInfo ann = new JQuickAnnotationInfo(descriptor);
            method.addAnnotation(ann);
            return new AnnotationCollector(JQuickAsmConstants.ASM_API, super.visitAnnotation(descriptor, visible), ann);
        }

        @Override
        public void visitParameter(String name, int access) {
            method.addParameterName(name);
            super.visitParameter(name, access);
        }
    }
}
