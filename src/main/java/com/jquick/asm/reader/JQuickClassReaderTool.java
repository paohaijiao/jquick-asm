package com.jquick.asm.reader;

import com.jquick.asm.core.*;
import com.jquick.asm.util.JQuickAccessUtil;
import com.jquick.asm.util.JQuickAsmConstants;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;

/**
 * asm-reader 类读取工具。
 *
 * <p>对外提供易用 API，一行代码即可解析类的父类、接口、字段、方法、注解、
 * 方法参数、方法异常列表、访问修饰符，无需手写 {@link org.objectweb.asm.ClassVisitor}。
 *
 * <p>采用 {@link JQuickAsmConstants#PARSE_FLAGS}（SKIP_FRAMES）模式，避免栈帧报错；
 * 保留调试信息（参数名）由 {@link #readKeepDebug} 系列方法提供。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 从 Class 对象读取
 * JQuickClassInfo info = JQuickClassReaderTool.read(MyClass.class);
 * System.out.println(info.getSuperName());
 *
 * // 2. 从字节码读取（保留参数名）
 * JQuickClassInfo info2 = JQuickClassReaderTool.readKeepDebug(bytes);
 * JQuickMethodInfo m = info2.findMethod("doSomething", "(I)V");
 * List<String> params = m.getParameterNames();
 *
 * // 3. 从类名读取（通过类加载器加载资源）
 * JQuickClassInfo info3 = JQuickClassReaderTool.read("com.demo.MyClass");
 * }</pre>
 */
public final class JQuickClassReaderTool {

    private JQuickClassReaderTool() {
    }

    /**
     * 从 {@link Class} 对象读取类信息。
     *
     * @param clazz Java Class 对象
     * @return 类信息容器
     */
    public static JQuickClassInfo read(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz 不能为 null");
        }
        String internalName = clazz.getName().replace('.', '/');
        String resource = internalName + ".class";
        try (InputStream in = clazz.getClassLoader() == null
                ? ClassLoader.getSystemResourceAsStream(resource)
                : clazz.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("无法找到类资源: " + resource);
            }
            return read(in);
        } catch (IOException e) {
            throw new RuntimeException("读取类资源失败: " + clazz.getName(), e);
        }
    }

    /**
     * 从字节码读取类信息（跳过调试信息，解析最快）。
     *
     * @param bytes 字节码
     * @return 类信息容器
     */
    public static JQuickClassInfo read(byte[] bytes) {
        return read(bytes, JQuickAsmConstants.PARSE_FLAGS);
    }

    /**
     * 从字节码读取类信息（保留调试信息，可获取参数名）。
     *
     * @param bytes 字节码
     * @return 类信息容器
     */
    public static JQuickClassInfo readKeepDebug(byte[] bytes) {
        return read(bytes, JQuickAsmConstants.PARSE_FLAGS_KEEP_DEBUG);
    }

    /**
     * 从输入流读取类信息。
     *
     * @param in 字节码输入流
     * @return 类信息容器
     */
    public static JQuickClassInfo read(InputStream in) {
        try {
            return read(toBytes(in));
        } catch (IOException e) {
            throw new RuntimeException("读取输入流失败", e);
        }
    }

    /**
     * 从类名读取类信息（使用系统类加载器加载类资源）。
     *
     * @param className 全限定类名，如 {@code "com.demo.MyClass"}
     * @return 类信息容器
     */
    public static JQuickClassInfo read(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className 不能为空");
        }
        String resource = className.replace('.', '/') + ".class";
        try (InputStream in = ClassLoader.getSystemResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("无法找到类资源: " + resource);
            }
            return read(in);
        } catch (IOException e) {
            throw new RuntimeException("读取类资源失败: " + className, e);
        }
    }

    /**
     * 从字节码读取类信息，自定义解析标志位。
     *
     * @param bytes 字节码
     * @param flags 解析标志位
     * @return 类信息容器
     */
    public static JQuickClassInfo read(byte[] bytes, int flags) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes 不能为空");
        }
        ClassReader reader = new ClassReader(bytes);
        JQuickClassInfo info = new JQuickClassInfo();
        JQuickBaseClassVisitor cv = new JQuickBaseClassVisitor(info);
        reader.accept(cv, flags);
        return info;
    }

    /**
     * 格式化打印类结构摘要（不含指令），用于快速浏览。
     *
     * @param info 类信息
     * @return 多行文本摘要
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
