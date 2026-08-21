package com.jquick.asm.core;

import com.jquick.asm.util.JQuickAsmConstants;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

/**
 * ASM‑core base ClassVisitor wrapper.
 * <p>Two‑mode implementation:
 * <ol>
 * <li><b>Read:</b> Traverse class structure to fill {@link JQuickClassInfo} for asm‑reader.</li>
 * <li><b>Enhance:</b> Delegate events to downstream {@code ClassVisitor}, expose
 * {@link JQuickBaseMethodVisitor} factory hook for asm‑enhance.</li>
 * </ol>
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * JQuickClassInfo info = new JQuickClassInfo();
 * ClassVisitor cv = new JQuickBaseClassVisitor(Opcodes.ASM9, null, info);
 * reader.accept(cv, JQuickAsmConstants.PARSE_FLAGS);
 * // info is populated
 * }</pre>
 */
public class JQuickBaseClassVisitor extends ClassVisitor {

    /**
     * Parsing result container (used in read mode; may be {@code null} for enhance mode).
     */
    protected final JQuickClassInfo classInfo;

    /**
     * Method Visitor Factory: Returning null indicates using the default JQuickBaseMethodVisitor
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
     * Sets method visitor factory (enhance‑mode only).
     *
     * @param factory factory; pass {@code null} to skip custom method visitor
     */
    public void setMethodVisitorFactory(MethodVisitorFactory factory) {
        this.methodVisitorFactory = factory;
    }

    /**
     * Visits class header.
     */
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
        if (cv != null) {//forward to downstream cv,eg: ClassWriter
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
        return cv != null ? cv.visitAnnotation(descriptor, visible) : null;//forward to downstream cv
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (classInfo != null) {
            JQuickFieldInfo field = new JQuickFieldInfo(access, name, descriptor, signature, value);
            classInfo.addField(field);
            return new FieldInfoCollector(JQuickAsmConstants.ASM_API, cv != null ? cv.visitField(access, name, descriptor, signature, value) : null, field);
        }
        return cv != null ? cv.visitField(access, name, descriptor, signature, value) : null;//forward to downstream cv
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
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
        if (methodVisitorFactory != null) { // Enhanced mode: Accessing through factory customized methods
            return methodVisitorFactory.create(JQuickAsmConstants.ASM_API, downstream, access, name, descriptor, signature, exceptions, classInfo);
        }
        if (classInfo != null) {// Read mode: Collect parameter names/comments using JQuickBaseMethodVisitor (without forwarding instructions)
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
     * The factory interface of the access controller: enhances the module by injecting bytecode to rewrite logic.
     */
    public interface MethodVisitorFactory {
        /**
         * Creates method visitor.
         *
         * @param api        ASM API version
         * @param downstream downstream MethodVisitor (usually from ClassWriter)
         * @param access     method access modifiers
         * @param name       method name
         * @param descriptor method descriptor
         * @param signature  method generic signature
         * @param exceptions method thrown exceptions
         * @param classInfo  owner class metadata
         */
        MethodVisitor create(int api, MethodVisitor downstream, int access, String name, String descriptor, String signature, String[] exceptions, JQuickClassInfo classInfo);
    }

    /**
     * Annotation collector: writes annotation properties into {@link JQuickAnnotationInfo}, forwards events to downstream visitor.
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
     * Field info collector: gathers field annotations.
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
     * Method metadata collector: gathers method annotations and parameter names in read‑mode, ignores bytecode instructions.
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
