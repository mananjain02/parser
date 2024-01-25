package customparser.parser.callgraph.stat;

import customparser.parser.callgraph.stat.support.Arguments;
import customparser.parser.callgraph.stat.support.coverage.ColoredNode;
import customparser.parser.callgraph.stat.support.coverage.CoverageStatistics;
import customparser.parser.callgraph.stat.support.coverage.JacocoCoverage;
import jakarta.xml.bind.JAXBException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.InputMismatchException;
import java.util.Optional;

public class JCallGraph {

    private static final Logger LOGGER = LoggerFactory.getLogger(JCallGraph.class);

    public static final String OUTPUT_DIRECTORY = "./output/";
    private static final String REACHABILITY = "reachability";
    private static final String COVERAGE = "coverage";
    private static final String ANCESTRY = "ancestry";
    private static final String DELIMITER = "-";
    private static final String DOT_SUFFIX = ".dot";
    private static final String CSV_SUFFIX = ".csv";

    public static void main(String[] args) {
        try {
            LOGGER.info("Starting java-cg!");
            Arguments arguments = new Arguments(args);
            Graph<String, DefaultEdge> graph = GraphUtils.staticCallgraph(arguments.getJars());
            JacocoCoverage jacocoCoverage = new JacocoCoverage(arguments.maybeCoverage());

            /* Should we store the graph in a file? */
            if (arguments.maybeOutput().isPresent()) {
                GraphUtils.writeGraph(graph, GraphUtils.defaultExporter(), arguments.maybeOutput().map(JCallGraph::asDot));
            }

            /* Should we compute reachability from the entry point? */
            if (arguments.maybeEntryPoint().isPresent()) {
                inspectReachability(graph, arguments, jacocoCoverage, arguments.maybeEntryPoint().get());
            }

            /* Should we compute ancestry from the entry point? */
            if (arguments.maybeAncestry().isPresent()) {
                inspectAncestry(graph, arguments, jacocoCoverage, arguments.maybeEntryPoint().get(), arguments.maybeAncestry().get());
            }

        } catch (InputMismatchException e) {
            LOGGER.error("Unable to load callgraph: " + e.getMessage());
            System.exit(1);
        } catch (ParserConfigurationException | SAXException | JAXBException | IOException e) {
            LOGGER.error("Error fetching Jacoco coverage");
            System.exit(1);
        }

        LOGGER.info("java-cg is finished! Enjoy!");
    }

    public static void inspectReachability(Graph<String, DefaultEdge> graph, Arguments arguments, JacocoCoverage jacocoCoverage, String entryPoint) {
        /* Fetch reachability */
        Graph<ColoredNode, DefaultEdge> reachability = GraphUtils.reachability(graph, entryPoint, arguments.maybeDepth());

        /* Apply coverage */
        jacocoCoverage.applyCoverage(reachability);

        /* Should we write the graph to a file? */
        Optional<String> outputName = arguments.maybeOutput().isPresent()
                ? Optional.of(arguments.maybeOutput().get() + DELIMITER + REACHABILITY)
                : Optional.empty();

        /* Attach depth to name if present */
        outputName = outputName.map(name -> {
            if (arguments.maybeDepth().isPresent()) {
                return name + DELIMITER + arguments.maybeDepth().get();
            } else {
                return name;
            }
        });

        /* Store reachability in file? */
        if (outputName.isPresent()) {
            GraphUtils.writeGraph(reachability, GraphUtils.coloredExporter(), outputName.map(JCallGraph::asDot));
        }

        /* Analyze reachability coverage? */
        if (jacocoCoverage.hasCoverage()) {
            CoverageStatistics.analyze(reachability, outputName.map(name -> asCsv(name + DELIMITER + COVERAGE)));
        }
    }

    public static void inspectAncestry(Graph<String, DefaultEdge> graph, Arguments arguments, JacocoCoverage jacocoCoverage, String entryPoint, int ancestryDepth) {
        Graph<ColoredNode, DefaultEdge> ancestry = GraphUtils.ancestry(graph, entryPoint, ancestryDepth);
        jacocoCoverage.applyCoverage(ancestry);

        /* Should we store the ancestry in a file? */
        if (arguments.maybeOutput().isPresent()) {
            String subgraphOutputName = arguments.maybeOutput().get() + DELIMITER + ANCESTRY + DELIMITER + ancestryDepth;
            GraphUtils.writeGraph(ancestry, GraphUtils.coloredExporter(), Optional.of(asDot(subgraphOutputName)));
        }
    }

    private static String asDot(String name) {
        return name.endsWith(DOT_SUFFIX) ? name : (name + DOT_SUFFIX);
    }

    private static String asCsv(String name) {
        return name.endsWith(CSV_SUFFIX) ? name : (name + CSV_SUFFIX);
    }

}
