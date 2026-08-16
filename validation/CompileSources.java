import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class CompileSources {
    public static void main(String[] args) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("System Java compiler is unavailable");
        int exit = compiler.run(null, System.out, System.err, args);
        if (exit != 0) throw new IllegalStateException("Compilation failed with exit code " + exit);
    }
}
