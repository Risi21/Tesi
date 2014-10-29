/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.step7;

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
    
    public static final String TOMCAT_PATH = "/srv/tomcat/instances"; //contiene le istanze di tomcat
    public static final String TOMCAT_SCRIPTS = "/srv/tomcat/scripts"; //contiene gli scripts per avviare / stoppare le istanze di tomcat
    public static final String CATALINA_HOME = "/usr/share/tomcat-8.0.12"; //versione di tomcat installata nel server
    public static final String TOMCAT_CONF = "/usr/share/tomcat-8.0.12/conf"; //file di configurazione di tomcat       
    
    public static final String ROOT_REPO_PATH = "/srv/repo"; //contiene tutti i repository di jackrabbit, hanno lo stesso nome dell'istanza di tomcat associata
    public static final String JACKRABBIT_WEBAPP_DIRNAME = "jackrabbit"; //nome della jackrabbit-webapp deployata dal .war, in tomcat/webapps

    
    
    //path dei backup dei repository
    public static final String PATH_BACKUP = "/srv/backup";

    //Definisce Range in cui cercare una porta per una nuova istanza di tomcat
    //per uno dei 3 servizi: connector, shutdown, ajp
    public static final int CONNECTOR_MIN = 11000;
    public static final int CONNECTOR_MAX = 11999;
    public static final int SHUTDOWN_MIN = 12000;
    public static final int SHUTDOWN_MAX = 12999;
    public static final int AJP_MIN = 13000;
    public static final int AJP_MAX = 13999;
    
    //url MySql server:
    public static final String MYSQL_URL = "localhost:3306";
    //nome del database di configurazione mysql:
    public static final String MYSQL_DB = "JRSAAS_CONFIG";
    //prefisso di ogni database mysql di ogni repository jackrabbit creato
    public static final String MYSQL_REPO_PREFIX = "JRSAAS_REPO_"; //ogni db ha poi il nome dell'istanza di tomcat associata
    
    //nome della tabella che contiene le configurazioni delle istanze di tomcat
    public static final String MYSQL_TABLE_INSTANCE = "INSTANCE";
    public static final String MYSQL_INSTANCE_COLUMN_CONNECTOR_PORT = "Connector_Port";
    public static final String MYSQL_INSTANCE_COLUMN_SHUTDOWN_PORT = "Shutdown_Port";
    public static final String MYSQL_INSTANCE_COLUMN_AJP_PORT = "Ajp_Port";
    
    //utente admin del database JRSAAS_CONFIG in mysql
    public static final String USER_JRSAAS = "USER_JRSAAS"; //username 
    public static final String USER_JRSAAS_PWD = "USER_JRSAAS_PWD"; //password
    
    //utente root di MySql, serve per poter creare un nuovo database
    public static final String ADMIN_JRSAAS = "root"; //username 
    public static final String ADMIN_JRSAAS_PWD = "rootpwd"; //password
    
}
