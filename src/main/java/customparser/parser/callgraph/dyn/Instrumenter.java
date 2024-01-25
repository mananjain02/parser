package customparser.parser.callgraph.dyn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Instrumenter implements ClassFileTransformer {

    static List<Pattern> pkgIncl = new ArrayList<>();
    static List<Pattern> pkgExcl = new ArrayList<>();

    public static void premain(String argument, Instrumentation instrumentation) {

        // incl=com.foo.*,gr.bar.foo;excl=com.bar.foo.*

        if (argument == null) {
            err("Missing configuration argument");
            return;
        }

        err("Argument is: " + argument);

        String[] tokens = argument.split(";");

        if (tokens.length < 1) {
            err("Missing delimiter ;");
            return;
        }

        for (String token : tokens) {
            String[] args = token.split("=");
            if (args.length < 2) {
                err("Missing argument delimiter =:" + token);
                return;
            }

            String argtype = args[0];

            if (!argtype.equals("incl") && !argtype.equals("excl")) {
                err("Wrong argument: " + argtype);
                return;
            }

            String[] patterns = args[1].split(",");

            for (String pattern : patterns) {
                Pattern p = null;
                err("Compiling " + argtype + " pattern:" + pattern + "$");
                try {
                    p = Pattern.compile(pattern + "$");
                } catch (PatternSyntaxException pse) {
                    err("pattern: " + pattern + " not valid, ignoring");
                }
                if (argtype.equals("incl"))
                    pkgIncl.add(p);
                else
                    pkgExcl.add(p);
            }
        }

        instrumentation.addTransformer(new Instrumenter());
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> clazz,
                            java.security.ProtectionDomain domain, byte[] bytes) {
        boolean enhanceClass = false;

        String name = className.replace("/", ".");

        for (Pattern p : pkgIncl) {
            Matcher m = p.matcher(name);
            if (m.matches()) {
                enhanceClass = true;
                break;
            }
        }

        for (Pattern p : pkgExcl) {
            Matcher m = p.matcher(name);
            if (m.matches()) {
                err("Skipping class: " + name);
                enhanceClass = false;
                break;
            }
        }

        if (enhanceClass) {
            return enhanceClass(className, bytes);
        } else {
            return bytes;
        }
    }

    private byte[] enhanceClass(String name, byte[] b) {
        ClassPool pool = ClassPool.getDefault();
        CtClass clazz = null;
        try {
            clazz = pool.makeClass(new ByteArrayInputStream(b));
            if (!clazz.isInterface()) {
                err("Enhancing class: " + name);
                CtBehavior[] methods = clazz.getDeclaredBehaviors();
                for (int i = 0; i < methods.length; i++) {
                    if (!methods[i].isEmpty()) {
                        enhanceMethod(methods[i], clazz.getName());
                    }
                }
                b = clazz.toBytecode();
            }
        } catch (CannotCompileException e) {
            e.printStackTrace();
            err("Cannot compile: " + e.getMessage());
        } catch (NotFoundException e) {
            e.printStackTrace();
            err("Cannot find: " + e.getMessage());
        } catch (IOException e) {
            err("Error writing: " + e.getMessage());
        } finally {
            if (clazz != null) {
                clazz.detach();
            }
        }
        return b;
    }

    private void enhanceMethod(CtBehavior method, String className)
            throws NotFoundException, CannotCompileException {
        String name = className.substring(className.lastIndexOf('.') + 1, className.length());
        String methodName = method.getName();

        if (method.getName().equals(name))
            methodName = "<init>";

        method.insertBefore("gr.gousiosg.javacg.dyn.MethodStack.push(\"" + className
                + ":" + methodName + "\");");
        method.insertAfter("gr.gousiosg.javacg.dyn.MethodStack.pop();");
    }

    private static void err(String msg) {
        //System.err.println("[JAVACG-DYN] " + msg);
    }
}
