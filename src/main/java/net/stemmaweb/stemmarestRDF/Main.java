package net.stemmaweb.stemmarestRDF;

import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.reasoner.ValidityReport;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
//import org.semanticweb.HermiT.Configuration;
//import org.semanticweb.HermiT.Reasoner;
//import org.semanticweb.HermiT.ReasonerFactory;
//import org.semanticweb.owlapi.apibinding.OWLManager;
//import org.semanticweb.owlapi.model.OWLDataFactory;
//import org.semanticweb.owlapi.model.OWLOntology;
//import org.semanticweb.owlapi.model.OWLOntologyCreationException;
//import org.semanticweb.owlapi.model.OWLOntologyManager;
//import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.lang.System.exit;

public class Main {

    public static LinkedHashMap<String,File> extractGraphMLZip(InputStream is) throws IOException {
        LinkedHashMap<String,File> result = new LinkedHashMap<>();
        BufferedInputStream buf = new BufferedInputStream(is);
        ZipInputStream zipIn = new ZipInputStream(buf);
        ZipEntry ze;
        while ((ze = zipIn.getNextEntry()) != null) {
            String zfName = ze.getName();
            File someTmp = File.createTempFile(zfName, "");
            FileOutputStream fo = new FileOutputStream(someTmp);
            IOUtils.copy(zipIn, fo);
            fo.close();
            zipIn.closeEntry();
            result.put(zfName, someTmp);
        }
        zipIn.close();
        return result;
    }

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(new Option("g", "graphml", true,
                "GraphML ZIP data to parse"));
        options.addOption(new Option("o", "outfile", true,
                "Output file for RDF data (default stdout)"));
        options.addOption(new Option("e", "ontology", true,
                "Ontology definition file"));
        options.addOption(new Option("c", "shacl", true,
                "SHACL constraint definitions"));
        options.addOption(new Option("s", "star", false,
                "Whether to output quoted triples / Turtle*"));

        CommandLineParser parser = new DefaultParser();
        CommandLine clm = null;
        try {
            clm = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Command line parsing error: " + e.getMessage());
            exit(1);
        }

        String oPrefix = "https://www.stemmaweb.net/ontologies/stemmarest#";
        String dPrefix = "https://www.stemmaweb.net/resource/";
        String iPrefix = "https://www.stemmaweb.net/ontologies/internal#";
        Model fullModel = ModelFactory.createDefaultModel();
        fullModel.setNsPrefix("srest", oPrefix);
        fullModel.setNsPrefix("sdata", dPrefix);
        fullModel.setNsPrefix("intern", iPrefix);
        // Extract the GraphML files in order
        String fileName = clm.getOptionValue("graphml");
        if (fileName == null) {
            System.err.println("Please specify input file with -g, --graphml");
            exit(1);
        }

        File inputZip = new File(fileName);
        LinkedHashMap<String,File> extractedFiles = null;
        try {
            FileInputStream f = new FileInputStream(inputZip);
            extractedFiles = extractGraphMLZip(f);
        } catch (FileNotFoundException e) {
            System.err.printf("File %s not found%n", fileName);
            exit(1);
        } catch (IOException e) {
            System.err.printf("File %s could not be read%n", fileName);
            exit(1);
        }

        // Send each one to the converter and get the model back
        System.out.println("Reading graph from file...");
        Converter c = new Converter(clm.hasOption("star"), oPrefix, dPrefix, iPrefix);
        for (String fk : extractedFiles.keySet()) {
            try {
                Model obtainedModel = c.readGraphML(extractedFiles.get(fk));
                // Merge the latest model statements into the overall model
                fullModel.add(obtainedModel);
            } catch (FileNotFoundException e) {
                System.err.println("We should not have got here!");
                exit(1);
            }
        }
        // Write out the model we parsed
        if (clm.hasOption("outfile")) {
            try {
                Writer.writeModel(fullModel, clm.getOptionValue("outfile"));
            } catch (Exception e) {
                System.err.println(e.getMessage());
                exit(1);
            }
        }

        // Validate the model against our OWL ontology
        if (clm.hasOption("ontology")) {
            System.out.println("Validating graph against provided ontology...");
            // With the Jena reasoner
            Model schema = RDFDataMgr.loadModel("file:" + clm.getOptionValue("ontology"));
            Reasoner reasoner = ReasonerRegistry.getOWLReasoner();
            reasoner = reasoner.bindSchema(schema);
            InfModel infModel = ModelFactory.createInfModel(reasoner, fullModel);
            ValidityReport validity = infModel.validate();
            if (validity.isValid()) {
                System.out.println("OWL validity check OK");
                fullModel = infModel;
            } else {
                System.out.println("Conflicts");
                for (Iterator<ValidityReport.Report> i = validity.getReports(); i.hasNext(); ) {
                    ValidityReport.Report report = i.next();
                    System.out.println(" - " + report);
                }
            }
            // With the OWL API and HermiT
//            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
//            OWLDataFactory dataFactory = manager.getOWLDataFactory();
//            File ontologyFile = new File(clm.getOptionValue("ontology"));
//            OWLOntology ontology = null;
//            try {
//                ontology = manager.loadOntologyFromOntologyDocument(ontologyFile);
//            } catch (OWLOntologyCreationException e) {
//                System.err.printf("Could not load ontology from file %s", clm.getOptionValue("ontology"));
//                exit(1);
//            }
//            Reasoner.ReasonerFactory factory = new ReasonerFactory();
//            Configuration config = new Configuration();
//            config.throwInconsistentOntologyException = false;
//            OWLReasoner reasoner = factory.createReasoner(ontology, config);

        }

        // Validate the model against our SHACL shapes file
        if (clm.hasOption("shacl")) {
            System.out.println("Validating graph against provided shapes file...");
            Graph shapesGraph = RDFDataMgr.loadGraph(clm.getOptionValue("shacl"));
            Shapes shapes = Shapes.parse(shapesGraph);
            Graph dataGraph = fullModel.getGraph();
            ValidationReport report = ShaclValidator.get().validate(shapes, dataGraph);
            ShLib.printReport(report);
        }

    }
}