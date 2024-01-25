package customparser.parser.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FileParser {
    private final String filePath;

    public FileParser(String filePath) {
        this.filePath = filePath;
    }

    private String getFullyQualifiedName(String packageName, String className, String methodName) {
        if (!packageName.isEmpty()) {
            return packageName + "." + className + "." + methodName;
        } else {
            return className + "." + methodName;
        }
    }

    private Optional<MethodDeclaration> findMethodDeclaration(CompilationUnit compilationUnit, String methodName) {
        return compilationUnit.findAll(MethodDeclaration.class).stream()
                .filter(methodDeclaration -> methodDeclaration.getNameAsString().equals(methodName))
                .findFirst();
    }

    public List<String> getMethodNames() {
        List<String> methodNames = new ArrayList<>();

        try {
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(new File(filePath));
            CompilationUnit compilationUnit = parseResult.getResult().get();
            List<MethodDeclaration> methods = compilationUnit.findAll(MethodDeclaration.class);
            for(MethodDeclaration method: methods) {
                methodNames.add(method.getNameAsString());
            }
            return methodNames;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return methodNames;
    }

    public Map<String, List<String>> getMethodCalls() {
        Map<String, List<String>> methodCalls = new HashMap<>();
        try {
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(new File(this.filePath));

            if(parseResult.isSuccessful()) {
                CompilationUnit compilationUnit = parseResult.getResult().get();
                String packageName = compilationUnit.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
                List<MethodDeclaration> methods = compilationUnit.findAll(MethodDeclaration.class);

                for (MethodDeclaration method : methods) {
                    String methodName = method.getNameAsString();

                    List<String> calledMethods = new ArrayList<>();

                    method.findAll(MethodCallExpr.class).forEach(callExpr -> {
                        String calledMethodName = callExpr.getNameAsString();

                        Optional<MethodDeclaration> calledMethodDeclaration = findMethodDeclaration(compilationUnit, calledMethodName);
                        if (calledMethodDeclaration.isPresent()) {
                            // Method is in the current class
                            calledMethods.add(getFullyQualifiedName(packageName, compilationUnit.getPrimaryTypeName().orElse(""), calledMethodName));
                        } else {
                            // Method is external
//                            JavaSymbolSolver symbolSolver = new JavaSymbolSolver();
                            System.out.println(callExpr.getScope().getClass());
                        }
                    });

                    methodCalls.put(getFullyQualifiedName(packageName, compilationUnit.getPrimaryTypeName().orElse(""), methodName), calledMethods);
                }
            } else {
                System.out.println("Error parsing file: " + filePath);
                List<Problem> problems = parseResult.getProblems();
                for (Problem problem : problems) {
                    System.out.println("Parse error: " + problem.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return methodCalls;
    }
}
