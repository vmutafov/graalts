package com.vmutafov.graalts;

import com.oracle.truffle.js.runtime.JSRealm;

import java.lang.reflect.Field;

public class JSRealmPatcher {
  public static void setTSContext(JSRealm jsRealm, TSContext newContext) {
    try {
      Field contextField = JSRealm.class.getDeclaredField("context");
      contextField.setAccessible(true);
      Object context = contextField.get(jsRealm);
      if (!(context instanceof TSContext)) {
        contextField.set(jsRealm, newContext);
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setTSModuleLoader(JSRealm jsRealm, TSModuleLoader newModuleLoader) {
    try {
      Field moduleLoaderField = JSRealm.class.getDeclaredField("moduleLoader");
      moduleLoaderField.setAccessible(true);
      Object moduleLoader = moduleLoaderField.get(jsRealm);
      if (!(moduleLoader instanceof TSModuleLoader)) {
        moduleLoaderField.set(jsRealm, newModuleLoader);
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
