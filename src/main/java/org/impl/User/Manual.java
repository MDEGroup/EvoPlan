package org.impl.User;

import org.impl.Recommend.Recommender;
import org.neo4j.driver.Driver;

public class Manual {

    public static void start(Driver driver, String updateLibFile, String library, String startingVer, String targetVer, Boolean issueRanking, String issueFolder){

        Recommender.recommend(driver,updateLibFile,library,startingVer,targetVer,issueRanking,issueFolder);
    }

}
