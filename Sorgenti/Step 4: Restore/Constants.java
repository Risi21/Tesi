/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.restore;

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
    
    //path dei backup dei repository
    public static final String PATH_BACKUP = "/srv/backup";
    //prefisso del nome del database in Mysql
    public static final String MYSQL_REPO_PREFIX = "JRSAAS_REPO_"; //ogni db ha poi il nome dell'istanza di tomcat associata
    //cartella contenente tutti i repository
    public static final String ROOT_REPO_PATH = "/srv/repo"; //contiene tutti i repository di jackrabbit, hanno lo stesso nome dell'istanza di tomcat associata

    public static final String TOMCAT_SCRIPTS = "/srv/tomcat/scripts"; //contiene gli scripts per avviare / stoppare le istanze di tomcat
    
    //url MySql server:
    public static final String MYSQL_URL = "localhost:3306";

    //utente root di MySql, serve per poter creare un nuovo database
    public static final String ADMIN_JRSAAS = "JRSAAS_ROOT"; //username 
    public static final String ADMIN_JRSAAS_PWD = "JRSAAS_ROOT_PWD"; //password
    
}

