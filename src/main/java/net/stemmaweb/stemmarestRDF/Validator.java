package net.stemmaweb.stemmarestRDF;

import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.reasoner.ValidityReport;
import org.apache.jena.riot.RDFDataMgr;
import org.semanticweb.HermiT.Configuration;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

import java.io.File;
import java.util.Iterator;

import static java.lang.System.exit;

public class Validator {

    public static Model owlJenaValid(Model initialModel, String ontologyFile) {
        System.out.println("Validating graph against provided ontology...");
        // With the Jena reasoner
        Model schema = RDFDataMgr.loadModel("file:" + ontologyFile);
        Reasoner reasoner = ReasonerRegistry.getOWLReasoner();
        reasoner = reasoner.bindSchema(schema);
        InfModel infModel = ModelFactory.createInfModel(reasoner, initialModel);
        ValidityReport validity = infModel.validate();
        if (validity.isValid()) {
            System.out.println("OWL validity check OK");
            return infModel;
        } else {
            System.out.println("Conflicts");
            for (Iterator<ValidityReport.Report> i = validity.getReports(); i.hasNext(); ) {
                ValidityReport.Report report = i.next();
                System.out.println(" - " + report);
            }
            return null;
        }
    }

    public static void owlHermitValid(Model initialModel, String ontologyFile) {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        File onto = new File(ontologyFile);
        OWLOntology ontology = null;
        try {
            ontology = manager.loadOntologyFromOntologyDocument(onto);
            for (Statement s : initialModel.listStatements().toList()) {
                OWLIndividual subj = df.getOWLNamedIndividual(s.getSubject().getURI());
                if (s.getObject().isLiteral()) {

                } else {
                    OWLObjectProperty pred = df.getOWLObjectProperty(s.getPredicate().getURI());
                    OWLIndividual obj = df.getOWLNamedIndividual(s.getObject().asResource().getURI());
                }
            }
        } catch (OWLOntologyCreationException e) {
            System.err.printf("Could not load ontology from file %s", ontologyFile);
            return;
        }
        ReasonerFactory factory = new ReasonerFactory();
        Configuration config = new Configuration();
        config.throwInconsistentOntologyException = false;
        OWLReasoner reasoner = factory.createReasoner(ontology, config);

    }
}
