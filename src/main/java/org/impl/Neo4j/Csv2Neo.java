package org.impl.Neo4j;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.impl.RepositoriesAnalysis.RepositoriesAnalysis;
import org.impl.Utils.CsvManager;
import org.impl.Utils.Properties;
import org.neo4j.driver.Driver;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class Csv2Neo
{
    public static Properties prop = new Properties();

    public static void createDB(String folder) {
        Driver driver = prop.getDriver();

        File dirGithubDiff = new File(folder);
        //File dirGithubDiff = new File(prop.getTenEvalDiffOutputFolder());

        createFromCSVwithSign(driver, dirGithubDiff, "emptyfornow");
    }

    public static void createFromCSVwithSign(Driver driver, File dir, String libraryVers){
        //File[] filesList = dir.listFiles();

        for(File f: RepositoriesAnalysis.getFilesByEndingValue(dir,".csv")) {
            Path p = Paths.get(f.toString());

            TreeMap<String, String> csvGithub = new TreeMap<String, String>();

            try {
                csvGithub = CsvManager.readFromDiff(p);

                for (String elem : csvGithub.keySet()) {
                    int i = elem.indexOf(";"); //lib;version;date
                    int ii = elem.indexOf(";",i+1);
                    if(Character.isDigit(elem.substring(i+1).charAt(0))) { //per controllare il problema con il primo carattere che a volte sta davanti il numero di versione
                        Neo4j.createNode(driver, elem.substring(i + 1,ii), elem.substring(0, i));
                    }
                    else{
                        Neo4j.createNode(driver, elem.substring(i + 2,ii), elem.substring(0, i));
                    }
                }
                //createCoupledRelationships(csvGithub, driver);
                createIterativeRelationships(csvGithub,driver);
            }

            catch (Exception e)
            {
                System.out.println(e);
            }
        }

        driver.close();
    }


    public static void createCoupledRelationships(TreeMap<String, String> csvGithub, Driver driver){
        /*
         In questa soluzione considero gli elementi direttamente a coppie, ciclo sul csv guardo il corrente col successivo e poi avanti di 2
        */
        for (Iterator<String> it = csvGithub.keySet().iterator(); it.hasNext(); ) {
            String elem = it.next();
            if(it.hasNext()) {

                int i = elem.indexOf(";");//lib;ver;date
                int id = elem.indexOf(";",i+1);
                String firstLibrary = elem.substring(0, i);
                String firstVersionString = elem.substring(i + 1,id);
                String firstVersionSign = csvGithub.get(elem);

                String nextElem = it.next();
                int ii = nextElem.indexOf(";");
                int iid = nextElem.indexOf(";",ii+1);
                String currentLibrary = nextElem.substring(0, ii);
                String currentVersionString = nextElem.substring(ii+1, iid);
                String currentVersionSign = csvGithub.get(nextElem);

                /*
                date examination
                 */
                String currentDateString = nextElem.substring(iid+1);


                if(firstLibrary.equals(currentLibrary)&&!firstVersionString.equals(currentVersionString)) {
//&&compareDate(currentLibrary, currentVersionString, currentDateString, currentVersionSign, prop.getMavenLibVersion())
                    if(firstVersionSign.equals("-")&&currentVersionSign.equals("+")) {
                        Neo4j.createRelationship(driver, firstLibrary, firstVersionString, currentLibrary, currentVersionString, 1.0, "Upgraded", false);
                        System.out.println(firstLibrary+";"+firstVersionString);
                        System.out.println(currentLibrary+";"+currentVersionString);
                    }
//&&compareDate(currentLibrary, currentVersionString, currentDateString, currentVersionSign, prop.getMavenLibVersion())
                    if(firstVersionSign.equals("+")&&currentVersionSign.equals("-")){
                        Neo4j.createRelationship(driver, firstLibrary, firstVersionString, currentLibrary, currentVersionString, 1.0,"Upgraded", false);
                        System.out.println(firstLibrary+";"+firstVersionString);
                        System.out.println(currentLibrary+";"+currentVersionString);
                    }

                }
            }
        }
    }

    public static void createIterativeRelationships(TreeMap<String, String> csvGithub, Driver driver){

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
                                Neo4j.createRelationship(driver, currentLibrary, currentVersionString, firstLibrary, firstVersionString, 1.0,"Upgraded", false);
                                break;
                            }
                            if (firstVersionSign.equals("-") && currentVersionSign.equals("+")) {
                                Neo4j.createRelationship(driver, firstLibrary, firstVersionString, currentLibrary, currentVersionString, 1.0, "Upgraded",false);
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

    public static boolean compareDate(String currentLibrary, String currentVersion, String currentDate, String sign, String libraryVers){

        Path path = Paths.get(libraryVers);

        try {
            BufferedReader reader = Files.newBufferedReader(path);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(reader);

            boolean result = false;

            for (CSVRecord record : records) {
                if(record.get("library").equals(currentLibrary)&&record.get("version").equals(currentVersion)&&sign.equals("+")){

                    Date dateCurrent = new SimpleDateFormat(prop.getDateFormat()).parse(currentDate);
                    //System.out.println("Client "+ currentVersion +"commit: "+dateCurrent);
                    //System.out.println("Version "+ record.get("version") +"Release Date: "+dateVer);

                    if(records.iterator().hasNext()){
                        Date dateVer = new SimpleDateFormat(prop.getDateFormat()).parse(records.iterator().next().get("date"));

                        if(dateVer.before(dateCurrent)) {
                            return true;
                            //System.out.println("Next Available Version" + records.iterator().next().get("version") + "release date: " + dateVer);
                            //System.out.println("OK---------------------");
                        }
                    }
                    //System.out.println(dateCurrent.after(dateVer));
                }
            }
        }
        catch(Exception e){
            System.out.println(e);
        }
        return false;

    }


    public static void createFromPygit(Driver driver, File dir) {
        File[] filesList = dir.listFiles();



        for(File f:filesList)
        {
            Path p = Paths.get(f.toString());

            String name = "";
            name = p.toString().replace("csv/","");
            name = name.replace(".csv","");

            HashMap<String, Integer> csvPygit = new HashMap<String, Integer>();

            try
            {
                csvPygit = CsvManager.readFromPyGit(p);

                for (String elem : csvPygit.keySet())
                {
                    int i = elem.indexOf(";");
                    Neo4j.createNode(driver, elem.substring(0, i), name);
                    Neo4j.createNode(driver, elem.substring(i + 1), name);
                    //Neo4j.createRelationship(driver, elem.substring(0, i), name, elem.substring(i + 1), name, "upgraded", String.valueOf(csvPygit.get(elem)));
                }
            }

            catch (Exception e)
            {
                System.out.println(e);
            }
        }
    }
}
