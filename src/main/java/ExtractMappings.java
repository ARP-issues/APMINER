import utils.WRFile;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import utils.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// <package name.class name: return type method Name(parameters)>
public class ExtractMappings {
    private static API api = new API();
    private static WRFile wrFile = new WRFile();

    public  void init() throws IOException {

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JarTypeSolver(config.pathToJar)); // import android.jar sdk
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
    }
    /**
     * 获取包名
     * @param file
     * @return
     */
    public static String getPackageName(File file)
    {
        String filePath = file.toString();
        String[] firstSplit = filePath.split("\\\\");
        String packageName = "";
        for(int i=6;i<firstSplit.length-1;i++)
        {
            packageName+=firstSplit[i]+".";
        }
        api.setPackageName(packageName);
        return  null;
    }
    public static String getClassFullName(String path,String className)
    {
        if(path == null) return className+"Not Found！！！";
        String filePath = path;
        String[] firstSplit = filePath.split("\\\\");
        String fullName = "";
        for(int i=6;i<firstSplit.length-1;i++)
        {
            fullName+=firstSplit[i]+".";
        }
        fullName+=className;
        return  fullName;
    }
    /**
     * @param javaFile
     * @throws IOException
     */
    public  void inspectSingleJavaFile(File javaFile) throws IOException {
        init();
        CompilationUnit cu;
        FileInputStream in = new FileInputStream(javaFile);
        try {
            cu = StaticJavaParser.parse(in);
            getPackageName(javaFile);
            // For class Name
            ClassVisitor classVisitor = new ClassVisitor();
            classVisitor.visit(cu,null);
            //annotation
            MethodAnnotationVisitor annotationVisitor = new MethodAnnotationVisitor();
            annotationVisitor.visit(cu, null);
            //doc
            MethodDocVisitor docVisitor = new MethodDocVisitor();
            docVisitor.visit(cu,null);

        } 
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally {
            in.close();
        }

    }

    // For Java Doc
    private static class MethodDocVisitor extends VoidVisitorAdapter
    {
        @Override
        public void visit(MethodDeclaration n, Object arg) {
            boolean needPermission = false;
            String permissionString = "";
            String returnType = "";
            String methodName = "";
            ArrayList<String> paras = new ArrayList<>();
            String filePath = null;

            //JavaDoc
            if ((n.getJavadoc()!=null) && n.getJavadocComment().toString().contains("android.Manifest.permission"))
            {
                    String docCommentString = n.getJavadocComment().toString();

                    String regex = "android.Manifest.permission(.*?)\\}";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher m = pattern.matcher(docCommentString);
                    boolean flag = false;
                    while(m.find())
                    {
                        if(!flag){permissionString += "android.permission."+m.group(1);}
                        else{permissionString += ", android.permission."+m.group(1);}
                        flag = true;
                    }
System.out.println(permissionString);
                    needPermission = true;
                    filePath = "out\\"+config.sourceVersion+"docs.txt";
            }
            MethodAnnotationVisitor.formatPermissions(n, needPermission, permissionString, returnType, paras, filePath);

        }
    }

    /**
     * Simple visitor implementation for visiting MethodDeclaration nodes.
     */
    private static class MethodAnnotationVisitor extends VoidVisitorAdapter {

        @Override
        public void visit(MethodDeclaration n, Object arg) {
            boolean needPermission = false;
            String permission = new String();
            String returnType = new String();
            String methodName = new String();
            ArrayList<String> paras = new ArrayList<String>();
            String filePath = null;
            //Annotations
            if (n.getAnnotations() != null) {
                for (AnnotationExpr annotation : n.getAnnotations()) {
                    System.out.println(annotation.getName());
                    if(annotation.getName().toString().contains("RequiresPermission"))
                    {
                        needPermission = true;
                        permission = annotation.toString(); //@RequiresPermission(anyOf = { ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION })
                        filePath = "anyORall\\"+config.sourceVersion+"annotationsMappings.txt";
                    }

                 }
                if(needPermission) {
                    permission = extractPermissionByAnnotation(permission);
                    //
                    if(!permission.contains("==>"))
                    {
                        needPermission = false;
                    }
                }
            }
            formatPermissions(n, needPermission, permission, returnType, paras, filePath);
        }

        private static void formatPermissions(MethodDeclaration n, boolean needPermission, String permission, String returnType, ArrayList<String> paras, String filePath) {
            String methodName;
            if(needPermission) {
                System.out.println("**************Begin***********");
                for (Parameter p : n.getParameters())
                {
                    try {
                        paras.add(p.getType().resolve().describe());
                    }
                    catch (Exception e)
                    {
                        String test = getClassFullName(getFullQualityName(config.sourcePath,p.getType().toString()),
                                p.getType().toString());
                        paras.add(test);
                    }
                }
                try {
                    returnType = n.getType().resolve().describe();
                }
                catch (Exception e)
                {
                    String test = getClassFullName(getFullQualityName(config.sourcePath,n.getType().toString()),
                            n.getType().toString());
                    paras.add(test);
                }
                methodName = n.getNameAsString();

                System.out.println("##########Out##############");
                String single = "";
                single += api.getPackageName()+api.getClassName()+"."+methodName+"(";
                for(int i=0;i<paras.size();i++)
                {
                    if(i!=0){single+=","+paras.get(i);}
                    else{single+= paras.get(i);}
                }
                single+=")"+returnType;
                single +=" :: " + permission;
                wrFile.saveContent(single,filePath);
                System.out.println("##########Out End##############");

                System.out.println("**************over***********");
            }
        }

    }
    /**
     * @RequiresPermission(USE_FINGERPRINT)
     * @RequiresPermission(android.Manifest.permission.CALL_PHONE)
     * @RequiresPermission(anyOf = { ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION })
     * @RequiresPermission(allOf = { Manifest.permission.INTERACT_ACROSS_USERS_FULL, Manifest.permission.MANAGE_USERS })
     * android.allOf = { Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.UPDATE_DEVICE_STATS }
     */
    private static   String extractPermissionByAnnotation(String permission) {
        String returnPermission = "";
        try {
            // all of
            if (permission.contains("anyOf") || permission.contains("allOf")) {
                String[] first = null;
                if(permission.contains("anyOf")){first = permission.split("anyOf = \\{ ");}
                if(permission.contains("allOf")){first = permission.split("allOf = \\{ ");}
                String[] second = first[1].split(" }\\)");
                String[] permissions = second[0].split(", ");

                for (String s : permissions) {
                    if (s.contains("Manifest.permission")) {
                        s = "android." + s;
                    } else if (s.contains("android.Manifest.permission")) {
                    } else {
                        s = "android.Manifest.permission." + s;
                    }

                    if (returnPermission == "" && permission.contains("anyOf")) {
                        returnPermission +=  "AnyOf ==>"+s;
                    }
                    else if(returnPermission == "" && permission.contains("allOf"))
                    {
                        returnPermission +=  "AllOf ==>"+s;
                    }
                    else {
                        returnPermission += ", " + s;
                    }
                }
            } else if (permission.contains("RequiresPermission")){
                String[] first = permission.split("\\(");
                String[] second = first[1].split("\\)");
                returnPermission = second[0];
                if (returnPermission.contains("Manifest.permission")) {
                    returnPermission = "android." + returnPermission;
                } else if (returnPermission.contains("android.Manifest.permission.")) {
                } else {
                    returnPermission = "android.Manifest.permission." + returnPermission;
                }
            }
            else
            {
                System.out.println(permission);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return returnPermission;
    }

    private static String getFullQualityName(String filePath,String className) {
        File file = new File(filePath);
        File [] files = file.listFiles();

        for(File a:files){
            if(a.isDirectory()){
                getFullQualityName(a.getAbsolutePath()+"\\",className);
            }
            else if(a.isFile())
            {
                if(a.toString().contains(className))
                {
                    return a.toString();
                }
            }
        }
        return null;
    }


    private static class ClassVisitor extends VoidVisitorAdapter
    {

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Object arg) {
            api.setClassName(n.getNameAsString());

            System.out.println(n.getNameAsString());

        }
    }
}

