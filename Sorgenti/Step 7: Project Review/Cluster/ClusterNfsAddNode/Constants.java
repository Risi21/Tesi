/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.clusternfsaddnode;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author luca
 */
public class Constants {
    
    private Constants() 
    {
            // restrict instantiation
    }
    
//TODO:
    /*
    Fai leggere le costanti da una tabella del database invocando un metodo
    ReadConstants(), poi vengono salvate nelle variabili public
    */
    
    //DEFINITE
    public static final String CATALINA_HOME = "/usr/share/tomcat-8.0.12"; //versione di tomcat installata nel server
    public static final String TOMCAT_CONF = "/usr/share/tomcat-8.0.12/conf"; //file di configurazione di tomcat       
    
    public static final String ROOT_CLUSTER_PATH = "/srv/cluster"; //contiene tutti i repository di jackrabbit, hanno lo stesso nome dell'istanza di tomcat associata
    public static final String JACKRABBIT_WEBAPP_DIRNAME = "jackrabbit"; //nome della jackrabbit-webapp deployata dal .war, in tomcat/webapps

    //DEFINITO
    public static final int SYNC_DELAY = 5000; //ms, ogni quanto tempo legge il journal per aggiornare repository
    
    //Definisce Range in cui cercare una porta per una nuova istanza di tomcat
    //per uno dei 3 servizi: connector, shutdown, ajp: DEFINITI
    public static final int CONNECTOR_MIN = 11000;
    public static final int CONNECTOR_MAX = 11999;
    public static final int SHUTDOWN_MIN = 12000;
    public static final int SHUTDOWN_MAX = 12999;
    public static final int AJP_MIN = 13000;
    public static final int AJP_MAX = 13999;
    
    //VARIABILI DEL JRSAAS, DI ALTO LIVELLO
    //url MySql server:
    public static final String MYSQL_URL = "localhost:3306";
    //nome del database di configurazione mysql: DEFINITO
    public static final String MYSQL_JRSAAS_CONFIG_DB = "JRSAAS_CONFIG";
    //prefisso di ogni database mysql di ogni repository jackrabbit creato: DEFINITO
    public static final String MYSQL_PM_PREFIX = "JRSAAS_CLUSTER_"; //ogni db ha poi il nome dell'istanza di tomcat associata
    //utente admin del database JRSAAS_CONFIG in mysql
    public static final String USER_JRSAAS = "root"; //username 
    public static final String USER_JRSAAS_PWD = "rootpwd"; //password    
    //utente root di MySql, serve per poter creare un nuovo database
    public static final String ADMIN_JRSAAS = "root"; //username 
    public static final String ADMIN_JRSAAS_PWD = "rootpwd"; //password    
    
    //VARIABILI DEL CLUSTER
    //nome della tabella che contiene le configurazioni delle istanze di tomcat
    public static final String MYSQL_TABLE_CLUSTER = "CLUSTER";
    public static final String MYSQL_TABLE_CLUSTER_NODE = "CLUSTER_NODE";
    public static final String MYSQL_INSTANCE_COLUMN_CONNECTOR_PORT = "Connector_Port";
    public static final String MYSQL_INSTANCE_COLUMN_SHUTDOWN_PORT = "Shutdown_Port";
    public static final String MYSQL_INSTANCE_COLUMN_AJP_PORT = "Ajp_Port";    
    
}
