/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.creadb;

import java.io.*;
import java.util.*;

import java.sql.*;



/**
 *
 * @author luca
 */
public class Main {
    
    //static String url = "http://localhost:8080/jackrabbit-webapp-2.8.0/server";
    
//percorso di dove si trova il repository.xml
    	static String url = "localhost:3306";
    	static String root = "root";
    	static String rootpwd = "rootpwd";
	static String db_name = "";
    	static String user = "";
	static String userpwd = "";
    
    public static void main(String[] args) throws Exception 
    {
        //Repository repository = JcrUtils.getRepository(url); 
        CheckArguments(args);                

        Connection cn = DriverManager.getConnection("jdbc:mysql://" + url + "/?user=" + root + "&password=" + rootpwd + "");
        Statement s = cn.createStatement();
        int Result = s.executeUpdate("CREATE DATABASE " + db_name + ";");
        Result = s.executeUpdate("grant usage on *.* to " + user + "@localhost identified by '" + userpwd + "';");
	Result = s.executeUpdate("grant all privileges on " + db_name + ".* to " + user + "@localhost;");
        cn.close();
    }
    
   static void CheckArguments(String[] args)
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //url del server mysql
            if(args[i].equals("-url"))
            {
                url = args[++i];
            }
            //utente per loggarsi su mysql (meglio se root)
            if(args[i].equals("-login"))
            {
                root = args[++i];
		rootpwd = args[++i];
            }
            //nome del nuovo database
            if(args[i].equals("-db"))
            {
                db_name = args[++i];
            }
            //nome del nuovo utente che avrà tutti i privilegi sul nuovo database creato
            if(args[i].equals("-user"))
            {
                user = args[++i];
                userpwd = args[++i];
            }
            if(args[i].equals("--help"))
            {
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-login <root> <rootpwd>, necessario per fare login in mysql\r\n"
                        + "-db <nome database>, nome del nuovo database che viene creato\r\n"
                        + "-user <username> <password>, crea nuovo utente per il database creato con tutti i privilegi\r\n");                
                System.exit(0);
            }              
        }
    }
}
