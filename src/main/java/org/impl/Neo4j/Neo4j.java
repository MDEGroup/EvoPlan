package org.impl.Neo4j;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.neo4j.driver.*;


/*
    http://localhost:7474/browser/ home url
    https://neo4j.com/developer/cypher-basics-ii/
    https://neo4j.com/developer/java/
    https://github.com/neo4j-contrib/neo4j-graph-algorithms algo plugin
 */

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Neo4j
{
    public static void createNode(Driver driver, String version, String node)
    {
        try ( Session session = driver.session() )
        {
            String greeting = session.writeTransaction( new TransactionWork<String>()
            {
                @Override
                public String execute( Transaction tx )
                {
                    Map<String,Object> params = new HashMap<>();
                    params.put("name",node);
                    params.put("version",version);
                    tx.run("MERGE (a:Library{name: $name, version: $version})", params);
                    return "Node Created";
                }
            } );
        }
    }

    public static void createRelationship(Driver driver, String nodeA, String versionNodeA, String nodeB, String versionNodeB, Double weight, String relationshipName,  Boolean update)
    {
        try (Session session = driver.session()) {

            try (Transaction transaction = session.beginTransaction()) {

                Iterable<Value> currentValues = Neo4j.getParametersFromRelationship(driver, nodeA, versionNodeA, nodeB, versionNodeB, relationshipName);
                if(currentValues!=null&& !update) {
                    Value val = currentValues.iterator().next();
                    weight = weight+Double.parseDouble(val.toString());
                    deleteRelationship(driver,nodeA,versionNodeA,nodeB,versionNodeB,relationshipName,Double.parseDouble(val.toString()));
                }
                if(currentValues!=null&& update){
                    Value val = currentValues.iterator().next();
                    deleteRelationship(driver,nodeA,versionNodeA,nodeB,versionNodeB,relationshipName,Double.parseDouble(val.toString()));
                }
                    transaction.run("MATCH (a:Library{name:'" + nodeA + "', version:'" + versionNodeA + "'})" +
                            "MATCH (b:Library{name: '" + nodeB + "', version:'" + versionNodeB + "'})" +
                            "MERGE (a)-[r:"+relationshipName+"{cost:"+weight+"}]->(b)");

                    transaction.commit();
                    //System.out.println("Relationship Created");
                }
            }
    }


    public static void deleteRelationship(Driver driver, String nodeA, String versionNodeA, String nodeB, String versionNodeB, String relationshipName, Double weight){

        try (Session session = driver.session()) {

            try (Transaction transaction = session.beginTransaction()) {

                transaction.run("MATCH (a:Library{name:'" + nodeA + "', version:'" + versionNodeA + "'})" +
                        "MATCH (b:Library{name: '" + nodeB + "', version:'" + versionNodeB + "'})" +
                        "MATCH (a)-[r:"+relationshipName+"{cost:"+weight+"}]->(b)" +
                        "DELETE r");

                transaction.commit();
                //System.out.println("Relationship Deleted");
            }
        }
    }

    public static Iterable<Value> getParametersFromRelationship(Driver driver, String nodeA, String versionNodeA, String nodeB, String versionNodeB, String relationship) {

        Iterable<Value> res = null;

        try (Session session = driver.session()) {

            try (Transaction transaction = session.beginTransaction()) {

                Result r = transaction.run("MATCH ({name : '" + nodeA + "', version: '" + versionNodeA + "'})-[rel:" + relationship + "]->({name : '" + nodeB + "', version: '" + versionNodeB + "'}) RETURN rel");
                while (r.hasNext()) {
                    Record record = r.next();
                    res = record.get("rel").asRelationship().values();
                }
            }
        }
        return res;
    }

    public static void deleteAll(Driver driver)
    {
        try ( Session session = driver.session() )
        {
            String greeting = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    tx.run("MATCH (n) DETACH DELETE n");
                    return("Db clear");
                }
            });
        }
    }

    public static ArrayList<Record> shortestPath(Driver driver, String startingNodeName, String endingNodeName, String startingNodeVersion, String endingNodeVersion, String weightName)
    {
        ArrayList<Record> result = new ArrayList<Record>();

        try (Session session = driver.session()) {

            try (Transaction transaction = session.beginTransaction()) {

                Result res = transaction.run("MATCH (start:Library{name:'"+startingNodeName+"',version:'"+startingNodeVersion+"'}), (end:Library{name:'"+endingNodeName+"',version:'"+endingNodeVersion+"'})\n" +
                        "CALL algo.shortestPath.stream(start, end, '"+weightName+"',{direction:'OUTGOING'})\n" +
                        "YIELD nodeId,cost\n" +
                        "RETURN algo.asNode(nodeId).name AS name ,algo.asNode(nodeId).version AS version,cost");

                while (res.hasNext()) {
                    Record record = res.next();
                    result.add(record);
                }
            }
        }
        return result;
    }

    public static ArrayList<Record> kShortestPath(Driver driver, String startingNodeName, String endingNodeName, String startingNodeVersion, String endingNodeVersion, String weightName, int k)
    {
        ArrayList<Record> result = new ArrayList<Record>();

        try (Session session = driver.session()) {

            try (Transaction transaction = session.beginTransaction()) {

                Result res = transaction.run("MATCH (start:Library{name:'"+startingNodeName+"',version:'"+startingNodeVersion+"'}), (end:Library{name:'"+endingNodeName+"',version:'"+endingNodeVersion+"'})\n" +
                "CALL algo.kShortestPaths.stream(start, end, "+String.valueOf(k)+" , '"+weightName+"',{direction:'OUTGOING'})\n"+
                "YIELD  nodeIds, costs\n"+
                "RETURN [node in algo.getNodesById(nodeIds) | node.version] AS places, costs," +
                "reduce(acc = 0.0, cost in costs | acc + cost) AS totalCost");

                while (res.hasNext()) {
                    Record record = res.next();
                    result.add(record);
                }
            }
        }
        return result;
    }

    public static void getAllNodes(Driver driver){

        try (Session session = driver.session()) {

            try (Transaction transaction = session.beginTransaction()) {
                Result res = transaction.run("");
            }
        }
    }

    public static void updateRelationships(Driver driver, String nodeName){

        try (Session session = driver.session()) {

            try (Transaction transaction = session.beginTransaction()) {
                Result res = transaction.run("MATCH (s{name : '"+nodeName+"'})-[r:Upgraded]->(t{name : '"+nodeName+"'})" +
                        "RETURN s,t,r");

                while (res.hasNext()) {
                    Record record = res.next();
                    Double value = record.get("r").get("cost").asDouble();
                    value = 1/value;
                    createRelationship(driver,record.get("s").get("name").asString(),record.get("s").get("version").asString(),record.get("t").get("name").asString(),record.get("t").get("version").asString(),value, "Upgraded",true);

                }
            }
        }
    }

    public static void updateRelationshipsRemovingUpgrade(Driver driver, String nodeName, String verFile){
       /*
       ver1-upgrade->ver2
       se trovo prima ver1 vuol dire che is older than ver2, quindi va bene upgrade e non devo fare niente
        */
        try (Session session = driver.session()) {

            try (Transaction transaction = session.beginTransaction()) {
                Result res = transaction.run("MATCH (s{name : '"+nodeName+"'})-[r:Upgraded]->(t{name : '"+nodeName+"'})" +
                        "RETURN s,t,r");
                while (res.hasNext()) {
                    Record record = res.next();
                    String ver1  = record.get("s").get("version").asString();
                    String ver2 = record.get("t").get("version").asString();
                    String firstVer = orderingVersionV1(ver1,ver2, verFile, nodeName);
                    if(firstVer!=null) {

                        if (firstVer.equals(ver1)) {
                           // createRelationship(driver, record.get("s").get("name").asString(), ver1, record.get("t").get("name").asString(), ver2, record.get("r").get("cost").asDouble(), "Downgraded", true);
                        } else {
                            deleteRelationship(driver, record.get("s").get("name").asString(), ver1, record.get("t").get("name").asString(), ver2, "Upgraded", record.get("r").get("cost").asDouble());
                            //createRelationship(driver, record.get("s").get("name").asString(), ver2, record.get("t").get("name").asString(), ver1, record.get("r").get("cost").asDouble(), "Downgraded", true);
                        }
                    }
                }
            }
        }
    }

    public static String orderingVersionV1(String ver1, String ver2, String verFile, String library){
        /*
        Compariamo le versioni in base alla lista ordinata crescente di maven
        */
        Path path = Paths.get(verFile);
        try {
            BufferedReader reader = Files.newBufferedReader(path);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(reader);
            for (CSVRecord record : records) {
                String ver = record.get("version");
                if(record.get("library").equals(library)) {
                    if (ver.equals(ver1)) {
                        return ver1;
                    }
                    if (ver.equals(ver2)) {
                        return ver2;
                    }
                }
            }
        }
        catch(Exception e) {
            System.out.println("Version not found: "+ver1);
        }
        return null;
    }

    public static void orderingVersionV2(){
        /*
        Compariamo le versioni in modo eursitico
        */
    }

}
