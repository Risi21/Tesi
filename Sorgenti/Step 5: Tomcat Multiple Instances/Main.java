/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mycompany.tomcatcreate;

import java.io.*;
import java.util.*;

import java.nio.file.*; //copiare .war file in tomcat webapps

/**
 *
 * @author luca
 */
public class Main {
    
    static String instance_name = ""; //nome deòò'istanza di Tomcat
    static String server_path = ""; //percorso della cartella che contiene tute le istanze di Tomcat
    static int shutdown_port = 0;
    static int connector_port = 0;
    static int ajp_port = 0;        
    
    static String tomcat = "";
    
     public static void main(String[] args) throws Exception 
    {
        //TODO: da controllare numeri di porta corretti e instance name (che non esista già e != da scripts, nel db mysql)
        CheckArguments(args);

        //N.B. non ci può essere un'istanza chiamata scripts
        CreateInstanceFolder();
        
        //crea sottocartelle:  conf  logs  temp  webapps  work
        CreateInstanceSubFolders();
        
         //TODO: Controlla che le 3 porte siano libere e non attualmente in uso
        EditServerConf();
        
        //crea start_<instance_name>.sh e stop_<instance_name>.sh
        //dentro alla cartella scripts
        CreateScripts();
        
        //DEBUG
        CopyWarFile();
        
        Start_Instance();
        
    }
    
   static void CheckArguments(String[] args)
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //url del server mysql
            if(args[i].equals("-name"))
          {
                instance_name = args[++i];
            }
            //utente per loggarsi su mysql (meglio se root)
            if(args[i].equals("-path"))
            {
                server_path = args[++i];
            }
            //nome del nuovo database
            if(args[i].equals("-shutdown"))
            {
                shutdown_port = Integer.parseInt(args[++i]);
            }
            //nome del nuovo utente che avrà tutti i privilegi sul nuovo database creato
            if(args[i].equals("-connector"))
            {
                   connector_port = Integer.parseInt(args[++i]);
            }
            if(args[i].equals("-ajp"))
            {
                ajp_port = Integer.parseInt(args[++i]);
            }
            if(args[i].equals("-tomcat"))
            {
                tomcat = args[++i];
            }            
            if(args[i].equals("--help"))
            {
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-name <new tomcat instance name>,\r\n"
                        + "-path <path to container of Tomcat instances>,\r\n"
                        + "-shutdown <port number>, la porta deve essere libera\r\n"
                        + "-connector <port number> la porta deve essere libera\r\n"
                        + "-ajp <port number>, la porta deve essere libera\r\n"
                        + "-tomcat <versione di tomcat> (es tomcat7)\r\n");
                System.exit(0);
            }              
        }
    }
   
   static void CreateInstanceFolder() throws IOException, InterruptedException
   {
        Process p = Runtime.getRuntime().exec("sudo mkdir " + server_path + "/" + instance_name);
        p.waitFor();  
        
        //cambia proprietario all'utente tomcat
        p = Runtime.getRuntime().exec("sudo chown -R " + tomcat + ":" + tomcat + " " + server_path + "/" + instance_name);
        p.waitFor();
   }        
   // conf  logs  temp  webapps  work
   static void CreateInstanceSubFolders() throws IOException, InterruptedException
   {
        Process p = Runtime.getRuntime().exec("sudo mkdir " + server_path + "/" + instance_name + "/conf");
        p.waitFor();
        p = Runtime.getRuntime().exec("sudo mkdir " + server_path + "/" + instance_name + "/logs");
        p.waitFor();
        p = Runtime.getRuntime().exec("sudo mkdir " + server_path + "/" + instance_name + "/temp");
        p.waitFor();
        p = Runtime.getRuntime().exec("sudo mkdir " + server_path + "/" + instance_name + "/webapps");
        p.waitFor();
        p = Runtime.getRuntime().exec("sudo mkdir " + server_path + "/" + instance_name + "/work");
        p.waitFor();  
        
        //cambia proprietario all'utente tomcat per tutte le sottocartelle
        p = Runtime.getRuntime().exec("sudo chown -R " + tomcat + ":" + tomcat + " " + server_path + "/" + instance_name);
        p.waitFor();  
        
        //riempie la cartella /conf con tutti i file che si trovano in /etc/tomcat7
        p = Runtime.getRuntime().exec("sudo cp -a /etc/" + tomcat + "/. " + server_path + "/" + instance_name + "/conf/");
        p.waitFor();          

       //cancella il file server.xml copiato prima in /conf
        p = Runtime.getRuntime().exec("sudo rm " + server_path + "/" + instance_name + "/conf/server.xml");
        p.waitFor();                
   }
   
   static void EditServerConf()
   {
       //scrive file/conf/server.xml
       BufferedWriter writer = null;
        try 
        {                        
            File bp = new File(server_path + "/" + instance_name +  "/conf/server.xml");

            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("<?xml version='1.0' encoding='utf-8'?>\n" +
"<!--Trovi tutti i commenti in /etc/tomcat7/server.xml-->\n" +
"<Server port=\"" + shutdown_port + "\" shutdown=\"SHUTDOWN\">\n" +
"  <Listener className=\"org.apache.catalina.core.JasperListener\" />\n" +
"  <Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" />\n" +
"  <Listener className=\"org.apache.catalina.mbeans.GlobalResourcesLifecycleListener\" />\n" +
"  <Listener className=\"org.apache.catalina.core.ThreadLocalLeakPreventionListener\" />\n" +
"  <GlobalNamingResources>\n" +
"    <Resource name=\"UserDatabase\" auth=\"Container\"\n" +
"              type=\"org.apache.catalina.UserDatabase\"\n" +
"              description=\"User database that can be updated and saved\"\n" +
"              factory=\"org.apache.catalina.users.MemoryUserDatabaseFactory\"\n" +
"              pathname=\"conf/tomcat-users.xml\" />\n" +
"  </GlobalNamingResources>\n" +
"  <Service name=\"Catalina\">\n" +
"    <Connector port=\"" + connector_port + "\" protocol=\"HTTP/1.1\"\n" +
"               connectionTimeout=\"20000\"\n" +
"               URIEncoding=\"UTF-8\"\n" +
"               redirectPort=\"8443\" />\n" +
"    <Connector port=\"" + ajp_port + "\" protocol=\"AJP/1.3\" redirectPort=\"8443\" />\n" +
"    <Engine name=\"Catalina\" defaultHost=\"localhost\">\n" +
"      <Realm className=\"org.apache.catalina.realm.LockOutRealm\">\n" +
"        <Realm className=\"org.apache.catalina.realm.UserDatabaseRealm\"\n" +
"               resourceName=\"UserDatabase\"/>\n" +
"      </Realm>\n" +
"\n" +
"      <Host name=\"localhost\"  appBase=\"webapps\"\n" +
"            unpackWARs=\"true\" autoDeploy=\"true\">\n" +
"\n" +
"        <Valve className=\"org.apache.catalina.valves.AccessLogValve\" directory=\"logs\"\n" +
"               prefix=\"localhost_access_log.\" suffix=\".txt\"\n" +
"               pattern=\"%h %l %u %t &quot;%r&quot; %s %b\" />\n" +
"\n" +
"      </Host>\n" +
"    </Engine>\n" +
"  </Service>\n" +
"</Server>");
           
            // This will output the full path where the file will be written to...
            System.out.println("Creato file: " + bp.getCanonicalPath() + "\r\n");            
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        } 
        finally 
        {
            try 
            {
                // Close the writer regardless of what happens...
                writer.close();
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }            
   }        
   
   static void CreateScripts() throws IOException, InterruptedException
   {
        File theDir = new File(server_path + "/scripts");
        // if the directory does not exist, create it
        if (!theDir.exists()) 
        {
            System.out.println("creating directory: scripts");
            try
            {
                theDir.mkdir();
             } 
            catch(Exception e)
            {
                e.printStackTrace();
            }
         }
        
        //dentro alla cartella scripts scrive gli script di start e stop per questa istanza di tomcat:
        //start_<instance_name>.sh
       BufferedWriter writer = null;
        try 
        {            
            File bp = new File(server_path + "/scripts/start_" + instance_name + ".sh");

            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("export CATALINA_HOME=/usr/share/" + tomcat + "\n" +
"export CATALINA_BASE=/srv/tomcat/" + instance_name + "\n" +
"cd $CATALINA_HOME/bin\n" +
"./startup.sh");        
           
            // This will output the full path where the file will be written to...
            System.out.println("Creato file: " + bp.getCanonicalPath() + "\r\n");            
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        } 
        finally 
        {
            try 
            {
                // Close the writer regardless of what happens...
                writer.close();
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }            
        
       //stop_<instance_name>.sh
        writer = null;
        try 
        {            
            File bp = new File(server_path + "/scripts/stop_" + instance_name + ".sh");            
            
            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("export CATALINA_HOME=/usr/share/" + tomcat + "\n" +
"export CATALINA_BASE=/srv/tomcat/" + instance_name + "\n" +
"cd $CATALINA_HOME/bin\n" +
"./shutdown.sh");        
           
            // This will output the full path where the file will be written to...
            System.out.println("Creato file: " + bp.getCanonicalPath() + "\r\n");            
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        } 
        finally 
        {
            try 
            {
                // Close the writer regardless of what happens...
                writer.close();
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }        
        
        //da i permessi di esecuzione ai 2 file
        Process p = Runtime.getRuntime().exec("sudo chmod u+x " + server_path + "/scripts/start_" + instance_name + ".sh");
        p.waitFor();        
        
        p = Runtime.getRuntime().exec("sudo chmod u+x " + server_path + "/scripts/stop_" + instance_name + ".sh");
        p.waitFor();        
        
   }        
   
   static void CopyWarFile() throws IOException
   {
	File src = new File("/home/luca/Scaricati/jr2.war"); 
	File dst = new File(server_path + "/" + instance_name + "/webapps/jr2.war"); 
       Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
       System.out.println("Copiato file jackrabbit-webapp.war da /home/luca/Scaricati/jr2.war a " + server_path + "/" + instance_name + "\r\n");
   }
   
   static void Start_Instance() throws IOException, InterruptedException
   {
        Process p = Runtime.getRuntime().exec("sudo sh " + server_path + "/scripts/start_" + instance_name + ".sh");
        p.waitFor();       
   }        

}
