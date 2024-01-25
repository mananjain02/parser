package customparser.parser.callgraph.stat;

import customparser.parser.callgraph.dyn.Pair;
import customparser.parser.callgraph.stat.support.IgnoredConstants;
import customparser.parser.callgraph.stat.support.JarMetadata;
import customparser.parser.callgraph.stat.support.coverage.ColoredNode;
import org.apache.bcel.classfile.ClassParser;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GraphUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphUtils.class);

    private static final String LABEL = "label";
    private static final String STYLE = "style";
    private static final String FILLCOLOR = "fillcolor";
    private static final String FILLED = "filled";
    private static final String NODE_DELIMITER = "\"";

    public static Graph<ColoredNode, DefaultEdge> reachability(Graph<String, DefaultEdge> graph, String entrypoint, Optional<Integer> maybeMaximumDepth) {

        if (!graph.containsVertex(entrypoint)) {
            LOGGER.error("---> " + entrypoint + "<---");
            LOGGER.error("The graph doesn't contain the vertex specified as the entry point!");
            throw new InputMismatchException("graph doesn't contain vertex " + entrypoint);
        }

        if (maybeMaximumDepth.isPresent() && (maybeMaximumDepth.get() < 0)) {
            LOGGER.error("Depth " + maybeMaximumDepth.get() + " must be greater than 0!");
            System.exit(1);
        }

        LOGGER.info("Starting reachability at entry point: " + entrypoint);
        maybeMaximumDepth.ifPresent(d -> LOGGER.info("Traversing to depth " + d));

        Graph<ColoredNode, DefaultEdge> subgraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        int currentDepth = 0;

        Deque<String> reachable = new ArrayDeque<>();
        reachable.push(entrypoint);

        Map<String, ColoredNode> subgraphNodes = new HashMap<>();
        Set<String> seenBefore = new HashSet<>();
        Set<String> nextLevel = new HashSet<>();

        while (!reachable.isEmpty()) {

            /* Stop once we've surpassed maximum depth */
            if (maybeMaximumDepth.isPresent() && (maybeMaximumDepth.get() < currentDepth)) {
                break;
            }

            while (!reachable.isEmpty()) {
                /* Visit reachable node */
                String source = reachable.pop();
                ColoredNode sourceNode = subgraphNodes.containsKey(source) ? subgraphNodes.get(source) : new ColoredNode(source);

                /* Keep track of who we've visited */
                seenBefore.add(source);
                if (!subgraphNodes.containsKey(source)) {
                    subgraph.addVertex(sourceNode);
                    subgraphNodes.put(source, sourceNode);
                }

                /* Check if we can add deeper edges or not */
                if (maybeMaximumDepth.isPresent() && (maybeMaximumDepth.get() == currentDepth)) {
                    break;
                }

                graph.edgesOf(source).forEach(edge -> {
                    String target = graph.getEdgeTarget(edge);
                    ColoredNode targetNode = subgraphNodes.containsKey(target) ? subgraphNodes.get(target) : new ColoredNode(target);

                    if (!subgraphNodes.containsKey(target)) {
                        subgraphNodes.put(target, targetNode);
                        subgraph.addVertex(targetNode);
                    }

                    if (graph.containsEdge(source, target) && !subgraph.containsEdge(sourceNode, targetNode)) {
                        subgraph.addEdge(sourceNode, targetNode);
                    }

                    /* Have we visited this vertex before? */
                    if (!seenBefore.contains(target)) {
                        nextLevel.add(target);
                        seenBefore.add(target);
                    }

                });
            }

            currentDepth++;
            reachable.addAll(nextLevel);
            nextLevel.clear();
        }

        subgraphNodes.get(entrypoint).markEntryPoint();
        return subgraph;
    }

    public static Graph<ColoredNode, DefaultEdge> ancestry(Graph<String, DefaultEdge> graph, String entrypoint, int ancestryDepth) {

        if (!graph.containsVertex(entrypoint)) {
            LOGGER.error("---> " + entrypoint + "<---");
            LOGGER.error("The graph doesn't contain the vertex specified as the entry point!");
            throw new InputMismatchException("graph doesn't contain vertex " + entrypoint);
        }

        LOGGER.info("Starting ancestry at entry point: " + entrypoint);
        LOGGER.info("Traversing to depth " + ancestryDepth);

        /* Book-keeping */
        Graph<ColoredNode, DefaultEdge> ancestry = new DefaultDirectedGraph<>(DefaultEdge.class);
        Map<String, ColoredNode> nodeMap = new HashMap<>();
        Deque<String> parentsToInspect = new ArrayDeque<>();
        Set<String> seenBefore = new HashSet<>();
        Set<String> nextLevel = new HashSet<>();

        /* Add root node to ancestry graph */
        ColoredNode root = new ColoredNode(entrypoint);
        ancestry.addVertex(root);
        nodeMap.put(entrypoint, root);
        parentsToInspect.push(entrypoint);

        int currentDepth = 0;
        while (!parentsToInspect.isEmpty()) {

            if (ancestryDepth < currentDepth) {
                break;
            }

            /* Loop over all nodes that we haven't yet seen yet and are reachable at depth "currentDepth" */
            while (!parentsToInspect.isEmpty()) {

                /* Fetch next node */
                String child = parentsToInspect.pop();
                ColoredNode childNode = nodeMap.containsKey(child) ? nodeMap.get(child) : new ColoredNode(child);

                /* Keep track of who we've seen before */
                seenBefore.add(child);
                if (!nodeMap.containsKey(child)) {
                    ancestry.addVertex(childNode);
                    nodeMap.put(child, childNode);
                }

                graph.incomingEdgesOf(child).forEach(incomingEdge -> {
                    String parent = graph.getEdgeSource(incomingEdge);
                    ColoredNode parentNode = nodeMap.containsKey(parent) ? nodeMap.get(parent) : new ColoredNode(parent);

                    if (!nodeMap.containsKey(parent)) {
                        nodeMap.put(parent, parentNode);
                        ancestry.addVertex(parentNode);
                    }

                    ancestry.addEdge(parentNode, childNode);

                    /* Have we visited this vertex before? */
                    if (!seenBefore.contains(parent)) {
                        nextLevel.add(parent);
                        seenBefore.add(parent);
                    }
                });
            }

            currentDepth++;
            parentsToInspect.addAll(nextLevel);
            nextLevel.clear();
        }

        nodeMap.get(entrypoint).markEntryPoint();
        return ancestry;
    }

    public static <T> void writeGraph(Graph<T, DefaultEdge> graph, DOTExporter<T, DefaultEdge> exporter, Optional<String> maybeOutputName) {
        LOGGER.info("Attempting to store callgraph...");

        if (maybeOutputName.isEmpty()) {
            LOGGER.error("No output name specified!");
            return;
        }

        /* Write to .dot file in output directory */
        String path = JCallGraph.OUTPUT_DIRECTORY + maybeOutputName.get();
        try {
            Writer writer = new FileWriter(path);
            exporter.exportGraph(graph, writer);
            LOGGER.info("Graph written to " + path + "!");
        } catch (IOException e) {
            LOGGER.error("Unable to write callgraph to " + path);
        }
    }

    public static Graph<String, DefaultEdge> staticCallgraph(List<Pair<String, File>> jars) throws InputMismatchException {
        LOGGER.info("Beginning callgraph analysis...");

        /* Load JAR URLs */
        List<URL> urls = new ArrayList<>();
        try {
            for (Pair<String, File> pair : jars) {
                URL url = new URL("jar:file:" + pair.first + "!/");
                urls.add(url);
            }
        } catch (MalformedURLException e) {
            LOGGER.error("Error loading URLs: " + e.getMessage());
            throw new InputMismatchException("Couldn't load provided JARs");
        }

        if (urls.isEmpty()) {
            LOGGER.error("No URLs to scan!");
            throw new InputMismatchException("There are no URLs to scan!");
        }

        /* Setup infrastructure for analysis */
        URLClassLoader cl = URLClassLoader.newInstance(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());
        Reflections reflections = new Reflections(cl, new SubTypesScanner(false));
        JarMetadata jarMetadata = new JarMetadata(cl, reflections);

        /* Store method calls (caller -> receiver) */
        Map<String, Set<String>> calls = new HashMap<>();

        for (Pair<String, File> pair : jars) {
            String jarPath = pair.first;
            File file = pair.second;

            try (JarFile jarFile = new JarFile(file)) {
                LOGGER.info("Analyzing: " + jarFile.getName());
                Stream<JarEntry> entries = enumerationAsStream(jarFile.entries());

                Function<ClassParser, ClassVisitor> getClassVisitor =
                        (ClassParser cp) -> {
                            try {
                                return new ClassVisitor(cp.parse(), jarMetadata);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        };

                /* Analyze each jar entry to find callgraph */
                entries.flatMap(e -> {
                    if (e.isDirectory() || !e.getName().endsWith(".class"))
                        return Stream.of();

                    /* Ignore specified JARs */
                    if (shouldIgnoreEntry(e.getName().replace("/", "."))) {
                        return Stream.of();
                    } else {
                        LOGGER.info("Inspecting " + e.getName());
                    }

                    ClassParser cp = new ClassParser(jarPath, e.getName());
                    return getClassVisitor.apply(cp).start().methodCalls().stream();
                }).forEach(p -> {
                    /* Create edges between nodes */
                    calls.putIfAbsent((p.first), new HashSet<>());
                    calls.get(p.first).add(p.second);
                });

            } catch (IOException e) {
                LOGGER.error("Error when analyzing JAR \"" + jarPath + "\": + e.getMessage()");
                e.printStackTrace();
            }
        }

        /* Convert calls into a graph */
        Graph<String, DefaultEdge> graph = buildGraph(calls);

        /* Prune bridge methods from graph */
        jarMetadata.getBridgeMethods().forEach(bridgeMethod -> {

            /* Fetch the bridge method and make sure it has exactly one outgoing edge */
            String bridgeNode = formatNode(bridgeMethod);
            Optional<DefaultEdge> maybeEdge = graph.outgoingEdgesOf(bridgeNode).stream().findFirst();

            if (graph.outDegreeOf(bridgeNode) != 1 || maybeEdge.isEmpty()) {

                graph.outgoingEdgesOf(bridgeNode).stream().forEach(e -> {
                    LOGGER.error("\t" + graph.getEdgeSource(e) + " -> " + graph.getEdgeTarget(e));
                });
                LOGGER.error("Found a bridge method that doesn't have exactly 1 outgoing edge: " + bridgeMethod + " : " + graph.outDegreeOf(bridgeNode));
                System.exit(1);
            }

            /* Fetch the bridge method's target */
            String bridgeTarget = graph.getEdgeTarget(maybeEdge.get());

            /* Redirect all edges from the bridge method to its target */
            graph.incomingEdgesOf(bridgeNode).forEach(edge -> {
                String sourceNode = graph.getEdgeSource(edge);
                graph.addEdge(sourceNode, bridgeTarget);
            });

            /* Remove the bridge method from the graph */
            graph.removeVertex(bridgeNode);
        });

        return graph;
    }

    private static Graph<String, DefaultEdge> buildGraph(Map<String, Set<String>> methodCalls) throws InputMismatchException {
        if (methodCalls.keySet().isEmpty()) {
            throw new InputMismatchException("There is no call graph to look at!");
        }

        /* initialize the graph */
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        /* fill the graph with vertices and edges */
        methodCalls.keySet().forEach(source -> {
            String sourceNode = formatNode(source);
            putIfAbsent(graph, sourceNode);

            methodCalls.get(source).forEach(destination -> {
                String destinationNode = formatNode(destination);
                putIfAbsent(graph, destinationNode);
                graph.addEdge(sourceNode, destinationNode);
            });
        });

        return graph;
    }

    private static void putIfAbsent(Graph<String, DefaultEdge> graph, String vertex) {
        if (!graph.containsVertex(vertex)) {
            graph.addVertex(vertex);
        }
    }

    private static boolean shouldIgnoreEntry(String entry) {
        return IgnoredConstants.IGNORED_CALLING_PACKAGES.stream()
                .anyMatch(entry::startsWith);
    }

    public static String formatNode(String node) {
        return NODE_DELIMITER + node + NODE_DELIMITER;
    }

    public static <T> Stream<T> enumerationAsStream(Enumeration<T> e) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        new Iterator<T>() {
                            public T next() {
                                return e.nextElement();
                            }

                            public boolean hasNext() {
                                return e.hasMoreElements();
                            }
                        },
                        Spliterator.ORDERED), false);
    }

    public static DOTExporter<String , DefaultEdge> defaultExporter() {
        DOTExporter<String , DefaultEdge> exporter = new DOTExporter<>(id -> id);
        exporter.setVertexAttributeProvider((v) -> {
            Map<String, Attribute> map = new LinkedHashMap<>();
            map.put(LABEL, DefaultAttribute.createAttribute(v));
            return map;
        });
        return exporter;
    }

    public static DOTExporter<ColoredNode, DefaultEdge> coloredExporter() {
        DOTExporter<ColoredNode , DefaultEdge> exporter = new DOTExporter<>(ColoredNode::getLabel);
        exporter.setVertexAttributeProvider((v) -> {
            Map<String, Attribute> map = new LinkedHashMap<>();
            map.put(LABEL, DefaultAttribute.createAttribute(v.getLabel()));
            map.put(STYLE, DefaultAttribute.createAttribute(FILLED));
            map.put(FILLCOLOR, DefaultAttribute.createAttribute(v.getColor()));
            return map;
        });
        return exporter;
    }
//
//    public static Graph<String, DefaultEdge> staticCallgraph(List<Pair<String, File>> jars) {
//    }
}
