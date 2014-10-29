/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.setupj_rsaas;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 *
 * @author luca
 */
public class Main {
    
    static String db_user = "";
    static String db_pass = "";
    static String mysql_url = "localhost:3306";
    static String tomcat = "tomcat-8.0.12.tar.gz";
    
    public static void main(String[] args) throws Exception 
    {       
        CheckArguments(args);
        
        //N.B: java deve già essere installato, quindi chiamare prima lo script sh installer java
        
        //estrae tar.gz, sposta cartella tomcat in /usr/share, aggiunge 2 export al file .bashrc
        Setup_Tomcat();
        
        //crea cartelle in /srv, crea database mysql JRSAAS_CONFIG, crea utente mysql root ?
        Setup_JRSAAS();
    }
    
  static void CheckArguments(String[] args)
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //nome della nuova istanza di tomcat
            if(args[i].equals("-mysql_url"))
            {
                mysql_url = args[++i];
            }
            
            if(args[i].equals("-dbuser"))
            {
                db_user = args[++i];
                db_pass = args[++i];
            }
            if(args[i].equals("--help"))
            {
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-dbuser <username> <password>, utente root di mysql\r\n"
                        + "-mysql_url <url>, url di mysql, default localhost:3306");
                System.exit(0);
            }              
        }
    }          

    static void Setup_Tomcat() throws IOException, InterruptedException
    {
        //N.B. il file tomcat.tar.gz DEVE essere nella stessa cartella del jar
        
        String current_exe_path = System.getProperty("user.dir");        
        System.out.println("path corrente: " + current_exe_path);
        
        //estrae cartella tomcat in /usr/share
        String cmd = "sudo tar -zxvf " + current_exe_path + "/" + tomcat + " -C /usr/share/";
        System.out.println("Estraggo tomcat in /usr/share/ " + cmd);
        ForkProcess(cmd);
        
        //aggiunge al file .bashrc i 2 export
        Append_Bashrc();
        
        //esegue file bashrc per applicare i 2 export:
        cmd = ". ~/.bashrc";
        System.out.println("Eseguo file bashrc " + cmd);
        ForkProcess(cmd);
        
    }
    
    static void Append_Bashrc() throws IOException
    {
        String home = System.getProperty("user.home");
        String bashrc = home + "/.bashrc";
        System.out.println("file bashrc: " + bashrc);
        
        File toAppend= new File(bashrc);
        //aggiunge 2 riche di export
        FileWriter f = new FileWriter(toAppend, true);
        f.write("export CATALINA_HOME=/usr/share/tomcat-8.0.12\n" +
"export JAVA_HOME=/usr/lib/jvm/java-8-oracle");
        f.close();            
    }    
    
    static void Setup_JRSAAS() throws IOException, InterruptedException, SQLException
    {
        //crea cartella /srv/cluster
        String cmd = "sudo mkdir /srv/cluster";
        System.out.println("Crea cartelle jrsaas " + cmd);
        ForkProcess(cmd);
        
        Create_MySqlDb();
    }  
    
    static void Create_MySqlDb() throws SQLException
    {
        System.out.println("\r\nCreo db mysql JRSAAS_CONFIG nel mysql server " + mysql_url + "\r\n");       
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + mysql_url + "/?user=" + db_user + "&password=" + db_pass + "");
        Statement s = cn.createStatement();        
        int Result = s.executeUpdate("CREATE DATABASE JRSAAS_CONFIG;");
        
        Result = s.executeUpdate("Create Table JRSAAS_CONFIG.CLUSTER(\n" +
"\n" +
"nome varchar(50) not null,\n" +
"user varchar(50) not null,\n" +
"userpwd varchar(50) not null,\n" +
"mysql_url varchar(50) not null,\n" +
"db_name varchar(50) not null,\n" +
"primary key (nome)\n" +
"\n" +
");");

                Result = s.executeUpdate("Create Table JRSAAS_CONFIG.CLUSTER_NODE(\n" +
"\n" +
"nome varchar(50) not null,\n" +
"cluster varchar(50) not null,\n" +
"host varchar(50) not null,\n" +
"connector_Port integer not null,\n" +
"shutdown_Port integer not null,\n" +
"ajp_Port integer not null,\n" +
"Node_Id integer not null,\n" +
"\n" +
"primary key (nome),\n" +
"foreign key (cluster) references JRSAAS_CONFIG.CLUSTER(nome)\n" +
"\n" +
");");
        
        /*//crea utente
        System.out.println("\r\nCreo utente mysql " + db_user + " con permessi di lettura e scrittura solo per il db " + repo_name + "\r\n");       
        Result = s.executeUpdate("grant usage on *.* to " + db_user + "@localhost identified by '" + db_password + "';");
        Result = s.executeUpdate("grant all privileges on " + repo_name + ".* to " + db_user + "@localhost;");
        */
        cn.close();        
    }    
    
    static int ForkProcess(String cmd) throws IOException, InterruptedException
    {
        //esporta datastore
        //dentro a /srv/backup/<mysqldb_cluster_name>
        
        Process p = Runtime.getRuntime().exec(new String[] { "/bin/bash", "-c", cmd });
        int status = p.waitFor();
        
        //DEBUG
        
        System.out.println("ERRORI:");
            String line = "";
        
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = in.readLine()) != null) 
            {
                 System.out.println(line);
            }
            in.close();
            
            System.out.println("INPUT:");
            line = "";
        
            in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = in.readLine()) != null) 
            {
                 System.out.println(line);
            }
            in.close();   
        
        /*
        if(status == 0)
        {
            //terminato normalmente                                         
        }
        else
        {
            //stampo errori
            String line2 = "";
        
            BufferedReader in2 = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line2 = in2.readLine()) != null) 
            {
                 System.out.println(line2);
            }
            in2.close();
        }
        */
        return status;
    }   
    
    
}

