package org.impl.Evaluation;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.impl.Recommend.Recommender;
import org.impl.RepositoriesAnalysis.RepositoriesAnalysis;
import org.impl.Neo4j.Csv2Neo;
import org.impl.Neo4j.Neo4j;
import org.impl.Utils.CsvManager;
import org.impl.Utils.Properties;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;


public class Evaluation {

    static Properties prop = new Properties();

    public static void startEvaluation(String mainFolder, String issueFolder, String updateLibFile, String round, int k, List<String> targets, String outputFolder) {

        Driver driver = prop.getDriver();

        File f = new File(mainFolder);

        //reading csv 10%
        ArrayList<HashMap<String, String>> tenFolder = readFromCSV(f, false);

        for (String target : targets) {
            System.out.println("Evaluating " + target);
            Neo4j.updateRelationshipsRemovingUpgrade(driver, target, updateLibFile);
            Neo4j.updateRelationships(driver, target);
            historicalEvaluation(driver, tenFolder, target, round, outputFolder, updateLibFile);
            historicalEvaluationkPaths(driver, tenFolder, target, round, k, 2.0, issueFolder, outputFolder, updateLibFile);
            Neo4j.updateRelationships(driver, target);
        }
        driver.close();
        System.out.print("Evaluation of round " + round + " completed\n please stop neo4j\n change db\n start neo4j again\n change round\n ");
    }

    public static ArrayList<String> reconstructMigrationHistoryV1(HashMap<String, String> client, String libsAvailableFilePath, String targetLib) {
        Path path = Paths.get(libsAvailableFilePath);
        ArrayList<String> result = new ArrayList<String>();

        try {
            BufferedReader reader = Files.newBufferedReader(path);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(reader);
            for (CSVRecord record : records) {
                String lib = record.get("library");
                if (lib.equals(targetLib)) {
                    String version = record.get("version");
                    for (String source : client.keySet()) {
                        if (source.substring(0, source.indexOf(";")).equals(targetLib)) {
                            if (result.size() == 0) {
                                result.add(source.substring(source.indexOf(";") + 1));
                            }
                            String target = client.get(source);
                            if (target.substring(target.indexOf(";") + 1).equals(version) && !result.contains(version)) {
                                result.add(version);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return result;
    }

    public static void pathEvaluation(Driver driver, ArrayList<HashMap<String, String>> tenFolder, String target) {
        int counter = 0;
        int counterNegatives = 0;
        System.out.println(tenFolder);
        for (HashMap<String, String> elem : tenFolder) {
            for (String subelem : elem.keySet()) {
                String sourceNode = subelem.substring(0, subelem.indexOf(";"));
                String sourceVersion = subelem.substring(subelem.indexOf(";") + 1);
                String targetVersion = elem.get(subelem).substring(elem.get(subelem).indexOf(";") + 1);
                if (sourceNode.equals(target)) {
                    //System.out.println(subelem);
                    ArrayList<Record> results = Neo4j.shortestPath(driver, sourceNode, sourceNode, sourceVersion, targetVersion, "cost");
                    if (results.size() == 2) {
                        //System.out.println(results);
                        counter += 1;
                    }
                    if (results.size() > 2) {
                        System.out.println(sourceVersion + "->" + targetVersion);
                        System.out.println(results);
                        counterNegatives += 1;
                    }
                }
            }
        }
        System.out.println(counter);
        System.out.println(counterNegatives);
    }

    public static void historicalEvaluation(Driver driver, ArrayList<HashMap<String, String>> tenFolder, String target, String round, String outputFolder, String libVersions) {

        ArrayList<String> reconstructedHistory = new ArrayList<>();

        for (HashMap<String, String> client : tenFolder) {
            reconstructedHistory = reconstructMigrationHistoryV1(client, libVersions, target);
            if (reconstructedHistory.size() >= 1) {
                long starting = System.nanoTime();

                ArrayList<Record> results = Neo4j.shortestPath(driver, target, target, reconstructedHistory.get(0), reconstructedHistory.get(reconstructedHistory.size() - 1), "cost");
                ArrayList<String> candidatePath = new ArrayList<>();
                for (Record res : results) {
                    candidatePath.add(res.get("version").asString());
                }

                if (results.size() == 0) {
                    //System.out.println("Null; "+reconstructedHistory);
                    writeEvaluation("Null", target + "NoIssue", reconstructedHistory, null, round, outputFolder);
                    continue;
                }
                String mark = comparePaths(candidatePath, reconstructedHistory);
                if (mark.equals("True")) {
                    //System.out.println("True; "+reconstructedHistory+" ; "+candidatePath);
                    writeEvaluation("True", target + "NoIssue", reconstructedHistory, candidatePath, round, outputFolder);
                }
                if (mark.equals("Wrong")) {
                    //System.out.println("Wrong; "+reconstructedHistory+" ; "+candidatePath);
                    writeEvaluation("Wrong", target + "NoIssue", reconstructedHistory, candidatePath, round, outputFolder);
                }
                if (mark.equals("Shorter")) {
                    //System.out.println("Shorter; "+reconstructedHistory+" ; "+candidatePath);
                    writeEvaluation("Shorter", target + "NoIssue", reconstructedHistory, candidatePath, round, outputFolder);
                }
                if (mark.equals("Longer")) {
                    //System.out.println("Longer; "+reconstructedHistory+" ; "+candidatePath);
                    writeEvaluation("Longer", target + "NoIssue", reconstructedHistory, candidatePath, round, outputFolder);
                }
            }
        }
        /*
        System.out.println("<----------------------->");
        System.out.println("True: "+Tcounter);
        System.out.println("Null: "+Ncounter);
        System.out.println("Wrong: "+Fcounter);
        System.out.println("Longer:"+Lcounter);
        System.out.println("Shorter: "+Scounter);
        */
    }

    public static void historicalEvaluationkPaths(Driver driver, ArrayList<HashMap<String, String>> tenFolder, String target, String round, int k, Double threshold, String issueFolder, String outputFolder, String libVersions) {
        ArrayList<String> reconstructedHistory = new ArrayList<>();

        for (HashMap<String, String> client : tenFolder) {
            reconstructedHistory = reconstructMigrationHistoryV1(client, libVersions, target);
            if (reconstructedHistory.size() >= 1) {
                long starting = System.nanoTime();

                ArrayList<Record> results = Neo4j.kShortestPath(driver, target, target, reconstructedHistory.get(0), reconstructedHistory.get(reconstructedHistory.size() - 1), "cost", k);
                results = Recommender.rankByIssues(results, issueFolder, target, threshold, outputFolder);

                long finishing = System.nanoTime();
                //System.out.println("Elapsed time: "+(finishing-starting));

                if (results.size() == 0) {
                    //System.out.println("Null; "+reconstructedHistory);
                    writeEvaluation("Null", target, reconstructedHistory, null, round, outputFolder);
                    continue;
                }

                ArrayList<String> candidatePath = transformPathList(results);
                String mark = comparePaths(candidatePath, reconstructedHistory);

                if (mark.equals("True")) {
                    //System.out.println("True; "+reconstructedHistory+" ; "+candidatePath);
                    writeEvaluation("True", target, reconstructedHistory, candidatePath, round, outputFolder);
                }
                if (mark.equals("Wrong")) {
                    //System.out.println("Wrong; "+reconstructedHistory+" ; "+candidatePath);
                    writeEvaluation("Wrong", target, reconstructedHistory, candidatePath, round, outputFolder);
                }
                if (mark.equals("Shorter")) {
                    //System.out.println("Shorter; "+reconstructedHistory+" ; "+candidatePath);
                    writeEvaluation("Shorter", target, reconstructedHistory, candidatePath, round, outputFolder);
                }
                if (mark.equals("Longer")) {
                    //System.out.println("Longer; "+reconstructedHistory+" ; "+candidatePath);
                    writeEvaluation("Longer", target, reconstructedHistory, candidatePath, round, outputFolder);
                }


            }
        }
    }



    public static ArrayList<HashMap<String, String>> readFromCSV(File dir, boolean timeFilter) {
        //File[] filesList = dir.listFiles();
        ArrayList<HashMap<String, String>> fileResult = new ArrayList<HashMap<String, String>>();


        for (File f : RepositoriesAnalysis.getFilesByEndingValue(dir, ".csv")) {
            Path p = Paths.get(f.toString());

            TreeMap<String, String> csvGithub = new TreeMap<String, String>();

            try {
                csvGithub = CsvManager.readFromDiff(p);
                HashMap<String, String> l = analyzeIteratively(csvGithub, timeFilter);
                if (l.size() > 0) {
                    fileResult.add(l);
                }

            } catch (Exception e) {
                System.out.println(p);
                System.out.println(e);
            }
        }
        return (fileResult);
    }

    public static HashMap<String, String> analyzeIteratively(TreeMap<String, String> csvGithub, Boolean timeFilter) {
        /*
        lib1;ver1 : lib2;ver2
        str;str : str;str
         */
        HashMap<String, String> result = new HashMap<String, String>();

        TreeMap<String, String> internalCSV = (TreeMap<String, String>) csvGithub.clone();
        for (Iterator<String> it = csvGithub.keySet().iterator(); it.hasNext(); ) {
            String elem = it.next();

            int i = elem.indexOf(";");//lib;ver;date
            int id = elem.indexOf(";", i + 1);
            String firstLibrary = elem.substring(0, i);
            String firstVersionString = elem.substring(i + 1, id);
            String firstVersionSign = csvGithub.get(elem);
            String strDate = elem.substring(elem.indexOf(";", id) + 1);
            try {
                Date firstDate = new SimpleDateFormat(prop.getDateFormat()).parse(strDate);

                internalCSV.remove(elem);
                for (Iterator<String> subIt = internalCSV.keySet().iterator(); it.hasNext(); ) {
                    String subelem = subIt.next();
                    int ii = subelem.indexOf(";");
                    int iid = subelem.indexOf(";", ii + 1);
                    String currentLibrary = subelem.substring(0, ii);
                    String currentVersionString = subelem.substring(ii + 1, iid);
                    String currentVersionSign = internalCSV.get(subelem);
                    String strCurrDate = subelem.substring(subelem.indexOf(";", iid) + 1);
                    try {
                        Date currentDate = new SimpleDateFormat(prop.getDateFormat()).parse(strCurrDate);
                        if (firstDate.equals(currentDate) && firstLibrary.equals(currentLibrary) && !firstVersionString.equals(currentVersionString)) {
                            if (firstVersionSign.equals("+") && currentVersionSign.equals("-")) {
                                result.put(currentLibrary + ";" + currentVersionString, firstLibrary + ";" + firstVersionString);
                                break;
                            }
                            if (firstVersionSign.equals("-") && currentVersionSign.equals("+")) {
                                result.put(firstLibrary + ";" + firstVersionString, currentLibrary + ";" + currentVersionString);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        //System.out.println(e);
                    }
                }
            } catch (Exception e) {
                //System.out.println(e);
            }
        }
        return result;
    }


    public static ArrayList<String> transformPathList(ArrayList<Record> results) {
        ArrayList<String> path = new ArrayList<>();
        Record res = results.get(0);
        Value elems = res.get("places");

        for (Object elem : elems.asList()) {
            path.add(elem.toString());
        }
        return path;
    }

    public static String comparePaths(ArrayList<String> candidatePath, ArrayList<String> historicPath) {

        if (candidatePath.size() > historicPath.size()) {
            return "Longer";
        }

        if (candidatePath.size() < historicPath.size()) {
            return "Shorter";
        }

        if (candidatePath.size() == historicPath.size()) {
            if (historicPath.equals(candidatePath)) {
                return "True";
            } else {
                return "Wrong";
            }
        }
        return "Void";
    }

    public static void writeEvaluation(String outcome, String library, ArrayList<String> history, ArrayList<String> candidatePath, String round, String outputFolder) {

        StringBuilder sb = new StringBuilder();

        File directory = new File(outputFolder + round + "/");
        if (!directory.exists()) {
            directory.mkdir();
        }

        try (FileWriter writer = new FileWriter(new File(outputFolder + round + "/" + library + ".csv"), true)) {
            sb.append(outcome);
            sb.append(';');
            sb.append(history);
            sb.append(';');
            if (candidatePath != null) {
                sb.append(candidatePath);
            } else {
                sb.append("");
            }
            sb.append('\n');
            writer.write(sb.toString());
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static void correlationEvaluation(Record results, ArrayList<Double> delta, ArrayList<Double> open, ArrayList<Double> closed, ArrayList<Double> ratios, String outputFolder) {

        List versions = results.get("places").asList();
        List popularity = results.get("costs").asList();

        int i = 0;
        try (FileWriter writer = new FileWriter(new File(outputFolder + "correlation.csv"), true)) {

            StringBuilder sb = new StringBuilder();

            while (i < versions.size() - 1) {


                if (versions.size() == open.size() && open.get(i + 1) != null) {
                    sb.append(versions.get(i));
                    sb.append(',');
                    sb.append(versions.get(i + 1));
                    sb.append(',');
                    sb.append(1.0 / Double.parseDouble(popularity.get(i).toString()));
                    sb.append(',');
                    sb.append(open.get(i + 1));
                    sb.append(',');
                    sb.append(closed.get(i + 1));
                    sb.append(',');
                    sb.append(delta.get(i + 1));
                    sb.append(',');
                    sb.append(ratios.get(i + 1));
                    sb.append('\n');
                    i += 1;
                } else {
                    i += 1;
                }

            }
            writer.write(sb.toString());
        } catch (Exception e) {
            System.out.println(e);
        }

    }

    /*
    old
     */
    public static HashMap<String, String> analyze(TreeMap<String, String> csvGithub, Boolean timeFilter) {

        /*
        lib1;ver1 : lib2;ver2
        str;str : str;str
         */
        HashMap<String, String> result = new HashMap<String, String>();

        for (Iterator<String> it = csvGithub.keySet().iterator(); it.hasNext(); ) {
            String elem = it.next();
            if (it.hasNext()) {

                int i = elem.indexOf(";");//lib;ver;date
                int id = elem.indexOf(";", i + 1);
                String firstLibrary = elem.substring(0, i);
                String firstVersionString = elem.substring(i + 1, id);
                String firstVersionSign = csvGithub.get(elem);

                String nextElem = it.next();
                int ii = nextElem.indexOf(";");
                int iid = nextElem.indexOf(";", ii + 1);
                String currentLibrary = nextElem.substring(0, ii);
                String currentVersionString = nextElem.substring(ii + 1, iid);
                String currentVersionSign = csvGithub.get(nextElem);

                /*
                date examination
                 */
                String currentDateString = nextElem.substring(iid + 1);

                if (timeFilter) {

                    if (firstLibrary.equals(currentLibrary) && !firstVersionString.equals(currentVersionString) && Csv2Neo.compareDate(currentLibrary, currentVersionString, currentDateString, currentVersionSign, prop.getMavenLibVersion())) {
                        if (firstVersionSign.equals("+") && currentVersionSign.equals("-")) {
                            result.put(currentLibrary + ";" + currentVersionString, firstLibrary + ";" + firstVersionString);
                        }
                        if (firstVersionSign.equals("-") && currentVersionSign.equals("+") && Csv2Neo.compareDate(currentLibrary, currentVersionString, currentDateString, currentVersionSign, prop.getMavenLibVersion())) {
                            //if(firstVersionSign.equals("-")&&currentVersionSign.equals("+")){
                            result.put(firstLibrary + ";" + firstVersionString, currentLibrary + ";" + currentVersionString);
                        }
                    }
                } else {

                    if (firstLibrary.equals(currentLibrary) && !firstVersionString.equals(currentVersionString)) {
                        if (firstVersionSign.equals("+") && currentVersionSign.equals("-")) {
                            result.put(currentLibrary + ";" + currentVersionString, firstLibrary + ";" + firstVersionString);
                        }
                        if (firstVersionSign.equals("-") && currentVersionSign.equals("+")) {
                            result.put(firstLibrary + ";" + firstVersionString, currentLibrary + ";" + currentVersionString);
                        }
                    }
                }
            }
        }
        return result;
    }

}