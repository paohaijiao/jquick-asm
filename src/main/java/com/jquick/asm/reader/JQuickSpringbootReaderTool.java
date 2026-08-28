package com.jquick.asm.reader;

import com.jquick.asm.core.JQuickClassInfo;
import com.jquick.asm.util.JQuickAsmConstants;
import com.jquick.asm.core.JQuickClassInfo;
import com.jquick.asm.core.JQuickBaseClassVisitor;
import com.jquick.asm.core.JQuickFieldInfo;
import com.jquick.asm.core.JQuickMethodInfo;
import com.jquick.asm.core.JQuickAnnotationInfo;
import com.jquick.asm.util.JQuickAsmConstants;
import com.jquick.asm.util.JQuickAccessUtil;
import org.objectweb.asm.ClassReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.io.IOException;
import java.io.InputStream;

public class JQuickSpringbootReaderTool {


    private JQuickSpringbootReaderTool() {
    }

    /**
     * Reads class metadata from a {@link Class} object.
     * 自动检测并处理Spring Boot嵌套JAR
     */
    public static JQuickClassInfo read(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz require not null");
        }
        try {
            byte[] bytes = readBytesFromClassLoader(clazz);
            return read(bytes);
        } catch (Exception e) {
            String internalName = clazz.getName().replace('.', '/');
            String resource = internalName + ".class";
            try (InputStream in = clazz.getClassLoader() == null
                    ? ClassLoader.getSystemResourceAsStream(resource)
                    : clazz.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Unable to find class resource : " + resource);
                }
                return read(in);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to read class resources: " + clazz.getName(), ex);
            }
        }
    }

    /**
     * Reads class metadata from bytecode. Skips debug information for fastest parsing.
     */
    public static JQuickClassInfo read(byte[] bytes) {
        return read(bytes, JQuickAsmConstants.PARSE_FLAGS);
    }

    /**
     * Reads class metadata from bytecode, keeps debug information for parameter names.
     */
    public static JQuickClassInfo readKeepDebug(byte[] bytes) {
        return read(bytes, JQuickAsmConstants.PARSE_FLAGS_KEEP_DEBUG);
    }

    /**
     * Reads class metadata from input stream.
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
     * 从ClassLoader读取类字节码，支持嵌套JAR
     */
    private static byte[] readBytesFromClassLoader(Class<?> clazz) throws IOException {
        String className = clazz.getName();
        String resourcePath = className.replace('.', '/') + ".class";

        ClassLoader loader = clazz.getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }

        URL resource = loader.getResource(resourcePath);
        if (resource == null) {
            throw new IOException("Cannot find class: " + className);
        }

        String urlStr = resource.toString();
        if (urlStr.startsWith("jar:") && urlStr.contains("!/BOOT-INF/lib/") && urlStr.contains("!/")) {
            byte[] bytes = readFromNestedJar(urlStr);
            if (bytes != null) {
                return bytes;
            }
        }
        try (InputStream in = resource.openStream()) {
            return toBytes(in);
        }
    }

    /**
     * 从嵌套JAR URL中读取类文件
     * 处理格式: jar:file:/path/to/app.jar!/BOOT-INF/lib/dep.jar!/com/xxx/Class.class
     */
    private static byte[] readFromNestedJar(String urlStr) throws IOException {
        // 提取外层JAR路径
        // jar:file:/path/app.jar!/BOOT-INF/lib/dep.jar!/com/xxx/Class.class
        int firstBang = urlStr.indexOf("!/");
        if (firstBang == -1) {
            return null;
        }

        String outerJarPath = urlStr.substring(4, firstBang); // 去掉 "jar:"
        if (outerJarPath.startsWith("file:")) {
            outerJarPath = outerJarPath.substring(5);
        }

        // 提取剩余部分: BOOT-INF/lib/dep.jar!/com/xxx/Class.class
        String remaining = urlStr.substring(firstBang + 2);
        int secondBang = remaining.indexOf("!/");
        if (secondBang == -1) {
            return null;
        }

        String innerJarPath = remaining.substring(0, secondBang);
        String classPath = remaining.substring(secondBang + 2);

        // 从外层JAR中读取内层JAR
        try (JarFile outerJar = new JarFile(outerJarPath)) {
            JarEntry innerJarEntry = outerJar.getJarEntry(innerJarPath);
            if (innerJarEntry == null) {
                // 尝试直接在外层JAR中查找类（某些打包方式）
                JarEntry directEntry = outerJar.getJarEntry(classPath);
                if (directEntry != null) {
                    try (InputStream in = outerJar.getInputStream(directEntry)) {
                        return toBytes(in);
                    }
                }
                return null;
            }

            // 读取内层JAR
            try (InputStream innerJarStream = outerJar.getInputStream(innerJarEntry);
                 JarInputStream jarInputStream = new JarInputStream(innerJarStream)) {

                JarEntry entry;
                while ((entry = jarInputStream.getNextJarEntry()) != null) {
                    if (entry.getName().equals(classPath)) {
                        return toBytes(jarInputStream);
                    }
                }
            }
        } catch (IOException e) {
            // 忽略异常，尝试其他方式
        }

        return null;
    }

    private static byte[] toBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * Formats and prints class structure summary (excludes bytecode instructions).
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
    public static byte[] readBytes(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz require not null");
        }
        try {
            return readBytesFromClassLoader(clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read class bytes: " + clazz.getName(), e);
        }
    }
    public static byte[] readBytes(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("ClassName cannot be empty");
        }
        try {
            String resourcePath = className.replace('.', '/') + ".class";
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {
                loader = ClassLoader.getSystemClassLoader();
            }
            URL resource = loader.getResource(resourcePath);
            if (resource == null) {
                throw new IOException("Cannot find class: " + className);
            }
            String urlStr = resource.toString();
            if (urlStr.startsWith("jar:")) {
                byte[] bytes = readFromNestedJar(urlStr);
                if (bytes != null) {
                    return bytes;
                }
            }
            try (InputStream in = resource.openStream()) {
                return toBytes(in);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read class bytes: " + className, e);
        }
    }
}
