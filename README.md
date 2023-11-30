# GraalTS

## What?
This is an experimental Truffle language adding support for executing TypeScript code directly in a GraalJS Context without having to manually compile to JavaScript. In order to execute TypeScript code, users should initialize a Context in almost the same way it's done for JavaScript or any other Truffle language:
```Java
import org.graalvm.polyglot.Context;
...
try (var context = Context.create()) {
  context.eval("ts", "const num: number = 123; console.log(num);");
}
```

## Why?
Executing TypeScript code in GraalJS requires compiling it first to JavaScript. Although this could be done with any TypeScript compiler, it requires an additional build step and a bit of knowledge how TypeScript modules are translated to JavaScript.

## How?
This Truffle language implementation does not add a custom parser for TypeScript. It reuses the TypeScript compiler under the hood and uses it for transpiling TypeScript code to JavaScript before passing it down to the GraalJS Context for execution. The GraalJS module loaders are also patched so that TypeScript modules work as expected when running in GraalJS.

## Limitations:
- Source maps are currently always inlined into the compiled JavaScript. This would most probably change and it would be configured with an option passed to the Context executing the TypeScript code
- Classpath isolation should be disabled using `-Dpolyglotimpl.DisableClassPathIsolation=true`. This would be fixed soon so that setting this property is not necessary an JS compilation is possible
