package net.stemmaweb.stemmarestRDF;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;

public class Converter {

    boolean useStar;
    String srest;
    String sdata;
    String intern;

    HashMap<String,Resource> substitutions;

    public Converter(boolean star, String ontology, String datapre, String internpre) {
        this.useStar = star;
        this.srest = ontology;
        this.sdata = datapre;
        this.intern = internpre;
        this.substitutions = new HashMap<>();
    }

    public Model readGraphML(File gmlFile) throws FileNotFoundException {
        InputStream filestream = new FileInputStream(gmlFile);
        Document doc = openFileStream(filestream);
        if (doc == null) return null;

        Element rootEl = doc.getDocumentElement();
        rootEl.normalize();

        Model model = ModelFactory.createDefaultModel();
        String traditionNode = null;

        // Get the data keys and their types; the map entries are e.g.
        // "dn0" -> ["neolabel", "string"]
        HashMap<String, String[]> dataKeys = new HashMap<>();
        NodeList keyNodes = rootEl.getElementsByTagName("key");
        for (int i = 0; i < keyNodes.getLength(); i++) {
            NamedNodeMap keyAttrs = keyNodes.item(i).getAttributes();
            String[] dataInfo = new String[]{keyAttrs.getNamedItem("attr.name").getNodeValue(),
                    keyAttrs.getNamedItem("attr.type").getNodeValue()};
            dataKeys.put(keyAttrs.getNamedItem("id").getNodeValue(), dataInfo);
        }

        // Stick here our hard-coded semantic conversions
        List<String> staticClasses = Arrays.asList("AnnotationLabel", "Links", "Properties", "Reading", "Emendation",
                "StartReading", "EndReading", "Lacuna", "RelationType", "Section", "Stemma", "Tradition", "Witness");
        List<String> internalLabels = Arrays.asList("quotesigil", "version", "from_jobid", "section_id");
        HashMap<String,String> subclassProperties = new HashMap<>();
        subclassProperties.put("is_start", "StartReading");
        subclassProperties.put("is_end", "EndReading");
        subclassProperties.put("is_lacuna", "Lacuna");

        // Iterate through the nodes and make RDF labels for them
        NodeList entityNodes = rootEl.getElementsByTagName("node");
        for (int i = 0; i < entityNodes.getLength(); i++) {
            Node n = entityNodes.item(i);
            NamedNodeMap entityAttrs = n.getAttributes();
            String xmlId = entityAttrs.getNamedItem("id").getNodeValue();
            NodeList dataNodes = ((Element) n).getElementsByTagName("data");
            HashMap<String, Object> nodeProperties = returnProperties(dataNodes, dataKeys);
            if (!nodeProperties.containsKey("neolabel"))
                continue;  // TODO real error handling
            String neolabel = nodeProperties.remove("neolabel").toString();
            String[] entityLabel = neolabel.replace("[", "").replace("]", "").split(",\\s+");
            // Make the thing's URI. If it is a tradition node, the URI uses the UUID
            boolean isTradition = Arrays.asList(entityLabel).contains("TRADITION");
            Resource iri;
            if (isTradition) {
                iri = model.createResource(sdata + nodeProperties.remove("id").toString());
                substitutions.put(xmlId, iri);
                traditionNode = xmlId;
            } else {
                iri = model.createResource(sdata + xmlId);
            }
            // Find its class
            String finalLabel;
            if (entityLabel.length > 1 && Arrays.asList(entityLabel).contains("EMENDATION"))
                finalLabel = "Emendation";
            else if (entityLabel[0].equals("ANNOTATIONLABEL"))
                finalLabel = "AnnotationLabel";
            else
                finalLabel = convertLabel(entityLabel[0], true);

            // Give it its data properties
            for (String k : nodeProperties.keySet()) {
                // Look for special-case properties, especially for readings, that we turn into subclasses
                if (subclassProperties.containsKey(k)) {
                    finalLabel = subclassProperties.get(k);
                    continue;
                }
                // Node data properties should be camel case; we might also need to change some
                String kconv = convertLabel(k, false);
                String ns = internalLabels.contains(k) ? intern : srest;
                Property predicate = model.createProperty(ns + kconv);
                Object v = nodeProperties.get(k);
                if (v instanceof String[] val)
                    for (String s : val)
                        iri.addProperty(predicate, s);
                else
                    iri.addProperty(predicate, model.createTypedLiteral(nodeProperties.get(k)));
            }
            // Now finally say what it is
            iri.addProperty(RDF.type, model.createResource(srest + finalLabel));
            if (!staticClasses.contains(finalLabel))
                iri.addProperty(RDF.type, model.createProperty(srest + "Annotation"));
            // If it is a witness, save its sigil in our lookup table
            if (finalLabel.equals("Witness") && nodeProperties.get("hypothetical").equals(false))
                substitutions.put("WITNESS-" + nodeProperties.get("sigil").toString(), iri);
            // If it is a relation type, save its name in our lookup table
            if (finalLabel.equals("RelationType"))
                substitutions.put("RTYPE-" + nodeProperties.get("name").toString(), iri);
        }

        // Iterate through the relationships and make object properties and, if we are asked to,
        // triple properties with RDF-star
        NodeList entityRelationships = rootEl.getElementsByTagName("edge");
        for (int i = 0; i < entityRelationships.getLength(); i++) {
            Node r = entityRelationships.item(i);
            NamedNodeMap relAttrs = r.getAttributes();

            String srcID = relAttrs.getNamedItem("source").getNodeValue();
            String trgID = relAttrs.getNamedItem("target").getNodeValue();
            Resource srcIRI = substitutions.getOrDefault(srcID, model.createResource(sdata + srcID));
            Resource trgIRI = substitutions.getOrDefault(trgID, model.createResource(sdata + trgID));
            // These will be the relationship properties, and type.
            NodeList dataNodes = ((Element) r).getElementsByTagName("data");
            HashMap<String, Object> nodeProperties = returnProperties(dataNodes, dataKeys);
            // Fish out the type
            if (!nodeProperties.containsKey("neolabel"))
                continue;  // TODO real error handling
            // Get the RDF name for the object property we have here
            String rdfname = convertLabel(nodeProperties.remove("neolabel").toString(), false);
            if (srcID.equals(traditionNode) && rdfname.equals("hasWitness"))
                rdfname = "witnessedBy";
            Property propIRI = model.createProperty(srest + rdfname);
            // Now add the relationship properties as RDF*
            if (nodeProperties.isEmpty() || !this.useStar)
                model.add(srcIRI, propIRI, trgIRI);
            else {
                Statement st = ResourceFactory.createStatement(srcIRI, propIRI, trgIRI);
                Resource rst = model.createResource(st);
                for (String k : nodeProperties.keySet()) {
                    String ns = internalLabels.contains(k) ? intern : srest;
                    Property spIRI = model.createProperty(ns + k);
                    Object v = nodeProperties.get(k);
                    if (v instanceof String[] val)
                        for (String s : val)
                            // Commented out witness sigil replacement logic
                            if (rdfname.equals("sequence") && substitutions.containsKey("WITNESS-" + s))
                                model.add(rst, spIRI, substitutions.get("WITNESS-" + s));
                            else
                                model.add(rst, spIRI, model.createTypedLiteral(s));
                    else
                        if (rdfname.equals("related") && k.equals("type") && substitutions.containsKey("RTYPE-" + v))
                            model.add(rst, spIRI, substitutions.get("RTYPE-" + v));
                        else
                            model.add(rst, spIRI, model.createTypedLiteral(nodeProperties.get(k)));
                }
            }
        }
        return model;
    }

    private static String convertLabel(String n4jlabel, boolean isEntity) {
        String[] parts = n4jlabel.split("_");
        // Special cases
        if (n4jlabel.equals("PART"))
            return "hasSection";
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.isEmpty() && !isEntity)
                sb.append(p.toLowerCase());
            else {
                sb.append(p.toUpperCase().charAt(0));
                sb.append(p.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private static Document openFileStream(InputStream filestream) {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(filestream);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static HashMap<String, Object> returnProperties(NodeList dataNodes, HashMap<String, String[]> dataKeys) {
        HashMap<String, Object> nodeProperties = new HashMap<>();
        for (int j = 0; j < dataNodes.getLength(); j++) {
            org.w3c.dom.Node datumXML = dataNodes.item(j);
            String keyCode = datumXML.getAttributes().getNamedItem("key").getNodeValue();
            String keyVal = datumXML.getTextContent();
            String[] keyInfo = dataKeys.get(keyCode);
            Object propValue = switch (keyInfo[1]) {
                case "boolean" -> Boolean.valueOf(keyVal);
                case "long" -> Long.valueOf(keyVal);
                case "int" -> Integer.valueOf(keyVal);
                case "stringarray" -> keyVal.replace("[", "").replace("]", "").split(",\\s+");
                default -> // e.g. "string"
                        keyVal;
            };
            // These datatypes need to be kept in sync with exporter.GraphMLExporter
            nodeProperties.put(keyInfo[0], propValue);
        }
        return nodeProperties;
    }

}
