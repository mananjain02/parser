package customparser.parser.callgraph.stat.support;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class JarMetadata {

    private static final Logger LOGGER = LoggerFactory.getLogger(JarMetadata.class);

    private final URLClassLoader cl;
    private final Reflections reflections;
    private final ClassHierarchyInspector inspector = new ClassHierarchyInspector();
    private final Set<String> bridgeMethods = new HashSet<>();

    /**
     * Wrapper class used for reflection on class hierarchies
     * @param cl the {@link ClassLoader} containing all provided jars
     * @param reflections the {@link Reflections} using the {@link cl} classloader
     */
    public JarMetadata(URLClassLoader cl, Reflections reflections) {
        this.cl = cl;
        this.reflections = reflections;
    }

    public Optional<Class<?>> getClass(String qualifiedName) {
        qualifiedName = qualifiedName.replace("/", ".");
        try {
            return Optional.of(
                    Class.forName(qualifiedName, false, cl)
            );
        } catch (NoClassDefFoundError | Exception e) {
            LOGGER.error("Unable to load class: " + qualifiedName);
            return Optional.empty();
        }
    }

    public Reflections getReflections() {
        return this.reflections;
    }

    public ClassHierarchyInspector getInspector() {
        return inspector;
    }

    public void addBridgeMethod(String bridgeMethodSignature) {
        bridgeMethods.add(bridgeMethodSignature);
    }

    public Set<String> getBridgeMethods() {
        return bridgeMethods;
    }
}
