package customparser.parser.callgraph.stat;

import customparser.parser.callgraph.dyn.Pair;
import customparser.parser.callgraph.stat.support.ClassHierarchyInspector;
import customparser.parser.callgraph.stat.support.JarMetadata;
import customparser.parser.callgraph.stat.support.MethodSignatureUtil;
import org.apache.bcel.generic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.bcel.classfile.JavaClass;

import static customparser.parser.callgraph.stat.support.IgnoredConstants.IGNORED_METHOD_NAMES;

public class MethodVisitor extends EmptyVisitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodVisitor.class);
    private static final Boolean EXPAND = true;
    private static final Boolean DONT_EXPAND = false;

    JavaClass visitedClass;
    private MethodGen mg;
    private ConstantPoolGen cp;
    private String format;

    // methodCalls helps us build the (caller -> receiver) call graph
    private Set<Pair<String, String>> methodCalls = new HashSet<>();
    private final JarMetadata jarMetadata;

    public MethodVisitor(MethodGen m, JavaClass jc, JarMetadata jarMetadata) {
        this.jarMetadata = jarMetadata;
        visitedClass = jc;
        mg = m;
        cp = mg.getConstantPool();
        format = "%s";
    }

    private String argumentList(Type[] arguments) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(arguments[i].toString());
        }
        return sb.toString();
    }

    public Set<Pair<String, String>> start() {
        if (mg.isAbstract() || mg.isNative())
            return Collections.emptySet();

        for (InstructionHandle ih = mg.getInstructionList().getStart();
             ih != null; ih = ih.getNext()) {
            Instruction i = ih.getInstruction();

            if (!visitInstruction(i))
                i.accept(this);
        }
        return methodCalls;
    }

    private boolean visitInstruction(Instruction i) {
        short opcode = i.getOpcode();
        return ((InstructionConst.getInstruction(opcode) != null)
                && !(i instanceof ConstantPushInstruction)
                && !(i instanceof ReturnInstruction));
    }

    @Override
    public void visitINVOKEVIRTUAL(INVOKEVIRTUAL i) {
        visit(i, EXPAND);
    }

    @Override
    public void visitINVOKEINTERFACE(INVOKEINTERFACE i) {
        visit(i, EXPAND);
    }

    @Override
    public void visitINVOKESPECIAL(INVOKESPECIAL i) {
        visit(i, DONT_EXPAND);
    }

    @Override
    public void visitINVOKESTATIC(INVOKESTATIC i) {
        visit(i, DONT_EXPAND);
    }

    @Override
    public void visitINVOKEDYNAMIC(INVOKEDYNAMIC i) {
        visit(i, EXPAND);
    }

    private void visit(InvokeInstruction i, Boolean shouldExpand) {
        /* caller method info */
        String callerClassType = visitedClass.getClassName();
        String callerMethodName = mg.getName();
        Type[] callerArgumentTypes = mg.getArgumentTypes();
        Type callerReturnType = mg.getReturnType();
        String callerSignature = MethodSignatureUtil.fullyQualifiedMethodSignature(callerClassType, callerMethodName, callerArgumentTypes, callerReturnType);

        /* receiver method info */
        String receiverClassType = String.format(format, i.getReferenceType(cp));
        String receiverMethodName = i.getMethodName(cp);
        Type[] receiverArgumentTypes = i.getArgumentTypes(cp);
        Type receiverReturnType = i.getReturnType(cp);
        String receiverSignature = MethodSignatureUtil.fullyQualifiedMethodSignature(receiverClassType, receiverMethodName, receiverArgumentTypes, receiverReturnType);

        /* Record initial method call */
        methodCalls.add(createEdge(callerSignature, receiverSignature));

        if (shouldExpand && !IGNORED_METHOD_NAMES.contains(receiverMethodName)) {
            Optional<Class<?>> maybeReceiverType = jarMetadata.getClass(receiverClassType);
            if (maybeReceiverType.isEmpty()) {
                LOGGER.error("Couldn't find Receiver class: " + receiverClassType);
                return;
            }

            Optional<Class<?>> maybeCallerType = jarMetadata.getClass(callerClassType);
            if (maybeCallerType.isEmpty()) {
                LOGGER.error("Couldn't find Caller class: " + callerClassType);
                return;
            }

            Optional<Method> maybeCallingMethod = jarMetadata.getInspector()
                    .getTopLevelSignature(
                            maybeCallerType.get(),
                            MethodSignatureUtil.namedMethodSignature(callerMethodName, callerArgumentTypes, callerReturnType)
                    );

            if (maybeCallingMethod.isEmpty()) {
                LOGGER.error("Couldn't find top level signature for " + callerSignature);
                return;
            }

            if (maybeCallingMethod.get().isBridge()) {
                jarMetadata.addBridgeMethod(receiverSignature);
            } else {
                /* Expand to all possible receiver class types */
                expand(maybeReceiverType.get(), receiverMethodName, receiverArgumentTypes, receiverReturnType, callerSignature);
            }
        }

    }

    private void expand(Class<?> receiverType, String receiverMethodName, Type[] receiverArgumentTypes, Type receiverReturnType, String callerSignature) {
        ClassHierarchyInspector inspector = jarMetadata.getInspector();
        LOGGER.info("\tExpanding to subtypes of " + receiverType.getName());
        jarMetadata.getReflections().getSubTypesOf(receiverType)
                .stream()
                .map(subtype ->
                        inspector.getTopLevelSignature(
                                subtype,
                                MethodSignatureUtil.namedMethodSignature(receiverMethodName, receiverArgumentTypes, receiverReturnType)
                        )
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(MethodSignatureUtil::fullyQualifiedMethodSignature)
                /* Record expanded method call */
                .forEach(toSubtypeSignature -> methodCalls.add(createEdge(callerSignature, toSubtypeSignature)));

    }

    public Pair<String, String> createEdge(String from, String to) {
        return new Pair<>(from, to);
    }
}
