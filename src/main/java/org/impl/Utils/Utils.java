package org.impl.Utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class Utils {

    public static Properties prop = new Properties();

    public static void main(String[] args) throws IOException {
        //getFromD("/home/rick/Scrivania/1223095_done_with_origins.txt","repos_pom_complete2.txt");
        getFromRaux("eval_repos.txt","eval_repos_refined.txt");
        //phuongDataset(new File("csv/githubDiff/"));
    }

    public static ArrayList<String> getFromD(String path, String resultFile){

        ArrayList<String> results = new ArrayList<String>();

        try (BufferedReader br = Files.newBufferedReader(Paths.get(path))) {
            String line;
            results.add(br.readLine());
            while ((line = br.readLine()) != null) {
                if(line.contains("github.com")){
                    String repoName = line.substring(line.indexOf("/",line.indexOf(".com")+5)+1);
                    if(!results.get(results.size()-1).contains(repoName)){
                        results.add(line.substring(line.indexOf(".com")+5));
                    }
                }
            }

        }
        catch(Exception e){
            System.out.println(e);
        }

        Set<String> set = new HashSet<>(results);
        results.clear();
        results.addAll(set);

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(resultFile)))
        {
            for(int i=0; i<30000; i+=1)
            {
                writer.write(results.get(i)+"\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
    }

    public static void getFromRaux(String originalPath, String destinationPath) throws IOException {

        BufferedReader reader = Files.newBufferedReader(Paths.get(originalPath));
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(destinationPath));
        Iterable <CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse( reader );


        for ( CSVRecord record : records )
        {
            String line = record.get("url");
            int i = line.indexOf("com/")+4;
            int ii = line.indexOf("/",i);
            writer.write(line.substring(i,ii)+line.substring(ii)+"\n");
        }
    }

    public static void phuongDataset(File dir){
        File[] filesList = dir.listFiles();
        Path path = Paths.get("phuong.csv");
        try {
            BufferedWriter writer = Files.newBufferedWriter(path);
            StringBuilder sb = new StringBuilder();
            sb.append("project");
            sb.append(",");
            sb.append("junit");
            sb.append(",");
            sb.append("log4j");
            sb.append(",");
            sb.append("commons-io");
            sb.append(",");
            sb.append("commons-lang3");
            sb.append(",");
            sb.append("slf4j");
            sb.append(",");
            sb.append("httpclient");
            sb.append(",");
            sb.append("guava");
            sb.append(",");
            sb.append("entry Date");
            sb.append("\n");
            writer.write(sb.toString());

            for(File f:filesList) {
                Path p = Paths.get(f.toString());
                String projectName = f.getName();
                TreeMap<String, String> csvGithub = new TreeMap<String, String>();
                csvGithub = CsvManager.readFromDiff(p);
                createIterative(csvGithub,writer,projectName);
            }
        }
        catch(Exception exc) {

        }
    }

    public static void createIterative(TreeMap<String, String> csvGithub, BufferedWriter writer,String projectName){

        TreeMap<String, String> internalCSV = (TreeMap<String, String>) csvGithub.clone();

            for (Iterator<String> it = csvGithub.keySet().iterator(); it.hasNext(); ) {
                String elem = it.next();

                int i = elem.indexOf(";");//lib;ver;date
                int id = elem.indexOf(";",i+1);
                String firstLibrary = elem.substring(0, i);
                String firstVersionString = elem.substring(i + 1,id);
                String firstVersionSign = csvGithub.get(elem);
                String strDate = elem.substring(elem.indexOf(";",id)+1);
                try {
                    Date firstDate = new SimpleDateFormat(prop.getDateFormat()).parse(strDate);

                    internalCSV.remove(elem);
                    for (Iterator<String> subIt = internalCSV.keySet().iterator(); it.hasNext(); ){
                        String subelem = subIt.next();
                        int ii = subelem.indexOf(";");
                        int iid = subelem.indexOf(";",ii+1);
                        String currentLibrary = subelem.substring(0, ii);
                        String currentVersionString = subelem.substring(ii+1, iid);
                        String currentVersionSign = internalCSV.get(subelem);
                        String strCurrDate = subelem.substring(subelem.indexOf(";",iid)+1);
                        try {
                            Date currentDate = new SimpleDateFormat(prop.getDateFormat()).parse(strCurrDate);
                            if(firstDate.equals(currentDate)&&firstLibrary.equals(currentLibrary)&&!firstVersionString.equals(currentVersionString)) {
                                if (firstVersionSign.equals("+") && currentVersionSign.equals("-")) {
                                    //Neo4j.createRelationship(driver, currentLibrary, currentVersionString, firstLibrary, firstVersionString, 1.0,"Upgraded", false);
                                    writer=write2Csv(writer,projectName,currentLibrary,currentVersionString,currentDate);
                                    break;
                                }
                                if (firstVersionSign.equals("-") && currentVersionSign.equals("+")) {
                                    //Neo4j.createRelationship(driver, firstLibrary, firstVersionString, currentLibrary, currentVersionString, 1.0, "Upgraded",false);
                                    writer=write2Csv(writer,projectName,firstLibrary,firstVersionString,currentDate);
                                    break;
                                }
                            }
                        }
                        catch(Exception e){
                            //System.out.println(e);
                        }
                    }
                }
                catch(Exception e){
                    //System.out.println(e);
                }
            }
        }

    public static BufferedWriter write2Csv(BufferedWriter writer, String projectName, String library, String version, Date currentDate){
        try {
            StringBuilder sb = new StringBuilder();

            if(library.equals("junit")){sb.append(projectName);sb.append(",");sb.append(version+",0,0,0,0,0,0");sb.append(",");sb.append(currentDate);sb.append("\n");writer.write(sb.toString());return writer;}
            if(library.equals("log4j")){sb.append(projectName);sb.append(",");sb.append("0,"+version+",0,0,0,0,0");sb.append(",");sb.append(currentDate);sb.append("\n");writer.write(sb.toString());return writer;}
            if(library.equals("commons-io")){sb.append(projectName);sb.append(",");sb.append("0,0,"+version+",0,0,0,0");sb.append(",");sb.append(currentDate);sb.append("\n");writer.write(sb.toString());return writer;}
            if(library.equals("commons-lang3")){sb.append(projectName);sb.append(",");sb.append("0,0,0,"+version+",0,0,0");sb.append(",");sb.append(currentDate);sb.append("\n");writer.write(sb.toString());return writer;}
            if(library.equals("log4j-slf4j-impl")){sb.append(projectName);sb.append(",");sb.append("0,0,0,0,"+version+",0,0");sb.append(",");sb.append(currentDate);sb.append("\n");writer.write(sb.toString());return writer;}
            if(library.equals("httpclient")){sb.append(projectName);sb.append(",");sb.append("0,0,0,0,0,"+version+",0");sb.append(",");sb.append(currentDate);sb.append("\n");writer.write(sb.toString());return writer;}
            if(library.equals("guava")){sb.append(projectName);sb.append(",");sb.append("0,0,0,0,0,0,"+version);sb.append(",");sb.append(currentDate);sb.append("\n");writer.write(sb.toString());return writer;}
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return writer;
    }
}
