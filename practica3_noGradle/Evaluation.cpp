#include <string.h>
#include <stdio.h>
#include <iostream>
#include <fstream>
#include <map>
#include <cstdlib>
#include <vector>

using namespace std;

string menu = "ejecutar de la siguiente forma: Evaluation.exe -qrels <fichero> -results <fichero> -output <fichero>";

vector<string> split(string str, char pattern) {
    
    int posInit = 0;
    int posFound = 0;
    string splitted;
    vector<string> results;
    
    while(posFound >= 0){
        posFound = str.find(pattern, posInit);
        splitted = str.substr(posInit, posFound - posInit);
        posInit = posFound + 1;
        results.push_back(splitted);
    }
    
    return results;
}

int main(int argc, char** argv)
{
    if(argc != 7)
    {
        cout << menu;
        exit(1);
    }

    string qrels;
    string results;
    string output;
    
    // Comprobamos que se le pasa al programa los parametros correctos
    for(int i = 1; i < argc; i++)
    {
        string comando = argv[i];
        if(comando.compare("-qrels") == 0)
        {
            i++;
            qrels = argv[i];
            cout << "fichero qrels: " << qrels << endl;
        }
        else if(comando.compare("-results") == 0)
        {
            i++;
            results == argv[i];
            cout << "fichero resultados: " << results << endl;
        }
        else if(comando.compare("-output") == 0)
        {
            i++;
            output == argv[i];
            cout << "fichero salida: " << output << endl;
        }
        else
        {
            cout << "comando" << argv[i] << "no detectado" << endl;
            exit(2);
        }
    }

    ifstream qrelsFile(qrels);
    ifstream resultsFile(results);
    ofstream outputFile(output);

    cout << "Creando el mapa de relevancia..." << endl;
    map<string, bool> mapa_relevancia;
    
    while(!qrelsFile.eof())
    {
        string consulta;
        string fichero;
        int relevante;

        qrelsFile >> consulta;
        cout << "consulta: " << consulta << endl;
        vector<string> splited = split(consulta, ' ');

        cout << "Se lee " << splited[0] << " " << splited[1]  << " " << splited[2];
        mapa_relevancia.insert(pair<string, bool>(splited[0] +"-"+splited[1] , splited[2] == "1"));
    }
    qrelsFile.close();

    cout << "Generando la precisión y recall de las consultas" << endl;
    string consulta;
    string fichero;
    qrelsFile >> consulta >> fichero;
    while(!resultsFile.eof())
    {
        bool rel = mapa_relevancia.at(consulta + "-" + fichero);
        string relevante = rel ? "Relevante" : "No relevante";
        cout << consulta << "\t" << fichero << relevante << endl;
        qrelsFile >> consulta >> fichero;
    }
    resultsFile.close();

}