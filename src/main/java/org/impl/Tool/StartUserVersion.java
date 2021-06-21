package org.impl.Tool;

import org.impl.User.Automated;
import org.impl.User.Manual;
import org.impl.Utils.Properties;
import org.neo4j.driver.Driver;

public class StartUserVersion {

    public static void main(String[] args){
        Properties properties = new Properties();
        Driver driver = properties.getDriver();
        Boolean automatic = false;
        /*
        manual version variables
         */
        String updateLibFile = "data/Utils/libraryVer/ver.csv";
        String library = "slf4j-api";
        String startingVar = "1.5.8";
        String targetVar = "1.7.25";
        Boolean issues = false;
        String issuesFolder = "data/Utils/Issue_data/";
        /*
        automatic version variables
         */
        String projectPath = "/home/rick/git/SoRec/";

        if(automatic){
            Automated.start(driver,projectPath,updateLibFile,issues,issuesFolder);
        }
        else{
            Manual.start(driver,updateLibFile,library,startingVar,targetVar,issues,issuesFolder);
        }
        driver.close();
    }
}
