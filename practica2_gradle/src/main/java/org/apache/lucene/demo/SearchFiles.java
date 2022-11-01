package org.apache.lucene.demo;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.Span;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;

import opennlp.tools.tokenize.SimpleTokenizer;

/** Simple command-line based search demo. */
public class SearchFiles {

  private SearchFiles() {}

  /** Simple command-line based search demo. */
  public static BooleanQuery BuildSpatialQuery(String line) throws Exception
  {
    BooleanQuery SpatialQuery = null;

    // Comprobamos si la consulta tiene spatial:
    int indexSpatial = line.indexOf("spatial");

    // Hay un campo spatial en la consulta
    if(indexSpatial != -1) {
      String[] trozos = line.split(" ");
      int indexTrozo = 0;

      for (int i = 0; i < trozos.length; i++) {
        if (trozos[i].contains("spatial")) {
          indexTrozo = i;
          break;
        }
      }
      String[] valores = trozos[indexTrozo].substring(8).split(",");
      Double north = Double.parseDouble(valores[3]);
      Double south = Double.parseDouble(valores[2]);
      Double east = Double.parseDouble(valores[1]);
      Double west = Double.parseDouble(valores[0]);

      Query northRangeQuery = DoublePoint.newRangeQuery("north", south, Double.POSITIVE_INFINITY);
      Query southRangeQuery = DoublePoint.newRangeQuery("south", Double.NEGATIVE_INFINITY, north);
      Query eastRangeQuery = DoublePoint.newRangeQuery("east", west, Double.POSITIVE_INFINITY);
      Query westRangeQuery = DoublePoint.newRangeQuery("west", Double.NEGATIVE_INFINITY, east);

      SpatialQuery = new BooleanQuery.Builder()
              .add(westRangeQuery, BooleanClause.Occur.MUST)
              .add(eastRangeQuery, BooleanClause.Occur.MUST)
              .add(northRangeQuery, BooleanClause.Occur.MUST)
              .add(southRangeQuery, BooleanClause.Occur.MUST).build();
    }

    return SpatialQuery;
  }

  public static Query BuildNormalQuery(String line, String field, Analyzer analyzer, boolean hasSpatial) throws Exception
  {
    QueryParser parser = new QueryParser(field, analyzer);
    String[] queriesStrings = line.split(" ");
    String notSpatialStr = "";

    int first = hasSpatial ? 1 : 0;

    for(int i = first; i < queriesStrings.length; i++)
      notSpatialStr += queriesStrings[i];

    Query query = null;
    if(!notSpatialStr.equals(""))
      query = parser.parse(notSpatialStr);
    return query;
  }

  public static Query BuildFechasQuery(String line) throws Exception
  {
    BooleanQuery.Builder fullQueryBuilder = new BooleanQuery.Builder();
    Query returnQuery = null;

    Pattern pattern = Pattern.compile("(issued|created):(\\[([0-9]+|\\*) TO ([0-9]+|\\*)\\]|[0-9]+)");
    Matcher matcher = pattern.matcher(line);

    if(matcher.find())
    {
      while(matcher.find()){
        String consulta = matcher.group();
        String[] consultaInterna = consulta.split(":");

        if(consultaInterna[1].contains("[")) {
          String[] rangos = consultaInterna[1].replace("[","").replace("]","").split(" ");
          String rangoIzq = null;
          String rangoDch = null;

          if(rangos[0].equals("*")){          // [* TO 1234]
            rangoDch = rangos[2];

          } else if(rangos[2].equals("*")) {  // [1234 TO *]
            rangoIzq = rangos[0];

          } else {                       // [1234 TO 1234]
            rangoIzq = rangos[0];
            rangoDch = rangos[2];
          }

          Query trq = TermRangeQuery.newStringRange(consultaInterna[0], rangoIzq, rangoDch, true, true);
          fullQueryBuilder.add(trq, BooleanClause.Occur.SHOULD);

        } else {
          fullQueryBuilder.add(new TermQuery (new Term(consultaInterna[0], consultaInterna[1])), BooleanClause.Occur.SHOULD);
        }
      }

      returnQuery = fullQueryBuilder.build();
    }

    return returnQuery;
  }

  public static void ExeNormalExecution(String field, Analyzer analyzer, String queryString, IndexSearcher searcher, BufferedReader in, BufferedWriter out) throws Exception
  {
    int numQuery = 1;
    while(true){
      String line = queryString != null ? queryString : in.readLine();

      if (line == null || line.length() == -1 || line.equals("")) {
        break;
      }

      BooleanQuery SpatialQuery = BuildSpatialQuery(line);
      Query fechasQuery = BuildFechasQuery(line);
      Query notSpatialQuery = null;

      if(fechasQuery == null)
        notSpatialQuery = BuildNormalQuery(line, field, analyzer, SpatialQuery != null);

      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      if(SpatialQuery != null){
        builder.add(SpatialQuery, BooleanClause.Occur.SHOULD);
      }
      if(notSpatialQuery != null){
        builder.add(notSpatialQuery, BooleanClause.Occur.SHOULD);
      }
      if(fechasQuery != null) {
        builder.add(fechasQuery, BooleanClause.Occur.SHOULD);
      }

      BooleanQuery query = builder.build();

      System.out.println("Searching for: " + query.toString(field));
      doPagingSearch(out, searcher, query, Integer.toString(numQuery));
      numQuery++;
    }
  }

  public static void ExeInfoNeeds(String field, Analyzer analyzer, IndexSearcher searcher, String file, BufferedWriter out) throws Exception
  {
    QueryParser parser = new QueryParser(field, analyzer);

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    org.w3c.dom.Document document = builder.parse(new File(file));
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
  }

  public static void main(String[] args) throws Exception
  {
    String usage = "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-queries file] [-query string] [-infoNeeds file] [-output file]\n";
    if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
      System.out.println(usage);
      System.exit(0);
    }

    String index = "index";
    String field = "contents";
    String infoNeeds = null;
    String output = null;
    String queries = null;
    String queryString = null;
    int hitsPerPage = 10;

    // Sacamos los argumentos
    for(int i = 0;i < args.length;i++) {
      if ("-index".equals(args[i])) {
        index = args[i+1];
        i++;

      } else if ("-field".equals(args[i])) {
        field = args[i+1];
        i++;

      } else if ("-queries".equals(args[i])) {
        queries = args[i+1];
        i++;

      } else if ("-query".equals(args[i])) {
        queryString = args[i+1];
        i++;

      } else if ("-output".equals(args[i])) {
        output = args[i+1];
        i++;

      } else if ("-infoNeeds".equals(args[i])) {
        infoNeeds = args[i+1];
        i++;

      } else if ("-paging".equals(args[i])) {
        hitsPerPage = Integer.parseInt(args[i+1]);
        if (hitsPerPage <= 0) {
          System.err.println("There must be at least 1 hit per page.");
          System.exit(1);
        }
        i++;
      }
    }

    // Obtenemos la salida de la búsqueda
    BufferedWriter out = null;
    if (output != null) {
      out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8));

    } else {
      out = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
    }

    // Creamos el buscador y analizador
    IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
    IndexSearcher searcher = new IndexSearcher(reader);
    Analyzer analyzer = new SpanishAnalyzer2();

    // Si se pide usar infoNeeds o no
    if(infoNeeds != null)
    {
      ExeInfoNeeds(field, analyzer, searcher, infoNeeds, out);
    }
    else
    {
      // Obtenemos la entrada de la búsqueda
      BufferedReader in = null;
      if (queries != null) {
        in = new BufferedReader(new InputStreamReader(new FileInputStream(queries), StandardCharsets.UTF_8));

      } else {
        in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      }

      ExeNormalExecution(field, analyzer, queryString, searcher, in, out);
      in.close();
      reader.close();
    }

    // Cerramos los Streams
    out.close();
  }

   public static void doPagingSearch(BufferedWriter out, IndexSearcher searcher, BooleanQuery query, String idQuery) throws IOException
   {
    TopDocs results = searcher.search(query, Integer.MAX_VALUE);
    ScoreDoc[] hits = results.scoreDocs;

    int numTotalHits = Math.toIntExact(results.totalHits.value);
    System.out.println(numTotalHits + " total matching documents\n");

    if(numTotalHits <= 0)
      return;

    hits = searcher.search(query, numTotalHits).scoreDocs;

    for (int i = 0; i < hits.length; i++) {
      Document doc = searcher.doc(hits[i].doc);
      String path = doc.get("path");

      System.out.println(path + "\n" + searcher.explain(query,hits[i].doc));

      if (path != null) {
        String[] a = path.split("\\\\");
        out.write(idQuery + "\t" + a[a.length - 1] + "\n");
      }
    }
  }
}