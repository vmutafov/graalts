package com.vmutafov.graalts;

import com.oracle.js.parser.ir.Expression;
import com.oracle.js.parser.ir.Module;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.ScriptNode;
import com.oracle.truffle.js.parser.GraalJSEvaluator;
import com.oracle.truffle.js.runtime.Evaluator;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSParserOptions;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.objects.*;
import org.graalvm.polyglot.Context;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class TSEvaluator implements Evaluator {

  private final GraalJSEvaluator graalJSEvaluator;

  public TSEvaluator(GraalJSEvaluator graalJSEvaluator) {
    this.graalJSEvaluator = graalJSEvaluator;
  }

  @Override
  public ScriptNode parseEval(JSContext context, Node lastNode, Source code, ScriptOrModule activeScriptOrModule) {
    var ts = compileToSource(code);
    return graalJSEvaluator.parseEval(context, lastNode, ts, activeScriptOrModule);
  }

  @Override
  public ScriptNode parseDirectEval(JSContext context, Node lastNode, Source source, Object currEnv) {
    var ts = compileToSource(source);
    return graalJSEvaluator.parseDirectEval(context, lastNode, ts, currEnv);
  }

  @Override
  public Integer[] parseDate(JSRealm realm, String date, boolean extraLenient) {
    return graalJSEvaluator.parseDate(realm, date, extraLenient);
  }

  @Override
  public String parseToJSON(JSContext context, String code, String name, boolean includeLoc) {
    return graalJSEvaluator.parseToJSON(context, code, name, includeLoc);
  }

  @Override
  public Object getDefaultNodeFactory() {
    return graalJSEvaluator.getDefaultNodeFactory();
  }

  @Override
  public JSModuleData parseModule(JSContext context, Source source) {
    var ts = compileToSource(source);
    return graalJSEvaluator.parseModule(context, ts);
  }

  @Override
  public JSModuleData envParseModule(JSRealm realm, Source source) {
    var ts = compileToSource(source);
    return graalJSEvaluator.envParseModule(realm, ts);
  }

  @Override
  public JSModuleRecord parseJSONModule(JSRealm realm, Source source) {
    return graalJSEvaluator.parseJSONModule(realm, source);
  }

  @Override
  public JSModuleRecord hostResolveImportedModule(JSContext context, ScriptOrModule referencingScriptOrModule, Module.ModuleRequest moduleRequest) {
    return graalJSEvaluator.hostResolveImportedModule(context, referencingScriptOrModule, moduleRequest);
  }

  @Override
  public void moduleLinking(JSRealm realm, JSModuleRecord moduleRecord) {
    graalJSEvaluator.moduleLinking(realm, moduleRecord);
  }

  @Override
  public Object moduleEvaluation(JSRealm realm, JSModuleRecord moduleRecord) {
    return graalJSEvaluator.moduleEvaluation(realm, moduleRecord);
  }

  @Override
  public JSDynamicObject getModuleNamespace(JSModuleRecord moduleRecord) {
    return graalJSEvaluator.getModuleNamespace(moduleRecord);
  }

  @Override
  public ExportResolution resolveExport(JSModuleRecord moduleRecord, TruffleString exportName) {
    return graalJSEvaluator.resolveExport(moduleRecord, exportName);
  }

  @Override
  public ScriptNode evalCompile(JSContext context, String sourceCode, String name) {
    var ts = compileToString(sourceCode, name);
    return graalJSEvaluator.evalCompile(context, ts, name);
  }

  @Override
  public ScriptNode parseFunction(JSContext context, String parameterList, String body, boolean generatorFunction, boolean asyncFunction, String sourceName, ScriptOrModule activeScriptOrModule) {
    return graalJSEvaluator.parseFunction(context, parameterList, body, generatorFunction, asyncFunction, sourceName, activeScriptOrModule);
  }

  @Override
  public ScriptNode parseScript(JSContext context, Source source) {
    var ts = compileToSource(source);
    return graalJSEvaluator.parseScript(context, ts);
  }

  @Override
  public ScriptNode parseScript(JSContext context, Source source, String prolog, String epilog, boolean isStrict) {
    var ts = compileToSource(source);
    return graalJSEvaluator.parseScript(context, ts, prolog, epilog, isStrict);
  }

  @Override
  public ScriptNode parseScript(JSContext context, Source source, String prolog, String epilog, boolean isStrict, List<String> argumentNames) {
    var ts = compileToSource(source);
    return graalJSEvaluator.parseScript(context, ts, prolog, epilog, isStrict, argumentNames);
  }

  @Override
  public ScriptNode parseScript(JSContext context, String sourceString) {
    var ts = compileToString(sourceString, "unknown");
    return graalJSEvaluator.parseScript(context, ts);
  }

  @Override
  public Expression parseExpression(JSContext context, String sourceString) {
    var ts = compileToString(sourceString, "unknown");
    return graalJSEvaluator.parseExpression(context, ts);
  }

  @Override
  public JavaScriptNode parseInlineScript(JSContext context, Source source, MaterializedFrame lexicalContextFrame, boolean isStrict, Node locationNode) {
    var ts = compileToSource(source);
    return graalJSEvaluator.parseInlineScript(context, ts, lexicalContextFrame, isStrict, locationNode);
  }

  @Override
  public void checkFunctionSyntax(JSContext context, JSParserOptions parserOptions, String parameterList, String body, boolean generator, boolean async, String sourceName) {
    graalJSEvaluator.checkFunctionSyntax(context, parserOptions, parameterList, body, generator, async, sourceName);
  }

  private String compileToString(CharSequence ts, String name) {
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

  private Source compileToSource(Source source) {
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
    try (var stream = TSEvaluator.class.getResourceAsStream(typescriptServicePath)) {
      var bytes = Objects.requireNonNull(stream).readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("TypeScript compiler not found in resources", e);
    }
  }
}
