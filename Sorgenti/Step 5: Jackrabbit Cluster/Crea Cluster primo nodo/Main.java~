/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.clusternfs;

import static com.luca.clusternfs.Constants.*; //importa tutte le costanti

import java.io.*;
import java.util.*; //per le liste e stringhe
import java.nio.file.*; //copiare .war file in tomcat webapps
import java.net.*; //ServerSocket, per trovare porte libere
import java.sql.*; //mysql

//Serve per Repository e Session
import javax.jcr.*;
import org.apache.jackrabbit.api.security.user.*;
import org.apache.jackrabbit.api.*;
import org.apache.jackrabbit.core.*;
import org.apache.jackrabbit.core.config.*;

/**
 *
 * @author luca
 */
public class Main {
    
    static int shutdown_port = 0;
    static int connector_port = 0;
    static int ajp_port = 0;
    //username utente nuovo mysql con permessi r/w solo per il nuovo database creato per la nuova istanza di tomcat / repository jackrabbit
    static String db_user = ""; 
    static String db_password = "";

    static String cluster_name = "";
    static String cluster_root_path = "";
    //path backup del datastore e pm del cluster
    static String cluster_backup_path = "";
    //path datastore cluster nel file system
    static String cluster_datastore_path = "";
    //nome mysqldb persistence manager
    static String cluster_pm_name = "";
    
    //primo nodo jackrabbit che appartiene al cluster
    static String jr_node_name = "";
    //cartella che contiene cartelle tomcat e repo
    static String jr_node_root_path = "";
    static String jr_node_tomcat_path = "";
    static String jr_node_tomcat_scripts_path = "";
    static String jr_node_tomcat_instance_path = "";
    //percorso alla cartella deployata di jackrabbit dentro a tomcat/webapps
    static String jr_node_tomcat_instance_webapp_path = "";
    //contiene anche il nome della cartella di jackrabbit deployata dal .war
    static String jr_node_tomcat_instance_webapp_jackrabbit_path = "";
    //log di tomcat catalina.out
    static String jr_node_tomcat_instance_log_path= "";
    static String jr_node_repo_path = "";

    
     public static void main(String[] args) throws Exception 
    {
        //TODO: da controllare numeri di porta corretti e instance name (che non esista già e != da scripts, nel db mysql)
        CheckArguments(args);

        CreateClusterFolders();
        
        CreateJR_NodeFolders();       
        
        //Controlla che le 3 porte siano libere e non attualmente in uso
        connector_port = EditServerConf();
        
        //crea start_<instance_name>.sh e stop_<instance_name>.sh
        //dentro alla cartella scripts
        CreateScripts();
        
        //copia jackrabit-webapp.war in webapps dell'istanza di tomcat appena creata
        //CopyWarFile();
        
        //esporta jackrabbit-webapp.war da dentro il jar nella cartella webapps di tomcat
        ExportWarFile();
        
        //richiama lo script di start
        Start_Instance();        
        
       //crea persistence manager (pm) in mysql
       //crea database mysql nuovo per questa istanza di tomcat e repository jackrabbit
       //crea utente con permessi di lettura e scrittura solo per questo database
       CreaMySqlPM();        
        
        //Inizio Creazione Repository (Cartelle Repository Workspaces e File repository.xml e bootstrap.properties)
        CreateNewRepo();
        
    }
    
   static void CheckArguments(String[] args)
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //nome della nuova istanza di tomcat
            if(args[i].equals("-name"))
            {
                cluster_name = args[++i];
                cluster_pm_name = MYSQL_PM_PREFIX + cluster_name;
                cluster_root_path = ROOT_CLUSTER_PATH + "/" + cluster_name;
                cluster_backup_path = cluster_root_path + "/backup";
                cluster_datastore_path = cluster_root_path + "/datastore";
                
                jr_node_name = cluster_name + "_1";
                jr_node_root_path = cluster_root_path + "/" + jr_node_name;
                jr_node_tomcat_path = jr_node_root_path + "/tomcat";
                jr_node_repo_path = jr_node_root_path + "/repo";
                jr_node_tomcat_instance_path = jr_node_tomcat_path + "/instance";
                jr_node_tomcat_scripts_path = jr_node_tomcat_path + "/scripts";
                jr_node_tomcat_instance_log_path = jr_node_tomcat_instance_path + "/logs/catalina.out";
                jr_node_tomcat_instance_webapp_path = jr_node_tomcat_instance_path + "/webapps";
                jr_node_tomcat_instance_webapp_jackrabbit_path = jr_node_tomcat_instance_webapp_path + "/" + JACKRABBIT_WEBAPP_DIRNAME;
            }
            //nome della nuova istanza di tomcat
            if(args[i].equals("-dbuser"))
            {            
                db_user = args[++i];
                db_password = args[++i];
            }
            if(args[i].equals("--help"))
            {
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-name <new tomcat instance name>,\r\n"
                        + "-dbuser <username> <password>, utente per database mysql e repository jackrabbit\r\n");                
                System.exit(0);
            }              
        }
    }

   static void CreateClusterFolders() throws IOException, InterruptedException
   {
       //creo cartella /srv/cluster/cluster_name
        String cmd = "sudo mkdir " + cluster_root_path;
        System.out.println("\r\nCreo la cartella root del cluster:\r\n" + cmd);
        ForkProcess(cmd);
        
        //creo cartella /srv/cluster/cluster_name/backup
        cmd = "sudo mkdir " + cluster_backup_path;
        System.out.println("\r\nCreo la cartella backup del cluster:\r\n" + cmd);
        ForkProcess(cmd);
        
        //creo cartella /srv/cluster/cluster_name/datastore
        cmd = "sudo mkdir " + cluster_datastore_path;
        System.out.println("\r\nCreo la cartella datastore del cluster:\r\n" + cmd);
        ForkProcess(cmd);        
        
   } 
   
   static void CreateJR_NodeFolders() throws IOException, InterruptedException
   {
       //creo cartella /srv/cluster/cluster_name/jr_node_name
        String cmd = "sudo mkdir " + jr_node_root_path;
        System.out.println("\r\nCreo la cartella root del primo jr_node:\r\n" + cmd);
        ForkProcess(cmd);       
        
        //creo cartella /srv/cluster/cluster_name/jr_node_name/tomcat
        cmd = "sudo mkdir " + jr_node_tomcat_path;
        System.out.println("\r\nCreo la cartella tomcat del primo jr_node:\r\n" + cmd);
        ForkProcess(cmd);
        
        //creo cartella /srv/cluster/cluster_name/jr_node_name/repo
        cmd = "sudo mkdir " + jr_node_repo_path;
        System.out.println("\r\nCreo la cartella repo del primo jr_node:\r\n" + cmd);
        ForkProcess(cmd);
        
        //creo cartella /srv/cluster/cluster_name/jr_node_name/tomcat/scripts
        cmd = "sudo mkdir " + jr_node_tomcat_scripts_path;
        System.out.println("\r\nCreo la cartella tomcat/scripts del primo jr_node:\r\n" + cmd);
        ForkProcess(cmd);
        
        //creo cartella /srv/cluster/cluster_name/jr_node_name/tomcat/instance
        cmd = "sudo mkdir " + jr_node_tomcat_instance_path;
        System.out.println("\r\nCreo la cartella tomcat/instance del primo jr_node:\r\n" + cmd);
        ForkProcess(cmd);
        
        CreateTomcatSubFolders();
        
   }    
   
   // conf  logs  temp  webapps  work
   static void CreateTomcatSubFolders() throws IOException, InterruptedException
   {
       System.out.println("\r\nCreo sottocartelle conf, logs, temp, webapps, work");       
       
       String cmd = "sudo mkdir " + jr_node_tomcat_instance_path + "/conf";       
       ForkProcess(cmd);
        
        cmd = "sudo mkdir " + jr_node_tomcat_instance_path + "/logs";
        ForkProcess(cmd);
        
        cmd = "sudo mkdir " + jr_node_tomcat_instance_path + "/temp";
        ForkProcess(cmd);
        
        cmd = "sudo mkdir " + jr_node_tomcat_instance_path + "/webapps";
        ForkProcess(cmd);

        cmd = "sudo mkdir " + jr_node_tomcat_instance_path + "/work";
        ForkProcess(cmd);
        
        //riempie la cartella /conf con tutti i file che si trovano in /usr/share/tomcat.../conf
        cmd = "sudo cp -a " + TOMCAT_CONF + "/* " + jr_node_tomcat_instance_path +  "/conf/";
        System.out.println("\r\nriempie la cartella /conf con tutti i file che si trovano in /etc/tomcat7:\r\n" + cmd);
        ForkProcess(cmd);
        
       //cancella il file server.xml copiato prima in /conf
        cmd = "sudo rm " + jr_node_tomcat_instance_path + "/conf/server.xml";
        System.out.println("\r\ncancella il file server.xml copiato prima in /conf:\r\n" + cmd);        
        ForkProcess(cmd);
        
   }
   
 
   static int EditServerConf() throws IOException, SQLException
   {       
       //vettore che contiene le 3 porte:
       //ports[0] = connector_port
       //ports[1] = shutdown_port
       //ports[2] = ajp_port
       int[] ports = new int [3];
       
       
       //trova 3 porte libere sia sul server che sul db per l'istanza di tomcat nuova
       System.out.println("\r\nTrova 3 porte libere per la nuova istanza di tomcat:");
       SetTomcatPorts(ports);
       
       //scrive file/conf/server.xml con le porte settate prima
       System.out.println("\r\nScrive /conf/server.xml con le 3 porte trovate prima:");
       System.out.println("Connector Port = " + ports[0]);
       System.out.println("Shutdown Port = " + ports[1]);
       System.out.println("Ajp Port = " + ports[2]);
       WriteConfServerXml(ports);
       
       //Update INSTANCE DB, ADD 3 porte
       System.out.println("\r\nAggiunge riga alla tabella INSTANCE con le 3 porte per la nuova istanza");
       UpdateDB(ports);
       
       return ports[0]; //ritorna la connector port dell'istanza di tomcat
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
            File bp = new File(jr_node_tomcat_instance_path + "/conf/server.xml");

            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("<?xml version='1.0' encoding='utf-8'?>\n" +
"\n" +
"<Server port=\"" + ports[1] + "\" shutdown=\"SHUTDOWN\">\n" +
"\n" +
"  <Listener className=\"org.apache.catalina.core.AprLifecycleListener\" SSLEngine=\"on\" />\n" +
"  <Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" />\n" +
"  <Listener className=\"org.apache.catalina.mbeans.GlobalResourcesLifecycleListener\" />\n" +
"  <Listener className=\"org.apache.catalina.core.ThreadLocalLeakPreventionListener\" />\n" +
"\n" +
"  <GlobalNamingResources>\n" +
"    <Resource name=\"UserDatabase\" auth=\"Container\"\n" +
"              type=\"org.apache.catalina.UserDatabase\"\n" +
"              description=\"User database that can be updated and saved\"\n" +
"              factory=\"org.apache.catalina.users.MemoryUserDatabaseFactory\"\n" +
"              pathname=\"conf/tomcat-users.xml\" />\n" +
"  </GlobalNamingResources>\n" +
"\n" +
"  <Service name=\"Catalina\">\n" +
"\n" +
"    <Connector port=\"" + ports[0] + "\" protocol=\"HTTP/1.1\"\n" +
"               connectionTimeout=\"20000\"\n" +
"               redirectPort=\"8443\" />\n" +
"\n" +
"    <Connector port=\"" + ports[2] + "\" protocol=\"AJP/1.3\" redirectPort=\"8443\" />\n" +
"\n" +
"    <Engine name=\"Catalina\" defaultHost=\"localhost\">\n" +
"\n" +
"      <Realm className=\"org.apache.catalina.realm.LockOutRealm\">\n" +
"\n" +
"        <Realm className=\"org.apache.catalina.realm.UserDatabaseRealm\"\n" +
"               resourceName=\"UserDatabase\"/>\n" +
"      </Realm>\n" +
"\n" +
"      <Host name=\"localhost\"  appBase=\"webapps\"\n" +
"            unpackWARs=\"true\" autoDeploy=\"true\">\n" +
"\n" +
"        <Valve className=\"org.apache.catalina.valves.AccessLogValve\" directory=\"logs\"\n" +
"               prefix=\"localhost_access_log\" suffix=\".txt\"\n" +
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
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_JRSAAS_CONFIG_DB + "?user=" + USER_JRSAAS + "&password=" + USER_JRSAAS_PWD + "");
        Statement s = cn.createStatement();
        int Result = s.executeUpdate("INSERT INTO " + MYSQL_JRSAAS_CONFIG_DB + "." + MYSQL_TABLE_INSTANCE + " VALUES ("
                + "'" + jr_node_name + "'," //nome del nodo jackrabbit
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
               //CheckPortDb 
        /*
        per controllare che le porte siano libere, bisogna controllare
        oltre che siano libere nel momento del check,
        che non siano già state assegnate ad una istanza di tomcat (anche se è spenta e le porte libere)
        Quindi fare una tabella in mysql con nome istanza e porte usate
        Se la porta appartiene ad una istanza spenta, si fa continue)
                */       
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_JRSAAS_CONFIG_DB + "?user=" + USER_JRSAAS + "&password=" + USER_JRSAAS_PWD + "");
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
        File theDir = new File(jr_node_tomcat_scripts_path);
        // if the directory does not exist, create it
        if (!theDir.exists()) 
        {
            System.out.println("\r\ncreating directory: " + jr_node_tomcat_scripts_path);
            try
            {
                theDir.mkdir();
             } 
            catch(Exception e)
            {
                e.printStackTrace();
            }
         }
        
        CreateScriptStart();
        
        CreateScriptStop();
        
        CreateScriptRestart();
                
   }        
   
   static void CreateScriptStart() throws IOException, InterruptedException
   {       
        //dentro alla cartella scripts scrive gli script di start e stop per questa istanza di tomcat:
        //start_<instance_name>.sh
       BufferedWriter writer = null;
        try 
        {            
            File bp = new File(jr_node_tomcat_scripts_path + "/start_" + jr_node_name + ".sh");

            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("export CATALINA_HOME=" + CATALINA_HOME + "\n" +
"export CATALINA_BASE=" + jr_node_tomcat_instance_path + "\n" +
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
        
        //da il permesso di esecuzione allo script creato:
        String cmd = "sudo chmod u+x " + jr_node_tomcat_scripts_path + "/start_" + jr_node_name + ".sh";
        ForkProcess(cmd);
   }
   
   static void CreateScriptStop() throws IOException, InterruptedException
   {       
       //stop_<instance_name>.sh
        BufferedWriter writer = null;
        try 
        {            
            File bp = new File(jr_node_tomcat_scripts_path + "/stop_" + jr_node_name + ".sh");            
            
            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("export CATALINA_HOME=" + CATALINA_HOME + "\n" +
"export CATALINA_BASE=" + jr_node_tomcat_instance_path + "\n" +
"cd $CATALINA_HOME/bin\n" +
"./shutdown.sh\n"
                    + "echo Tomcat " + jr_node_name + " Stoppped");        
           
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
        
        String cmd = "sudo chmod u+x " + jr_node_tomcat_scripts_path + "/stop_" + jr_node_name + ".sh";
        ForkProcess(cmd);
   }   
   
   static void CreateScriptRestart() throws IOException, InterruptedException
   {
       //stop_<instance_name>.sh
        BufferedWriter writer = null;
        try 
        {            
            File bp = new File(jr_node_tomcat_scripts_path + "/restart_" + jr_node_name + ".sh");            
            
            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("sh " + jr_node_tomcat_scripts_path + "/stop_" + jr_node_name + ".sh\n"
                    + "sh " + jr_node_tomcat_scripts_path + "/start_" + jr_node_name + ".sh");        
           
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
        
        String cmd = "sudo chmod u+x " + jr_node_tomcat_scripts_path + "/restart_" + jr_node_name + ".sh";
        ForkProcess(cmd);
   }        
   
   /*
   static void CopyWarFile() throws IOException
   {
	File src = new File("/home/luca/Scaricati/jr2.war"); 
	File dst = new File(tomcat_webapp_jackrabbit_path + ".war"); 
       Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
       System.out.println("Copiato file jackrabbit-webapp.war da /home/luca/Scaricati/jr2.war a " + tomcat_webapp_jackrabbit_path + "\r\n");
   }
   */
   
   static void ExportWarFile() throws IOException
   {
        System.out.println("\r\nSto esportando jackrabbit.war dentro alla cartella webapps di tomcat...");       
        //stream all'interno del file jar, punta al file .war da esportare
       InputStream stream = Main.class.getResourceAsStream("/export/jackrabbit.war");
        if (stream == null) 
        {
            //send your exception or warning
        }
        OutputStream resStreamOut = null;
        int readBytes;
        byte[] buffer = new byte[4096];
        try 
        {
            resStreamOut = new FileOutputStream(new File(jr_node_tomcat_instance_webapp_jackrabbit_path + ".war"));
            while ((readBytes = stream.read(buffer)) > 0) 
            {
                resStreamOut.write(buffer, 0, readBytes);
            }
        } 
        catch (IOException e1) 
        {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            System.exit(1);
        }
        finally 
        {
            stream.close();
            resStreamOut.close();
        }       
   }
   
   static void Start_Instance() throws IOException, InterruptedException
   {
       String cmd = "sudo sh " + jr_node_tomcat_scripts_path + "/start_" + jr_node_name + ".sh";
       System.out.println("\r\nAvvio istanza di tomcat richiamando lo script di start: \r\n" + cmd);
       ForkProcess(cmd);
       
        //Aspetta che Tomcat sia partito, quando cioè nel log catalina.out c'è scritto Tomcat server startup in ... ms
        WaitFor_TomcatServerStartUp();       
   }
   
   static void Stop_Instance() throws IOException, InterruptedException
   {
       String cmd = "sudo sh " + jr_node_tomcat_scripts_path + "/stop_" + jr_node_name + ".sh";
       System.out.println("\r\nFermo istanza di tomcat richiamando lo script di stop: \r\n" + cmd);
       ForkProcess(cmd);
   }   

   static void CreateNewRepo() throws SQLException, IOException, InterruptedException, RepositoryException 
   {              
        //crea file bootstrap.properties dentro alla cartella del repository
        CreateBootstrapProperties();
        
        //crea file repository.xml dentro alla cartella del repository
        CreateRepositoryXml();
        
        //cambia web.xml dentro a WEB-INF della cartella del repository che è in tomcat/webapps
        //setta il percorso giusto al file bootstrap.properties
        EditWebXml();   
        
        //riavvia tomcat per creare il repository jackrabbit
        //(creazione cartelle e file + popolazione database mysql)
        //dopo che sono stati modificati i file di configurazione
        //(repository.xml, workspace.xml)
        System.out.println("\r\nRiavvio server tomcat per creare tutti i file"
                + "del nuovo repository e per farlo configurare per il suo primo utilizzo"
                + "con mysql");         
        Restart_Tomcat();        
        
        //Aspetta che la cartella e i file del repository vengano creati
        //nel path prefissato
        System.out.println("\r\nAspetto che il repository venga completamente creato, controllando l'esistenza di tutte le cartelle e i file");                 
        System.out.println("\r\nAspetto quindi che l'istanza di tomcat sia partita");      
                
        Stop_Instance();
        
        //WaitFor_LockDelete();
        
        //1) cambia password admin
        //2) disabilita anonymous     
        ChangeRepoUsers();     
        
        //avvia tomcat con le modifiche apportate agli utenti
        Start_Instance();
   }
   
   static void CreaMySqlPM() throws SQLException
   {
        System.out.println("\r\nCreo db mysql " + cluster_pm_name + " nel mysql server " + MYSQL_URL + "\r\n");       
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");
        Statement s = cn.createStatement();        
        int Result = s.executeUpdate("CREATE DATABASE " + cluster_pm_name + ";");
        
        System.out.println("\r\nCreo utente mysql " + db_user + " con permessi di lettura e scrittura solo per il db " + cluster_pm_name + "\r\n");       
        Result = s.executeUpdate("grant usage on *.* to " + db_user + "@localhost identified by '" + db_password + "';");
        Result = s.executeUpdate("grant all privileges on " + cluster_pm_name + ".* to " + db_user + "@localhost;");
        
        //creo tabella NODE, inserendo il primo nodo di jackrabbit:
        Result = s.executeUpdate("CREATE TABLE " + cluster_pm_name + ".NODE(\n" +
        "Id integer not null,\n" +
        "Nome varchar(50) not null,\n" +
        "primary key (Id)    \n" +
        ");");
        
        //inserisce primo record: id = 1 e nome del primo nodo jackrabbit
        Result = s.executeUpdate("INSERT INTO " + cluster_pm_name + ".NODE VALUES(1,'" + jr_node_name + "');");
        
         cn.close();
   }      
   
    static void CreateBootstrapProperties()
    {
        System.out.println("\r\nCreo file" + jr_node_repo_path + "/bootstrap.properties");
        BufferedWriter writer = null;
        try 
        {            
            
            //create bootstrap.properties file
            File bp = new File(jr_node_repo_path + "/bootstrap.properties");

            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("#bootstrap properties for the repository startup servlet.\n" +
"#Fri Aug 01 22:09:45 CEST 2014\n" +
"java.naming.factory.initial=org.apache.jackrabbit.core.jndi.provider.DummyInitialContextFactory\n" +
"repository.home=" + jr_node_repo_path + "\n" +
"rmi.enabled=true\n" +
"repository.config=" + jr_node_repo_path + "/repository.xml\n" +
"repository.name=jackrabbit.repository\n" +
"rmi.host=localhost\n" +
"java.naming.provider.url=http\\://www.apache.org/jackrabbit\n" +
"jndi.enabled=true\n" +
"rmi.port=0");
            
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
    
    static void CreateRepositoryXml()
    {
        System.out.println("\r\nCreo file " + jr_node_repo_path + "/repository.xml");
        BufferedWriter writer = null;
        try 
        {
            //create repository.xml file
            File bp = new File(jr_node_repo_path + "/repository.xml");
            
            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("<?xml version=\"1.0\"?>\n" +
"\n" +
"<!DOCTYPE Repository\n" +
"          PUBLIC \"-//The Apache Software Foundation//DTD Jackrabbit 2.0//EN\"\n" +
"          \"http://jackrabbit.apache.org/dtd/repository-2.0.dtd\">\n" +
"\n" +
"<Repository>\n" +
"    <!--\n" +
"        virtual file system where the repository stores global state\n" +
"        (e.g. registered namespaces, custom node types, etc.)\n" +
"    -->\n" +
"<FileSystem class=\"org.apache.jackrabbit.core.fs.db.DbFileSystem\">\n" +
"  <param name=\"driver\" value=\"com.mysql.jdbc.Driver\"/>\n" +
"  <param name=\"url\" value=\"jdbc:mysql://" + MYSQL_URL + "/" + cluster_pm_name + "\"/>\n" +
"  <param name=\"user\" value=\"" + db_user + "\"/>\n" +
"  <param name=\"password\" value=\"" + db_password + "\"/>\n" +
"  <param name=\"schema\" value=\"mysql\"/>\n" +
"  <param name=\"schemaObjectPrefix\" value=\"fs_global_state_\"/>\n" +
"</FileSystem>\n" +
"\n" +
"    <!--\n" +
"        data store configuration\n" +
"    -->\n" +
"<DataStore class=\"org.apache.jackrabbit.core.data.FileDataStore\">\n" +
"    <param name=\"path\" value=\"" + cluster_datastore_path + "\"/>\n" +
"    <param name=\"minRecordLength\" value=\"100\"/>\n" +
"</DataStore>\n" +
"\n" +
"    <!--\n" +
"        security configuration\n" +
"    -->\n" +
"    <Security appName=\"Jackrabbit\">\n" +
"        <!--\n" +
"            security manager:\n" +
"            class: FQN of class implementing the JackrabbitSecurityManager interface\n" +
"        -->\n" +
"        <SecurityManager class=\"org.apache.jackrabbit.core.DefaultSecurityManager\" workspaceName=\"security\">\n" +
"            <!--\n" +
"            workspace access:\n" +
"            class: FQN of class implementing the WorkspaceAccessManager interface\n" +
"            -->\n" +
"            <!-- <WorkspaceAccessManager class=\"...\"/> -->\n" +
"            <!-- <param name=\"config\" value=\"${rep.home}/security.xml\"/> -->\n" +
"        </SecurityManager>\n" +
"\n" +
"        <!--\n" +
"            access manager:\n" +
"            class: FQN of class implementing the AccessManager interface\n" +
"        -->\n" +
"        <AccessManager class=\"org.apache.jackrabbit.core.security.DefaultAccessManager\">\n" +
"            <!-- <param name=\"config\" value=\"${rep.home}/access.xml\"/> -->\n" +
"        </AccessManager>\n" +
"\n" +
"        <LoginModule class=\"org.apache.jackrabbit.core.security.authentication.DefaultLoginModule\">\n" +
"           <!-- \n" +
"              anonymous user name ('anonymous' is the default value)\n" +
"            -->\n" +
"           <param name=\"anonymousId\" value=\"anonymous\"/>\n" +
"           <!--\n" +
"              administrator user id (default value if param is missing is 'admin')\n" +
"            -->\n" +
"           <param name=\"adminId\" value=\"" + db_user + "\"/>\n" +
"        </LoginModule>\n" +
"    </Security>\n" +
"\n" +
"    <!--\n" +
"        location of workspaces root directory and name of default workspace\n" +
"    -->\n" +
"    <Workspaces rootPath=\"${rep.home}/workspaces\" defaultWorkspace=\"default\"/>\n" +
"    <!--\n" +
"        workspace configuration template:\n" +
"        used to create the initial workspace if there's no workspace yet\n" +
"    -->\n" +
"    <Workspace name=\"${wsp.name}\">\n" +
"        <!--\n" +
"            virtual file system of the workspace:\n" +
"            class: FQN of class implementing the FileSystem interface\n" +
"        -->\n" +
"<FileSystem class=\"org.apache.jackrabbit.core.fs.db.DbFileSystem\">\n" +
"  <param name=\"driver\" value=\"com.mysql.jdbc.Driver\"/>\n" +
"  <param name=\"url\" value=\"jdbc:mysql://" + MYSQL_URL + "/" + cluster_pm_name + "\"/>\n" +
"  <param name=\"user\" value=\"" + db_user + "\"/>\n" +
"  <param name=\"password\" value=\"" + db_password + "\"/>\n" +
"  <param name=\"schema\" value=\"mysql\"/>\n" +
"  <param name=\"schemaObjectPrefix\" value=\"fs_workspace_\"/>\n" +
"</FileSystem>\n" +
"\n" +
"        <!--\n" +
"            persistence manager of the workspace:\n" +
"            class: FQN of class implementing the PersistenceManager interface\n" +
"        -->\n" +
"<PersistenceManager class=\"org.apache.jackrabbit.core.persistence.pool.MySqlPersistenceManager\">\n" +
"    <param name=\"url\" value=\"jdbc:mysql://" + MYSQL_URL + "/" + cluster_pm_name + "\"/> <!-- use your database setup -->\n" +
"    <param name=\"user\" value=\"" + db_user + "\" />                           <!-- use your database user -->\n" +
"    <param name=\"password\" value=\"" + db_password + "\" />                       <!-- use your database user's password -->\n" +
"    <param name=\"schema\" value=\"mysql\"/>\n" +
"    <param name=\"schemaObjectPrefix\" value=\"pm_ws_${wsp.name}_\"/>\n" +
"</PersistenceManager>\n" +
"        <!--\n" +
"            Search index and the file system it uses.\n" +
"            class: FQN of class implementing the QueryHandler interface\n" +
"        -->\n" +
"        <SearchIndex class=\"org.apache.jackrabbit.core.query.lucene.SearchIndex\">\n" +
"            <param name=\"path\" value=\"${wsp.home}/index\"/>\n" +
"            <param name=\"supportHighlighting\" value=\"true\"/>\n" +
"        </SearchIndex>\n" +
"    </Workspace>\n" +
"\n" +
"    <!--\n" +
"        Configures the versioning\n" +
"    -->\n" +
"    <Versioning rootPath=\"${rep.home}/version\">\n" +
"        <!--\n" +
"            Configures the filesystem to use for versioning for the respective\n" +
"            persistence manager\n" +
"        -->\n" +
"<FileSystem class=\"org.apache.jackrabbit.core.fs.db.DbFileSystem\">\n" +
"  <param name=\"driver\" value=\"com.mysql.jdbc.Driver\"/>\n" +
"  <param name=\"url\" value=\"jdbc:mysql://" + MYSQL_URL + "/" + cluster_pm_name + "\"/>\n" +
"  <param name=\"user\" value=\"" + db_user + "\"/>\n" +
"  <param name=\"password\" value=\"" + db_password + "\"/>\n" +
"  <param name=\"schema\" value=\"mysql\"/>\n" +
"  <param name=\"schemaObjectPrefix\" value=\"fs_version_\"/>\n" +
"</FileSystem>\n" +
"\n" +
"\n" +
"        <!--\n" +
"            Configures the persistence manager to be used for persisting version state.\n" +
"            Please note that the current versioning implementation is based on\n" +
"            a 'normal' persistence manager, but this could change in future\n" +
"            implementations.\n" +
"        -->\n" +
"<PersistenceManager class=\"org.apache.jackrabbit.core.persistence.pool.MySqlPersistenceManager\">\n" +
"    <param name=\"url\" value=\"jdbc:mysql://" + MYSQL_URL + "/" + cluster_pm_name + "\"/> <!-- use your database setup -->\n" +
"    <param name=\"user\" value=\"" + db_user + "\" />                           <!-- use your database user -->\n" +
"    <param name=\"password\" value=\"" + db_password + "\" />                       <!-- use your database user's password -->\n" +
"    <param name=\"schema\" value=\"mysql\"/>\n" +
"    <param name=\"schemaObjectPrefix\" value=\"pm_vs_\"/>\n" +
"</PersistenceManager>\n" +
"    </Versioning>\n" +
"\n" +
"    <!--\n" +
"        Search index for content that is shared repository wide\n" +
"        (/jcr:system tree, contains mainly versions)\n" +
"    -->\n" +
"    <SearchIndex class=\"org.apache.jackrabbit.core.query.lucene.SearchIndex\">\n" +
"        <param name=\"path\" value=\"${rep.home}/repository/index\"/>\n" +
"        <param name=\"supportHighlighting\" value=\"true\"/>\n" +
"    </SearchIndex>\n" +
"\n" +
"    <!--\n" +
"        Run with a cluster journal\n" +
"    -->\n" +
"<Cluster id=\"" + jr_node_name + "\" syncDelay=\"" + SYNC_DELAY + "\">\n" +
"		<Journal class=\"org.apache.jackrabbit.core.journal.DatabaseJournal\">\n" +
"			<param name=\"revision\" value=\"${rep.home}/revision\"/>\n" +
"			<param name=\"driver\" value=\"com.mysql.jdbc.Driver\"/>\n" +
"    <param name=\"url\" value=\"jdbc:mysql://" + MYSQL_URL + "/" + cluster_pm_name + "\"/> <!-- use your database setup -->\n" +
"    <param name=\"user\" value=\"" + db_user + "\" />                           <!-- use your database user -->\n" +
"    <param name=\"password\" value=\"" + db_password + "\" />                       <!-- use your database user's password -->\n" +
"			<param name=\"schema\" value=\"mysql\"/>\n" +
"			<param name=\"schemaObjectPrefix\" value=\"JOURNAL_\"/>\n" +
"		</Journal>\n" +
"</Cluster>" +
//"<RepositoryLockMechanism class=\"org.apache.jackrabbit.core.util.CooperativeFileLock\"/>\n" +
"</Repository>");
            
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
   
    static void EditWebXml()
    {
        System.out.println("\r\nCancello file web.xml nella cartella deployata da tomcat");        
        //cancello file web.xml creato da tomcat
        File toCanc = new File(jr_node_tomcat_instance_webapp_jackrabbit_path + "/WEB-INF/web.xml");
        toCanc.delete();
        
        System.out.println("\r\nCreo nuovo file web.xml nella cartella deployata da tomcat");        
        BufferedWriter writer = null;
        try 
        {
            //create web.xml file
            System.out.println("scrivendo file: " + jr_node_tomcat_instance_webapp_jackrabbit_path + "/WEB-INF/web.xml");
            File bp = new File(jr_node_tomcat_instance_webapp_jackrabbit_path + "/WEB-INF/web.xml");
            
            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
"\n" +
"<!DOCTYPE web-app PUBLIC \"-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN\" \"http://java.sun.com/dtd/web-app_2_3.dtd\">\n" +
"<web-app>\n" +
"    <display-name>Jackrabbit JCR Server</display-name>\n" +
"\n" +
"    <listener>\n" +
"        <!-- Releases all Derby resources when the webapp is undeployed.  -->\n" +
"        <!-- See https://issues.apache.org/jira/browse/JCR-1301           -->\n" +
"        <listener-class>\n" +
"            org.apache.jackrabbit.j2ee.DerbyShutdown\n" +
"        </listener-class>\n" +
"    </listener>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- R E P O S I T O R Y   S T A R T U P  S E R V L E T                     -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet>\n" +
"        <servlet-name>RepositoryStartup</servlet-name>\n" +
"        <description>\n" +
"            Repository servlet that starts the repository and registers it to JNDI ans RMI.\n" +
"            If you already have the repository registered in this appservers JNDI context,\n" +
"            or if its accessible via RMI, you do not need to use this servlet.\n" +
"        </description>\n" +
"        <servlet-class>org.apache.jackrabbit.j2ee.RepositoryStartupServlet</servlet-class>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>bootstrap-config</param-name>\n" +
"            <param-value>" + jr_node_repo_path + "/bootstrap.properties</param-value>\n" +
"            <description>\n" +
"                Property file that hold the same initialization properties than\n" +
"                the init-params below. If a parameter is specified in both\n" +
"                places the one in the bootstrap-config wins.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <load-on-startup>2</load-on-startup>\n" +
"    </servlet>\n" +
"\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- R E P O S I T O R Y   S E R V L E T                                    -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet>\n" +
"        <servlet-name>Repository</servlet-name>\n" +
"        <description>\n" +
"            This servlet provides other servlets and jsps a common way to access\n" +
"            the repository. The repository can be accessed via JNDI, RMI or Webdav.\n" +
"        </description>\n" +
"        <servlet-class>org.apache.jackrabbit.j2ee.RepositoryAccessServlet</servlet-class>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>bootstrap-config</param-name>\n" +
"            <param-value>" + jr_node_repo_path + "/bootstrap.properties</param-value>\n" +
"            <description>\n" +
"                Property file that hold the same initialization properties than\n" +
"                the init-params below. If a parameter is specified in both\n" +
"                places the one in the bootstrap-config wins.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <load-on-startup>3</load-on-startup>\n" +
"    </servlet>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- W E B D A V  S E R V L E T                                              -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet>\n" +
"        <servlet-name>Webdav</servlet-name>\n" +
"        <description>\n" +
"            The webdav servlet that connects HTTP request to the repository.\n" +
"        </description>\n" +
"        <servlet-class>org.apache.jackrabbit.j2ee.SimpleWebdavServlet</servlet-class>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>resource-path-prefix</param-name>\n" +
"            <param-value>/repository</param-value>\n" +
"            <description>\n" +
"                defines the prefix for spooling resources out of the repository.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>resource-config</param-name>\n" +
"            <param-value>/WEB-INF/config.xml</param-value>\n" +
"            <description>\n" +
"                Defines various dav-resource configuration parameters.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <load-on-startup>4</load-on-startup>\n" +
"    </servlet>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- J C R  R E M O T I N G  S E R V L E T                                  -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet>\n" +
"        <servlet-name>JCRWebdavServer</servlet-name>\n" +
"        <description>\n" +
"            The servlet used to remote JCR calls over HTTP.\n" +
"        </description>\n" +
"        <servlet-class>org.apache.jackrabbit.j2ee.JcrRemotingServlet</servlet-class>\n" +
"        <init-param>\n" +
"            <param-name>missing-auth-mapping</param-name>\n" +
"            <param-value></param-value>\n" +
"            <description>\n" +
"                Defines how a missing authorization header should be handled.\n" +
"                 1) If this init-param is missing, a 401 response is generated.\n" +
"                    This is suitable for clients (eg. webdav clients) for which\n" +
"                    sending a proper authorization header is not possible if the\n" +
"                    server never sent a 401.\n" +
"                 2) If this init-param is present with an empty value,\n" +
"                    null-credentials are returned, thus forcing an null login\n" +
"                    on the repository.\n" +
"                 3) If this init-param is present with the value 'guestcredentials'\n" +
"                    java.jcr.GuestCredentials are used to login to the repository.\n" +
"                 4) If this init-param has a 'user:password' value, the respective\n" +
"                    simple credentials are generated.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>resource-path-prefix</param-name>\n" +
"            <param-value>/server</param-value>\n" +
"            <description>\n" +
"                defines the prefix for spooling resources out of the repository.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>batchread-config</param-name>\n" +
"            <param-value>/WEB-INF/batchread.properties</param-value>\n" +
"            <description>JcrRemotingServlet: Optional mapping from node type names to default depth.</description>\n" +
"        </init-param>\n" +
"        \n" +
"      <load-on-startup>5</load-on-startup>\n" +
"    </servlet>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- R M I   B I N D I N G   S E R V L E T                                  -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet>\n" +
"      <servlet-name>RMI</servlet-name>\n" +
"      <servlet-class>org.apache.jackrabbit.servlet.remote.RemoteBindingServlet</servlet-class>\n" +
"    </servlet>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- S E R V L E T   M A P P I N G                                          -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet-mapping>\n" +
"        <servlet-name>RepositoryStartup</servlet-name>\n" +
"        <url-pattern>/admin/*</url-pattern>\n" +
"    </servlet-mapping>\n" +
"    <servlet-mapping>\n" +
"        <servlet-name>Webdav</servlet-name>\n" +
"        <url-pattern>/repository/*</url-pattern>\n" +
"    </servlet-mapping>\n" +
"    <servlet-mapping>\n" +
"        <servlet-name>JCRWebdavServer</servlet-name>\n" +
"        <url-pattern>/server/*</url-pattern>\n" +
"    </servlet-mapping>\n" +
"    <servlet-mapping>\n" +
"        <servlet-name>RMI</servlet-name>\n" +
"        <url-pattern>/rmi</url-pattern>\n" +
"    </servlet-mapping>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- W E L C O M E   F I L E S                                              -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <welcome-file-list>\n" +
"      <welcome-file>index.jsp</welcome-file>\n" +
"    </welcome-file-list>\n" +
"\n" +
"    <error-page>\n" +
"        <exception-type>org.apache.jackrabbit.j2ee.JcrApiNotFoundException</exception-type>\n" +
"        <location>/error/classpath.jsp</location>\n" +
"    </error-page>\n" +
"    <error-page>\n" +
"        <exception-type>javax.jcr.RepositoryException</exception-type>\n" +
"        <location>/error/repository.jsp</location>\n" +
"    </error-page>\n" +
"    \n" +
"</web-app>");
            
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
    
    static void ChangeRepoUsers() throws RepositoryException, IOException, InterruptedException
    {                
                System.out.println("\r\nGestione utenti repository:"
                + "Cancello utente anonymous"
                + "Cambio password all'admin, di default la sua password è = alla username"
                + "che è definita in repository.xml");    
        //serve per loggarsi / creare ad un repository jackrabbit
                //avvia l'istanza, non di deve essere nessun altra istanza attiva
                //se il .lock è presente genera un eccezione
         RepositoryConfig config = RepositoryConfig.install(new File(jr_node_repo_path));
         //NB nella cartella ci devono essere i permessi di scrittura per creare il file .lock
         RepositoryImpl repository = RepositoryImpl.create(config);                  
         
        Session session; 
        
        //controllo se l'utente esiste        
        try
        {
            session = repository.login(new SimpleCredentials(db_user,db_user.toCharArray())); 
            //per default, all'inizio jackrabbit crea admin con stessa username e password
                    //definito in web.xml o repository.xml (uno dei 2, non mi ricordo)                         
        }    
        catch (Exception e)
        {
            System.out.println("Errore Login Credenziali");
            e.printStackTrace();
            return;
        }        
                                     
        UserManager userManager = ((JackrabbitSession) session).getUserManager();                

        //NB il server deve essere riavviato per rendere effettiva ogni singola operazione fatta qui dentro                 
        
        //disabilita utente anonymous
        Authorizable a2 = userManager.getAuthorizable("anonymous");
         ((User) a2).disable("prevent anonymous login"); 
         
         //cambia password all'utente admin
        Authorizable authorizable = userManager.getAuthorizable(db_user);
        ((User) authorizable).changePassword(db_password);

        //System.out.println("Creo nuovo utente \r\n");
        
        //crea utente stessa username e password di mysql
        //final User repoUser = userManager.createUser(user, userpwd);
                
        session.save();
        session.logout();                     

        repository.shutdown();
        
    }    
    
    static void Restart_Tomcat() throws IOException, InterruptedException
    {
        Stop_Instance();
        Start_Instance();
    }    
    
    static void WaitFor_TomcatServerStartUp() throws IOException
    {
        System.out.println("Aspetto che l'istanza di tomcat finisca di avviarsi");
        System.out.println("Quindi finisce di deployare la webapp di jackrabbit");
        System.out.println("LOG: " + jr_node_tomcat_instance_log_path);
        while(!isTomcatUp(connector_port))
        {
            //aspetto
        }
        //pulisco log catalina.out
        CleanLog(jr_node_tomcat_instance_log_path);
    }        
    
    static int ForkProcess(String cmd) throws IOException, InterruptedException
    {
        //esporta datastore
        //dentro a /srv/backup/<mysqldb_cluster_name>
        
        Process p = Runtime.getRuntime().exec(new String[] { "/bin/bash", "-c", cmd });
        int status = p.waitFor();
        
        //DEBUG
        /*
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
        */
        
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
        
        return status;
    }
    
static boolean isTomcatUp(int port) throws IOException 
{
    /*
    BufferedReader br = null;
        String lastLine = "";
    
    try 
    {

        String sCurrentLine;
        
        br = new BufferedReader(new FileReader(jr_node_tomcat_instance_log_path));
        //arrivo a leggere l'ultima riga
        while ((sCurrentLine = br.readLine()) != null) 
        {
            lastLine = sCurrentLine;
        }

    } 
    catch (IOException e) 
    {
        e.printStackTrace();
    }
    
    br.close();
    */
    
    File log = new File(jr_node_tomcat_instance_log_path);
    String lastLine = LastLine(log);
    
    
    //System.out.println(lastLine);
    
    String toCheck = "org.apache.catalina.startup.Catalina.start Server startup in";
    //se l'ultima riga è uguale allo startup del server...   
    if(lastLine.toLowerCase().contains(toCheck.toLowerCase()))
    {
        return true;
    }
    
    return false;

}

static String LastLine(File file) 
{
    RandomAccessFile fileHandler = null;
    try 
    {
        fileHandler = new RandomAccessFile( file, "r" );
        long fileLength = fileHandler.length() - 1;
        StringBuilder sb = new StringBuilder();

        for(long filePointer = fileLength; filePointer != -1; filePointer--)
        {
            fileHandler.seek( filePointer );
            int readByte = fileHandler.readByte();

            if( readByte == 0xA ) 
            {
                if( filePointer == fileLength ) 
                {
                    continue;
                }
                break;

            } 
            else if( readByte == 0xD ) 
            {
                if( filePointer == fileLength - 1 ) 
                {
                    continue;
                }
                break;
            }

            sb.append( ( char ) readByte );
        }

        String lastLine = sb.reverse().toString();
        return lastLine;
    } 
    catch( java.io.FileNotFoundException e ) 
    {
        e.printStackTrace();
        return null;
    } 
    catch( java.io.IOException e ) 
    {
        e.printStackTrace();
        return null;
    } 
    finally 
    {
        if (fileHandler != null )
            try 
            {
                fileHandler.close();
            } 
            catch (IOException e) 
            {
                /* ignore */
            }
    }
}

static void CleanLog(String tomcat_log)
{
    File toCanc = new File(tomcat_log);
    toCanc.delete();
}        
    
}

