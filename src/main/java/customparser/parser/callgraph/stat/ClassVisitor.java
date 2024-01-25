package customparser.parser.callgraph.stat;

import customparser.parser.callgraph.dyn.Pair;
import customparser.parser.callgraph.stat.support.JarMetadata;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.MethodGen;

import java.util.HashSet;
import java.util.Set;

public class ClassVisitor extends EmptyVisitor {

    private JavaClass clazz;
    private ConstantPoolGen constants;
    private String classReferenceFormat;
    private final DynamicCallManager DCManager = new DynamicCallManager();

    private Set<Pair<String, String>> methodCalls = new HashSet<>();
    private final JarMetadata jarMetadata;

    public ClassVisitor(JavaClass jc, JarMetadata jarMetadata) {
        clazz = jc;
        constants = new ConstantPoolGen(clazz.getConstantPool());
        this.jarMetadata = jarMetadata;
        classReferenceFormat = "C:" + clazz.getClassName() + " %s";
    }

    public void visitJavaClass(JavaClass jc) {
        jc.getConstantPool().accept(this);
        Method[] methods = jc.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];

            DCManager.retrieveCalls(method,jc);
            DCManager.linkCalls(method);
            method.accept(this);
        }
    }

    public void visitConstantPool(ConstantPool constantPool) {
        for (int i = 0; i < constantPool.getLength(); i++) {
            Constant constant = constantPool.getConstant(i);
            if (constant == null)
                continue;
            if (constant.getTag() == 7) {
                String referencedClass =
                        constantPool.constantToString(constant);
            }
        }
    }

    public void visitMethod(Method method) {
        MethodGen mg = new MethodGen(method, clazz.getClassName(), constants);
        MethodVisitor visitor = new MethodVisitor(mg, clazz, jarMetadata);
        methodCalls.addAll(visitor.start());
    }

    public ClassVisitor start() {
        visitJavaClass(clazz);
        return this;
    }

    public Set<Pair<String, String>> methodCalls() {
        return this.methodCalls;
    }
}
