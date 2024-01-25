package customparser.parser.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectParser {
    private final String folderPath;
    private final ArrayList<String> filePaths;
    private final ArrayList<CompilationUnit> compilationUnits;

    ProjectParser(String folderPath) {
        this.folderPath = folderPath;
        this.filePaths = listJavaFilePathsRecursively(this.folderPath);
        this.compilationUnits = getCompilationUnits(this.filePaths);
    }

    private String getFullyQualifiedName(String packageName, String className, String methodName) {
        if (!packageName.isEmpty()) {
            return packageName + "." + className + "." + methodName;
        } else {
            return className + "." + methodName;
        }
    }

    private static ArrayList<String> listJavaFilePathsRecursively(String folderPath) {
        ArrayList<String> javaFilePaths = new ArrayList<>();
        File folder = new File(folderPath);

        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFilePaths.addAll(listJavaFilePathsRecursively(file.getPath()));
                    } else {
                        String filePath = file.getAbsolutePath();
                        if (filePath.endsWith(".java")) {  // Filter for ".java" files
                            javaFilePaths.add(filePath);
                        }
                    }
                }
            }
        }

        return javaFilePaths;
    }

    private ArrayList<CompilationUnit> getCompilationUnits(ArrayList<String> filePaths) {
        ArrayList<CompilationUnit> compilationUnitArrayList = new ArrayList<>();
        JavaParser parser = new JavaParser();
        for(String filePath: filePaths) {
            try {
                ParseResult<CompilationUnit> result = parser.parse(new File(filePath));
                if(result.isSuccessful()) {
                    compilationUnitArrayList.add(result.getResult().get());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return compilationUnitArrayList;
    }

    public Map<String, List<String>> getAllMethodCalls() {
        Map<String, List<String>> methodCalls = new HashMap<>();

        for(int i=0; i<this.compilationUnits.size(); i++) {
            String packageName = this.compilationUnits.get(i).getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
            List<MethodDeclaration> methods = this.compilationUnits.get(i).findAll(MethodDeclaration.class);

            for(MethodDeclaration method: methods) {
                String methodName = method.getNameAsString();


            }
        }

        return methodCalls;
    }
}
