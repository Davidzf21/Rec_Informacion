package org.apache.lucene.demo;
import com.github.jsonldjava.utils.Obj;
import jdk.jshell.spi.ExecutionControl;
import org.apache.commons.io.FileUtils;
import org.apache.jena.base.Sys;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.*;

import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.util.FileManager;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import openllet.jena.PelletReasonerFactory;
import org.apache.jena.vocabulary.RDF;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class SemanticGenerator {

  private SemanticGenerator() {  }

  /**
   * Index all text files under a directory.
   */
  public static void main(String[] args) throws Exception {
    String usage = "Usage:\tjava SemanticGenerator -rdf <rdfPath> -skos <skosPath> -owl <owlPath> -docs <docsPath>\n";

    String rdfPath = null;
    String skosPath = null;
    String owlPath = null;
    String docsPath = null;

    for (int i = 0; i < args.length; i++) {
      if ("-rdf".equals(args[i])) {
        rdfPath = args[i + 1];
        i++;

      } else if ("-skos".equals(args[i])) {
        skosPath = args[i + 1];
        i++;

      } else if ("-owl".equals(args[i])) {
        owlPath = args[i + 1];
        i++;

      } else if ("-docs".equals(args[i])) {
        docsPath = args[i + 1];
        i++;
      }
    }

    if (rdfPath == null || skosPath == null || owlPath == null || docsPath == null) {
      System.out.println("Faltan argumentos");
      System.err.println("Usage: " + usage);
      System.exit(1);
    }

    final File docDir = new File(docsPath);
    if (!docDir.exists() || !docDir.canRead()) {
      System.out.println("Document directory '" +docDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
      System.exit(1);
    }

    // Cargamos el modelo
    Model modelo = FileManager.get().loadModel(owlPath);
    Model skos = FileManager.get().loadModel(skosPath,"TURTLE");
    Model union = ModelFactory.createUnion(modelo, skos);

    HashMap<String, Property> mapaPropiedades = new HashMap<>();
    mapaPropiedades.put("identifier", modelo.getProperty("zaguanVoc:identificador"));
    mapaPropiedades.put("relation", modelo.getProperty("zaguanVoc:relacion"));
    mapaPropiedades.put("title", modelo.getProperty("zaguanVoc:titulo"));
    mapaPropiedades.put("creator", modelo.getProperty("zaguanVoc:creador"));
    mapaPropiedades.put("subject", modelo.getProperty("zaguanVoc:tema"));
    //mapaPropiedades.put("description", modelo.getProperty("zaguanVoc:"));
    mapaPropiedades.put("publisher", modelo.getProperty("zaguanVoc:editor"));
    mapaPropiedades.put("contributor", modelo.getProperty("zaguanVoc:contribuidor"));
    mapaPropiedades.put("date", modelo.getProperty("zaguanVoc:fecha"));

    mapaPropiedades.put("nombreEditor", modelo.getProperty("zaguanVoc:nombreEditor"));
    mapaPropiedades.put("nombrePerona", modelo.getProperty("zaguanVoc:nombrePerona"));
    mapaPropiedades.put("apellido", modelo.getProperty("zaguanVoc:apellido"));

    mapaPropiedades.put("Persona", modelo.getProperty("zaguanVoc:Persona"));


    ArrayList<RDFNode> tesauro = new ArrayList<>();
    NodeIterator it = skos.listObjects();
    while(it.hasNext())
    {
      RDFNode no = it.next();
      if(!no.toString().startsWith("http"))
      {
        tesauro.add(no);
      }
    }

    // Creamos un modelo de inferencia OWL2
    // InfModel inf = ModelFactory.createInfModel(PelletReasonerFactory.theInstance().create(), modelo);

    // Añadimos las instancias al modelo
    Model coleccion = ModelFactory.createDefaultModel();
    Model modeloFinal = cargarDocumentos(coleccion, tesauro, mapaPropiedades, new File(docsPath));
    union = ModelFactory.createUnion(union, modeloFinal);


    union.write(new FileOutputStream(new File("librosColeccionInf.ttl")),"TURTLE");
  }

  static Model cargarDocumentos(Model modelo, ArrayList<RDFNode> tesauro, HashMap<String, Property> propiedades, File file) throws Exception {
    if (file.canRead()) {
      if (file.isDirectory()) {
        String[] files = file.list();
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            cargarDocumentos(modelo, tesauro, propiedades, new File(file, files[i]));
          }
        }
      } else {
        Resource documento = modelo.createResource("http://www.practicaZaguan.com/zaguan/" + file.getName());

        // Creamos el parser
        DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        org.w3c.dom.Document doc2 = docBuilder.parse(file);

        String[] tipos = new String[]{
                "dc:title",
                "dc:identifier",
                "dc:relation",
                //"dc:description"
        };

        // Tipos
        for(String tipo : tipos) {
          org.w3c.dom.NodeList listaNodos = doc2.getElementsByTagName(tipo);
          for (int i =0; i<listaNodos.getLength(); i++){
            Node nodo = listaNodos.item(i);
            String content = nodo.getTextContent();
            documento.addProperty(propiedades.get(tipo.substring(3)), content);
          }
        }

        String[] personas = new String[]{
                "dc:creator",
                "dc:contributor"
        };

        for(String persona : personas) {
          org.w3c.dom.NodeList nodosPersona = doc2.getElementsByTagName(persona);
          for (int i = 0; i < nodosPersona.getLength(); i++) {
            Node nodo = nodosPersona.item(i);
            String content = nodo.getTextContent();
            String[] fullName = content.split(", ");

            if (fullName.length == 2) {
              documento.addProperty(propiedades.get(persona.substring(3)),
                      modelo.createResource()
                              .addProperty(propiedades.get("nombrePerona"), fullName[1])
                              .addProperty(propiedades.get("apellido"), fullName[0])
              );
            }else{
              documento.addProperty(propiedades.get(persona.substring(3)),
                      modelo.createResource()
                              .addProperty(propiedades.get("nombrePerona"), fullName[0])
              );
            }
          }
        }

        org.w3c.dom.NodeList nodosPublisher = doc2.getElementsByTagName("dc:publisher");
        for (int i =0; i<nodosPublisher.getLength(); i++){
          Node nodo = nodosPublisher.item(i);
          String content = nodo.getTextContent();

          String[] publishers = content.split("; ");
          for(String publisher : publishers)
            documento.addProperty(propiedades.get("publisher"),
                    modelo.createResource()
                            .addProperty(propiedades.get("nombreEditor"), publisher)
            );
        }

        org.w3c.dom.NodeList listaNodos = doc2.getElementsByTagName("dc:subject");
        for (int i =0; i<listaNodos.getLength(); i++){
          Node nodo = listaNodos.item(i);
          String content = nodo.getTextContent();

          RDFNode match = null;
          for(RDFNode tipo : tesauro)
          {
            if(tipo.toString().equals(content))
            {
              match = tipo;
              break;
            }
          }

          if(match != null)
            documento.addProperty(propiedades.get("subject"), match);
        }

        // Fecha
        org.w3c.dom.NodeList listaNodosDate = doc2.getElementsByTagName("dc:date");
        Node nodoDate = listaNodosDate.item(0);
        if(nodoDate != null)
        {
          String content = nodoDate.getTextContent();
          documento.addProperty(propiedades.get("date"), content);
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

          documento.addProperty(RDF.type, "zaguanVoc:<" + tipo + ">");
        }
      }
    }

    return modelo;
  }

  /**
   * borramos las clases del modelo rdfs que se añaden automáticamene al hacer la inferencia
   * simplemente para facilitar la visualización de la parte que nos interesa
   * si quieres ver todo lo que genera el motor de inferencia comenta estas lineas
   */
  private static Model borrarRecursosRDFS(Model inf) {
    //hacemos una copia del modelo ya que el modelo inferido es inmutable
    Model model2 = ModelFactory.createDefaultModel();
    model2.add(inf);

    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#List"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#subject"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#Alt"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#Bag"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#Seq"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#first"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#object"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#Class"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#label"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#Resource"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#ContainerMembershipProperty"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#isDefinedBy"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#seeAlso"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#Container"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#Datatype"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#comment"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#range"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#subPropertyOf"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#subClassOf"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#Literal"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#domain"), null, null);
    model2.removeAll(inf.createResource("http://www.w3.org/2000/01/rdf-schema#nil"), null, null);

    return model2;
  }
}