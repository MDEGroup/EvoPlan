package org.impl.Recommend;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.impl.Evaluation.Evaluation;
import org.impl.Neo4j.Neo4j;
import org.impl.Utils.Properties;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.stream.Collectors.toMap;

public class Recommender {

    static Properties prop = new Properties();

    public static ArrayList<Record> planReccommendV1(Driver driver, String library, String clientVersion){

        String latestVersion = "4.13";
        ArrayList<Record> results = Neo4j.shortestPath(driver, library, library, clientVersion, latestVersion, "cost");
        return results;
    }

    public static ArrayList<String> planReccommendV2(Driver driver, String lib, String ver, String libraryVers){

        ArrayList<String> plan = new ArrayList<String>();


        plan.add(ver);
        String intermediateVer = intermediateReccommend(driver, lib, ver, libraryVers);
        plan.add(intermediateVer);

        while(true){
            intermediateVer = intermediateReccommend(driver, lib, intermediateVer, libraryVers);
            assert intermediateVer != null;
            if(Integer.parseInt(intermediateVer.replace(".",""))<Integer.parseInt(plan.get(plan.size()-1).replace(".",""))){
                break;
            }
            else{
                plan.add(intermediateVer);
            }

        }

        return plan;

    }

    public static String intermediateReccommend (Driver driver, String lib, String ver, String libraryVers) {
        Path path = Paths.get(libraryVers);

        try {
            BufferedReader reader = Files.newBufferedReader(path);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(reader);

            ArrayList<String> versionsAvailable = new ArrayList<String>();
            ArrayList<Record> best = new ArrayList<Record>();

            for (CSVRecord record : records) {
                String version = record.get("version");
                ArrayList<Record> results = Neo4j.shortestPath(driver, lib, lib, ver, version, "cost");
                System.out.println(results);
                int size = results.size();
                if(size>=1) {
                    double actual = results.get(size-1).get("cost").asDouble();
                    if(best.size()<1){
                        best = results;
                        continue;
                    }
                    else{
                        double bestVal = best.get(best.size()-1).get("cost").asDouble();
                        if(actual<bestVal){
                            best = results;
                        }
                    }
                }
            }
            System.out.println(best);
            return best.get(best.size()-1).get("version").asString();
        }
        catch (Exception e) { System.out.println(e);}
        return null;
    }

    public static ArrayList<Record> rankByIssues(ArrayList<Record> results, String issuesFolder, String target, Double threshold, String outputFolder) {
        /*
        [Record<{name: "slf4j-api", version: "1.5.10", cost: 0.0}>, Record<{name: "slf4j-api", version: "1.6.6", cost: 0.09090909090909091}>]
        [Record<{places: ["1.5.10", "1.6.6"], costs: [0.09090909090909091], totalCost: 0.09090909090909091}>, Record<{places: ["1.5.10", "1.6.4", "1.6.6"], costs: [0.5, 0.125], totalCost: 0.625}>]
        */

        Path path = Paths.get(issuesFolder + "/" + target + "_issue_data.csv");
        HashMap<Record, Integer> resultMap = new HashMap<>();

        /*
        Only for correlation purposes
         */
        ArrayList<Double> partialDeltas = new ArrayList<>();
        ArrayList<Double> partialOpen = new ArrayList<>();
        ArrayList<Double> partialClosed = new ArrayList<>();
        ArrayList<Double> partialRatios = new ArrayList<>();
        /*
        Only for correlation purposes
         */

        int issueTotalCounter = 0;

        for (Record res : results) {
            partialDeltas = new ArrayList<>();
            partialOpen = new ArrayList<>();
            partialClosed = new ArrayList<>();
            partialRatios = new ArrayList<>();

            int issuesCounter = 0;
            int elemCounter = 0;
            if (Double.parseDouble(res.get("totalCost").toString()) < threshold) {
                Value elems = res.get("places");
                for (Object elem : elems.asList()) {
                    try {
                        BufferedReader reader = Files.newBufferedReader(path);
                        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(reader);

                        for (CSVRecord record : records) {
                            if (elem.toString().equals(record.get("version"))) {
                                partialDeltas.add(Double.parseDouble(record.get("delta ")));
                                partialClosed.add(Double.parseDouble(record.get("closed_issues")));
                                partialOpen.add(Double.parseDouble(record.get("open_issues")));
                                partialRatios.add(Double.parseDouble(record.get("delta_ratio")));

                                issuesCounter += Double.parseDouble(record.get("delta_ratio"));
                                issueTotalCounter += issuesCounter;
                                elemCounter += 1;
                            }

                        }
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }

                Evaluation.correlationEvaluation(res, partialDeltas, partialOpen, partialClosed, partialRatios, outputFolder);

                if (elemCounter > 0) {
                    resultMap.put(res, issuesCounter / elemCounter);
                } else {
                    resultMap.put(res, 0);
                }

            }

        }

        //System.out.println(resultMap);

        ArrayList<Record> res = new ArrayList<>();
        if (issueTotalCounter > 0) {
            Map<Record, Integer> sorted = resultMap
                    .entrySet()
                    .stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    //.sorted(comparingByValue())
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                            LinkedHashMap::new));

            if (sorted.size() > 0) {
                res.add(sorted.keySet().iterator().next());
            }
            //System.out.println(res);
            return res;
        }
        /*
        else
         */
        if (resultMap.size() > 0) {
            res.add(resultMap.entrySet().iterator().next().getKey());
            return res;
        }
        //System.out.println(resultMap);
        //System.out.println(res);
        return res;
    }

    public static void recommend(Driver driver, String libUpdate, String library, String startingVer, String targetVer,Boolean issues, String issuesPath){
        int k = 5;
        double threshold = 2.0;

        Neo4j.updateRelationshipsRemovingUpgrade(driver,library,libUpdate);
        Neo4j.updateRelationships(driver,library);
        ArrayList<Record> results = Neo4j.kShortestPath(driver, library, library, startingVer, targetVer, "cost",k);
        Neo4j.updateRelationships(driver,library);

        System.out.println(results);

        if(issues){
            ArrayList<Record> resultsRanked = Recommender.rankByIssues(results, issuesPath, library, threshold,"");

            for(Record res:resultsRanked){

                Value elems = res.get("places");
                for(Object elem:elems.asList()){
                    System.out.println(elem.toString());
                }

            }

        }


    }

}

