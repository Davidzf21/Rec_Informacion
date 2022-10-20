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
import java.util.*;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import org.apache.lucene.search.BooleanQuery ;

/** Simple command-line based search demo. */
public class SearchFiles {

  private SearchFiles() {}

  /** Simple command-line based search demo. */
  public static void main(String[] args) throws Exception {
    String usage =
      "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-repeat n] [-queries file] [-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
    if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
      System.out.println(usage);
      System.exit(0);
    }

    String index = "index";
    String field = "contents";
    String output = null;
    String queries = null;
    int repeat = 0;
    boolean raw = false;
    String queryString = null;
    int hitsPerPage = 10;
    
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
      } else if ("-repeat".equals(args[i])) {
        repeat = Integer.parseInt(args[i+1]);
        i++;
      } else if ("-raw".equals(args[i])) {
        raw = true;
      } else if ("-paging".equals(args[i])) {
        hitsPerPage = Integer.parseInt(args[i+1]);
        if (hitsPerPage <= 0) {
          System.err.println("There must be at least 1 hit per page.");
          System.exit(1);
        }
        i++;
      } else if () {

      }
    }
    
    IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
    IndexSearcher searcher = new IndexSearcher(reader);
    Analyzer analyzer = new SpanishAnalyzer2();

    BufferedReader in = null;
    if (queries != null) {
      in = new BufferedReader(new InputStreamReader(new FileInputStream(queries), StandardCharsets.UTF_8));
    } else {
      in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    BufferedWriter out = null;
    if (output != null) {
      out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8));
    } else {
      out = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
    }

    QueryParser parser = new QueryParser(field, analyzer);
    int numQuery = 1;
    while (true) {

      String line = queryString != null ? queryString : in.readLine();

      if (line == null || line.length() == -1) {
        break;
      }
      //           v
      // ......... spatial:1,23,2,6
      int indexSpatial = line.IndexOf("spatial");
      if(indexSpatial != -1){
        string[] trozos = line.split(" ");
        int indexTrozo = 0;
        for(int i = 0; i < trozos.length; i++)
        {
          if(trozos[i].contains("spatial")) {
            indexTrozo = i;
            break;
          }
        }
        string[] valores = trozos[indexTrozo].substring(7).split(",");
        Double north = Double.ParseDouble(valores[3]);
        Double south = Double.ParseDouble(valores[2]);
        Double east = Double.ParseDouble(valores[1]);
        Double west = Double.ParseDouble(valores[0]);

        Query northRangeQuery = DoublePoint.newRangeQuery("north",Double.INFINITY,south);
        Query southRangeQuery = DoublePoint.newRangeQuery("south",Double.NEGATIVE_INFINITY,north);
        Query eastRangeQuery = DoublePoint.newRangeQuery("east",Double.INFINITY,west);
        Query westRangeQuery = DoublePoint.newRangeQuery("west",Double.NEGATIVE_INFINITY,east);

        BooleanQuery query = new BooleanQuery . Builder ()
                . add ( westRangeQuery , BooleanClause . Occur . MUST )
                . add ( southRangeQuery , BooleanClause . Occur . MUST )
                . add ( eastRangeQuery , BooleanClause . Occur . MUST )
                . add ( northRangeQuery , BooleanClause . Occur . MUST ) . build () ;
      }


     /* line = line.trim();
      if (line.length() == 0) {
        break;
      }

      Query query = parser.parse(line);
      System.out.println("Searching for: " + query.toString(field));

      doPagingSearch(in, out, searcher, query, numQuery);
      numQuery++;
      */
    }
    in.close();
    out.close();
    reader.close();
  }

  /**
   * This demonstrates a typical paging search scenario, where the search engine presents 
   * pages of size n to the user. The user can then go to the next page if interested in
   * the next hits.
   * 
   * When the query is executed for the first time, then only enough results are collected
   * to fill 5 result pages. If the user wants to page beyond this limit, then the query
   * is executed another time and all hits are collected.
   * 
   */
  public static void doPagingSearch(BufferedReader in, BufferedWriter out,IndexSearcher searcher, Query query,
                                     int numQuery) throws IOException {
 
    System.out.println(query.toString());
    // Collect enough docs to show 5 pages
    TopDocs results = searcher.search(query, 100);
    ScoreDoc[] hits = results.scoreDocs;
    
    int numTotalHits = Math.toIntExact(results.totalHits.value);
    System.out.println(numTotalHits + " total matching documents" + "\n");

    hits = searcher.search(query, numTotalHits).scoreDocs;
      
    for (int i = 0; i < hits.length; i++) {

      Document doc = searcher.doc(hits[i].doc);
      String path = doc.get("path");
      // NUEVO -> 2.2

      if (path != null) {
        String[] a = path.split("\\\\");
        out.write(numQuery + "\t" + a[a.length - 1] + "\n");

      }
    }
    }
  }
