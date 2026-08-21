package com.jquick.asm.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ASM‑core annotation metadata container.
 * <p>Stores annotation descriptor and attribute key‑value pairs, unified carrier for parsed annotations on class, field and method.
 * <h3>Examples</h3>
 * <pre>{@code
 * JQuickAnnotationInfo ann = new JQuickAnnotationInfo("Lcom/demo/Trace;");
 * ann.setAttribute("value", "doSomething");
 * String value = (String) ann.getAttribute("value");
 * }</pre>
 */
public class JQuickAnnotationInfo {

    /**
     * Annotation type descriptor, e.g. {@code "Lcom/demo/Trace;"}.
     */
    private final String descriptor;

    /**
     * Annotation attribute key‑value pairs, preserves insertion order.
     */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public JQuickAnnotationInfo(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("Annotation descriptor cannot be empty");
        }
        this.descriptor = descriptor;
    }

    public String getDescriptor() {
        return descriptor;
    }

    /**
     * Gets fully‑qualified annotation class name (dot‑separated).
     *
     * @return e.g. {@code "com.demo.Trace"}
     */
    public String getClassName() {
        //Remove the L prefix and; Suffix and convert/to
        String name = descriptor;
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        return name.replace('/', '.');
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public String toString() {
        return "JQuickAnnotationInfo{" + descriptor + ", attrs=" + attributes + "}";
    }
}
