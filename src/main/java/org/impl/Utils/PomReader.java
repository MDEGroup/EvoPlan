package org.impl.Utils;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
https://zenodo.org/record/1489120
 */

public class PomReader {

        public static ArrayList<String> getList(String path) throws IOException, XmlPullParserException {
            ArrayList<String> data = new ArrayList<String>();

            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new FileReader(path));

            data.add(model.getGroupId());
            data.add(model.getArtifactId());
            data.add(model.getVersion());

            return data;
        }

        public static List<Dependency> getDependencies(String path){

            try {
                MavenXpp3Reader reader = new MavenXpp3Reader();
                Model model = reader.read(new FileReader(path));
                List<Dependency> data = model.getDependencies();
                return data;
            }
            catch(Exception e){
                System.out.println(e);
                return null;
            }

        }

    }
