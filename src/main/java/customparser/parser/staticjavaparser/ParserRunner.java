package customparser.parser.staticjavaparser;

public class ParserRunner {
    public static final String EMPLOY_FILEPATH = "/Users/mananjain/Desktop/java_proj/employ/src/main/java/com/bny/employ/Employ.java";
    public static final String EXTRACT_FILEPATH = "/Users/mananjain/Desktop/java_proj/employ/src/main/java/com/bny/employ/Extract.java";
    public static final String PROJECT_PATH = "/Users/mananjain/Desktop/java_proj/employ/src/main/java/com/bny/employ";
    public static void main(String[] args) {
//        FileParser fileParser = new FileParser(EXTRACT_FILEPATH);
//        System.out.println(fileParser.getMethodCalls());

        ProjectParser parser = new ProjectParser(PROJECT_PATH);
        System.out.println(parser.getAllMethodCalls());
    }
}
