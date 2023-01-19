package org.apache.lucene.demo;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import opennlp.tools.parser.Cons;
import org.apache.jena.base.Sys;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;

import org.apache.jena.util.FileManager;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.w3c.dom.Node;

import javax.swing.plaf.nimbus.State;
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
    mapaPropiedades.put("description", modelo.getProperty("zaguanVoc:descripcion"));
    mapaPropiedades.put("publisher", modelo.getProperty("zaguanVoc:editor"));
    mapaPropiedades.put("contributor", modelo.getProperty("zaguanVoc:contribuidor"));
    mapaPropiedades.put("date", modelo.getProperty("zaguanVoc:fecha"));

    mapaPropiedades.put("idioma", modelo.getProperty("zaguanVoc:idioma"));
    mapaPropiedades.put("nombreEditor", modelo.getProperty("zaguanVoc:nombreEditor"));
    mapaPropiedades.put("nombrePerona", modelo.getProperty("zaguanVoc:nombrePerona"));
    mapaPropiedades.put("apellido", modelo.getProperty("zaguanVoc:apellido"));

    HashMap<String, Resource> mapaRecursos = new HashMap<>();
    mapaRecursos.put("tfg", modelo.getResource("zaguanVoc:TFG"));
    mapaRecursos.put("tfm", modelo.getResource("zaguanVoc:TFM"));
    mapaRecursos.put("tesis", modelo.getResource("zaguanVoc:TESIS"));

    mapaRecursos.put("español", modelo.getResource("xsd:es"));
    mapaRecursos.put("ingles", modelo.getResource("xsd:en"));
    mapaRecursos.put("italiano", modelo.getResource("xsd:it"));
    mapaRecursos.put("aleman", modelo.getResource("xsd:de"));
    mapaRecursos.put("frances", modelo.getResource("xsd:fr"));

    HashMap<String, Resource> mapaTesauro = new HashMap<>();
    NodeIterator it = skos.listObjects();
    while(it.hasNext())
    {
      RDFNode nodo = it.next();
      if(nodo instanceof Resource)
      {
        Resource entrada = (Resource) nodo;
        StmtIterator iter = entrada.listProperties();

        while (iter.hasNext())
        {
          Statement st = iter.next();
          Triple trip = st.asTriple();

          if(trip.predicateMatches(skos.getProperty("http://www.w3.org/2000/01/rdf-schema#prefLabel").asNode()))
            mapaTesauro.put(trip.getObject().toString().replace("\"", "").toLowerCase(), entrada);
        }
      }
    }

    // Añadimos las instancias al modelo
    Model coleccion = ModelFactory.createDefaultModel();
    Model modeloFinal = cargarDocumentos(coleccion, mapaRecursos, mapaPropiedades, mapaTesauro, new File(docsPath));
    union = ModelFactory.createUnion(union, modeloFinal);

    union.write(new FileOutputStream("zaguanColeccion.ttl"),"TURTLE");
  }

  static Model cargarDocumentos(Model modelo, HashMap<String, Resource> recursos, HashMap<String, Property> propiedades, HashMap<String, Resource> tesauro, File file) throws Exception {
    if (file.canRead()) {
      if (file.isDirectory()) {
        String[] files = file.list();
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            cargarDocumentos(modelo, recursos, propiedades, tesauro, new File(file, files[i]));
          }
        }
      } else {
        Resource documento = modelo.createResource("http://zaguan.unizar.es/record/" + file.getName());

        // Creamos el parser
        DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        org.w3c.dom.Document doc2 = docBuilder.parse(file);

        String[] tipos = new String[]{
                "dc:title",
                "dc:identifier",
                "dc:relation",
                "dc:description"
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

        // Idioma
        org.w3c.dom.NodeList listaNodos = doc2.getElementsByTagName("dc:language");
        for (int i =0; i<listaNodos.getLength(); i++){
          Node nodo = listaNodos.item(i);
          String content = nodo.getTextContent();

          if(content == null)
            continue;

          String idioma;

          if(content.equals("spa")){
            idioma = "español";
          }else if(content.equals("eng") || content.equals("en")){
            idioma = "ingles";
          }else if(content.equals("fre")){
            idioma = "frances";
          }else if(content.equals("ita")){
            idioma = "italiano";
          }else if(content.equals("ger")){
            idioma = "aleman";
          }else {
            continue;
          }
          documento.addProperty(propiedades.get("idioma"), recursos.get(idioma));
        }

        String[] personas = new String[]{
                "dc:creator",
                "dc:contributor"
        };

        // Creador y Contribuidor
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

        // Publisher
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

        // Tesauro
        org.w3c.dom.NodeList nodosTemas = doc2.getElementsByTagName("dc:subject");
        for (int i =0; i<nodosTemas.getLength(); i++){
          Node nodo = nodosTemas.item(i);
          String content = nodo.getTextContent().toLowerCase();

          if(tesauro.containsKey(content))
            documento.addProperty(propiedades.get("subject"), tesauro.get(content));
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
          String contentTipo = nodoTipo.getTextContent().toLowerCase().trim();
          String tipo = "";

          if(contentTipo.equals("taz-tfg")){
            tipo = "tfg";
          }
          else if(contentTipo.equals("taz-tfm")){
            tipo = "tfm";
          }
          else if(contentTipo.equals("taz-pfc")){
            tipo = "tfg";
          }
          else if(contentTipo.equals("tesis")){
            tipo = "tesis";
          }

          documento.addProperty(RDF.type, recursos.get(tipo));
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