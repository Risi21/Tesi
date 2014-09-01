/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.step7;

import static com.luca.step7.Constants.*; //importa tutte le costanti

import java.io.*;
import java.util.*; //per le liste e stringhe
import java.nio.file.*; //copiare .war file in tomcat webapps
import java.net.*; //ServerSocket, per trovare porte libere
import java.sql.*; //mysql

/**
 *
 * @author luca
 */
public class Main {
    
    static String instance_name = ""; //nome deòò'istanza di Tomcat
    static int shutdown_port = 0;
    static int connector_port = 0;
    static int ajp_port = 0;            
    
     public static void main(String[] args) throws Exception 
    {
        //TODO: da controllare numeri di porta corretti e instance name (che non esista già e != da scripts, nel db mysql)
        CheckArguments(args);

        //N.B. non ci può essere un'istanza chiamata scripts
        CreateInstanceFolder();
        
        //crea sottocartelle:  conf  logs  temp  webapps  work
        CreateInstanceSubFolders();
        
         //TODO: Controlla che le 3 porte siano libere e non attualmente in uso
        //TODO: 
        /*
        per controllare che le porte siano libere, bisogna controllare
        oltre che siano libere nel momento del check,
        che non siano già state assegnate ad una istanza di tomcat (anche se è spenta e le porte libere)
        Quindi fare una tabella in mysql con nome istanza e porte usate
        */
        EditServerConf();
        
        //crea start_<instance_name>.sh e stop_<instance_name>.sh
        //dentro alla cartella scripts
        CreateScripts();
        
        CopyWarFile();
        
        Start_Instance();
        
        //Inizio Creazione Repository (Cartelle Repository Workspaces e File repository.xml e bootstrap.properties)
        
        //Aspetta che tomcat deploy jackrabbit-webapp.war
        
        //modifica WEB-INF/web.xml
        
        //...
        
        
    }
    
   static void CheckArguments(String[] args)
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //nome della nuova istanza di tomcat
            if(args[i].equals("-name"))
            {
                instance_name = args[++i];
            }            
            if(args[i].equals("--help"))
            {
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-name <new tomcat instance name>,\r\n");                
                System.exit(0);
            }              
        }
    }
   
   static void CreateInstanceFolder() throws IOException, InterruptedException
   {
        Process p = Runtime.getRuntime().exec("sudo mkdir " + TOMCAT_PATH + "/" + instance_name);
        p.waitFor();  
        
        //cambia proprietario all'utente tomcat
        p = Runtime.getRuntime().exec("sudo chown -R " + TOMCAT_VERSION + ":" + TOMCAT_VERSION + " " + TOMCAT_PATH + "/" + instance_name);
        p.waitFor();
   }        
   // conf  logs  temp  webapps  work
   static void CreateInstanceSubFolders() throws IOException, InterruptedException
   {
        Process p = Runtime.getRuntime().exec("sudo mkdir " + TOMCAT_PATH + "/" + instance_name + "/conf");
        p.waitFor();
        p = Runtime.getRuntime().exec("sudo mkdir " + TOMCAT_PATH + "/" + instance_name + "/logs");
        p.waitFor();
        p = Runtime.getRuntime().exec("sudo mkdir " + TOMCAT_PATH + "/" + instance_name + "/temp");
        p.waitFor();
        p = Runtime.getRuntime().exec("sudo mkdir " + TOMCAT_PATH + "/" + instance_name + "/webapps");
        p.waitFor();
        p = Runtime.getRuntime().exec("sudo mkdir " + TOMCAT_PATH + "/" + instance_name + "/work");
        p.waitFor();  
        
        //cambia proprietario all'utente tomcat per tutte le sottocartelle
        p = Runtime.getRuntime().exec("sudo chown -R " + TOMCAT_VERSION + ":" + TOMCAT_VERSION + " " + TOMCAT_PATH + "/" + instance_name);
        p.waitFor();  
        
        //riempie la cartella /conf con tutti i file che si trovano in /etc/tomcat7
        p = Runtime.getRuntime().exec("sudo cp -a /etc/" + TOMCAT_VERSION + "/. " + TOMCAT_PATH + "/" + instance_name + "/conf/");
        p.waitFor();          

       //cancella il file server.xml copiato prima in /conf
        p = Runtime.getRuntime().exec("sudo rm " + TOMCAT_PATH + "/" + instance_name + "/conf/server.xml");
        p.waitFor();                
   }
   
 
   static void EditServerConf() throws IOException, SQLException
   {       
       //vettore che contiene le 3 porte:
       //ports[0] = connector_port
       //ports[1] = shutdown_port
       //ports[2] = ajp_port
       int[] ports = new int [3];
       
       
       //trova 3 porte libere sia sul server che sul db per l'istanza di tomcat nuova
       SetTomcatPorts(ports);
       
       //scrive file/conf/server.xml con le porte settate prima
       WriteConfServerXml(ports);
       
       //Update INSTANCE DB, ADD 3 porte
       UpdateDB(ports);
   }
   
   public static void SetTomcatPorts(int[] ports) throws IOException, SQLException
   {
       
       int connector_port = FindFreePort(CONNECTOR_MIN,CONNECTOR_MAX, MYSQL_INSTANCE_COLUMN_CONNECTOR_PORT);
       int shutdown_port = FindFreePort(SHUTDOWN_MIN,SHUTDOWN_MAX, MYSQL_INSTANCE_COLUMN_SHUTDOWN_PORT);
       int ajp_port = FindFreePort(AJP_MIN,AJP_MAX, MYSQL_INSTANCE_COLUMN_AJP_PORT);
       
       ports[0] = connector_port;
       ports[1] = shutdown_port;
       ports[2] = ajp_port;
       
   }        
   
   //ports[0] = connector, ports[1] = shutdown, ports[2] = ajp
   public static void WriteConfServerXml(int[] ports)
   {
       BufferedWriter writer = null;
        try 
        {                        
            File bp = new File(TOMCAT_PATH + "/" + instance_name +  "/conf/server.xml");

            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("<?xml version='1.0' encoding='utf-8'?>\n" +
"<!--Trovi tutti i commenti in /etc/tomcat7/server.xml-->\n" +
"<Server port=\"" + ports[1] + "\" shutdown=\"SHUTDOWN\">\n" +
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
"    <Connector port=\"" + ports[0] + "\" protocol=\"HTTP/1.1\"\n" +
"               connectionTimeout=\"20000\"\n" +
"               URIEncoding=\"UTF-8\"\n" +
"               redirectPort=\"8443\" />\n" +
"    <Connector port=\"" + ports[2] + "\" protocol=\"AJP/1.3\" redirectPort=\"8443\" />\n" +
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
   
   public static void UpdateDB(int[] ports) throws SQLException
   {
       //inserisce nome istanza nel database JRSAAS mysql e le 3 porte configurate
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_DB + "?user=" + USER_JRSAAS + "&password=" + PWD_JRSAAS + "");
        Statement s = cn.createStatement();
        int Result = s.executeUpdate("INSERT INTO " + MYSQL_DB + "." + MYSQL_TABLE_INSTANCE + " VALUES ("
                + "'" + instance_name + "'," //nome dell'istanza di tomcat
                + ports[0] + ", "   //connector port
                + ports[1] + ", "  //shutdown port
                + ports[2] + ");"); //ajp port
        cn.close();
        System.out.println("1 riga aggiunta alla tabella " + MYSQL_TABLE_INSTANCE + " \r\n");       
   }        
   
    public static int FindFreePort(int min_port, int max_port, String port_column_name) throws IOException, SQLException 
    {        
        ServerSocket output;
        for (int port = min_port; port <= max_port; port++) 
        {
            try 
            {
                output = new ServerSocket(port);
                //controlla se la porta, oltre ad essere libera attualmente nel server,
                //non appartiene ad un'istanza di tomcat che attualmente è spenta
                //(quindi le sue porte risultano libere per il S.O.)
                boolean used = Is_Port_In_MySql(port, port_column_name);
                if(used)
                {
                    //controlla la porta successiva in senso numerico (++)
                    continue;
                }    
                return output.getLocalPort();
            } 
            catch (IOException ex) 
            {            
                continue; // try next port
            }
    }

    // if the program gets here, no port in the range was found
    throw new IOException("no free port found");
    
}   
   
    static boolean Is_Port_In_MySql(int port, String port_column_name) throws SQLException
   {
               //TODO: CheckPortDb 
        /*
        per controllare che le porte siano libere, bisogna controllare
        oltre che siano libere nel momento del check,
        che non siano già state assegnate ad una istanza di tomcat (anche se è spenta e le porte libere)
        Quindi fare una tabella in mysql con nome istanza e porte usate
        Se la porta appartiene ad una istanza spenta, si fa continue)
                */       
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_DB + "?user=" + USER_JRSAAS + "&password=" + PWD_JRSAAS + "");
        Statement s = cn.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + MYSQL_TABLE_INSTANCE + " WHERE " + port_column_name + " = " + port);
        //prende il valore di count
        rs.next();
        int count = rs.getInt("COUNT(*)");
         // close ResultSet rs
         rs.close();        
        cn.close();       
       //se la porta è già in uso => count = 1, altrimenti = 0
        if(count == 1)
        {
            return true; //used = true
        }
        return false; //used = true
   }         
    
   static void CreateScripts() throws IOException, InterruptedException
   {
        File theDir = new File(TOMCAT_PATH + "/scripts");
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
            File bp = new File(TOMCAT_PATH + "/scripts/start_" + instance_name + ".sh");

            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("export CATALINA_HOME=/usr/share/" + TOMCAT_VERSION + "\n" +
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
            File bp = new File(TOMCAT_PATH + "/scripts/stop_" + instance_name + ".sh");            
            
            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("export CATALINA_HOME=/usr/share/" + TOMCAT_VERSION + "\n" +
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
        Process p = Runtime.getRuntime().exec("sudo chmod u+x " + TOMCAT_PATH + "/scripts/start_" + instance_name + ".sh");
        p.waitFor();        
        
        p = Runtime.getRuntime().exec("sudo chmod u+x " + TOMCAT_PATH + "/scripts/stop_" + instance_name + ".sh");
        p.waitFor();        
        
   }        
   
   static void CopyWarFile() throws IOException
   {
	File src = new File("/home/luca/Scaricati/jr2.war"); 
	File dst = new File(TOMCAT_PATH + "/" + instance_name + "/webapps/jr2.war"); 
       Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
       System.out.println("Copiato file jackrabbit-webapp.war da /home/luca/Scaricati/jr2.war a " + TOMCAT_PATH + "/" + instance_name + "\r\n");
   }
   
   static void Start_Instance() throws IOException, InterruptedException
   {
        Process p = Runtime.getRuntime().exec("sudo sh " + TOMCAT_PATH + "/scripts/start_" + instance_name + ".sh");
        p.waitFor();       
   }        

}

