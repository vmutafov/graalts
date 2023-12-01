open module com.vmutafov.graalts {
    requires org.graalvm.truffle;
    requires org.graalvm.js;

    provides com.oracle.truffle.api.provider.TruffleLanguageProvider with com.vmutafov.graalts.TypeScriptLanguageProvider;
}
