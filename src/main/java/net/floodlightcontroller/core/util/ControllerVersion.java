package net.floodlightcontroller.core.util;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class ControllerVersion {
    private static final Logger log = LoggerFactory.getLogger(ControllerVersion.class);

    public synchronized static final String getFloodlightVersion() {  
        /* First, check our manifest to see if it's been set */
        String version = null;
        String title = null;
        Package pkg = ControllerVersion.class.getPackage();
        if (pkg != null) {
            version = pkg.getImplementationVersion();
            if (version == null) {
                version = pkg.getSpecificationVersion();
            }
            if (version != null) {
                version = version.trim();
                if (!version.isEmpty()) {
                    log.info("Floodlight version {} found in manifest", version);
                    return version;
                }
            }
            title = pkg.getImplementationTitle();
            if (title == null) {
                title = pkg.getSpecificationTitle();
            }
        }
        
        
        /* Try to get version number from pom.xml (available in Eclipse) */
        try {
            String className = ControllerVersion.class.getName();
            String classfileName = "/" + className.replace('.', '/') + ".class";
            URL classfileResource = ControllerVersion.class.getResource(classfileName);
            if (classfileResource != null) {
                Path absolutePackagePath = Paths.get(classfileResource.toURI())
                        .getParent();
                int packagePathSegments = className.length()
                        - className.replace(".", "").length();
                /*
                 * Remove package segments from path, plus two more levels
                 * for "target/classes", which is the standard location for
                 * classes in Eclipse.
                 */
                Path path = absolutePackagePath;
                for (int i = 0, segmentsToRemove = packagePathSegments + 2;
                        i < segmentsToRemove; i++) {
                    path = path.getParent();
                }
                Path pom = path.resolve("pom.xml");
                try (InputStream is = Files.newInputStream(pom)) {
                    Document doc = DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder().parse(is);
                    doc.getDocumentElement().normalize();
                    version = (String) XPathFactory.newInstance()
                            .newXPath().compile("/project/version")
                            .evaluate(doc, XPathConstants.STRING);
                    if (version != null) {
                        version = version.trim();
                        if (version.isEmpty()) {
                            return "unknown";
                        } else {
                            log.info("Floodlight version {} found in pom.xml", version);
                            return version;
                        }
                    }
                }
            }
        } catch (Exception e) { }
        return "unknown";
    }
    
    public synchronized static final String getFloodlightName() {  
        /* First, check our manifest to see if it's been set */
        String title = null;
        Package pkg = ControllerVersion.class.getPackage();
        if (pkg != null) {
            title = pkg.getImplementationTitle();
            if (title == null) {
                title = pkg.getSpecificationTitle();
            }
            if (title != null) {
                title = title.trim();
                if (!title.isEmpty()) {
                    log.info("Floodlight name {} found in manifest", title);
                    return title;
                }
            }
        }
        
        
        /* Try to get version number from pom.xml (available in Eclipse) */
        try {
            String className = ControllerVersion.class.getName();
            String classfileName = "/" + className.replace('.', '/') + ".class";
            URL classfileResource = ControllerVersion.class.getResource(classfileName);
            if (classfileResource != null) {
                Path absolutePackagePath = Paths.get(classfileResource.toURI())
                        .getParent();
                int packagePathSegments = className.length()
                        - className.replace(".", "").length();
                /*
                 * Remove package segments from path, plus two more levels
                 * for "target/classes", which is the standard location for
                 * classes in Eclipse.
                 */
                Path path = absolutePackagePath;
                for (int i = 0, segmentsToRemove = packagePathSegments + 2;
                        i < segmentsToRemove; i++) {
                    path = path.getParent();
                }
                Path pom = path.resolve("pom.xml");
                try (InputStream is = Files.newInputStream(pom)) {
                    Document doc = DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder().parse(is);
                    doc.getDocumentElement().normalize();
                    title = (String) XPathFactory.newInstance()
                            .newXPath().compile("/project/name")
                            .evaluate(doc, XPathConstants.STRING);
                    if (title != null) {
                        title = title.trim();
                        if (title.isEmpty()) {
                            return "unknown";
                        } else {
                            log.info("Floodlight name {} found in pom.xml", title);
                            return title;
                        }
                    }
                }
            }
        } catch (Exception e) { }
        return "unknown";
    }
}