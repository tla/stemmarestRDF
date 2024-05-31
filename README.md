# stemmarestRDF - RDF conversion and validation of StemmaREST data

This repository contains a proof of concept for RDF(-star) validation of [StemmaREST](https://github.com/DHUniWien/tradition_repo) data. Contents are:

- base_ontology.ttl: an OWL ontology that describes the data model
- constraints.ttl: a SHACL shapefile that describes the data constraints
- n4j-constraints.ttl: a SHACL shapefile that describes the data constraints, insofar as they can be implemented using Neo4J [neosemantics](https://github.com/neo4j-labs/neosemantics/)
- src/main/java/net/stemmaweb/: source for a Java program to read in StemmaREST data as exported from [Stemmaweb](https://stemmaweb.net/stemmaweb), convert it to RDF using Apache Jena, and perform OWL inferencing followed by SHACL validation.
- data/: contains some example StemmaREST data.

The program can be compiled and run via the command line:

    java -jar target/stemmarest.jar -s -e base_ontology.ttl -c constraints.ttl -g data/parzival_orig.zip -o parzival.ttl

The options are as follows:

- -g, --graphml: GraphML ZIP data to parse
- -o, --outfile: Output file for RDF data (default stdout)
- -e, --ontology: Ontology definition file
- -c, --shacl: SHACL constraint definition file
- -s, --star: Whether to output quoted triples / Turtle* in the conversion from StemmaREST GraphML to RDF format
- -n, --neo4j: Whether to output RDF* in a Neo4J-compatible format (triples may not have object properties)