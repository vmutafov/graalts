package com.vmutafov.graalts;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.nodes.ScriptNode;
import com.oracle.truffle.js.nodes.interop.ExportValueNode;
import com.oracle.truffle.js.nodes.interop.ImportValueNode;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import org.graalvm.polyglot.SandboxPolicy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.oracle.truffle.js.lang.JavaScriptLanguage.NODE_ENV_PARSE_TOKEN;

@TruffleLanguage.Registration(
    id = TypeScriptLanguage.ID,
    name = TypeScriptLanguage.NAME,
    implementationName = TypeScriptLanguage.IMPLEMENTATION_NAME,
    characterMimeTypes = {
        TypeScriptLanguage.APPLICATION_MIME_TYPE,
        TypeScriptLanguage.TEXT_MIME_TYPE,
        TypeScriptLanguage.MODULE_MIME_TYPE,
    },
    defaultMimeType = TypeScriptLanguage.APPLICATION_MIME_TYPE,
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
    dependentLanguages = {"js"},
    fileTypeDetectors = TSFileTypeDetector.class,
    sandbox = SandboxPolicy.UNTRUSTED)
public class TypeScriptLanguage extends TruffleLanguage<JSRealm> {

  public static final String TEXT_MIME_TYPE = "text/typescript";
  public static final String APPLICATION_MIME_TYPE = "application/typescript";
  public static final String MODULE_MIME_TYPE = "application/typescript+module";
  public static final String NAME = "TypeScript";
  public static final String IMPLEMENTATION_NAME = "TypeScript";
  public static final String ID = "ts";
  private final TSCompiler tsCompiler = new TSCompiler();
  private JavaScriptLanguage javaScriptLanguage;

  @Override
  protected JSRealm createContext(Env env) {
    LanguageInfo jsInfo = env.getInternalLanguages().get("js");
    env.initializeLanguage(jsInfo);
    javaScriptLanguage = JavaScriptLanguage.getCurrentLanguage();

    JSRealm jsRealm = javaScriptLanguage.getJSContext().createRealm(env);
    JSRealmPatcher.setTSModuleLoader(jsRealm, new TSModuleLoader(jsRealm));

    return jsRealm;
  }

  @CompilerDirectives.TruffleBoundary
  @Override
  public CallTarget parse(TruffleLanguage.ParsingRequest parsingRequest) {
    Source tsSource = parsingRequest.getSource();
    Source jsSource = tsCompiler.compileToSource(tsSource);
    List<String> argumentNames = parsingRequest.getArgumentNames();
    final JSContext context = javaScriptLanguage.getJSContext();

    final ScriptNode program;
    assert argumentNames != null;
    if (argumentNames.size() == 4 && argumentNames.get(0).equals(NODE_ENV_PARSE_TOKEN)) {
      String prolog = argumentNames.get(1);
      String epilog = argumentNames.get(2);
      boolean strict = Boolean.parseBoolean(argumentNames.get(3));
      program = parseScript(context, jsSource, prolog, epilog, strict, new ArrayList<>());
    } else {
      program = parseScript(context, jsSource, "", "", context.getParserOptions().strict(), argumentNames);
    }

    if (context.isOptionParseOnly()) {
      return createEmptyScript(context).getCallTarget();
    }

    return new ParsedProgramRoot(this, context, program).getCallTarget();
  }

  @CompilerDirectives.TruffleBoundary
  private static ScriptNode parseScript(JSContext context, Source code, String prolog, String epilog, boolean strict, List<String> argumentNames) {
    boolean profileTime = context.getLanguageOptions().profileTime();
    long startTime = profileTime ? System.nanoTime() : 0L;
    try {
      return context.getEvaluator().parseScript(context, code, prolog, epilog, strict, argumentNames.isEmpty() ? null : argumentNames);
    } finally {
      if (profileTime) {
        context.getTimeProfiler().printElapsed(startTime, "parsing " + code.getName());
      }
    }
  }

  @CompilerDirectives.TruffleBoundary
  private static ScriptNode createEmptyScript(JSContext context) {
    return ScriptNode.fromFunctionData(JSFunction.createEmptyFunctionData(context));
  }

  private final class ParsedProgramRoot extends RootNode {
    private final JSContext context;
    private final ScriptNode program;
    @Child private DirectCallNode directCallNode;
    @Child private ExportValueNode exportValueNode = ExportValueNode.create();
    @Child private ImportValueNode importValueNode = ImportValueNode.create();

    private ParsedProgramRoot(TruffleLanguage<?> language, JSContext context, ScriptNode program) {
      super(language);
      this.context = context;
      this.program = program;
      this.directCallNode = DirectCallNode.create(program.getCallTarget());
    }

    @Override
    public Object execute(VirtualFrame frame) {
      JSRealm realm = JSRealm.get(this);
      JSRealmPatcher.setTSModuleLoader(realm, new TSModuleLoader(realm));
      assert realm.getContext() == context : "unexpected JSContext";
      try {
        javaScriptLanguage.interopBoundaryEnter(realm);
        Object[] arguments = frame.getArguments();
        for (int i = 0; i < arguments.length; i++) {
          arguments[i] = importValueNode.executeWithTarget(arguments[i]);
        }
        arguments = program.argumentsToRunWithArguments(realm, arguments);
        Object result = directCallNode.call(arguments);
        return exportValueNode.execute(result);
      } finally {
        javaScriptLanguage.interopBoundaryExit(realm);
      }
    }

    @Override
    public boolean isInternal() {
      return true;
    }

    @Override
    protected boolean isInstrumentable() {
      return false;
    }
  }
}
