package com.vmutafov.graalts.test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class TypeScriptLanguageTest {

  @BeforeClass
  public static void beforeTests() {
    System.setProperty("polyglotimpl.DisableClassPathIsolation", "true");
    TypeScriptLanguageTest.class.getClassLoader().setClassAssertionStatus(TypeScriptLanguageTest.class.getName(), false);
  }

  @Test
  public void testSimpleTypeScript() {
    try (var context = Context.create()) {
      var res = context.eval("ts", "const num: number = 123; num;");
      assertEquals(123, res.asInt());
    }
  }

  @Test
  public void testTypeScriptExportedMember() throws IOException {
    try (var context = Context.newBuilder()
        .allowExperimentalOptions(true)
        .option("js.esm-eval-returns-exports", "true")
        .build()
    ) {
      var source = Source.newBuilder("ts", """
          export function getNumber(): number {
            return 123;
          }
          """, "testTypeScriptExportedMember.ts").mimeType("application/typescript+module").build();
      var res = context.eval(source).getMember("getNumber").execute();
      assertEquals(123, res.asInt());
    }
  }

  @Test
  public void testTypeScriptModules() throws IOException {
    var index = """
        import { message } from "./lib";
        export const data = message;
        """;

    var lib = """
        export const message: string = "test data";
        """;

    var dirPath = Files.createTempDirectory("graalts-test");
    var indexPath = dirPath.resolve("index.ts");
    var libPath = dirPath.resolve("lib.ts");
    Files.writeString(indexPath, index);
    Files.writeString(libPath, lib);

    try (var context = Context.newBuilder()
        .allowAllAccess(true)
        .option("js.esm-eval-returns-exports", "true")
        .build()
    ) {
      var res = context.eval(
          Source.newBuilder("ts", indexPath.toFile()).mimeType("application/typescript+module").build()
      ).getMember("data");
      assertEquals("test data", res.asString());
    }

    Files.deleteIfExists(indexPath);
    Files.deleteIfExists(libPath);
    Files.deleteIfExists(dirPath);
  }
}
