import utils.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Main {

    private static ArrayList<String> fileName  = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        run();
        return;
    }
    private static void run() throws IOException {
        ExtractMappings extractor = new ExtractMappings();
        for(int v=23;v<=29;v++) {//API 23->29
            datainit(v);
            fileName.clear();
            getAllFileName(config.sourcePath, fileName);

            for (int i = 0; i < fileName.size(); i++) {
                if (fileName.get(i).endsWith(".java")) {
                    extractor.inspectSingleJavaFile(new File(fileName.get(i)));
                }
            }
        }
        return;
    }
    private static   void datainit(int version) {

        config.sourceVersion = "sources-"+version+"_r01";
        config.sourcePath ="D:\\Android\\AndroidPlatform\\source\\"+ config.sourceVersion;
        config.pathToJar ="C:\\Users\\wyb\\AppData\\Local\\Android\\Sdk\\platforms\\android-"+version+"\\android.jar";
    }

    public static   void getAllFileName(String path, ArrayList<String> listFileName){
        File file = new File(path);
        File [] files = file.listFiles();
        String [] names = file.list();

        for(File a:files){
            if(a.isDirectory()){
                getAllFileName(a.getAbsolutePath()+"\\",listFileName);
            }
            else if(a.isFile()) //为文件
            {
                listFileName.add(a.toString());
            }
        }
    }
}
