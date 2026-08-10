# jquick-asm

> A **purely natively encapsulated** Java bytecode manipulation library built atop ASM 9.x, designed to lower the technical barrier of bytecode enhancement through an extremely minimalist API.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Java](https://img.shields.io/badge/JDK-1.8%2B-orange.svg)](https://www.oracle.com/java/)
[![ASM](https://img.shields.io/badge/ASM-9.6-red.svg)](https://asm.ow2.io/)

`jquick-asm` hides the tedious instruction-level details of ASM's `ClassVisitor` / `MethodVisitor` and exposes three minimal capabilities: **read / write / enhance** classes. With just a few lines of code you can instrument methods, capture exceptions, rewrite class structure, or generate brand-new classes.

- Pure native encapsulation: depends only on `asm` + `asm-util`, not on `asm-commons` (return-instruction interception and try-catch wrapping are implemented from scratch)
- Minimal fluent API: `from(bytes).match("name").advice(...).apply()`
- Safe by default: a built-in `JQuickEnhanceGuard` automatically protects constructors, native, and abstract methods
- Broad compatibility: supports reading and writing bytecode for JDK 8 through JDK 21

---

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Module Overview](#module-overview)
- [Core API](#core-api)
  - [1. Read class info (reader)](#1-read-class-info-reader)
  - [2. Generate a class from scratch (writer)](#2-generate-a-class-from-scratch-writer)
  - [3. Modify class structure (modifier)](#3-modify-class-structure-modifier)
  - [4. Method head/tail instrumentation (enhance)](#4-method-headtail-instrumentation-enhance)
  - [5. try-catch exception wrapping (enhance)](#5-try-catch-exception-wrapping-enhance)
  - [6. Security guard (guard)](#6-security-guard-guard)
  - [7. Utilities (util)](#7-utilities-util)
- [Security Model](#security-model)
- [Requirements](#requirements)
- [License](#license)

---

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.paohaijiao</groupId>
    <artifactId>jquick-asm</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.paohaijiao:jquick-asm:1.0.0'
```

> Transitive dependencies: `org.ow2.asm:asm:9.6` and `org.ow2.asm:asm-util:9.6` — no extra declaration needed.

---

## Quick Start

Example: add enter/exit tracing to `Calculator.add`:

```java
byte[] enhanced = JQuickMethodEnhancer.from(originalBytes)
        .match("add")                              // enhance only the add method
        .advice(JQuickMethodAdvice.builder()
                .onEnter(ctx -> {                   // insert at method head
                    MethodVisitor mv = ctx.methodVisitor();
                    mv.visitLdcInsn(ctx.name());
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/jquick/asm/demo/Tracer", "onEnter",
                            "(Ljava/lang/String;)V", false);
                })
                .onExit((ctx, opcode) -> {          // insert before each return
                    MethodVisitor mv = ctx.methodVisitor();
                    mv.visitLdcInsn(ctx.name());
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/jquick/asm/demo/Tracer", "onExit",
                            "(Ljava/lang/String;)V", false);
                })
                .build())
        .apply();                                   // returns the new bytecode

// Load into memory and invoke reflectively
Class<?> clazz = JQuickBytecodeUtil.defineClass("com.demo.Calculator", enhanced);
```

A full runnable example is available at [src/test/java/com/jquick/asm/demo/JQuickDemoTest.java](src/test/java/com/jquick/asm/demo/JQuickDemoTest.java).

---

## Module Overview

The `com.jquick.asm` package contains five decoupled sub-packages with clear responsibilities:

```
com.jquick.asm
├── core        Base wrappers: Visitor base classes, metadata containers, security guard
├── reader      Class reading: parse class structure from Class / byte[] / InputStream / class name
├── writer      Class generation & modification: build classes from scratch, structurally edit existing ones
├── enhance     Method enhancement: head/tail instrumentation, try-catch wrapping
└── util        Utilities: constants, bytecode load/write/print, type conversion, access flags
```

| Module | Key classes | Responsibility |
| --- | --- | --- |
| core | `JQuickBaseClassVisitor`, `JQuickBaseMethodVisitor` | ASM Visitor wrappers with `onMethodEnter/onMethodExit/wrapWithTryCatch` hooks |
| core | `JQuickClassInfo`, `JQuickMethodInfo`, `JQuickFieldInfo`, `JQuickAnnotationInfo` | Metadata containers |
| core | `JQuickEnhanceGuard` | Security guard: blacklist/whitelist + constructor/native/abstract protection |
| reader | `JQuickClassReaderTool` | Parse class structure in one line |
| writer | `JQuickClassWriterTool` | Fluent Builder to generate classes from scratch |
| writer | `JQuickClassModifierTool` | Add/remove fields, methods, annotations; change access on existing classes |
| enhance | `JQuickMethodEnhancer`, `JQuickTryCatchEnhancer` | Method bytecode enhancers |
| enhance | `JQuickMethodAdvice`, `JQuickTryCatchAdvice`, `JQuickMethodContext` | Advice interfaces and context |
| util | `JQuickBytecodeUtil`, `JQuickTypeUtil`, `JQuickAccessUtil`, `JQuickAsmConstants` | Common utilities and constants |

---

## Core API

### 1. Read class info (reader)

`JQuickClassReaderTool` offers multiple entry points to parse class structure without writing a `ClassVisitor`:

```java
// 1. Read from a Class object
JQuickClassInfo info = JQuickClassReaderTool.read(MyClass.class);
System.out.println(info.getSuperName());           // "java/lang/Object"

// 2. Read from bytecode (keep debug info to obtain parameter names)
JQuickClassInfo info2 = JQuickClassReaderTool.readKeepDebug(bytes);
JQuickMethodInfo m = info2.findMethod("doSomething", "(I)V");
List<String> params = m.getParameterNames();

// 3. Read from a fully-qualified class name (via the system classloader)
JQuickClassInfo info3 = JQuickClassReaderTool.read("com.demo.MyClass");

// 4. Look up a method / field by name and descriptor
JQuickMethodInfo method = info.findMethod("add", "(II)I");
JQuickFieldInfo field   = info.findField("base");

// 5. Print a human-readable class summary
System.out.println(JQuickClassReaderTool.summarize(info));
```

### 2. Generate a class from scratch (writer)

`JQuickClassWriterTool` uses a fluent Builder to dynamically build and emit a `Class` in memory:

```java
byte[] bytes = JQuickClassWriterTool.builder("com.demo.Hello")
        .extendSuper("com.demo.Base")
        .implementInterface(Runnable.class)
        .addField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;")
        .addMethod(Opcodes.ACC_PUBLIC, "run", "()V", mv -> {
            mv.visitCode();
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out",
                    "Ljava/io/PrintStream;");
            mv.visitLdcInsn("hello from generated class");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                    "println", "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(2, 1);
            mv.visitEnd();
        })
        .build();                                  // returns the bytecode

Class<?> clazz = JQuickClassWriterTool.define(bytes, "com.demo.Hello");
JQuickClassWriterTool.writeToFile(bytes, "com.demo.Hello", "d:/out");
```

### 3. Modify class structure (modifier)

`JQuickClassModifierTool` performs structural CRUD on existing bytecode:

```java
byte[] modified = JQuickClassModifierTool.from(originalBytes)
        .addField(Opcodes.ACC_PRIVATE, "counter", "I")          // add field
        .removeField("oldField")                                // remove field
        .changeFieldAccess("name", Opcodes.ACC_PUBLIC)          // change field access
        .addMethod(Opcodes.ACC_PUBLIC, "hello", "()V", mv -> { // add method
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        })
        .removeMethod("oldMethod", "()V")                       // remove method
        .changeMethodAccess("doSomething", "(I)V", Opcodes.ACC_PUBLIC)
        .addMethodAnnotation("doSomething", "(I)V", "Lcom/demo/Trace;") // add method annotation
        .addClassAnnotation("Lcom/demo/Service;")               // add class annotation
        .apply();
```

> All destructive operations (removal / access changes) go through `JQuickEnhanceGuard.DEFAULT`; constructors and native methods are rejected.

### 4. Method head/tail instrumentation (enhance)

`JQuickMethodEnhancer` inserts logic at the method head and before each return for matched methods:

```java
byte[] enhanced = JQuickMethodEnhancer.from(bytes)
        .match("doSomething")                       // match by name (overloads all hit)
        // .match("doSomething", "(I)V")             // match by name + descriptor
        // .match(meta -> meta.name.startsWith("do")) // custom predicate
        // .matchAll()                                // match every safely-modifiable method
        .advice(JQuickMethodAdvice.builder()
                .onEnter(ctx -> {
                    // ctx.name() / ctx.descriptor() / ctx.methodVisitor()
                })
                .onExit((ctx, opcode) -> {
                    // opcode is the return instruction: RETURN / IRETURN / ARETURN / ATHROW ...
                })
                .build())
        .apply();                                    // returns the new bytecode

// One-liner: enhance + load into memory
Class<?> c = JQuickMethodEnhancer.from(bytes)
        .matchAll().advice(advice).applyAndDefine("com.demo.Foo");

// One-liner: enhance + write to file
JQuickMethodEnhancer.from(bytes)
        .matchAll().advice(advice).applyAndWrite("com.demo.Foo", "d:/out");
```

**Hook trigger points**:

| Hook | Triggered at | Notes |
| --- | --- | --- |
| `onEnter` | First line of the method body | For constructors, after the `super()` call |
| `onExit` | Before each return instruction | `RETURN/IRETURN/.../ATHROW` each fire once |

### 5. try-catch exception wrapping (enhance)

`JQuickTryCatchEnhancer` wraps the entire method body in a try-catch and provides three hooks: enter / normal exit / exception exit:

```java
byte[] enhanced = JQuickTryCatchEnhancer.from(bytes)
        .match("divide")
        .advice(JQuickTryCatchAdvice.builder()
                .onEnter(ctx -> print(ctx, ">> enter " + ctx.name()))
                .onExit(ctx -> print(ctx, "<< exit  " + ctx.name()))
                .onException(ctx -> {
                    // The caught exception is on top of the stack and must stay there
                    // so the enhancer can ATHROW it afterwards.
                    MethodVisitor mv = ctx.methodVisitor();
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitLdcInsn(ctx.name());
                    mv.visitInsn(Opcodes.SWAP);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/demo/Tracer", "onError",
                            "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
                })
                .build())
        .apply();
```

**Bytecode structure**:

```
onEnter                       // before the try block, outside the catch range
startLabel:
  <original method body>
  onExit                      // before return (inside the try block)
  return
endLabel:
handlerLabel:                 // catch Throwable [startLabel, endLabel)
  onException                 // exception is on the stack, hook must keep it
  ATHROW                      // rethrow
```

> When `onException` is called, the caught exception object is on top of the operand stack. **The implementation MUST keep that exception on the stack** so the enhancer can execute `ATHROW` to rethrow it afterward. If you need to inspect the exception, `DUP` it first before calling a static method.

### 6. Security guard (guard)

`JQuickEnhanceGuard` is the unified security entry point for all enhancement operations, supporting blacklists, whitelists, and default protections:

```java
JQuickEnhanceGuard guard = JQuickEnhanceGuard.builder()
        .blacklist("hashCode", "toString")          // custom blacklist
        .whitelist("doSomething", "doOther")        // once set, only whitelisted methods are allowed
        .build();

guard.checkModifiable("doSomething", "(I)V", Opcodes.ACC_PUBLIC); // OK
guard.checkModifiable("<init>", "()V", Opcodes.ACC_PUBLIC);        // throws SecurityException
```

### 7. Utilities (util)

```java
// Load into memory / write to file / pretty-print
Class<?> clazz = JQuickBytecodeUtil.defineClass("com.demo.Foo", bytes);
String path    = JQuickBytecodeUtil.writeToFile("com.demo.Foo", bytes, "d:/out");
String text    = JQuickBytecodeUtil.dump(bytes);     // human-readable instruction text

// Type conversion
String desc     = JQuickTypeUtil.toDescriptor(String.class);  // "Ljava/lang/String;"
String internal = JQuickTypeUtil.classNameToInternal("com.demo.Foo"); // "com/demo/Foo"
String mDesc    = JQuickTypeUtil.methodDescriptor(int.class, String.class); // "(Ljava/lang/String;)I"

// Access flags
String text   = JQuickAccessUtil.toString(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC); // "public static"
boolean isStc = JQuickAccessUtil.isStatic(access);
int newAccess = JQuickAccessUtil.changeVisibility(access, Opcodes.ACC_PUBLIC);
```

---

## Security Model

`jquick-asm` validates every enhancement operation by default to preserve JVM semantics:

- **No modifying constructors** `<init>`: instrumentation would break object initialization semantics
- **No modifying static initializers** `<clinit>`
- **No modifying native methods**: no method body to instrument
- **No modifying abstract methods**: no method body
- **Default blacklist**: JVM-critical methods such as `getClass`, `wait`, `notify`, `notifyAll`
- **Custom blacklist/whitelist**: secondary filtering; once a whitelist is set, only listed methods may be modified

When a constraint is violated, a `SecurityException` is thrown. Inside the enhancers, a failed check on a single method is silently skipped (it does not abort the overall flow).

---

## Requirements

| Item | Requirement |
| --- | --- |
| JDK | 1.8+ (compile target 1.8; reads/writes JDK 8 through JDK 21 bytecode) |
| ASM | 9.6 (transitive dependency, no manual declaration needed) |
| Build tool | Maven 3.x |
| Testing | JUnit 4.13.2 (scope=test) |

---

## License

This project is licensed under the [Apache License 2.0](LICENSE).

Author: Martin (goudingcheng@gmail.com)
