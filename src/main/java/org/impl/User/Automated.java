package org.impl.User;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.maven.model.Dependency;
import org.impl.Recommend.Recommender;
import org.impl.RepositoriesAnalysis.RepositoriesAnalysis;
import org.impl.Utils.PomReader;
import org.neo4j.driver.Driver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Automated {

    public static void start(Driver driver, String projectPath, String updateLibFile, boolean issue, String issuesFolder){

        Set<HashMap<String, String>> projectsLibs = scan(projectPath);

        //System.out.println(projectsLibs);
        Iterator<HashMap<String, String>> itr = projectsLibs.iterator();
        String targetVersion = "";


        while(itr.hasNext()){

            HashMap<String,String> currentCouple = itr.next();
            String library = currentCouple.keySet().iterator().next();
            String currentVersion = currentCouple.get(library);

            targetVersion = getLatestVersion(library,updateLibFile);

            if(targetVersion.equals("")){
                System.out.println("Not present in the maven update file");
            }
            else{
                Recommender.recommend(driver,updateLibFile,library,currentVersion,targetVersion,issue,issuesFolder);
            }
        }

    }

    public static Set<HashMap<String, String>> scan(String projectPath){
        /*
        torna la lista delle librerie e versioni per ogni pom che trova
         */
        List<File> pomsList = RepositoriesAnalysis.getFilesByEndingValue(new File(projectPath), "pom.xml");

        ArrayList<HashMap<String,String>> res = new ArrayList<HashMap<String,String>>();

        for(File pom: pomsList){
            res = getAdoptedLibraries(pom,res);
        }

        Set<HashMap<String,String>> result = new HashSet<HashMap<String,String>>(res);

        return result;
    }

    public static ArrayList<HashMap<String,String>> getAdoptedLibraries(File localPomLocation, ArrayList<HashMap<String,String>> mainList){
        /*
        usato da scan
         */

        List<Dependency> deps = PomReader.getDependencies(localPomLocation.toString());

        for(Dependency dep:deps){
            HashMap<String,String> current = new HashMap<>();
            current.put(dep.getArtifactId(),dep.getVersion());
            mainList.add(current);
        }

        return mainList;
    }

    public static String getLatestVersion(String library, String updateLibFile){
        /*
        data una libreria, torna la versione most recent - dati presi da maven
         */
        String ver = "";

        Path path = Paths.get(updateLibFile);
        try {
            BufferedReader reader = Files.newBufferedReader(path);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(reader);
            for (CSVRecord record : records) {
                if(record.get("library").equals(library)) {
                    ver = record.get("version");
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return ver;
    }

}
