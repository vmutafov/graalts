package com.vmutafov.graalts;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.ScriptNode;
import com.oracle.truffle.js.nodes.interop.ExportValueNode;
import com.oracle.truffle.js.nodes.interop.ImportValueNode;
import com.oracle.truffle.js.parser.GraalJSEvaluator;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.SandboxPolicy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
  private JavaScriptLanguage javaScriptLanguage;
  private TSContext context;

  private static final Method JAVASCRIPT_LANGUAGE_CREATE_CONTEXT_METHOD;
  private static final Method JAVASCRIPT_LANGUAGE_PARSE_INLINE_SCRIPT;
  private static final Method JAVASCRIPT_LANGUAGE_ARE_OPTIONS_COMPATIBLE;
  private static final Method JAVASCRIPT_LANGUAGE_INITIALIZE_CONTEXT;
  private static final Method JAVASCRIPT_LANGUAGE_FINALIZE_CONTEXT;
  private static final Method JAVASCRIPT_LANGUAGE_INITIALIZE_MULTIPLE_CONTEXTS;
  private static final Method JAVASCRIPT_LANGUAGE_DISPOSE_CONTEXT;
  private static final Method JAVASCRIPT_LANGUAGE_PATCH_CONTEXT;
  private static final Method JAVASCRIPT_LANGUAGE_GET_SCOPE;
  private static final Method JAVASCRIPT_LANGUAGE_IS_VISIBLE;
  private static final Method JAVASCRIPT_LANGUAGE_GET_LANGUAGE_VIEW;

  static {
    try {
      JAVASCRIPT_LANGUAGE_CREATE_CONTEXT_METHOD = JavaScriptLanguage.class.getDeclaredMethod("createContext", Env.class);
      JAVASCRIPT_LANGUAGE_CREATE_CONTEXT_METHOD.setAccessible(true);

      JAVASCRIPT_LANGUAGE_PARSE_INLINE_SCRIPT = JavaScriptLanguage.class.getDeclaredMethod("parseInlineScript", JSContext.class, Source.class, MaterializedFrame.class, boolean.class, Node.class);
      JAVASCRIPT_LANGUAGE_PARSE_INLINE_SCRIPT.setAccessible(true);

      JAVASCRIPT_LANGUAGE_ARE_OPTIONS_COMPATIBLE = JavaScriptLanguage.class.getDeclaredMethod("areOptionsCompatible", OptionValues.class, OptionValues.class);
      JAVASCRIPT_LANGUAGE_ARE_OPTIONS_COMPATIBLE.setAccessible(true);

      JAVASCRIPT_LANGUAGE_INITIALIZE_CONTEXT = JavaScriptLanguage.class.getDeclaredMethod("initializeContext", JSRealm.class);
      JAVASCRIPT_LANGUAGE_INITIALIZE_CONTEXT.setAccessible(true);

      JAVASCRIPT_LANGUAGE_FINALIZE_CONTEXT = JavaScriptLanguage.class.getDeclaredMethod("finalizeContext", JSRealm.class);
      JAVASCRIPT_LANGUAGE_FINALIZE_CONTEXT.setAccessible(true);

      JAVASCRIPT_LANGUAGE_INITIALIZE_MULTIPLE_CONTEXTS = JavaScriptLanguage.class.getDeclaredMethod("initializeMultipleContexts");
      JAVASCRIPT_LANGUAGE_INITIALIZE_MULTIPLE_CONTEXTS.setAccessible(true);

      JAVASCRIPT_LANGUAGE_DISPOSE_CONTEXT = JavaScriptLanguage.class.getDeclaredMethod("disposeContext", JSRealm.class);
      JAVASCRIPT_LANGUAGE_DISPOSE_CONTEXT.setAccessible(true);

      JAVASCRIPT_LANGUAGE_PATCH_CONTEXT = JavaScriptLanguage.class.getDeclaredMethod("patchContext", JSRealm.class, Env.class);
      JAVASCRIPT_LANGUAGE_PATCH_CONTEXT.setAccessible(true);

      JAVASCRIPT_LANGUAGE_GET_SCOPE = JavaScriptLanguage.class.getDeclaredMethod("getScope", JSRealm.class);
      JAVASCRIPT_LANGUAGE_GET_SCOPE.setAccessible(true);

      JAVASCRIPT_LANGUAGE_IS_VISIBLE = JavaScriptLanguage.class.getDeclaredMethod("isVisible", JSRealm.class, Object.class);
      JAVASCRIPT_LANGUAGE_IS_VISIBLE.setAccessible(true);

      JAVASCRIPT_LANGUAGE_GET_LANGUAGE_VIEW = JavaScriptLanguage.class.getDeclaredMethod("getLanguageView", JSRealm.class, Object.class);
      JAVASCRIPT_LANGUAGE_GET_LANGUAGE_VIEW.setAccessible(true);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected JSRealm createContext(Env env) {
    LanguageInfo jsInfo = env.getInternalLanguages().get("js");
    env.initializeLanguage(jsInfo);
    javaScriptLanguage = JavaScriptLanguage.getCurrentLanguage();

    JSRealm jsRealm = (JSRealm) invoke(JAVASCRIPT_LANGUAGE_CREATE_CONTEXT_METHOD, javaScriptLanguage, env);

    var jsContext = jsRealm.getContext();
    context = new TSContext((GraalJSEvaluator) jsContext.getEvaluator(), jsContext.getLanguage(), jsContext.getLanguageOptions(), env);

    JSRealmPatcher.setTSModuleLoader(jsRealm, new TSModuleLoader(jsRealm));
    JSRealmPatcher.setTSContext(jsRealm, context);

    return jsRealm;
  }

  private static Object invoke(Method method, Object thiz, Object... args) {
    try {
      return method.invoke(thiz, args);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected CallTarget parse(ParsingRequest request) throws Exception {
    var source = request.getSource();
    List<String> argumentNames = request.getArgumentNames();

    assert argumentNames != null;

    ScriptNode program;
    if (argumentNames.size() == 4 && argumentNames.get(0).equals("%NODE_ENV_PARSE_TOKEN%")) {
      String prolog = argumentNames.get(1);
      String epilog = argumentNames.get(2);
      boolean strict = Boolean.parseBoolean(argumentNames.get(3));
      program = context.getEvaluator().parseScript(context, source, prolog, epilog, strict, new ArrayList<>());
    } else {
      program = context.getEvaluator().parseScript(context, source, "", "", context.getParserOptions().strict(), argumentNames);
    }

    return context.isOptionParseOnly() ? ScriptNode.fromFunctionData(JSFunction.createEmptyFunctionData(context)).getCallTarget() : (new ParsedProgramRoot(this, context, program)).getCallTarget();
  }

  @Override
  protected ExecutableNode parse(InlineParsingRequest request) throws Exception {
    var source = request.getSource();
    final MaterializedFrame requestFrame = request.getFrame();
    final Node locationNode = request.getLocation();
    final boolean strict =  true;

    return new ExecutableNode(TypeScriptLanguage.this) {

      @Child
      private JavaScriptNode expression = this.insert((JavaScriptNode)invoke(JAVASCRIPT_LANGUAGE_PARSE_INLINE_SCRIPT, null, context, source, requestFrame, strict, locationNode));
      @Child
      private ExportValueNode exportValueNode = ExportValueNode.create();

      public Object execute(VirtualFrame frame) {
        Object result = this.expression.execute(frame);
        return this.exportValueNode.execute(result);
      }
    };
  }

  @Override
  protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
    return (boolean) invoke(JAVASCRIPT_LANGUAGE_ARE_OPTIONS_COMPATIBLE, javaScriptLanguage, firstOptions, newOptions);
  }

  @Override
  protected void initializeContext(JSRealm context) throws Exception {
    invoke(JAVASCRIPT_LANGUAGE_INITIALIZE_CONTEXT, javaScriptLanguage, (JSRealm)context);
  }

  @Override
  protected void finalizeContext(JSRealm context) {
    invoke(JAVASCRIPT_LANGUAGE_FINALIZE_CONTEXT, javaScriptLanguage, context);
  }

  @Override
  protected void initializeMultipleContexts() {
    invoke(JAVASCRIPT_LANGUAGE_INITIALIZE_MULTIPLE_CONTEXTS, javaScriptLanguage);
  }

  @Override
  protected void disposeContext(JSRealm context) {
    invoke(JAVASCRIPT_LANGUAGE_DISPOSE_CONTEXT, javaScriptLanguage, context);
  }

  @Override
  protected OptionDescriptors getOptionDescriptors() {
    return OptionDescriptors.create(Collections.emptyList());
  }

  @Override
  protected boolean patchContext(JSRealm context, Env newEnv) {
    return (boolean) invoke(JAVASCRIPT_LANGUAGE_PATCH_CONTEXT, javaScriptLanguage, context, newEnv);
  }

  @Override
  protected Object getScope(JSRealm context) {
    return invoke(JAVASCRIPT_LANGUAGE_GET_SCOPE, javaScriptLanguage, context);
  }

  @Override
  protected boolean isVisible(JSRealm context, Object value) {
    return (boolean) invoke(JAVASCRIPT_LANGUAGE_IS_VISIBLE, javaScriptLanguage, context, value);
  }

  @Override
  protected Object getLanguageView(JSRealm context, Object value) {
    return invoke(JAVASCRIPT_LANGUAGE_GET_LANGUAGE_VIEW, javaScriptLanguage, context, value);
  }

  private final class ParsedProgramRoot extends RootNode {
    private final JSContext context;
    private final ScriptNode program;
    @Child
    private DirectCallNode directCallNode;
    @Child
    private ExportValueNode exportValueNode = ExportValueNode.create();
    @Child
    private ImportValueNode importValueNode = ImportValueNode.create();

    private ParsedProgramRoot(TruffleLanguage<?> language, JSContext context, ScriptNode program) {
      super(language);
      this.context = context;
      this.program = program;
      this.directCallNode = DirectCallNode.create(program.getCallTarget());
    }

    public Object execute(VirtualFrame frame) {
      JSRealm realm = JSRealm.get(this);
      var jsContext = realm.getContext();
      JSRealmPatcher.setTSContext(realm, new TSContext((GraalJSEvaluator) jsContext.getEvaluator(), jsContext.getLanguage(), jsContext.getLanguageOptions(), realm.getEnv()));
      JSRealmPatcher.setTSModuleLoader(realm, new TSModuleLoader(realm));

      assert realm.getContext() == this.context : "unexpected JSContext";

      try {
        javaScriptLanguage.interopBoundaryEnter(realm);
        Object[] arguments = frame.getArguments();

        for(int i = 0; i < arguments.length; ++i) {
          arguments[i] = this.importValueNode.executeWithTarget(arguments[i]);
        }

        arguments = this.program.argumentsToRunWithArguments(realm, arguments);
        Object result = this.directCallNode.call(arguments);
        var executed = this.exportValueNode.execute(result);
        return executed;
      } finally {
        javaScriptLanguage.interopBoundaryExit(realm);
      }
    }

    public boolean isInternal() {
      return true;
    }

    protected boolean isInstrumentable() {
      return false;
    }
  }
}
