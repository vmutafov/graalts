package com.vmutafov.graalts;

import com.oracle.js.parser.ir.Module;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.runtime.JSException;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.objects.DefaultESModuleLoader;
import com.oracle.truffle.js.runtime.objects.JSModuleData;
import com.oracle.truffle.js.runtime.objects.JSModuleRecord;
import com.oracle.truffle.js.runtime.objects.ScriptOrModule;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;

public class TSModuleLoader extends DefaultESModuleLoader {
  protected TSModuleLoader(JSRealm realm) {
    super(realm);
  }

  @Override
  public JSModuleRecord resolveImportedModule(ScriptOrModule referrer, Module.ModuleRequest moduleRequest) {
    try {
      return super.resolveImportedModule(referrer, moduleRequest);
    } catch (JSException e1) {
      var originalSpecifier = moduleRequest.getSpecifier().toJavaStringUncached();
      var specifier = originalSpecifier + ".ts";
      var specifierTS = TruffleString.fromJavaStringUncached(specifier, TruffleString.Encoding.UTF_8);
      var tsModuleRequest = Module.ModuleRequest.create(specifierTS, moduleRequest.getAssertions());

      try {
        return super.resolveImportedModule(referrer, tsModuleRequest);
      } catch (JSException e2) {
        specifier = originalSpecifier + "/index.ts";
        specifierTS = TruffleString.fromJavaStringUncached(specifier, TruffleString.Encoding.UTF_8);
        tsModuleRequest = Module.ModuleRequest.create(specifierTS, moduleRequest.getAssertions());
        return super.resolveImportedModule(referrer, tsModuleRequest);
      }
    }
  }
}
