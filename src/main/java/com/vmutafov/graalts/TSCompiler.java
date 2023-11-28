package com.vmutafov.graalts;

import static com.oracle.truffle.api.TruffleLanguage.Env;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

public class TSCompiler {

  private static final Source TYPESCRIPT_COMPILER_SOURCE = createTypeScriptCompilerSource();
  private static final Source TYPESCRIPT_TRANSPILE_FUNCTION_SOURCE = createTypeScriptTranspileFunctionSource();
  private final Env env;

  public TSCompiler(Env env) {
    this.env = env;
  }

  public String compileToString(CharSequence ts, String name) {
    try (var context = env.newInnerContextBuilder("js")
        .arguments("js", new String[] {ts.toString(), name})
        .build()) {
      context.evalInternal(null, TYPESCRIPT_COMPILER_SOURCE);
      var js = (TruffleString) context.evalInternal(null, TYPESCRIPT_TRANSPILE_FUNCTION_SOURCE);
      return js.toJavaStringUncached();
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
        const code = arguments[0];
        const fileName = arguments[1];
        
        ts.transpile(code, {
          module: "ESNext",
          inlineSourceMap: true,
          inlineSources: true,
        }, fileName);
        """;
    return Source.newBuilder("js", function, "typescript-transpile.js").build();
  }

  private static String getTypeScriptCompilerCode() {
    var typescriptServicePath = "/META-INF/resources/webjars/typescript/5.3.2/lib/typescript.js";
    try (var stream = TSCompiler.class.getResourceAsStream(typescriptServicePath)) {
      var bytes = Objects.requireNonNull(stream).readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("TypeScript compiler not found in resources", e);
    }
  }
}
