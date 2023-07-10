package net.stemmaweb.stemmarestRDF;


import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class Writer {
    public static void writeModel (Model model, String outfile)
            throws IllegalArgumentException, FileNotFoundException {
        // Serialize the model to turtle / turtle-star
        OutputStream out;
        if (outfile == null) {
            out = System.out;
        } else {
            out = new FileOutputStream(outfile);
        }

        RDFDataMgr.write(out, model, Lang.TURTLE);
    }
}
