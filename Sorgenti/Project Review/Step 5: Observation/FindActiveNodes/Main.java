/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.findactivenodes;

import static com.luca.findactivenodes.Constants.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import javax.jcr.*;
import org.apache.jackrabbit.commons.JcrUtils;

/**
 *
 * @author luca
 */
public class Main {
    
    static String cluster_name = "";
    static String db_user = "";
    static String db_pass = "";
    
    public static void main(String[] args) throws Exception 
    {
        CheckArguments(args);
        
        Get_User_Credentials();
        
        List<String> active_nodes_url = Get_Active_Nodes_Url();
        
        //DEBUG
        for(int i = 0; i < active_nodes_url.size(); i++)
        {
            System.out.println("nodo attivo: " + active_nodes_url.get(i) + "\r\n");
        }
        System.out.println("Fine\r\n");
    }

   static void CheckArguments(String[] args)
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //nome della nuova istanza di tomcat
            if(args[i].equals("-cluster"))
            {
                cluster_name = args[++i];                
            }            
            if(args[i].equals("--help"))
            {
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-cluster <cluster name find all active nodes>,\r\n");                
                System.exit(0);
            }              
        }
    }

static void Get_User_Credentials() throws SQLException
{
       String query = "SELECT * FROM " + MYSQL_JRSAAS_CONFIG_DB + "." + MYSQL_TABLE_CLUSTER + " WHERE nome = '" + cluster_name + "';";
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_JRSAAS_CONFIG_DB + "?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");
        Statement s = cn.createStatement();
        
         //definisce nuovo Id e Nome per il nuovo nodo jackrabbit
         ResultSet rs = s.executeQuery(query);         
         
         if(rs.next() == false)
         {
             System.out.println("Nessun cluster trovato con questo nome: " +cluster_name);
             System.exit(1);
         }
         else
         {
             db_user = rs.getString("user");
             db_pass = rs.getString("userpwd");
         }    
       
        cn.close();    
        return;
}        
   
   static List<String> Get_Nodes_Url() throws SQLException
   {       
       //contiene l'url di tutti i nodi del cluster
         List<String> url = new ArrayList<String>();
         //contiene l'url solo dei nodi attivi
         List<String> output = new ArrayList<String>();
       String query = "SELECT * FROM " + MYSQL_JRSAAS_CONFIG_DB + "." + MYSQL_TABLE_CLUSTER_NODE + " WHERE cluster = '" + cluster_name + "';";
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_JRSAAS_CONFIG_DB + "?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");
        Statement s = cn.createStatement();
        
         //definisce nuovo Id e Nome per il nuovo nodo jackrabbit
         ResultSet rs = s.executeQuery(query);         
         
         while(rs.next())
         {
             String host = rs.getString("host");
             String connector_port = rs.getString("connector_port");
             String u = "http://" + host + ":" + connector_port + "/jackrabbit/server/";
             url.add(u);
         }    
       
        cn.close();
        return url;
   }
   
static List<String> Get_Active_Nodes_Url() throws SQLException
{
    List<String> url = Get_Nodes_Url();
    List<String> active_nodes_url = new ArrayList<String>();
//per ogni url prova a creare una sessione, se non genera eccezioni vuol dire che il nodo è attivo
    for(int i = 0; i < url.size(); i++)
    {
        try
        {
            Repository repo = JcrUtils.getRepository(url.get(i));
            Credentials sc = new SimpleCredentials(db_user,db_pass.toCharArray());
            Session s = repo.login(sc,"default");
            active_nodes_url.add(url.get(i));
        }
        catch(RepositoryException re)
        {
            
        }            
    }    
    return active_nodes_url;
}        
    
}

