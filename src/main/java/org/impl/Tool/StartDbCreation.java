package org.impl.Tool;

import org.impl.Neo4j.Csv2Neo;

public class StartDbCreation {

    public static void main(String[] args){

        String csvFolder = "data/Projects/OutputDiff/";

        Csv2Neo.createDB(csvFolder);
    }

}
