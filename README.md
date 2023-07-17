# stemmarestRDF - RDF conversion and validation of StemmaREST data

This repository contains a proof of concept for RDF(-star) validation of [StemmaREST](https://github.com/DHUniWien/tradition_repo) data. Contents are:

- base_ontology.ttl: an OWL ontology that describes the data model
- constraints.ttl: a SHACL shapefile that describes the data constraints
- n4j-constraints.ttl: a SHACL shapefile that describes the data constraints, insofar as they can be implemented using Neo4J [neosemantics](https://github.com/neo4j-labs/neosemantics/)
- src/main/java/net/stemmaweb/: source for a Java program to read in StemmaREST data as exported from [Stemmaweb](https://stemmaweb.net/stemmaweb), convert it to RDF using Apache Jena, and perform OWL inferencing followed by SHACL validation.
- data/: contains some example StemmaREST data.
