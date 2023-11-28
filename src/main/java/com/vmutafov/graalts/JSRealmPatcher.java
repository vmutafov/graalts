package com.vmutafov.graalts;

import com.oracle.truffle.js.runtime.JSRealm;

import java.lang.reflect.Field;

public class JSRealmPatcher {

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
