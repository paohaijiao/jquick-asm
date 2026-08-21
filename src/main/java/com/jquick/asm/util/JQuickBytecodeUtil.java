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
 * asm‑util bytecode utility.
 *
 * <p>Provides common utilities such as conversion between bytecode and {@link Class} objects,
 * in‑memory dynamic class loading, writing class files to local disk, and formatted bytecode dumping.
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // 1. Load class in‑memory
 * Class<?> clazz = JQuickBytecodeUtil.defineClass("com.demo.Foo", bytes);
 * // 2. Write class file to disk
 * JQuickBytecodeUtil.writeToFile("com.demo.Foo", bytes, "d:/out");
 * // 3. Format and print bytecode
 * String text = JQuickBytecodeUtil.dump(bytes);
 * System.out.println(text);
 * }</pre>
 */
public final class JQuickBytecodeUtil {

    /**
     * Memory class loader: each generated Class uses a separate loader, allowing multiple redefinitions of the same class.
     */
    private static volatile ByteArrayClassLoader currentLoader = new ByteArrayClassLoader();

    private JQuickBytecodeUtil() {
    }

    /**
     * Load a {@link Class} object from a byte array (in‑memory dynamic generation).
     *
     * @param className Fully qualified class name, e.g. {@code "com.demo.Foo"}
     * @param bytes     Bytecode
     * @return Loaded Class object
     */
    public static Class<?> defineClass(String className, byte[] bytes) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className cannot be null or empty");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes cannot be null or empty array");
        }
        // To avoid the exception of 'class with the same name already loaded', a new loader instance is defined each time
        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        Class<?> clazz = loader.define(className, bytes);
        currentLoader = loader;
        return clazz;
    }

    /**
     * Write a class file to local disk.
     *
     * @param className Fully qualified class name, used to calculate directory structure
     * @param outputDir Output root directory, e.g. {@code "d:/out"}
     * @return Absolute path of the written file
     */
    public static String writeToFile(String className, byte[] bytes, String outputDir) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className Cannot be empty");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes cannot be null or empty array");
        }
        if (outputDir == null || outputDir.isEmpty()) {
            throw new IllegalArgumentException("outputDir cannot be null or empty");
        }
        String relative = className.replace('.', '/').concat(".class");
        Path target = Paths.get(outputDir, relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write class file: " + target, e);
        }
    }

    /**
     * Format bytecode to human-readable instruction text.
     *
     * @param bytes Bytecode
     * @return Formatted text
     */
    public static String dump(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes cannot be null or empty array");
        }
        ClassReader reader = new ClassReader(bytes);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Printer printer = new Textifier();
        TraceClassVisitor trace = new TraceClassVisitor(null, printer, pw);
        // Do not skip DEBUG to keep line numbers
        reader.accept(trace, ClassReader.SKIP_FRAMES);
        pw.flush();
        return sw.toString();
    }

    /**
     * Get the current memory class loader (for reflection loading resources).
     */
    public static ClassLoader currentClassLoader() {
        return currentLoader;
    }

    /**
     * Byte array class loader: used to load byte arrays as {@link Class} objects in memory.
     */
    private static class ByteArrayClassLoader extends ClassLoader {

        private final AtomicLong counter = new AtomicLong(0);

        Class<?> define(String name, byte[] data) {
            return defineClass(name, data, 0, data.length);
        }
    }
}
