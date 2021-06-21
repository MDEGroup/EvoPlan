package org.impl.Tool;

import org.impl.RepositoriesAnalysis.RepositoriesAnalysis;

public class StartProjectAnalysis {

    public static void main(String[] args){

        String repositoriesList = "data/Projects/list.txt"; //the list of projects to analyze
        String repositoriesFolder = "data/Projects/StoredProjects/";//the folder where the projects will be downloaded
        String outputFolder = "data/Projects/OutputDiff/";//the folder where the csv will be saved
        String gitHubToken = "";

        RepositoriesAnalysis.startAnalysis(repositoriesList,repositoriesFolder,outputFolder,gitHubToken);
    }

}
