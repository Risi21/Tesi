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
    
    public static final String TOMCAT_PATH = "/srv/tomcat"; //contiene le istanze di tomcat
    public static final String TOMCAT_VERSION = "tomcat7"; //versione di tomcat installata nel server
    
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
    public static final String MYSQL_DB = "JRSAAS";
    //nome della tabella che contiene le configurazioni delle istanze di tomcat
    public static final String MYSQL_TABLE_INSTANCE = "INSTANCE";
    
    //utente admin del database JRSAAS in mysql
    public static final String USER_JRSAAS = "ADMIN"; //username 
    public static final String PWD_JRSAAS = "ADMIN_PWD"; //password
    
}

