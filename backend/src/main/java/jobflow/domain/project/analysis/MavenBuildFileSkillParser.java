package jobflow.domain.project.analysis;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class MavenBuildFileSkillParser implements BuildFileSkillParser {

    @Override
    public boolean supports(BuildFileType type) {
        return type == BuildFileType.MAVEN;
    }

    @Override
    public List<BuildFileSkillCandidate> parse(RepositoryBuildFile buildFile) {
        if (buildFile == null || !buildFile.hasContent()) {
            return List.of();
        }

        try {
            Element root = parseRoot(buildFile.content());
            List<BuildFileSkillCandidate> candidates = new ArrayList<>();
            candidates.addAll(parseElements(root, "dependency", "maven dependency"));
            candidates.addAll(parseElements(root, "plugin", "maven plugin"));
            return candidates;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Element parseRoot(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        return factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(content)))
                .getDocumentElement();
    }

    private List<BuildFileSkillCandidate> parseElements(
            Element root,
            String tagName,
            String evidencePrefix
    ) {
        NodeList nodes = root.getElementsByTagName(tagName);
        List<BuildFileSkillCandidate> candidates = new ArrayList<>();

        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            String groupId = childText(element, "groupId");
            String artifactId = childText(element, "artifactId");
            String evidence = evidencePrefix + " " + groupId + ":" + artifactId;
            candidates.addAll(BuildDependencySkillDictionary.match(evidence));
        }

        return candidates;
    }

    private String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }
}
