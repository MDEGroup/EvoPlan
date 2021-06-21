package org.impl.RepositoriesAnalysis;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import com.google.common.collect.Lists;
import org.apache.maven.model.Dependency;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.impl.Utils.PomReader;
import org.impl.Utils.Properties;

/*
https://stackoverflow.com/questions/39935160/how-to-use-jgit-to-get-list-of-changes-in-files
https://stackoverflow.com/questions/27361538/how-to-show-changes-between-commits-with-jgit
https://stackoverflow.com/questions/53295431/how-to-do-git-log-l-with-jgit
 */

public class RepositoriesAnalysis {

    public static Properties prop = new Properties();
    public static DateFormat dateFormat = new SimpleDateFormat(prop.getDateFormat());

    public static void startAnalysis(String repositoriesList, String repositoriesFolder,String outputFolder, String gitHubToken){

        ArrayList<String> listOfRepositories = new ArrayList<String>();

        String owner = "";
        String repo = "";
        File f = new File(repositoriesList);

        if (f.exists()) {
            listOfRepositories = getRepoFromFile(repositoriesList);
        }

        for (String elem : listOfRepositories) {
            if(elem.contains("/")) {
                int index = elem.indexOf("/");
                owner = elem.substring(0, index);
                repo = elem.substring(index + 1);
            }
            else{continue;}

            Git repository = download(owner, repo, repositoriesFolder + owner + "/" + repo, gitHubToken);
            if (repository == null) {
                continue;
            }
            List<File> pomsList = getFilesByEndingValue(new File(repositoriesFolder + owner + "/" + repo), "pom.xml");

            int progrNumber = 0;
            for (File pomFile : pomsList) {
                HashMap<String, ArrayList<String>> dependenciesVersions = new HashMap<String, ArrayList<String>>();
                List<Dependency> dep = PomReader.getDependencies(pomFile.toString());

                if (dep != null&&dep.size()>0) {
                    ArrayList<HashMap<String, Date>> commits = log(repository, repositoriesFolder + elem, pomFile);
                    //for tutti i commit relativi a questo file
                    for (int i = 0; i < commits.size() - 1; i += 1) {
                        dependenciesVersions = diff(repository, commits.get(i), commits.get(i + 1), dep, dependenciesVersions, owner + "/" + repo);
                    }
                }
                writeDiffToFile(elem.replace("/", "@"), dependenciesVersions, progrNumber, outputFolder);
                progrNumber = progrNumber + 1;
            }
        }
    }

    public static ArrayList<String> getRepoFromFile(String path) {
        ArrayList<String> l = new ArrayList<String>();

        try (BufferedReader br = Files.newBufferedReader(Paths.get(path))) {

            String line;

            while ((line = br.readLine()) != null) {
                l.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return l;
    }

    public static Git download(String owner, String repoName, String location, String token){
        // i file li salva in c:\nome utente
        String baseUrl = "https://github.com/";
        Git repo = null;

        try {
            File f = new File(location);
            if (!f.exists()) {
                repo = Git.cloneRepository()
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider("${token}", token))
                        //.setCredentialsProvider(new UsernamePasswordCredentialsProvider("",""))
                        .setURI(baseUrl + owner + "/" + repoName)
                        .setDirectory(new File(location))
                        .call();
            } else {
                repo = Git.open(new File(location));
            }
        } catch (Exception e) {
            System.out.println(e);
        }

        return repo;
    }

    public static ArrayList<HashMap<String, Date>> log(Git repo, String path, File file) {
        Iterable<RevCommit> logs = null;
        ArrayList<HashMap<String, Date>> result = new ArrayList<HashMap<String, Date>>();

        System.out.println("Log for " + file);
        try {
            logs = repo.log()
                    .addPath(file.getPath().replace(path + "/", ""))
                    .call();

            if(!logs.iterator().hasNext()){
                logs = repo.log()
                        .addPath(file.getAbsolutePath().replace(path + "/", ""))
                        .call();
            }

            for (RevCommit rev : logs) {
                //System.out.println("Commit: " + rev  + ", name: " + rev.getName() + ", id: " + rev.getId().getName() );
                PersonIdent authorIdent = rev.getAuthorIdent();
                Date authorDate = authorIdent.getWhen();
                HashMap<String, Date> r = new HashMap<>();
                r.put(rev.getName(),authorDate);
                result.add(r);
            }
        }
        catch (Exception e) {
            System.out.println(e);
        }
        return result;

    }

    public static HashMap<String, ArrayList<String>> diff(Git repo, HashMap<String, Date> newCommit, HashMap<String, Date> oldCommit, List<Dependency> dep, HashMap<String, ArrayList<String>> dependenciesVersions, String s){

        ObjectReader reader = repo.getRepository().newObjectReader();
        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
        try{
            ObjectId oldTree = repo.getRepository().resolve(oldCommit.entrySet().iterator().next().getKey() + "^{tree}");
            oldTreeIter.reset(reader, oldTree);
            ObjectId newTree = repo.getRepository().resolve(newCommit.entrySet().iterator().next().getKey() + "^{tree}");
            newTreeIter.reset(reader, newTree);
        }
        catch(Exception e){
        }
        OutputStream outputStream = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(outputStream)) {
            formatter.setRepository(repo.getRepository());
            formatter.format(oldTreeIter, newTreeIter);
        }
        catch(Exception e){
            System.out.println(e);
        }
        String diff = outputStream.toString();

        dependenciesVersions = analyzeDiff(diff, dep, dependenciesVersions,oldCommit.entrySet().iterator().next().getValue(),s);

        return dependenciesVersions;
    }

    public static HashMap<String, ArrayList<String>> analyzeDiff(String diff, List<Dependency> dep, HashMap<String, ArrayList<String>> dependenciesVersions, Date date, String s) {

        /* caso mal gestito
        *  		<dependency>
 			<groupId>org.slf4j</groupId>
 			<artifactId>slf4j-log4j12</artifactId>
-			<version>1.6.6</version>
+			<version>1.7.1</version>
 		</dependency>
        * */


        String strDate = dateFormat.format(date);
        /*
        qui devo analizzare una stringa che mi rappresenta il contenuto di tutte le modifiche fatte tra 2 commit, tali
        modifiche sicuramente interesseranno il pom che sto guardando sopra, però ce ne protrebbero essere anche altri
        in teoria dovrei cercare proprio quel file anzichè un generico pom.xml
         */
        ArrayList<String> aux = new ArrayList<String>();
        /*
        Sistema stupido, controllo solo ci sono <dep.version> e <artifact>dep
         */
        ArrayList<Integer> indexes = new ArrayList<>();

        for (Dependency d : dep) {
            indexes = getIndexes(diff, "<" + d.getArtifactId() + ".version>");
            for (Integer i : indexes) {
                //int startPropertiesDependencyIndex = diff.indexOf("<" + d.getArtifactId() + ".version>");
                int startPropertiesDependencyIndex = i;
                int endPropertiesDependencyIndex = diff.indexOf("</" + d.getArtifactId() + ".version>", startPropertiesDependencyIndex);

                /*
                se trova qualcosa(!=1) e se la lunghezza di quello in mezzo minore di 10, vuol dire se la lunghezza del potenziale numero di versione <10
                &&(endPropertiesDependencyIndex-startPropertiesDependencyIndex+10+d.getArtifactId().length())<=10
                 */

                if (startPropertiesDependencyIndex != -1 && endPropertiesDependencyIndex != -1) {
                    String ver = diff.substring(startPropertiesDependencyIndex + 10 + d.getArtifactId().length(), endPropertiesDependencyIndex);
                    String rawSign = diff.substring(diff.lastIndexOf("\n", startPropertiesDependencyIndex), startPropertiesDependencyIndex);
                    if (rawSign.contains("+") || rawSign.contains("-")) {
                        String sign = findSign(rawSign);
                        if (!dependenciesVersions.computeIfAbsent(d.getArtifactId(), k -> new ArrayList<String>()).add(sign + ver+";"+strDate)) {
                            aux = dependenciesVersions.get(d.getArtifactId());
                            aux.add(sign + ver+";"+strDate);
                            dependenciesVersions.put(d.getArtifactId(), aux);
                        }
                        String followingVer = getDeletionInsertion$(diff,startPropertiesDependencyIndex,d.getArtifactId());
                        if(followingVer!=null){
                            String followingSign = followingVer.substring(0,1);
                            if (!dependenciesVersions.computeIfAbsent(d.getArtifactId(), k -> new ArrayList<String>()).add(followingSign + followingVer.substring(1)+";"+strDate)) {
                                aux = dependenciesVersions.get(d.getArtifactId());
                                aux.add(followingSign + followingVer.substring(1)+";"+strDate);
                                dependenciesVersions.put(d.getArtifactId(), aux);
                            }
                        }
                    }
                }
            }

            indexes = getIndexes(diff, "<artifactId>" + d.getArtifactId() + "</artifactId>");
            for (Integer i : indexes) {
                String dependencySearchString = "<artifactId>" + d.getArtifactId() + "</artifactId>";
                //int startDependencyIndex = diff.indexOf(dependencySearchString);
                int startDependencyIndex = i;
                int startVersionIndex = diff.indexOf("<version>", startDependencyIndex);
                if (startVersionIndex != -1 && (startVersionIndex - (startDependencyIndex + dependencySearchString.length()) < 20)) {
                    String ver = diff.substring(startVersionIndex + 9, diff.indexOf("</version>", startVersionIndex));
                    if (!ver.contains("${")) {
                        String rawSign = diff.substring(diff.lastIndexOf("\n", startVersionIndex), startVersionIndex);
                        if (rawSign.contains("+") || rawSign.contains("-")) {
                            String sign = findSign(rawSign);
                            if (!dependenciesVersions.computeIfAbsent(d.getArtifactId(), k -> new ArrayList<String>()).add(sign + ver+";"+strDate)) {
                                aux = dependenciesVersions.get(d.getArtifactId());
                                aux.add(sign + ver+";"+strDate);
                                dependenciesVersions.put(d.getArtifactId(), aux);
                            }
                            String followingVer = getDeletionInsertion(diff,startVersionIndex);
                            if(followingVer!=null) {
                                String followingSign = followingVer.substring(0,1);
                                if (!dependenciesVersions.computeIfAbsent(d.getArtifactId(), k -> new ArrayList<String>()).add(followingSign + followingVer.substring(1) + ";" + strDate)) {
                                    aux = dependenciesVersions.get(d.getArtifactId());
                                    aux.add(followingSign + followingVer.substring(1) + ";" + strDate);
                                    dependenciesVersions.put(d.getArtifactId(), aux);
                                }
                            }
                        }
                    }
                }
            }
        }
        return (dependenciesVersions);
    }

    public static ArrayList<Integer> getIndexes(String diff, String searchString) {

        int index = 0;
        ArrayList<Integer> indexPositions = new ArrayList<Integer>();

        while (diff.indexOf(searchString, index) != -1) {
            index = diff.indexOf(searchString, index);
            indexPositions.add(index);
            index = index + searchString.length();
        }
        return indexPositions;
    }

    public static String findSign(String snippet) {

        String result = "";
        int index = snippet.indexOf("+");

        if (index != -1) {
            result = snippet.substring(index, index + 1);
        } else {
            index = snippet.indexOf("-");
            result = snippet.substring(index, index + 1);
        }

        return result;
    }

    public static String getDeletionInsertion(String diff, int startVersionIndex){

        int eol = diff.indexOf("\n",startVersionIndex);
        int followingEol = diff.indexOf("\n",eol+1);
        String potentialVersion = diff.substring(eol,followingEol);
        if(potentialVersion.contains("+")||potentialVersion.contains("-")) {
            String sign = findSign(potentialVersion);
            if(potentialVersion.contains("<version>")&&potentialVersion.contains("</version>")&&!potentialVersion.contains("%{")){
                String ver = potentialVersion.substring(potentialVersion.indexOf("<version>")+9, potentialVersion.indexOf("</version>", 9));
                return(sign+ver);
            }
        }


        return null;
    }

    public static String getDeletionInsertion$(String diff, int startVersionIndex, String artifactId){

        int eol = diff.indexOf("\n",startVersionIndex);
        int followingEol = diff.indexOf("\n",eol+1);
        String potentialVersion = diff.substring(eol,followingEol);
        if (potentialVersion.contains(artifactId)) {
            if(potentialVersion.contains("+")||potentialVersion.contains("-")) {
                String sign = findSign(potentialVersion);
                String ver = potentialVersion.substring(potentialVersion.indexOf(artifactId+".version")+artifactId.length()+9,potentialVersion.indexOf("</"));
                return(sign+ver);
            }
        }
        return null;
    }

    public static HashMap<String, String> analyzeBlame(BlameResult result) {
        HashMap<String, String> res = new HashMap<String, String>();

        final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("YYYY-MM-dd HH:mm");
        final RawText rawText = result.getResultContents();

        for (int i = 0; i < rawText.size(); i++) {
            final RevCommit sourceCommit = result.getSourceCommit(i);


            System.out.println(rawText.getString(i));


            if (rawText.getString(i).contains("<dependency>")) {
                String depend = rawText.getString(i + 2).replace("<artifactId>", "");
                depend = depend.replace("</artifactId>", "");
                String version = rawText.getString(i + 3).replace("<version>", "");
                version = version.replace("</version>", "");
                res.put(depend.trim() + ";" + version.trim(), DATE_FORMAT.format(((long) sourceCommit.getCommitTime()) * 1000));
            }

        }

        HashMap<String, String> newRes = new HashMap<String, String>();

        for (String elem : res.keySet()) {

            if (!elem.matches(".*\\d+.*")) {
                String ver = elem.substring(elem.indexOf("${") + 2, elem.length() - 1);

                for (int i = 0; i < rawText.size(); i++) {
                    String s = rawText.getString(i);
                    if (s.contains(ver)) {
                        try {
                            String date = res.get(elem);
                            String newVersion = s.substring(s.indexOf(">") + 1, s.indexOf("<", s.indexOf(">"))).trim();
                            if (!newVersion.matches(".*\\d+.*") || newVersion.contains("${")) {
                                continue;
                            } else {
                                newRes.put(elem.substring(0, elem.indexOf(";")).trim() + ";" + newVersion, date);
                                break;
                            }

                        } catch (Exception e) {
                            System.out.println(e);
                        }
                    }
                }

            } else {
                newRes.put(elem, res.get(elem));
            }
        }


        return newRes;
    }

    public static void writeDiffToFile(String repoName, HashMap<String, ArrayList<String>> dependenciesVersions, int progrNumber, String outputFolder) {


        try {
            if(!Files.exists(Paths.get(outputFolder))) {
                Files.createDirectory(Paths.get(outputFolder));
            }
            if(!Files.exists(Paths.get(outputFolder + repoName))) {
                Files.createDirectory(Paths.get(outputFolder + repoName));
            }
        }
        catch(Exception e){
            System.out.println(e);
        }
        Path path = Paths.get(outputFolder + repoName + "/" + repoName + progrNumber + ".csv");

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            StringBuilder sb = new StringBuilder();
            sb.append("library");
            sb.append(",");
            sb.append("version");
            sb.append(",");
            sb.append("sign");
            sb.append(",");
            sb.append("date");
            sb.append("\n");
            writer.write(sb.toString());

            for (String res : dependenciesVersions.keySet()) {
                List<String> versions = new ArrayList<>(new HashSet<>(dependenciesVersions.get(res)));

                for (String elem : versions) {
                    /*
                    elem = +1.7;01-01-2020
                     */
                    sb = new StringBuilder();
                    sb.append(res);
                    sb.append(",");
                    sb.append(elem, 1, elem.indexOf(";"));
                    sb.append(",");
                    sb.append(elem, 0, 1);
                    sb.append(",");
                    sb.append(elem.substring(elem.indexOf(";")+1));
                    sb.append("\n");
                    writer.write(sb.toString());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static List<File> getFilesByEndingValue(File rootFolder, String extension) {
        List<File> result = Lists.newArrayList();

        if (rootFolder.isDirectory())
            result.addAll(Arrays.asList(rootFolder.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String filename) {
                    File f = new File(dir.getAbsolutePath() + "/" + filename);
                    return filename.toLowerCase().endsWith(extension) && !f.isDirectory();
                }
            })));
        List<File> listFolder = Arrays.asList(rootFolder.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                File f = new File(dir.getAbsolutePath() + File.separator + filename);
                return f.isDirectory();
            }
        }));

        for (File file : listFolder) {
            result.addAll(getFilesByEndingValue(file, extension));
        }
        return result;
    }

    /*
    Roba vecchia
     */

    public static void writeBlameToFile(String repoName, ArrayList<HashMap<String, String>> results) {

        Path path = Paths.get("csv/githubBlame/" + repoName + ".csv");

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            StringBuilder sb = new StringBuilder();
            sb.append("library");
            sb.append(",");
            sb.append("version");
            sb.append(",");
            sb.append("date");
            sb.append("\n");
            writer.write(sb.toString());

            for (HashMap<String, String> res : results) {
                for (String elem : res.keySet()) {
                    sb = new StringBuilder();
                    sb.append(elem, 0, elem.indexOf(";"));
                    sb.append(",");
                    sb.append(elem.substring(elem.indexOf(";") + 1));
                    sb.append(",");
                    sb.append(res.get(elem));
                    sb.append("\n");
                    writer.write(sb.toString());
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<String> getRepos() throws IOException {
        //a3d3c26c8cf3683b1c18559051429f6cc6ecfb1f
        //https://api.github.com/search/code?q=addClass+in:file+language:js+repo:jquery/jquery
        String url = "https://api.github.com/search/code?q=pom";
        //String url = "https://api.github.com/repositories?since=" + since;

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet(url);
        request.addHeader("content-type", "application/json");
        HttpResponse result = httpClient.execute(request);
        String json = EntityUtils.toString(result.getEntity(), "UTF-8");


        JsonElement jelement = new JsonParser().parse(json);

        ArrayList<String> reposList = new ArrayList<String>();

        String s = jelement.toString();
        int index = 0;
        while (index + 10000 <= s.length()) {
            index = s.indexOf("full_name", index) + 12;
            int index2 = s.indexOf('"', index);
            reposList.add(s.substring(index, index2));
        }

        return reposList;
    }

    public static void deleteLocalFolder(String destination) throws IOException {
        FileUtils.deleteDirectory(new File(destination));
    }

    public static void blame(Git repo, String path, String repoName) {
        BlameResult res = null;
        ArrayList<HashMap<String, String>> results = new ArrayList<>();
        List<File> files = null;

        try {
            files = getFilesByEndingValue(new File(path), "pom.xml");
        } catch (Exception e) {
            System.out.println(e);
        }

        if (files != null) {
            for (File file : files) {

                System.out.println("Blaming " + file);
                try {

                    BlameCommand blameCommand = repo.blame();
                    blameCommand.setStartCommit(repo.getRepository().resolve("HEAD"));
                    blameCommand.setFilePath(file.getAbsolutePath().replace(path + "/", ""));
                    res = blameCommand.call();
                } catch (Exception e) {
                    System.out.println(e);
                }

                if (res != null) {
                    results.add(analyzeBlame(res));
                }

            }
        }
        writeBlameToFile(repoName, results);
    }

}