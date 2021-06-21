package org.impl.Utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.TreeMap;

public class CsvManager
{

    public static HashMap<String, Integer> readFromPyGit(Path path) throws IOException {
        BufferedReader reader = Files.newBufferedReader( path );
        Iterable < CSVRecord > records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse( reader );

        HashMap<String,Integer> result = new HashMap<String, Integer>();

        for ( CSVRecord record : records )
        {
            result.put(record.get("source")+";"+record.get("target"),Integer.valueOf(record.get("closed_issues"))-Integer.valueOf(record.get("open_issues")));
        }

        return result;

    }

    public static HashMap<String, String> readFromBlame(Path path) throws IOException {
        BufferedReader reader = Files.newBufferedReader( path );
        Iterable < CSVRecord > records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse( reader );

        HashMap<String,String> result = new HashMap<String, String>();

        for ( CSVRecord record : records )
        {
            result.put(record.get("library")+";"+record.get("version"),record.get("date"));
        }

        return result;

    }

    public static TreeMap<String, String> readFromDiff(Path path) throws IOException {

        BufferedReader reader = Files.newBufferedReader( path );
        Iterable < CSVRecord > records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse( reader );

        TreeMap<String,String> result = new TreeMap<String, String>();

        for ( CSVRecord record : records )
        {
            result.put(record.get("library")+";"+record.get("version")+";"+record.get("date"),record.get("sign"));
        }

        reader.close();
        return result;
    }

}
