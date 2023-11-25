package com.vmutafov.graalts;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.parser.GraalJSEvaluator;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSLanguageOptions;

public class TSContext extends JSContext {

  public TSContext(GraalJSEvaluator evaluator, JavaScriptLanguage lang, JSLanguageOptions languageOptions, TruffleLanguage.Env env) {
    super(new TSEvaluator(evaluator), lang, languageOptions, env);
  }
}
