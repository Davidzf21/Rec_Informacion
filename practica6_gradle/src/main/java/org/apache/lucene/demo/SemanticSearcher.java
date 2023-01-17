package org.apache.lucene.demo;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.*;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.RAMDirectory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/** Simple command-line based search demo. */
public class SemanticSearcher {

  private SemanticSearcher() {}

  public static void ExeInfoNeeds(String rdf, String infoNeedsFile, BufferedWriter out) throws Exception
  {
    // cargamos el fichero deseado
    Model model = FileManager.get().loadModel(rdf);

    // Leemos el fichero de necesidades de información
    BufferedReader br = null;
    try {
      br = new BufferedReader(new FileReader(infoNeedsFile));
      String texto = br.readLine();
      while(texto != null) {
        String[] parts = texto.split(" ");
        String id = parts[0]; // id
        String queryString = ""; // queryString
        for (int i=1; i < parts.length; i++) {
          if (i==1) {
            queryString += parts[i];
          } else {
            queryString += " " + parts[i];
          }
        }
        //ejecutamos la consulta y obtenemos los resultados
        Query query = QueryFactory.create(queryString) ;
        QueryExecution qexec = QueryExecutionFactory.create(query, model) ;
        try {
          ResultSet results = qexec.execSelect() ;
          for ( ; results.hasNext() ; ) {
            QuerySolution soln = results.nextSolution();
            Resource nombreDoc = soln.getResource("nombreDoc");
            String[] splitResults = nombreDoc.getURI().split("/");
            out.write(id+"\t"+splitResults[splitResults.length - 1]+"\n");
          }
        } finally { qexec.close() ; }

        // Leer la siguiente línea
        texto = br.readLine();
      }
    } catch (FileNotFoundException ex) {
      System.out.println("Error: Fichero no encontrado");
      ex.printStackTrace();
    } finally {
        if(br != null) {
          br.close();
        }
    }
  }

  public static void main(String[] args) throws Exception
  {
    String usage = "Usage:\tjava SemanticSearcher -rdf <rdfPath> -infoNeeds <infoNeedsFile> -output <resultsFile>\n";
    if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
      System.out.println(usage);
      System.exit(0);
    }

    String rdfPath = null;
    String rdfNeedsFile = null;
    String outputFile = null;

    // Sacamos los argumentos
    for(int i = 0;i < args.length;i++) {
      if ("-rdf".equals(args[i])) {
        rdfPath = args[i+1];
        i++;

      } else if ("-infoNeeds".equals(args[i])) {
        rdfNeedsFile = args[i+1];
        i++;

      } else if ("-output".equals(args[i])) {
        outputFile = args[i+1];
        i++;
      }
    }

    if(rdfPath == null || rdfNeedsFile == null || outputFile == null)
    {
      System.out.println("Faltan argumentos");
      System.out.println(usage);
      System.exit(1);
    }

    // Obtenemos la salida de la búsqueda
    BufferedWriter out = null;
    out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8));

    // Ejecutamos info needs
    ExeInfoNeeds(rdfPath, rdfNeedsFile, out);

    // Cerramos los Streams
    out.close();
  }
}