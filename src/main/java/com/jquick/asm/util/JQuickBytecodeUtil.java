package com.jquick.asm.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

/**
 * asm-util 字节码工具。
 *
 * <p>提供字节码与 {@link Class} 对象互转、内存动态加载、本地文件写出、格式化打印等通用能力。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 内存加载
 * Class<?> clazz = JQuickBytecodeUtil.defineClass("com.demo.Foo", bytes);
 * // 2. 写出 class 文件
 * JQuickBytecodeUtil.writeToFile("com.demo.Foo", bytes, "d:/out");
 * // 3. 格式化打印
 * String text = JQuickBytecodeUtil.dump(bytes);
 * System.out.println(text);
 * }</pre>
 */
public final class JQuickBytecodeUtil {

    /**
     * 内存类加载器：每个生成的 Class 使用独立加载器，便于多次重定义同名类。
     */
    private static volatile ByteArrayClassLoader currentLoader = new ByteArrayClassLoader();

    private JQuickBytecodeUtil() {
    }

    /**
     * 将字节数组加载为 {@link Class} 对象（内存动态生成）。
     *
     * @param className 全限定类名，如 {@code "com.demo.Foo"}
     * @param bytes     字节码
     * @return 加载后的 Class 对象
     */
    public static Class<?> defineClass(String className, byte[] bytes) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className 不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes 不能为空");
        }
        // 为避免「同名类已加载」异常，每次定义使用新的加载器实例
        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        Class<?> clazz = loader.define(className, bytes);
        currentLoader = loader;
        return clazz;
    }

    /**
     * 将字节码写入本地 class 文件。
     *
     * @param className 全限定类名，用于推算目录结构
     * @param bytes     字节码
     * @param outputDir 输出根目录，如 {@code "d:/out"}
     * @return 写入的文件绝对路径
     */
    public static String writeToFile(String className, byte[] bytes, String outputDir) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className 不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes 不能为空");
        }
        if (outputDir == null || outputDir.isEmpty()) {
            throw new IllegalArgumentException("outputDir 不能为空");
        }
        String relative = className.replace('.', '/').concat(".class");
        Path target = Paths.get(outputDir, relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("写入 class 文件失败: " + target, e);
        }
    }

    /**
     * 将字节码格式化打印为人类可读的指令文本。
     *
     * @param bytes 字节码
     * @return 格式化文本
     */
    public static String dump(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes 不能为空");
        }
        ClassReader reader = new ClassReader(bytes);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Printer printer = new Textifier();
        TraceClassVisitor trace = new TraceClassVisitor(null, printer, pw);
        // 打印时保留所有信息（不 SKIP_DEBUG）以便看到行号
        reader.accept(trace, ClassReader.SKIP_FRAMES);
        pw.flush();
        return sw.toString();
    }

    /**
     * 获取当前内存类加载器（供反射加载资源使用）。
     */
    public static ClassLoader currentClassLoader() {
        return currentLoader;
    }

    /**
     * 字节数组类加载器：在内存中将字节数组定义为 Class。
     */
    private static class ByteArrayClassLoader extends ClassLoader {

        private final AtomicLong counter = new AtomicLong(0);

        Class<?> define(String name, byte[] data) {
            return defineClass(name, data, 0, data.length);
        }
    }
}
