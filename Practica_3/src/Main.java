import javax.xml.crypto.dsig.keyinfo.KeyValue;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;


public class Main {
    public static class Resultado
    {
        public double precision;
        public double recall;
        public double f1;
    }

    public static class Estadisticas
    {
        public double documentosNR = 0;
        public double documentosR = 0;
        public int contados = 0;

        public int relevantesTotales;

        public Estadisticas(int relevantesTotales)
        {
            this.relevantesTotales = relevantesTotales;
        }

        public void NuevoNoRelevante()
        {
            if(contados < 45)
                documentosNR++;
            contados++;
        }
        public void NuevoRelevante()
        {
            if(contados < 45)
                documentosR++;
            contados++;
        }

        public Resultado ObtenerResultado()
        {
            Resultado res = new Resultado();
            res.precision = documentosR / (documentosNR + documentosR);
            res.recall = documentosR / relevantesTotales;
            res.f1 = 2 * res.precision * res.recall / (res.precision + res.recall);
            return res;
        }
    }

    static HashMap<String, HashMap<String, Boolean>> Mapa_Relevancia = new HashMap();
    static HashMap<String, Integer> Mapa_DocsRelevantes = new HashMap();

    static HashMap<String, ArrayList<String>> Mapa_Documentos = new HashMap();
    static HashMap<String, Estadisticas> Mapa_Estadisticas = new HashMap();
    static ArrayList<String> NecesidadesInfo = new ArrayList<>();


    static File qrels;
    static File results;
    static File output;

    static void LeerArgumentos(String[] args)
    {
        if (args.length != 6)
            System.exit(1);

        for(int i = 0; i < args.length; i++)
        {
            if(args[i].equals("-qrels"))
            {
                qrels = new File(args[++i]);
            }
            else if(args[i].equals("-results"))
            {
                results = new File(args[++i]);
            }
            else if(args[i].equals("-output"))
            {
                output = new File(args[++i]);
            }
            else
            {
                System.out.println("Comando no reconocido: " + args[i]);
                System.exit(2);
            }
        }
    }

    static void CargarMapaRelevancia()
    {
        try
        {
            Scanner reader = new Scanner(qrels);
            HashMap<String, Integer> docRelevantes = new HashMap<>();

            while(reader.hasNext())
            {
                String linea = reader.nextLine();
                String[] split = linea.split("[ |\t]");

                String necesiadadInfo = split[0];
                String documento = split[1];
                boolean relevancia = split[2].equals("1");

                if(Mapa_Relevancia.containsKey(necesiadadInfo))
                {
                    Mapa_Relevancia.get(necesiadadInfo).put(documento, relevancia);
                    if(relevancia)
                    {
                        int rels = docRelevantes.get(necesiadadInfo);
                        docRelevantes.replace(necesiadadInfo, rels+1);
                    }
                }
                else
                {
                    HashMap<String, Boolean> mapa = new HashMap<String, Boolean>();
                    mapa.put(documento, relevancia);
                    Mapa_Relevancia.put(necesiadadInfo, mapa);
                    docRelevantes.put(necesiadadInfo, relevancia ? 1 : 0);
                    NecesidadesInfo.add(necesiadadInfo);
                    Mapa_Documentos.put(necesiadadInfo, new ArrayList<>());
                }
            }

            for (String necesidad : NecesidadesInfo)
            {
                Mapa_Estadisticas.put(necesidad, new Estadisticas(docRelevantes.get(necesidad)));
            }
        }
        catch (Exception e)
        {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void ObtenerEstadisticas()
    {
        try
        {
            Scanner reader = new Scanner(results);

            while (reader.hasNext()) {
                String linea = reader.nextLine();
                String[] split = linea.split("[ |\t]");

                String necesiadadInfo = split[0];
                String documentoObtenido = split[1];
                Mapa_Documentos.get(necesiadadInfo).add(documentoObtenido);
                boolean relevante = false;
                try{
                    relevante = Mapa_Relevancia.get(necesiadadInfo).get(documentoObtenido);
                }
                catch(NullPointerException ne){}

                if(relevante) Mapa_Estadisticas.get(necesiadadInfo).NuevoRelevante();
                else Mapa_Estadisticas.get(necesiadadInfo).NuevoNoRelevante();
            }

            reader.close();
        }
        catch(Exception e)
        {
            System.out.println("ERROR AL OBTENER LAS ESTADISTICAS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void GenerarEstadisticas()
    {
        try
        {
            output.createNewFile();
            FileWriter myWriter = new FileWriter(output);

            double totalPrecision = 0;
            double totalRecall= 0;
            double totalF1= 0;
            double totalPrec10= 0;
            double totalMAP = 0;
            int necesidades = NecesidadesInfo.size();
            ArrayList<Double> totalInterpolated = new ArrayList<>();
            for(int i = 0; i <= 10; i++)
                totalInterpolated.add(0.0);

            for (String necesidad : NecesidadesInfo)
            {
                Estadisticas estadisticas = Mapa_Estadisticas.get(necesidad);
                Resultado res = estadisticas.ObtenerResultado();

                totalPrecision += res.precision;
                totalRecall += res.recall;
                totalF1 += res.f1;

                ArrayList<Double> listaPrecision = new ArrayList<>();
                ArrayList<Double> listaRecall = new ArrayList<>();

                ArrayList<String> documentos = Mapa_Documentos.get(necesidad);
                double relevantesObtenidos = 0;
                double sumaPrecisiones = 0;
                double precision10 = 0;
                double MAP = 0;

                for(int i = 0; i < documentos.size() && i < 45; i++)
                {
                    String necesidadDoc = documentos.get(i);
                    boolean relevante = false;
                    try{
                        relevante = Mapa_Relevancia.get(necesidad).get(documentos.get(i));
                    }
                    catch(NullPointerException ne) {}

                    if (relevante)
                    {
                        relevantesObtenidos++;
                        double precision = relevantesObtenidos / (i+1);
                        MAP += precision;
                        sumaPrecisiones += precision;
                        listaPrecision.add(precision);
                        listaRecall.add(relevantesObtenidos / estadisticas.relevantesTotales);
                        if(relevantesObtenidos < 10)
                            precision10 = precision;
                    }
                }

                MAP /= estadisticas.documentosR;
                totalMAP += MAP;

                totalPrec10 += precision10;

                ArrayList<Double> listaPrecisionInterpolada = new ArrayList<>();
                int index = 0;
                for(double recall = 0; recall <= 1; recall += 0.1)
                {
                    double precision = 0;

                    for(int i = listaPrecision.size()-1; i >= 0; i--)
                    {
                        if(recall > listaRecall.get(i))
                            break;

                        if(precision < listaPrecision.get(i))
                            precision = listaPrecision.get(i);
                    }

                    listaPrecisionInterpolada.add(precision);

                    double last = totalInterpolated.get(index);
                    totalInterpolated.remove(index);
                    totalInterpolated.add(index, last + precision);
                    index++;
                }

                myWriter.write("INFORMATION_NEED " + necesidad + "\n");
                myWriter.write("precision " + String.format("%,.3f",res.precision) + "\n");
                myWriter.write("recall " + String.format("%,.3f",res.recall) + "\n");
                myWriter.write("F1 " + String.format("%,.3f",res.f1) + "\n");
                myWriter.write("prec@10 " + String.format("%,.3f", precision10) + "\n");
                myWriter.write("average_precision " + String.format("%,.3f", sumaPrecisiones/ listaPrecision.size()) + "\n");
                myWriter.write("recall_precision\n");
                for(int i = 0; i < listaRecall.size(); i++)
                {
                    myWriter.write(String.format("%,.3f", listaRecall.get(i)) + " " + String.format("%,.3f",listaPrecision.get(i)) + "\n");
                }
                myWriter.write("interpolated_recall_precision\n");
                double recallInterpolada = 0;
                for(int i = 0; i < listaPrecisionInterpolada.size(); i++)
                {
                    myWriter.write(String.format("%,.3f",recallInterpolada) + " " + String.format("%,.3f",listaPrecisionInterpolada.get(i)) + "\n");
                    recallInterpolada += 0.1;
                }
                myWriter.write("\n");
            }

            myWriter.write("TOTAL\n");
            myWriter.write("precision " + String.format("%,.3f",totalPrecision / necesidades) + "\n");
            myWriter.write("recall " + String.format("%,.3f",totalRecall / necesidades) + "\n");
            myWriter.write("F1 " + String.format("%,.3f",totalF1 / necesidades) + "\n");
            myWriter.write("prec@10 " + String.format("%,.3f", totalPrec10 / necesidades) + "\n");
            myWriter.write("MAP " + String.format("%,.3f", totalMAP / necesidades) + "\n");
            double totalRecallInterpolado = 0;
            myWriter.write("interpolated_recall_precision\n");
            for(int i = 0; i < totalInterpolated.size(); i++)
            {
                myWriter.write(String.format("%,.3f", totalRecallInterpolado) + " " + String.format("%,.3f",totalInterpolated.get(i)/ necesidades) + "\n");
                totalRecallInterpolado += 0.1;
            }

            myWriter.close();
        }
        catch(Exception e)
        {
            System.out.println("ERROR AL GENERAR ESTADISTICAS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        LeerArgumentos(args);
        CargarMapaRelevancia();
        ObtenerEstadisticas();
        GenerarEstadisticas();
    }
}