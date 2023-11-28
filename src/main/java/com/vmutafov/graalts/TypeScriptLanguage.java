package com.vmutafov.graalts;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultCallTarget;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSRealm;
import org.graalvm.polyglot.SandboxPolicy;

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
  private TSCompiler tsCompiler;
  private Env env;

  @Override
  protected JSRealm createContext(Env env) {
    this.env = env;
    tsCompiler = new TSCompiler(env);
    LanguageInfo jsInfo = env.getInternalLanguages().get("js");
    env.initializeLanguage(jsInfo);
    var javaScriptLanguage = JavaScriptLanguage.getCurrentLanguage();

    JSRealm jsRealm = javaScriptLanguage.getJSContext().createRealm(env);
    JSRealmPatcher.setTSModuleLoader(jsRealm, new TSModuleLoader(jsRealm, tsCompiler));

    return jsRealm;
  }

  @CompilerDirectives.TruffleBoundary
  @Override
  public CallTarget parse(TruffleLanguage.ParsingRequest parsingRequest) {
    Source tsSource = parsingRequest.getSource();
    Source jsSource = tsCompiler.compileToNewSource(tsSource.getCharacters(), tsSource.getName(), true, tsSource.getPath());
    List<String> argumentNames = parsingRequest.getArgumentNames();

    var parsed = (DefaultCallTarget) env.parseInternal(jsSource, argumentNames.toArray(new String[0]));
    var wrapper = new TSRootNode(this, parsed.getRootNode());
    return wrapper.getCallTarget();
  }

  private class TSRootNode extends RootNode {
    private final RootNode delegate;

    protected TSRootNode(TruffleLanguage<?> language, RootNode delegate) {
      super(language);
      this.delegate = delegate;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      JSRealm realm = JSRealm.get(delegate);
      JSRealmPatcher.setTSModuleLoader(realm, new TSModuleLoader(realm, tsCompiler));
      return delegate.execute(frame);
    }
  }
}
