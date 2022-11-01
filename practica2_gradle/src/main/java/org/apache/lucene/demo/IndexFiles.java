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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

//import javax.xml.bind.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.file.Paths;
import java.util.Date;

/** Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class IndexFiles {
  static boolean print = true;

  private IndexFiles() {}

  /** Index all text files under a directory. */
  public static void main(String[] args) {
    String usage = "java org.apache.lucene.demo.IndexFiles"
                 + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                 + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                 + "in INDEX_PATH that can be searched with SearchFiles";
    String indexPath = "index";
    String docsPath = null;
    boolean create = true;
    for(int i=0;i<args.length;i++) {
      if ("-index".equals(args[i])) {
        indexPath = args[i+1];
        i++;
      } else if ("-docs".equals(args[i])) {
        docsPath = args[i+1];
        i++;
      } else if ("-update".equals(args[i])) {
        create = false;
      }
    }

    if (docsPath == null) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }

    final File docDir = new File(docsPath);
    if (!docDir.exists() || !docDir.canRead()) {
      System.out.println("Document directory '" +docDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
      System.exit(1);
    }
    
    Date start = new Date();
    try {
      System.out.println("Indexing to directory '" + indexPath + "'...");

      Directory dir = FSDirectory.open(Paths.get(indexPath));
      Analyzer analyzer = new SpanishAnalyzer2();
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

      if (create) {
        // Create a new index in the directory, removing any
        // previously indexed documents:
        iwc.setOpenMode(OpenMode.CREATE);
      } else {
        // Add new documents to an existing index:
        iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
      }

      // Optional: for better indexing performance, if you
      // are indexing many documents, increase the RAM
      // buffer.  But if you do this, increase the max heap
      // size to the JVM (eg add -Xmx512m or -Xmx1g):
      //
      // iwc.setRAMBufferSizeMB(256.0);


      IndexWriter writer = new IndexWriter(dir, iwc);
      indexDocs(writer, docDir);

      // NOTE: if you want to maximize search performance,
      // you can optionally call forceMerge here.  This can be
      // a terribly costly operation, so generally it's only
      // worth it when your index is relatively static (ie
      // you're done adding documents to it):
      //
      // writer.forceMerge(1);

      writer.close();

      Date end = new Date();
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
       "\n with message: " + e.getMessage());
    }
  }

  /**
   * Indexes the given file using the given writer, or if a directory is given,
   * recurses over files and directories found under the given directory.
   * 
   * NOTE: This method indexes one document per input file.  This is slow.  For good
   * throughput, put multiple documents into your input file(s).  An example of this is
   * in the benchmark module, which can create "line doc" files, one document per line,
   * using the
   * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
   * >WriteLineDocTask</a>.
   *  
   * @param writer Writer to the index where the given file/dir info will be stored
   * @param file The file to index, or the directory to recurse into to find files to index
   * @throws IOException If there is a low-level I/O error
   */
  static void indexDocs(IndexWriter writer, File file)
    throws IOException {

    // do not try to index files that cannot be read
    if (file.canRead()) {
      if (file.isDirectory()) {
        String[] files = file.list();
        // an IO error could occur
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            indexDocs(writer, new File(file, files[i]));
          }
        }
      } else {

        FileInputStream fis;
        try {
          fis = new FileInputStream(file);
        } catch (FileNotFoundException fnfe) {
          // at least on windows, some temporary files raise this exception with an "access denied" message
          // checking if the file can be read doesn't help
          return;
        }

        try {
          // make a new, empty document
          Document doc = new Document();

          // Ruta del fichero
          Field pathField = new StringField("path", file.getPath(), Field.Store.YES);
          doc.add(pathField);

          if(print)
            System.out.println(file.getPath());

          // Ultima modificación
          doc.add(new StoredField("modified", file.lastModified()));

          DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
          org.w3c.dom.Document doc2 = docBuilder.parse(file);

          String[] tipos = new String[]{
                  "dc:title",
                  "dc:creator",
                  "dc:subject",
                  "dc:description",
                  "dc:publisher",
                  "dc:contributor"
          };

          // Tipos
          for(String tipo : tipos) {
            org.w3c.dom.NodeList listaNodos = doc2.getElementsByTagName(tipo);
            for (int i =0; i<listaNodos.getLength(); i++){
              Node nodo = listaNodos.item(i);
              String content = nodo.getTextContent();
              doc.add(new TextField(tipo.substring(3), new StringReader(content)));
            }
          }

          // Fecha
          org.w3c.dom.NodeList listaNodosDate = doc2.getElementsByTagName("dc:date");
          Node nodoDate = listaNodosDate.item(0);
          if(nodoDate != null)
          {
            String contentDate = nodoDate.getTextContent();
            doc.add(new StringField("date", contentDate, Field.Store.YES));
          }


          // Tipo de proyecto
          org.w3c.dom.NodeList listaNodosTipo = doc2.getElementsByTagName("dc:type");
          Node nodoTipo = listaNodosTipo.item(0);
          if(nodoTipo != null)
          {
            String contentTipo = nodoTipo.getTextContent();
            String tipo = "";

            if(contentTipo.equals("TAZ-TFG")){
              tipo = "tfg";
            }
            else if(contentTipo.equals("TAZ-TFM")){
              tipo = "tfm";
            }
            else if(contentTipo.equals("TAZ-PFC")){
              tipo = "tfg";
            }
            else if(contentTipo.equals("TESIS")){
              tipo = "tesis";
            }

            doc.add(new StringField("type", tipo, Field.Store.YES));
          }

          // Issued
          org.w3c.dom.NodeList listaNodosIssued = doc2.getElementsByTagName("dcterms:issued");
          Node nodoIssued = listaNodosIssued.item(0);
          if(nodoIssued != null)
          {
            String contentIssued = nodoIssued.getTextContent();
            String fecha = contentIssued.replace("-", "");
            doc.add(new StringField("issued", fecha, Field.Store.YES));
          }

          // Created
          org.w3c.dom.NodeList listaNodosCreated = doc2.getElementsByTagName("dcterms:created");
          Node nodoCreated = listaNodosCreated.item(0);
          if(nodoCreated != null)
          {
            String contentCreated = nodoCreated.getTextContent();
            String fecha = contentCreated.replace("-", "");
            doc.add(new StringField("created", fecha, Field.Store.YES));
          }

          // Obtención del campo ows:LowerCorner
          org.w3c.dom.NodeList listaNodosLowCorner = doc2.getElementsByTagName("ows:LowerCorner");
          if(listaNodosLowCorner.getLength() == 1) {
            Node nodoLowCorner = listaNodosLowCorner.item(0);
            String contentLowCorener = nodoLowCorner.getTextContent();

            String[] split = contentLowCorener.split(" ");
            String west = split[0];
            String south = split[1];

            DoublePoint southField = new DoublePoint ("south", Double.parseDouble(south));
            DoublePoint westField = new DoublePoint ("west", Double.parseDouble(west));

            // Localizaciones sur y oeste
            doc.add(southField);
            doc.add(westField);
          }

          // Obtención del campo ows:UpperCorner
          org.w3c.dom.NodeList listaNodosUpCorener = doc2.getElementsByTagName("ows:UpperCorner");
          if(listaNodosUpCorener.getLength() == 1) {
            Node nodoUpCorener = listaNodosUpCorener.item(0);
            String contentUpCorener = nodoUpCorener.getTextContent();

            String[] split = contentUpCorener.split(" ");
            String east = split[0];
            String north = split[1];

            DoublePoint northField = new DoublePoint ("north", Double.parseDouble(north));
            DoublePoint eastField = new DoublePoint ("east", Double.parseDouble(east));

            // Localizaciones norte y este
            doc.add(northField);
            doc.add(eastField);
          }

          // Creación del fichero de indexación
          if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            if(print)
              System.out.println("adding " + file);
            writer.addDocument(doc);
          } else {
            if(print)
              System.out.println("updating " + file);
            writer.updateDocument(new Term("path", file.getPath()), doc);
          }
          if(print)
            System.out.println();

        } catch (ParserConfigurationException e) {
          throw new RuntimeException(e);
        } catch (SAXException e) {
          throw new RuntimeException(e);
        } finally {
          fis.close();
        }
      }
    }
  }
}