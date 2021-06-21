# EvoPlan
This folder contains the replication package for EvoPlan - EASE 2021 conference.

## Usage Step 1
```
mvn exec:java -Dexec.mainClass="org.impl.Tool.StartProjectAnalysis -Dexec.cleanupDaemonThreads=false"
```
This command will start the download and analysis of the project written inside "list.txt", you can open the file and add your GitHub token.

## Usage Step 2
After an installation of neo4j db - versions tested 3.5.14 up to 3.5.24 - you can modify the username and password inside "Utils/properties neo4jUser and neo4jPassword fields"

```
mvn exec:java -Dexec.mainClass="org.impl.Tool.StartDbCreation"
```
## Usage Step 3
Now you can launch the Evaluation for the ten-folder evaluation. To be noticed that you HAVE to change the string round inside the file and move or delete the current neo4j graph.db otherwise the current evaluation round will overwrite the previous one.
```
mvn exec:java -Dexec.mainClass="org.impl.Tool.StartEvaluation"
```
## Usage Step 4
You can try directly the tool in 2 way:
 * The manual version in which you can decide the library and the versions to check.
 * The automatic version in which the tool will scan a give project path to search possible upgrade plans.
By default is setted a manual version for "slf4j - version 1.5.8 to 1.7.25"
```
mvn exec:java -Dexec.mainClass="org.impl.Tool.StartUserVersion"
```
If there are problems related to read only folders inside OutputDiff you may try to open the project with an external envirnoment like IntelliJ Idea.
