package com.vmutafov.graalts;

import com.oracle.truffle.api.TruffleFile;

import java.io.IOException;
import java.nio.charset.Charset;

public final class TSFileTypeDetector implements TruffleFile.FileTypeDetector {

  public String findMimeType(TruffleFile file) {
    String fileName = file.getName();
    if (fileName != null) {
      if (fileName.endsWith(".ts")) {
        return "application/typescript";
      }

      if (fileName.endsWith(".mts")) {
        return "application/typescript+module";
      }
    }

    return null;
  }

  public Charset findEncoding(TruffleFile file) {
    return null;
  }
}

