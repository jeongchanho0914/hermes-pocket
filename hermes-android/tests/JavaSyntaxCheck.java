import java.io.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
public class JavaSyntaxCheck {
 public static void main(String[] args) throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  if(compiler==null)throw new IllegalStateException("JDK compiler required");
  DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
  try(StandardJavaFileManager fm=compiler.getStandardFileManager(diagnostics,null,null)){
   Iterable<? extends JavaFileObject> units=fm.getJavaFileObjectsFromStrings(Arrays.asList(args));
   JavacTask task=(JavacTask)compiler.getTask(null,fm,diagnostics,Arrays.asList("-proc:none","--release","8"),null,units);
   for(Object ignored:task.parse()){}
   boolean failed=false;
   for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){failed=true;System.err.println(d);}
   if(failed)System.exit(1);
   System.out.println("PASS: "+args.length+" Java files parsed. Android symbol resolution and APK compilation were NOT performed.");
  }
 }
}
