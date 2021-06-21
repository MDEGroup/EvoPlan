package org.impl.Utils;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class Properties {

    private String repositoriesList = "repos_pom.txt";
    private String repositoriesFolder = "/home/rick/Scrivania/repos/";
    private String mavenLibVersion = "csv/libraryVer/ver.csv";
    private String tenFolderList = "eval_repos_refined.txt";
    private String tenEvalFolderBase = "evaluation/tenFolder/";
    private String tenEvalDiffOutputFolder = "evaluation/oct2020/";
    private String githubToken = "";
    private String outputDiffFolder = "csv/githubDiff/";
    private String issuesFolder = "csv/Issue_data/";
    private String outputBlameFolder = "";
    private String dateFormat = "yyyy-MM-dd";
    private Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic(Properties.getNeo4jUser(), Properties.getNeo4jPassword()));
    private static String neo4jUser = "neo4j";
    private static String neo4jPassword = "";

    public static String getNeo4jUser() {
        return neo4jUser;
    }

    public void setNeo4jUser(String neo4jUser) {
        this.neo4jUser = neo4jUser;
    }

    public static String getNeo4jPassword() {
        return neo4jPassword;
    }

    public void setNeo4jPassword(String neo4jPassword) {
        this.neo4jPassword = neo4jPassword;
    }

    public String getRepositoriesList() {
        return repositoriesList;
    }

    public void setRepositoriesList(String repositoriesList) {
        this.repositoriesList = repositoriesList;
    }

    public String getRepositoriesFolder() {
        return repositoriesFolder;
    }

    public void setRepositoriesFolder(String repositoriesFolder) {
        this.repositoriesFolder = repositoriesFolder;
    }

    public String getGithubToken() {
        return githubToken;
    }

    public void setGithubToken(String githubToken) {
        this.githubToken = githubToken;
    }

    public String getOutputDiffFolder() {
        return outputDiffFolder;
    }

    public void setOutputDiffFolder(String outputDiffFolder) {
        this.outputDiffFolder = outputDiffFolder;
    }

    public String getOutputBlameFolder() {
        return outputBlameFolder;
    }

    public void setOutputBlameFolder(String outputBlameFolder) {
        this.outputBlameFolder = outputBlameFolder;
    }

    public Driver getDriver() {
        return driver;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public String getMavenLibVersion() {
        return mavenLibVersion;
    }

    public void setMavenLibVersion(String mavenLibVersion) {
        this.mavenLibVersion = mavenLibVersion;
    }

    public String getTenEvalFolderBase() {
        return tenEvalFolderBase;
    }

    public void setTenEvalFolderBase(String tenEvalFolder) {
        this.tenEvalFolderBase = tenEvalFolder;
    }

    public String getTenFolderList() {
        return tenFolderList;
    }

    public void setTenFolderList(String tenFolderList) {
        this.tenFolderList = tenFolderList;
    }

    public String getTenEvalDiffOutputFolder() {
        return tenEvalDiffOutputFolder;
    }

    public void setTenEvalDiffOutputFolder(String tenEvalDiffOutputFolder) {
        this.tenEvalDiffOutputFolder = tenEvalDiffOutputFolder;
    }

    public String getIssuesFolder() {
        return issuesFolder;
    }

    public void setIssuesFolder(String issuesFolder) {
        this.issuesFolder = issuesFolder;
    }
}
