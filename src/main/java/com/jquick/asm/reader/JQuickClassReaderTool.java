package com.jquick.asm.reader;

import com.jquick.asm.core.*;
import com.jquick.asm.util.JQuickAccessUtil;
import com.jquick.asm.util.JQuickAsmConstants;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;

/**
 * ASM bytecode reader utility.
 *
 * <p>Simple high‑level API to parse class metadata: superclass, interfaces, fields,
 * methods, annotations, parameters, exceptions and access modifiers.
 * No manual {@link org.objectweb.asm.ClassVisitor} implementation needed.
 *
 * <p>Uses {@link JQuickAsmConstants#PARSE_FLAGS}(SKIP_FRAMES) by default.
 * Use {@link #readKeepDebug} to retain debug info including parameter names.
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * // Read from Class
 * JQuickClassInfo info = JQuickClassReaderTool.read(MyClass.class);
 *
 * // Read byte array with debug info
 * JQuickClassInfo info2 = JQuickClassReaderTool.readKeepDebug(bytes);
 * List<String> params = info2.findMethod("doSomething", "(I)V").getParameterNames();
 *
 * // Read by fully‑qualified class name
 * JQuickClassInfo info3 = JQuickClassReaderTool.read("com.demo.MyClass");
 * }</pre>
 */

public final class JQuickClassReaderTool {

    private JQuickClassReaderTool() {
    }

    /**
     * Reads class metadata from a {@link Class} object.
     *
     * @param clazz Java Class instance
     * @return class metadata container
     */
    public static JQuickClassInfo read(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz require not null");
        }
        String internalName = clazz.getName().replace('.', '/');
        String resource = internalName + ".class";
        try (InputStream in = clazz.getClassLoader() == null
                ? ClassLoader.getSystemResourceAsStream(resource)
                : clazz.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Unable to find class resource : " + resource);
            }
            return read(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read class resources: " + clazz.getName(), e);
        }
    }

    /**
     * Reads class metadata from bytecode. Skips debug information for fastest parsing.
     *
     * @param bytes class bytecode
     * @return class info container
     */
    public static JQuickClassInfo read(byte[] bytes) {
        return read(bytes, JQuickAsmConstants.PARSE_FLAGS);
    }

    /**
     * Reads class metadata from bytecode, keeps debug information for parameter names.
     *
     * @param bytes class bytecode
     * @return class info container
     */
    public static JQuickClassInfo readKeepDebug(byte[] bytes) {
        return read(bytes, JQuickAsmConstants.PARSE_FLAGS_KEEP_DEBUG);
    }

    /**
     * Reads class metadata from input stream.
     *
     * @param in bytecode input stream
     * @return class info container
     */
    public static JQuickClassInfo read(InputStream in) {
        try {
            return read(toBytes(in));
        } catch (IOException e) {
            throw new RuntimeException("Reading input stream failed ", e);
        }
    }

    /**
     * Reads class metadata by class name, loads class resource using system classloader.
     *
     * @param className fully‑qualified class name, e.g. {@code "com.demo.MyClass"}
     * @return class info container
     */
    public static JQuickClassInfo read(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("ClassName cannot be empty ");
        }
        String resource = className.replace('.', '/') + ".class";
        try (InputStream in = ClassLoader.getSystemResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Unable to find class resource : " + resource);
            }
            return read(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read class resources : " + className, e);
        }
    }

    /**
     * Reads class metadata from bytecode with custom parse flags.
     *
     * @param bytes class bytecode
     * @param flags parse flags
     * @return class info container
     */
    public static JQuickClassInfo read(byte[] bytes, int flags) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be empty ");
        }
        ClassReader reader = new ClassReader(bytes);
        JQuickClassInfo info = new JQuickClassInfo();
        JQuickBaseClassVisitor cv = new JQuickBaseClassVisitor(info);
        reader.accept(cv, flags);
        return info;
    }

    /**
     * Formats and prints class structure summary (excludes bytecode instructions) for quick inspection.
     *
     * @param info class metadata
     * @return multi‑line text summary
     */
    public static String summarize(JQuickClassInfo info) {
        if (info == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Class: ").append(info.getClassName()).append('\n');
        sb.append("  Access: ").append(JQuickAccessUtil.toString(info.getAccess())).append('\n');
        sb.append("  Super: ").append(info.getSuperName() == null
                ? "(none)" : info.getSuperName().replace('/', '.')).append('\n');
        sb.append("  Interfaces: ");
        if (info.getInterfaces().isEmpty()) {
            sb.append("(none)");
        } else {
            for (String itf : info.getInterfaces()) {
                sb.append(itf.replace('/', '.')).append(' ');
            }
        }
        sb.append('\n');
        sb.append("  Fields (").append(info.getFields().size()).append("):\n");
        for (JQuickFieldInfo f : info.getFields()) {
            sb.append("    ").append(JQuickAccessUtil.toString(f.getAccess())).append(' ')
                    .append(f.getDescriptor()).append(' ').append(f.getName()).append('\n');
        }
        sb.append("  Methods (").append(info.getMethods().size()).append("):\n");
        for (JQuickMethodInfo m : info.getMethods()) {
            sb.append("    ").append(JQuickAccessUtil.toString(m.getAccess())).append(' ')
                    .append(m.getName()).append(m.getDescriptor());
            if (!m.getExceptions().isEmpty()) {
                sb.append(" throws ");
                for (String ex : m.getExceptions()) {
                    sb.append(ex.replace('/', '.')).append(' ');
                }
            }
            if (!m.getAnnotations().isEmpty()) {
                sb.append("  // annotations: ");
                for (JQuickAnnotationInfo a : m.getAnnotations()) {
                    sb.append(a.getClassName()).append(' ');
                }
            }
            sb.append('\n');
        }
        if (!info.getAnnotations().isEmpty()) {
            sb.append("  Class Annotations: ");
            for (JQuickAnnotationInfo a : info.getAnnotations()) {
                sb.append(a.getClassName()).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static byte[] toBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
