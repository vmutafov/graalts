package com.vmutafov.graalts;

import com.oracle.truffle.api.source.Source;
import org.graalvm.polyglot.Context;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class TSCompiler {
  public String compileToString(CharSequence ts, String name) {
    try (var context = Context
        .newBuilder("js")
        .option("engine.WarnInterpreterOnly", "false")
        .build()
    ) {
      String tsService = getTypeScriptServiceCode();
      context.eval("js", tsService);
      context.getBindings("js").putMember("code", ts);

      return context.eval("js", """
            const js = ts.transpile(code, {
              module: "ESNext",
              inlineSourceMap: true,
              inlineSources: true,
            }, "FILE_NAME");
            js;
          """.replace("FILE_NAME", name)).asString();
    }
  }

  public Source compileToNewSource(CharSequence ts, String name, boolean isModule) {
    var js = compileToString(ts, name);
    return Source.newBuilder("js", js, name)
        .mimeType(isModule ? "application/javascript+module" : "application/javascript")
        .build();
  }

  public Source compileToSource(Source source) {
    var ts = source.getCharacters();
    var name = source.getName();
    var mimeType = source.getMimeType();
    var js = compileToString(ts, name);
    return Source.newBuilder(source)
        .mimeType("application/typescript+module".equals(mimeType) ? "application/javascript+module" : mimeType)
        .content(js)
        .build();
  }

  private static String getTypeScriptServiceCode() {
    var typescriptServicePath = "/META-INF/resources/webjars/typescript/5.3.2/lib/typescript.js";
    try (var stream = TSCompiler.class.getResourceAsStream(typescriptServicePath)) {
      var bytes = Objects.requireNonNull(stream).readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("TypeScript compiler not found in resources", e);
    }
  }
}
