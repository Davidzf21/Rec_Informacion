package org.apache.lucene.demo;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.*;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.tdb2.TDB2Factory;
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
    EntityDefinition entDef = new EntityDefinition("uri", "name", ResourceFactory.createProperty("http://xmlns.com/foaf/0.1/","name"));
    entDef.set("description", DCTerms.description.asNode());
    TextIndexConfig config = new TextIndexConfig(entDef);
    config.setAnalyzer(new SpanishAnalyzer2());
    config.setQueryAnalyzer(new SpanishAnalyzer2());
    config.setMultilingualSupport(true);

    // definimos el repositorio indexado en memoria
    Dataset ds1 = DatasetFactory.createGeneral() ;
    Directory dir = new RAMDirectory();
    Dataset ds = TextDatasetFactory.createLucene(ds1, dir, config) ;

    // cargamos el fichero deseado y lo almacenamos en el repositorio indexado
    RDFDataMgr.read(ds.getDefaultModel(), rdf) ;

    System.out.println("---------------------------------------------");
    System.out.println("Resultados finales eliminando los duplicados");
    System.out.println("---------------------------------------------");

    String q ="prefix foaf: <http://xmlns.com/foaf/0.1/> "
            + "prefix text: <http://jena.apache.org/text#> "
            + "prefix dct:	<http://purl.org/dc/terms/> "
            + "Select distinct ?x  where { "
            + "optional {(?x ?score2) text:query (dct:description 'music' )}. "
            + "optional {(?x ?score1) text:query (foaf:name 'music' )}. "
            + "bind (coalesce(?score1,0)+coalesce(?score2,0) as ?scoretot) "
            + "} ORDER BY DESC(?scoretot)";

    Query query = QueryFactory.create(q) ;
    ds.begin(ReadWrite.READ) ;
    try (QueryExecution qexec = QueryExecutionFactory.create(query, ds)) {
      ResultSet results = qexec.execSelect() ;
      for ( ; results.hasNext() ; ) {
        QuerySolution soln = results.nextSolution() ;
        System.out.println(soln);
      }
    }
    ds.end();

    /*
    QueryParser parser = new QueryParser(field, analyzer);

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    org.w3c.dom.Document document = builder.parse(new File(infoNeedsFile));
    Element root = document.getDocumentElement();
    NodeList listaNodos = root.getElementsByTagName("informationNeed");

    InputStream inputStream = new FileInputStream("maxent-pos-universal.model");
    POSModel posModel = new POSModel(inputStream);
    POSTaggerME posTagger = new POSTaggerME(posModel);

    // Recorremos todos los informationNeed
    for (int i =0; i<listaNodos.getLength(); i++){
      Node nodo = listaNodos.item(i);
      NodeList listaHijos = nodo.getChildNodes();

      // Obtenemos el contenido del identifier
      String identifier = listaHijos.item(1).getTextContent();
      String text = listaHijos.item(3).getTextContent();
      System.out.println(identifier);

      // Creamos la consulta
      BooleanQuery.Builder booleanBuilder = new BooleanQuery.Builder();

      SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
      String[] tokens = tokenizer.tokenize(text);

      String tags[] = posTagger.tag(tokens);
      ArrayList<String> numeros = new ArrayList<>();
      for (int o = 0; o<tags.length; o++){
        // Consultamos los sustantivos
        if(tags[o].equals("NOUN")){
          Query publisherQuery = parser.parse("publisher:"+tokens[o]);
          Query subjectQuery = parser.parse("subject:"+tokens[o]);
          Query descriptionQuery = parser.parse("description:"+tokens[o]);
          Query titleQuery = parser.parse("title:"+tokens[o]);

          Query creatorQuery = parser.parse("creator:"+tokens[o]);
          Query contributorQuery = parser.parse("contributor:"+tokens[o]);
          booleanBuilder.add(publisherQuery, BooleanClause.Occur.SHOULD)
                  .add(subjectQuery, BooleanClause.Occur.SHOULD)
                  .add(descriptionQuery, BooleanClause.Occur.SHOULD)
                  .add(titleQuery, BooleanClause.Occur.SHOULD)
                  .add(creatorQuery, BooleanClause.Occur.SHOULD)
                  .add(contributorQuery, BooleanClause.Occur.SHOULD);
        }
        // Consultamos los números
        else if(tags[o].equals("NUM")){
          numeros.add(tokens[o]);
        }
      }

      // Si pide una fecha en concreto
      if(numeros.size() == 1)
      {
        Query dateQuery = parser.parse("date:"+numeros.get(0));
        booleanBuilder.add(dateQuery, BooleanClause.Occur.SHOULD);
      }
      // Si pide un rango de fechas
      else if(numeros.size() > 1)
      {
        Query dateQuery = TermRangeQuery.newStringRange("date", numeros.get(0), numeros.get(1), true, true);
        booleanBuilder.add(dateQuery, BooleanClause.Occur.SHOULD);
      }

      // Regex de TFG
      Pattern patternTFG = Pattern.compile("\\[TFG|Trabajo (de )?Fin (de )?Grado\\]");
      Matcher matcherTFG = patternTFG.matcher(text);
      if(matcherTFG.find()){
        Query tfgQuery = parser.parse("type:tfg");
        booleanBuilder.add(tfgQuery, BooleanClause.Occur.SHOULD);
      }

      // Regex de Tesis
      Pattern patternTesis = Pattern.compile("\\[T|t\\]esis");
      Matcher matcherTesis = patternTesis.matcher(text);
      if(matcherTesis.find()){
        Query tesisQuery = parser.parse("type:tesis");
        booleanBuilder.add(tesisQuery, BooleanClause.Occur.SHOULD);
      }

      // Regex de TFM
      Pattern patternTFM = Pattern.compile("\\[TFM|Trabajo (de )?Fin (de )?Master\\]");
      Matcher matcherTFM = patternTFM.matcher(text);
      if(matcherTFM.find()){
        Query tfmQuery = parser.parse("type:tfm");
        booleanBuilder.add(tfmQuery, BooleanClause.Occur.SHOULD);
      }

      // Regex de idioma
      if(text.contains("en español")){
        Query langQuery = parser.parse("language:es");
        booleanBuilder.add(langQuery, BooleanClause.Occur.SHOULD);
      }
      if(text.contains("en inglés")){
        Query langQuery = parser.parse("language:en");
        booleanBuilder.add(langQuery, BooleanClause.Occur.SHOULD);
      }

      BooleanQuery query = booleanBuilder.build();

      System.out.println("Searching for: " + query.toString(field));
      doPagingSearch(out, searcher, query, identifier);

      System.out.println("------------");
      out.write(identifier + " - " + text + "\n");
    }
    */
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
    //ExeInfoNeeds();

    // Cerramos los Streams
    out.close();
  }
}