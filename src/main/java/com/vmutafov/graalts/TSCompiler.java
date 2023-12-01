package com.vmutafov.graalts;

import static com.oracle.truffle.api.TruffleLanguage.Env;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

public class TSCompiler implements AutoCloseable {
  private static final Source TYPESCRIPT_COMPILER_SOURCE = createTypeScriptCompilerSource();
  private static final Source TYPESCRIPT_TRANSPILE_FUNCTION_SOURCE = createTypeScriptTranspileFunctionSource();
  private final TruffleContext context;
  private final Object transpileFunction;

  public TSCompiler(Env env) {
    this.context = env.newInnerContextBuilder("js").build();
    context.evalInternal(null, TYPESCRIPT_COMPILER_SOURCE);
    transpileFunction = context.evalInternal(null, TYPESCRIPT_TRANSPILE_FUNCTION_SOURCE);
  }

  public String compileToString(CharSequence ts, String name) {
    try {
      var js = (TruffleString) InteropLibrary.getUncached().execute(transpileFunction, ts, name);
      return js.toJavaStringUncached();
    } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
      throw new RuntimeException(e);
    }
  }

  public Source compileToNewSource(CharSequence ts, String name, boolean isModule, String filePath) {
    var js = compileToString(ts, name);
    if (filePath == null) {
      return Source.newBuilder("js", js, name)
          .mimeType(isModule ? "application/javascript+module" : "application/javascript")
          .build();
    } else {
      try {
        return Source.newBuilder("js", Path.of(filePath).toUri().toURL())
            .name(name)
            .content(js)
            .mimeType(isModule ? "application/javascript+module" : "application/javascript")
            .build();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static Source createTypeScriptCompilerSource() {
    return Source.newBuilder("js", getTypeScriptCompilerCode(), "typescript.js").build();
  }

  private static Source createTypeScriptTranspileFunctionSource() {
    String function = """
        (code, fileName) => ts.transpile(code, {
          module: "ESNext",
          inlineSourceMap: true,
          inlineSources: true,
        }, fileName);
        """;
    return Source.newBuilder("js", function, "typescript-transpile.js").build();
  }

  private static String getTypeScriptCompilerCode() {
    try (var stream = TSCompiler.class.getResourceAsStream("/typescript.js")) {
      var bytes = Objects.requireNonNull(stream).readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("TypeScript compiler not found in resources", e);
    }
  }

  @Override
  public void close() {
    this.context.close();
  }
}
