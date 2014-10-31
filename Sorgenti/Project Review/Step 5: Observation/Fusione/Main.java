/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.cloneasync;

import static com.luca.cloneasync.Constants.*;

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

import org.apache.jackrabbit.commons.JcrUtils;
import javax.jcr.observation.*;

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
    
    //Variabili che definiscono il cluster da copiare
    static String old_cluster_name = "";
    static String old_cluster_root_path = "";
    //nome mysqldb persistence manager
    static String old_cluster_pm_name = "";
    static String old_cluster_backup_path = "";
    static String old_cluster_datastore_path = "";        
    static String old_cluster_mysql_url = "";
    
    //Variabili che definiscono il nuovo cluster copiato
    static String new_cluster_name = "";
    static String new_cluster_root_path = "";
    //nome mysqldb persistence manager
    static String new_cluster_pm_name = "";
    static String new_cluster_backup_path = "";
    static String new_cluster_datastore_path = "";
    static String new_cluster_mysql_url = "";
    
//primo nodo jackrabbit che appartiene al nuovo cluster copiato
    static String jr_node_host = "";
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
        
        static List<String> added_nodes = new ArrayList<String>();
    static List<String> incomplete_nodes = new ArrayList<String>();
    
     public static void main(String[] args) throws Exception 
    {
        //TODO: da controllare numeri di porta corretti e instance name (che non esista già e != da scripts, nel db mysql)
        CheckArguments(args);
        
        Read_Variables();
        
        //crea il nuovo cluster, sarà il clone del vecchio cluster da copiare
        Create_New_Cluster();
        
       //copia datastore       
       //esporta pm dal vecchio, importa pm nel nuovo              
       //avvia cluster clone nuovo e aspetta che crei tutto il repository
        Clone_Cluster();
              
        //Inizio Creazione Repository (Cartelle Repository Workspaces e File repository.xml e bootstrap.properties)
        StartNewCluster();
        
        Session s_old = Get_Session(old_cluster_name);
        Session s_new = Get_Session(new_cluster_name);
        long clone_date = Get_Last_Date(new_cluster_name);
        long original_date = Get_Last_Date(old_cluster_name);
        
        if(clone_date != original_date)
        {
            //ritorna la data dell'ultimo evento nel journal da sincronizzare
            Sync_Journal(original_date, s_old, s_new);
        }
        
        //pronto per essere sincronizzato con gli eventi asincroni
        //...
    ObservationManager omgr = s_old.getWorkspace().getObservationManager();

    // ----- ATTACH SOME EVENT LISTENER -------------------------------

    MyListener syncronize = new MyListener();
    syncronize.s_old = s_old;
    syncronize.s_new = s_new;
    
    omgr.addEventListener(
    syncronize,
    Event.NODE_ADDED | Event.PROPERTY_ADDED | Event.NODE_REMOVED | Event.NODE_MOVED | Event.PROPERTY_REMOVED | Event.PROPERTY_CHANGED | Event.PERSIST, // Listen to node additions
    //Event.NODE_ADDED, // Listen to node additions
    "/", // On root node...
    true, // ...and below
    null, // No filter on UUID
    null, // No filter on type name
    //false // Listen to local events as well
    true //solo per una sessione esterna? I nodi modificati da questa sessione non si vedono
    );

    while(true)
    {
        System.out.println("In attesa di eventi\r\n");
        Thread.sleep(5000);
    }            
}
    
   static void CheckArguments(String[] args)
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //nome della nuova istanza di tomcat
            if(args[i].equals("-oldname"))
            {
                old_cluster_name = args[++i];
                old_cluster_root_path = ROOT_CLUSTER_PATH + "/" + old_cluster_name;
                old_cluster_backup_path = old_cluster_root_path + "/backup";
                old_cluster_datastore_path = old_cluster_root_path + "/datastore";                
            }
            if(args[i].equals("-newname"))
            {
                new_cluster_name = args[++i];
                new_cluster_pm_name = MYSQL_PM_PREFIX + new_cluster_name;
                new_cluster_root_path = ROOT_CLUSTER_PATH + "/" + new_cluster_name;
                new_cluster_backup_path = new_cluster_root_path + "/backup";
                new_cluster_datastore_path = new_cluster_root_path + "/datastore"; 
                
                jr_node_name = new_cluster_name + "_1";
                jr_node_root_path = new_cluster_root_path + "/" + jr_node_name;
                jr_node_tomcat_path = jr_node_root_path + "/tomcat";
                jr_node_repo_path = jr_node_root_path + "/repo";
                jr_node_tomcat_instance_path = jr_node_tomcat_path + "/instance";
                jr_node_tomcat_scripts_path = jr_node_tomcat_path + "/scripts";
                jr_node_tomcat_instance_log_path = jr_node_tomcat_instance_path + "/logs/catalina.out";
                jr_node_tomcat_instance_webapp_path = jr_node_tomcat_instance_path + "/webapps";
                jr_node_tomcat_instance_webapp_jackrabbit_path = jr_node_tomcat_instance_webapp_path + "/" + JACKRABBIT_WEBAPP_DIRNAME;
                
            }            
            if(args[i].equals("-mysql"))
            {            
                new_cluster_mysql_url = args[++i];
            }            
            if(args[i].equals("-host"))
            {            
                jr_node_host = args[++i];
            }            
            if(args[i].equals("--help"))
            {
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-oldname <cluster name to copy>,\r\n"
                        + "-newname <cluster name cloned>,\r\n"
                        + "-host <host nuovo nodo cluster url>, es. localhost, www.node1.com\r\n"
                        + "-mysql <mysql url:porta>, es localhost:3306, www.abc.com:3306\r\n");                
                System.exit(0);
            }              
        }
    }

static void Create_New_Cluster() throws IOException, InterruptedException, SQLException
{
            //aggiunge alla tabella JRSAAS_CONFIG.CLUSTER le info del nuovo cluster che verrà creato
        Insert_MySql_Row_Cluster();
    
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
        
        //richiama lo script di start per deployare il war di jackrabbit nel nuovo cluster clone
        Start_Instance();        
        
        //per adesso mi interessava che deployasse il .war di jackrabbit, ferma cluster clone
        Stop_Instance();
        
       //crea persistence manager (pm) in mysql
       //crea database mysql nuovo per questa istanza di tomcat e repository jackrabbit
       //crea utente con permessi di lettura e scrittura solo per questo database
       CreaMySqlPM();      
    
}

   static void Read_Variables() throws SQLException
   {       
       String query = "SELECT * FROM " + MYSQL_JRSAAS_CONFIG_DB + "." + MYSQL_TABLE_CLUSTER + " WHERE nome = '" + old_cluster_name + "';";
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_JRSAAS_CONFIG_DB + "?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");
        Statement s = cn.createStatement();
        
         //definisce nuovo Id e Nome per il nuovo nodo jackrabbit
         ResultSet rs = s.executeQuery(query);         
         rs.next();

        db_user = rs.getString("user");
        db_password = rs.getString("userpwd");
        old_cluster_pm_name = rs.getString("db_name");
        old_cluster_mysql_url = rs.getString("mysql_url");
       
        cn.close();
        
   }        

static void Insert_MySql_Row_Cluster() throws SQLException
{
       //inserisce nome istanza nel database JRSAAS mysql e le 3 porte configurate        
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_JRSAAS_CONFIG_DB + "?user=" + USER_JRSAAS + "&password=" + USER_JRSAAS_PWD + "");
        Statement s = cn.createStatement();
        String cmd = "INSERT INTO " + MYSQL_JRSAAS_CONFIG_DB + "." + MYSQL_TABLE_CLUSTER + " VALUES (" 
                + "'" + new_cluster_name + "', "
                + "'" + db_user + "', "
                + "'" + db_password + "', "
                + "'" + new_cluster_mysql_url + "', "
                + "'" + new_cluster_pm_name + "');";
        System.out.println("Aggiungo riga a CLUSTER:\r\n\r\n" + cmd);
        int Result = s.executeUpdate(cmd);
        cn.close();    
}        


//come per l'hotbackup, blocca il cluster da copiare
static void Clone_Cluster() throws SQLException, IOException, InterruptedException
{
        //inizio sessione Mysql nel vecchio cluster da copiare
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + old_cluster_mysql_url + "/" + old_cluster_pm_name + "?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");

        LockTables(cn); //i lock rimangono attivi solo all'interno di questa sessione
        
        ClonePersistenceManager();        
        
        CloneDatastore();          
        
        UnLockTables(cn);
        
        //fine sessione Mysql, di default tutti i lock vengono cancellati
        cn.close();    
}        

    
    static void LockTables(Connection cn) throws SQLException
    {
        
        System.out.println("SHOW TABLES FROM " + old_cluster_pm_name + ";");
        Statement s = cn.createStatement();
        ResultSet rs = s.executeQuery("SHOW TABLES FROM " + old_cluster_pm_name + ";");
        
        //per ogni tabella del database scelto fa il lock
        String cmd = "LOCK TABLES ";
        while(rs.next())
        {
            String table = rs.getString(1);
            //System.out.println("LOCK TABLES " + db_name + "." + table + " READ;");
            cmd += old_cluster_pm_name + "." + table + " READ, ";
        } 

        //elimino ultimi 2 caratteri, lo spazio finale e la virgola
        cmd = cmd.substring(0, cmd.length()-2);
        //fine query mysql
        cmd += ";";
        
        System.out.println(cmd);
        Statement lock = cn.createStatement();
        lock.executeUpdate(cmd);
        
        
/*
            Statement lock = cn.createStatement();
            lock.executeUpdate("LOCK TABLES JRSAAS_CONFIG.INSTANCE READ;");
             */   
    }
    
    static void UnLockTables(Connection cn) throws SQLException
    {
        System.out.println("UNLOCK TABLES;");
        Statement s = cn.createStatement();
        s.executeUpdate("UNLOCK TABLES;");
    }        

static void ClonePersistenceManager() throws IOException, InterruptedException, SQLException
{
        //esporta persistence manager (database mysql)
        //dentro alla cartella di backup del cluster vecchio
    //es: /srv/cluster/c1/backup
        String cmd = "sudo mysqldump -u " + ADMIN_JRSAAS + " -p" + ADMIN_JRSAAS_PWD + " " + old_cluster_pm_name + " > " + old_cluster_backup_path + "/pm.sql";
        System.out.println("\r\nEsporto database mysql:\r\n" + cmd);
        int status = ForkProcess(cmd);
        //se status != 0 ERRORE
        
        //importa persistence manager nel nuovo cluster
       cmd = "sudo mysql -u " + ADMIN_JRSAAS + " -p" + ADMIN_JRSAAS_PWD + " " + new_cluster_pm_name + " < " + old_cluster_backup_path + "/pm.sql";
       System.out.println("\r\nRestore Persistence Manager: " + cmd);
       status =  ForkProcess(cmd);
       
       //prende l'ultima data presente nel journal del cluster vecchio:
//       long last_date = Get_LastDate(old_cluster_name);
       
        //aggiunge alla tabella SYNC l'ultima data alla quale è sincronizzato con il journal
       /*
       Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + new_cluster_pm_name + "?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");
       Statement s = cn.createStatement();
       int Result = s.executeUpdate("INSERT INTO " + new_cluster_pm_name + ".SYNC VALUES('" + old_cluster_name + "'," + last_date + ");");
      
       cn.close();
      */  
}        

static void CloneDatastore() throws IOException, InterruptedException
{
        //esporta datastore
        //dentro a /sv/cluster/c1/datastore (es)        
        String cmd = "sudo cp -r " + old_cluster_datastore_path + "/* " + new_cluster_datastore_path;
        System.out.println("\r\n Clono il datastore:\r\n" + cmd);                
        int status = ForkProcess(cmd);
        //se status != 0 ERRORE    
}                

   static void CreateClusterFolders() throws IOException, InterruptedException
   {
       //creo cartella /srv/cluster/cluster_name
        String cmd = "sudo mkdir " + new_cluster_root_path;
        System.out.println("\r\nCreo la cartella root del cluster:\r\n" + cmd);
        ForkProcess(cmd);
        
        //creo cartella /srv/cluster/cluster_name/backup
        cmd = "sudo mkdir " + new_cluster_backup_path;
        System.out.println("\r\nCreo la cartella backup del cluster:\r\n" + cmd);
        ForkProcess(cmd);
        
        //creo cartella /srv/cluster/cluster_name/datastore
        cmd = "sudo mkdir " + new_cluster_datastore_path;
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
       Insert_MySql_Row_Cluster_Node(ports);
       
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
   
    public static void Insert_MySql_Row_Cluster_Node(int[] ports) throws SQLException
   {
       //inserisce nome istanza nel database JRSAAS mysql e le 3 porte configurate
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_JRSAAS_CONFIG_DB + "?user=" + USER_JRSAAS + "&password=" + USER_JRSAAS_PWD + "");        
        Statement s = cn.createStatement();
        String cmd = "INSERT INTO " + MYSQL_JRSAAS_CONFIG_DB + "." + MYSQL_TABLE_CLUSTER_NODE + " VALUES ("
                + "'" + jr_node_name + "'," //nome del nodo jackrabbit
                + "'" + new_cluster_name + "'," //nome del cluster, foreign key CLUSTER.nome
                + "'" + jr_node_host + "'," //host del primo nodo jackrabbit del cluster
                + ports[0] + ", "   //connector port
                + ports[1] + ", "  //shutdown port
                + ports[2] + ", " //ajp port
                + "1);"; //node_id, è il primo nodo
        System.out.println("Aggiungo riga a CLUSTER_NODE: \r\n\r\n" + cmd);
        int Result = s.executeUpdate(cmd); 
        cn.close();
        System.out.println("1 riga aggiunta alla tabella " + MYSQL_TABLE_CLUSTER_NODE + " \r\n");       
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
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + MYSQL_TABLE_CLUSTER_NODE + " WHERE " + port_column_name + " = " + port + " AND host = '" + jr_node_host + "'");
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

   static void StartNewCluster() throws SQLException, IOException, InterruptedException, RepositoryException 
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
       
        //crea il repository
        Start_Instance();
        
        //Aspetta che la cartella e i file del repository vengano creati
        //nel path prefissato
        System.out.println("\r\nAspetto che il repository venga completamente creato, controllando l'esistenza di tutte le cartelle e i file");                 
        System.out.println("\r\nAspetto quindi che l'istanza di tomcat sia partita");      
         
        /*//NON DOVREBBE ESSERCI BISOGNO DI CAMBIARE UTENTE
        Stop_Instance();
        
        //WaitFor_LockDelete();
        
        //1) cambia password admin
        //2) disabilita anonymous     
        ChangeRepoUsers();     
        
        //avvia tomcat con le modifiche apportate agli utenti
        Start_Instance();
        */
   }
   
   static void CreaMySqlPM() throws SQLException
   {
        System.out.println("\r\nCreo db mysql " + new_cluster_pm_name + " nel mysql server " + MYSQL_URL + "\r\n");       
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + new_cluster_mysql_url + "/?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");
        Statement s = cn.createStatement();        
        int Result = s.executeUpdate("CREATE DATABASE " + new_cluster_pm_name + ";");
        
        System.out.println("\r\nCreo utente mysql " + db_user + " con permessi di lettura e scrittura solo per il db " + new_cluster_pm_name + "\r\n");       
        Result = s.executeUpdate("grant usage on *.* to " + db_user + "@localhost identified by '" + db_password + "';");
        Result = s.executeUpdate("grant all privileges on " + new_cluster_pm_name + ".* to " + db_user + "@localhost;");
        
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
"  <param name=\"url\" value=\"jdbc:mysql://" + new_cluster_mysql_url + "/" + new_cluster_pm_name + "\"/>\n" +
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
"    <param name=\"path\" value=\"" + new_cluster_datastore_path + "\"/>\n" +
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
"  <param name=\"url\" value=\"jdbc:mysql://" + new_cluster_mysql_url + "/" + new_cluster_pm_name + "\"/>\n" +
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
"    <param name=\"url\" value=\"jdbc:mysql://" + new_cluster_mysql_url + "/" + new_cluster_pm_name + "\"/> <!-- use your database setup -->\n" +
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
"  <param name=\"url\" value=\"jdbc:mysql://" + new_cluster_mysql_url + "/" + new_cluster_pm_name + "\"/>\n" +
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
"    <param name=\"url\" value=\"jdbc:mysql://" + new_cluster_mysql_url + "/" + new_cluster_pm_name + "\"/> <!-- use your database setup -->\n" +
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
"    <param name=\"url\" value=\"jdbc:mysql://" + new_cluster_mysql_url + "/" + new_cluster_pm_name + "\"/> <!-- use your database setup -->\n" +
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

static void CleanLog(String tomcat_log) throws IOException
{
    File toClean= new File(tomcat_log);
    //sovrascrive file cancellando tutti i caratteri
    FileWriter f2 = new FileWriter(toClean, false);
    f2.write("");
    f2.close();    
}        

//prende l'ultima data del cluster passato in input
//l'utente è uguale, sia per il clone per il cluster originale
static long Get_Last_Date(String cluster_name) throws RepositoryException, SQLException
{
    
    List<String> active_nodes_url = Get_Active_Nodes_Url(cluster_name);
    //si collega al primo nodo per leggere la data dell'ultimo evento scritto nel journal
            Repository repo = JcrUtils.getRepository(active_nodes_url.get(0));
            Credentials sc = new SimpleCredentials(db_user,db_password.toCharArray());
            Session s = repo.login(sc,"default");

   int event_filter = Event.NODE_ADDED | Event.PROPERTY_ADDED | Event.NODE_REMOVED | Event.NODE_MOVED | Event.PROPERTY_REMOVED | Event.PROPERTY_CHANGED | Event.PERSIST;            
    
    ObservationManager omgr = s.getWorkspace().getObservationManager();           

        // ----- READ THE EVENT JOURNAL -----------------------------------
        EventJournal ej = omgr.getEventJournal(event_filter, "/", true, null, null);
        
        Event e = null;
        
        //legge ultimo evento, quindi ultima data
        while(ej.hasNext())
        {
            e = ej.nextEvent();
        }    
                        
        long date = e.getDate();        
        return date;
}        

static Session Get_Session(String cluster_name) throws SQLException, RepositoryException
{
    List<String> active_nodes_url = Get_Active_Nodes_Url(cluster_name);
                Repository repo = JcrUtils.getRepository(active_nodes_url.get(0));
            Credentials sc = new SimpleCredentials(db_user,db_password.toCharArray());
            Session s = repo.login(sc,"default");
            return s;
}       
   
   static List<String> Get_Nodes_Url(String cluster_name) throws SQLException
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
   
static List<String> Get_Active_Nodes_Url(String cluster_name) throws SQLException
{
    List<String> url = Get_Nodes_Url(cluster_name);
    List<String> active_nodes_url = new ArrayList<String>();
//per ogni url prova a creare una sessione, se non genera eccezioni vuol dire che il nodo è attivo
    for(int i = 0; i < url.size(); i++)
    {
        try
        {
            Repository repo = JcrUtils.getRepository(url.get(i));
            Credentials sc = new SimpleCredentials(db_user,db_password.toCharArray());
            Session s = repo.login(sc,"default");
            active_nodes_url.add(url.get(i));
        }
        catch(RepositoryException re)
        {
            
        }            
    }    
    return active_nodes_url;
}        

static EventJournal Read_Journal(Session s_old, int event_filter) throws RepositoryException
{
            ObservationManager omgr = s_old.getWorkspace().getObservationManager();           
            
            // ----- READ THE EVENT JOURNAL -----------------------------------
            return omgr.getEventJournal(event_filter, "/", true, null, null);

}

static Queue< Stack<Event> > Reverse_Events(EventJournal ej, long date)
{
    //fare lo skipTo alla data precedente a quella da cui si vuole partire per leggere tutti gli eventi
    ej.skipTo(date);    
    
    //Stack<Event> output = new Stack<Event>();
    Queue< Stack<Event> > global_set = new LinkedList< Stack<Event> >();
    
        try 
        {                
            //se non ce ne sono altri, va nel catch
            //esempio se lo skipToDate risulta già essere aggiornato all'ultima data va subito nel catch
            //Event e = ej.nextEvent();
            //output.push();                
            Event e;
            while (true) 
            {
                Stack<Event> local_set = new Stack<Event>();
                //ogni gruppo di eventi termina con l'evento PERSIST                
                do
                {
                    //inserisco tutti gli eventi nella coda temporanea
                    e = ej.nextEvent();
                    local_set.push(e);
                }
                while(e.getType() != Event.PERSIST);
                //aggiungo questo gruppo di eventi invertiti nella coda
                global_set.add(local_set);
            }
        } 
        catch (NoSuchElementException ex) 
        {
            //fine eventi
        }    
    
    return global_set;
}        

static void Sync_Journal(long date, Session s_old, Session s_new) throws RepositoryException, IOException
{
    
    
   int event_filter = Event.NODE_ADDED | Event.PROPERTY_ADDED | Event.NODE_REMOVED | Event.NODE_MOVED | Event.PROPERTY_REMOVED | Event.PROPERTY_CHANGED | Event.PERSIST;            
    //legge journal del repo (cluster) identificato dall'url
    EventJournal ej = Read_Journal(s_old, event_filter);    
    
    //ogni gruppo di eventi è formato dagli eventi base e finisce col PERSIST (session.save() )
    //il gruppo di eventi rimane ordinato ma all'interno gli eventi sono invertiti
    //quindi il primo evento di ogni gruppo sarà il PERSIST
    Queue< Stack<Event> > event_sets = Reverse_Events(ej, date); 
    
    while(!event_sets.isEmpty())
    {
        Redo_Events(event_sets.remove(), s_old, s_new);
    }
    
    //adesso per ogni incomplete_node faccio export_import
    //solo se il nodo esiste nel repo vecchio
    //infatti potrebbero essere rimasti in questa lista dei nodi incompleti figli di altri nodi
    //che sono stati cancellati nel repo, ma non dalla lista
    //System.out.println("numero di nodi incompleti: " + incomplete_nodes.size());
    boolean export_recursively = true;
    Iterator<String> itn = incomplete_nodes.iterator();
    while(itn.hasNext())
    {
        Node incomplete = s_new.getNode(itn.next());

        //se c'è un nodo clone con lo stesso nome e che finisce quindi con le [], è solo per debug
        String tmp = "";
        String incomplete_path = "";
        //DEBUG
        try
        {
            tmp = incomplete.getPath();
            incomplete_path = tmp;                    
        }
        catch(javax.jcr.InvalidItemStateException ie)
        {
            //se sono qui è perchè ho cancellato il nodo incompleto prima di fare l'export import di un altro nodo
            //in questo caso passo al nodo incompleto successivo
            System.out.println("se sono qui è perchè ho cancellato il nodo incompleto prima di fare l'export import di un altro nodo"
                    + "in questo caso passo al nodo incompleto successivo");
            System.out.println("--incomplete: " + incomplete );
            continue;
        }    

        System.out.println("Faccio export import del nodo: " + incomplete_path);

//controllo se il nodo esiste nel repo vecchio:
        if(s_old.nodeExists(incomplete_path))
        {
            System.out.println("--Il nodo esiste nel repo vecchio");
            //potrebbe capitare di aveve un nodo incomplete, che esiste nel repo vecchio,
            //ma che nel repo nuovo non esiste il padre??? DA VERIFICARE
            //---
            //faccio export_import senza binary data
            Node source = s_old.getNode(incomplete_path);
            String parent_node_path = GetParentNodePath(incomplete_path); //prende il path fino all'ultima sbarra / (senza la sbarra, es /home/luca ritorna /home)            
            Node parent_node = s_new.getNode(parent_node_path);                        

            //cancello il nodo che verrà poi esportato dal repo vecchio e aggiunto nel padre del repo nuovo                        
            System.out.println("cancello nodo " + incomplete_path);
            try
            {
                s_new.removeItem(incomplete_path);
                //se il save genera l'eccezione...
                s_new.save();
            }
            catch(javax.jcr.nodetype.ConstraintViolationException cve)
            {
                //se finisco in questo catch è perchè il nodo non si può cancellare, altrimenti
                //violo la definizione del tipo di nodo che dice che deve avere questo figlio obbligatorio
                System.out.println("\r\nNon cancello nodo xkè è stata generata javax.jcr.nodetype.ConstraintViolationException \r\n");
                System.out.println("\r\nRitorna la sessione allo stato consistente con il refresh()");
                s_new.refresh(false);
//cve.printStackTrace();
                System.out.println("\r\n");                
            //    Node dfssf = s_new.getNode(incomplete_path);
             //   System.out.println("figli = " + dfssf.getNodes().getSize());                
                continue;
            }    
                        
            //System.out.println("cancello nodo " + to_delete.getPath());
            
//Node nn = s_new.getNode(incomplete_path);
            ByteArrayOutputStream baos = EsportaSource(source, export_recursively, s_old, s_new);
            ImportaDest(parent_node, baos.toByteArray(), s_old, s_new); 
            System.out.println("Salvo sessione dopo cancellazione import export");
            s_new.save();
            //ImportaDest(nn, baos.toByteArray()); 
            //poi controllo, per ogni nodo importato, se aveva una proprietà Binary
            //per ogni proprietà Binary, chiamo il metodo WriteBinaryData()
            //leggo quindi tutto il sottoaolbero del nodo importato
            Sync_BinaryData(parent_node, s_old, s_new);
            s_new.save();
        }    
    }
    System.out.println("session save ");
    s_new.save();
    
}


static void Redo_Events(Stack<Event> events, Session s_old, Session s_new) throws RepositoryException, IOException
{
   
 try
    {
        Event e = events.pop();
        while(true)
        {
        //DEBUG
        //System.out.println("Print Event Info");
        //Print_Event_Info(e);

            switch(e.getType())
            {
                case Event.NODE_ADDED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Add_Node()");
                        //aggiungo nodo
                        Add_Node(e, s_old, s_new);
                        break;
                    }
                case Event.NODE_REMOVED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Remove_Node()");                        
                        Remove_Node(e, s_old, s_new);
                        break;
                    }
                case Event.NODE_MOVED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Move_Node()");
                        Move_Node(e, s_old, s_new);                                                
                        break;
                    }          
                
                case Event.PROPERTY_ADDED:
                    {
                                                //DEBUG
                        System.out.println("Chiamo Evento Add_Prop()");
                        Add_Property(e, s_old, s_new);
                        break;
                    }
                case Event.PROPERTY_CHANGED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Change_Prop()");                        
                        Change_Property(e, s_old, s_new);
                        break;
                    }
                case Event.PROPERTY_REMOVED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento REmove_Prop()");                        
                        Remove_Property(e,s_old, s_new);
                        break;
                    }
                
                case Event.PERSIST:
                    {
                                                //DEBUG
                        System.out.println("Chiamo Evento Persist() session.save()");
//inizia un'altra serie di eventi, quindi faccio il save per salvare gli eventi già sincronizzati:
                        s_new.save();
                        //cancello la lista degli added_nodes, altrimenti se dopo un session.save c'è
                        //un Add-Property di un nodo aggiunto nella sessione prima, non mi aggiunge
                        //la proprietà nuova
                        added_nodes.clear();
                        break;
                    }
                default:
                    {
                        //non dovrebbe mai andare qui
                        System.out.println("ERROR: default generated in switch type_event");
                        break;
                    }
            }         
            System.out.println("\r\n");
            e = events.pop();
        }        
    }    
    catch(java.util.EmptyStackException e)
    {
        System.out.println(" Stack finito: " + e.getMessage());
        System.out.println("Chiamo session.save()");
        s_new.save();
    }
        
    
}        

static void Sync_BinaryData(Node root, Session s_old, Session s_new) throws RepositoryException
{
    System.out.println("Scrivo tutte le proprietà binarie sotto a: " + root.getPath());
    Stack<Node> stackSource = new Stack<Node>();
    stackSource.push(root);
    boolean first = true;
    while(!stackSource.empty())
    {
        Node currentSource = stackSource.pop();
        //scrive tutte le proprietà Binary dal repo vecchio al nuovo per questo nodo:
        //System.out.println("Chiamo WriteBinaryData per il nodo: " + currentSource.getPath());
        
        //salto tutte le prorprietà del nodo root
        if(!first)
        {
            Write_BinaryData(currentSource, s_old, s_new);
        }                    
        first = false;
        
        //controllo se il nodo corrente ha almeno un nodo figlio 
        if(currentSource.hasNodes())
        {
            //System.out.println("----ha nodi");
            //aggiunge allo stack tutti i suoi figli nodi di primo livello
            NodeIterator ni = currentSource.getNodes();
            int cont = 0;
            Node[] childnodes = new Node[(int)ni.getSize()];

            while(ni.hasNext())
            {
                //per riscrivere i nodi nello stesso ordine del server originale,
                //faccio la push() nello stack in ordine inverso, così le future pop()
                //prenderanno per primi i primi nodi
                childnodes[cont++] = ni.nextNode();                                      
            }
            //System.out.println("----sono qui, cont: " + cont);
            //push in ordine inverso dei nodi
            for(int i = cont - 1; i >= 0; i--)
            {                
                stackSource.push(childnodes[i]);
            }    
        }
        /*else
        {
            System.out.println("non ha nodi");
        }*/    
    }
            
}        

static void Write_BinaryData(Node parent_node, Session s_old, Session s_new) throws RepositoryException
{
//per ogni proprietà controllo se è di tipo Binary
    PropertyIterator pi = parent_node.getProperties();    
    while(pi.hasNext())
    {
        Property p = pi.nextProperty();
        //System.out.println("--WriteBinaryData prop: " + p.getPath());
        if(p.getType() == PropertyType.BINARY)
        {
            //System.out.println("----è binaria");
            //trovo proprietà del repo vecchio
            
            String property_path = p.getPath();
            //String property_path = Rimuovi_Quadre(tmp);
            //DEBUG
            //se vedo le [] le elimino
            Property p_old = s_old.getProperty(property_path);
            if(p.isMultiple())
            {
                p.setValue(p_old.getValues());
            }
            else
            {
                p.setValue(p_old.getBinary());
            }                
        }    
    }    
}        


static void Add_Node(Event e, Session s_old, Session s_new) throws RepositoryException, IOException
{
    //parametri: absolute_path, node_type, session
    String adding_node_path = e.getPath();
    
    //controllo prima che il nodo da importare non sia già presente nel repo nuovo
    /*
    potrebbe capitare che nello stesso set di eventi, il vecchio repo abbia aggiunto sia il nodo padre 
    che il nodo figlio, quindi quando faccio l'import ricorsivo del padre ho anche il figlio
    */
    if(s_new.nodeExists(adding_node_path))
    {
    System.out.println("Esiste già nel repo nuovo");    
    //se il nodo esiste già non faccio niente
        System.out.println("Salto Add_Node()");
        //lo aggiungo negli added_nodes:
        System.out.println("Lo aggiungo negli added nodes:");
        //added_nodes.add(s_new.getNode(adding_node_path));
        added_nodes.add(adding_node_path);
        return;
    }
    else
    {
        //se non esiste, può essere per 2 motivi:
        //1) il nodo è stato aggiunto nuovo completamente
        //2) è stato aggiunto il nodo padre, nel futuro questo nodo verrà spostato o eliminato nel repo vecchio
        //quindi quando aggiungi il padre con export import non aggiungi il figlio
      
    }    
    
        
    //se il nodo non esiste più nel repo vecchio, vuol dire che dopo è stato fatto un NODE_REMOVED
    //o un NODE_MOVED, quindi lo aggingo solo con il nome e lo metto nella lista degli incompleti
    //il padre del nodo nel repo nuovo esiste perchè tutti i nodi incompleti sono aggiunti
    //in questo caso si aggiungono anche tutti i figli dei nodi incompleti (etichettando incompleti anche loro)
    //perchè se dopo un PERSIST c'è un MOVE di un nodo figlio incompleto, da PathNotFoundException
    if(!s_old.nodeExists(adding_node_path))
    {
        JcrUtils.getOrCreateByPath(adding_node_path, null, s_new);
        //rileggo nodo aggiuntocon l'istruzione precedente
        //Node incomplete = s_new.getNode(adding_node_path);
        //incomplete_nodes.add(incomplete);
        incomplete_nodes.add(adding_node_path);
        System.out.println("Add_node() sono nel caso in cui nel repo vecchio non esiste più il nodo da aggiungere, perchè"
                + "è stato fatto dopo un Move o un REMOVE");        
        System.out.println("Aggiungo nodo " + adding_node_path + " alla liste degli incompleti");        
        return;
    }        
    
    
    //se sono qui, il nodo esiste ancoa nel repo vecchio e non è il figlio di un altro nodo a cui era stato
    //fatto in precedenza un export_import
    //---
    //prendo nodo padre, al quale farò poi l'import:
    String parent_node_path = GetParentNodePath(adding_node_path); //prende il path fino all'ultima sbarra / (senza la sbarra, es /home/luca ritorna /home)
    Node parent_node = s_new.getNode(parent_node_path);    
    //faccio l'export sempre ricorsivo
    boolean export_recursively = true;
    //prendo nodo da esposrtare nella sessione del cluster vecchio:
    Node to_export = s_old.getNode(adding_node_path);
    ByteArrayOutputStream baos = EsportaSource(to_export, export_recursively, s_old, s_new);
    ImportaDest(parent_node, baos.toByteArray(), s_old, s_new);    
    
    //aggiunge il nodo importato alla lista dei nodi aggiunti
    //added_nodes.add(s_new.getNode(adding_node_path));
    added_nodes.add(adding_node_path);
}

static void Remove_Node(Event e, Session s_old, Session s_new) throws RepositoryException
{
    String removing_node_path = e.getPath();
    try
    {
        Node to_remove = s_new.getNode(removing_node_path);        
        //cancello il nodo anche dalla lista degli added_node o degli incomplete_nodes
        boolean eliminated = false; 
        eliminated = added_nodes.remove(to_remove);
        if(eliminated)
        {
            System.out.println("Nodo Eliminato Da Added_Nodes");
        }    
        eliminated = incomplete_nodes.remove(to_remove);
        if(eliminated)
        {
            System.out.println("Nodo Eliminato Da Added_Nodes");
        }        
        to_remove.remove();
        System.out.println("Nodo Rimosso dal repo nuovo");
        //N.B
        //non elimino dalle liste tutti i sotto nodi del nodo eliminato, perchè
        //non mi serve, in quanto
        //added_nodes serve solo per saltare l'add_Property
        //incomplete_nodes serve per saltare ADD_Property e per fare alla fine di Redo_Events tutti gli export_import
        //solo se il nodo incompleto esiste nel repo vecchio
    }
    catch(javax.jcr.PathNotFoundException pe)
    {
            //se capita qui è perchè prima ha fatto l'evento NODE_MOVED,
        //il quale dopo genera l'evento NODE_REMOVED
        //non trova il nodo perchè l'ha già spostato
        //quindi non faccio niente
        System.out.println("Remove_Node() sono finito nel catch perchè il nodo da rimuovere non esiste più,"
                + "probabilmente xkè prima è stato fatto un Node_Moved()");
        return;
    }

}

static void Move_Node(Event e, Session s_old, Session s_new) throws RepositoryException
{
        //stampo informazioni aggiuntive:                
        Map info = e.getInfo();

        //DEBUG
        System.out.println(info);
        System.out.println(e);
        
        //controllo se è stato generato da Node.orderBefore o no
        //in ogni caso i 2 parametri letti sono gli stessi parametri di input che hanno causato l'evento
        
        String src_path = "";
        String dest_path = "";
        try
        {
            src_path = info.get("dcr:srcChildRelPath").toString();
            dest_path = info.get("dcr:destChildRelPath").toString();
            //richiamo il metodo orderBefore con i 2 parametri letti:
            Node to_order = s_new.getNode(e.getPath());
            to_order.orderBefore(src_path, dest_path);
        }
        catch(Exception ex) //session or workspace move
        {
            src_path = info.get("dcr:srcAbsPath").toString();
            dest_path = info.get("dcr:destAbsPath").toString();                    
            //leggi JCR observation move and order
            
            //N.B.
            //prima di fare la move() devo controllare se il nodo destinazione esiste già
            //se esiste già e faccio move mi crea un alias con [2] alla fine del nome del nodo
            //es:
            //prima fai NODE_ADDED di /img che ha come figlio /img/b
            //fai export import xkè il nodo /img esiste ancora nel repo vecchio
            //poi dopo fai una move di: /a/b/c/d in /img/b
            //prima di fare la move devo quindi cancellare il nodo destinazione, 
            //in questo caso cancello /img/b
            if(s_new.nodeExists(dest_path))
            {
                //lo cancello
                s_new.removeItem(dest_path);
            }    
            
            s_new.move(src_path, dest_path);
            //chiami session move con questi 2 parametri nel repo copia
        }
        //controllo se il src_path era un nodo incompleto, se lo era lo cambio anche lì
        for(int i = 0; i < incomplete_nodes.size(); i++)
        {
            String s = incomplete_nodes.get(i);
            if(s.equals(src_path))
            {
                //cambio la radice del nodo incompleto
                    System.out.println("Cambio la radice al nodo incompleto " + src_path + " in " + dest_path);
                    incomplete_nodes.set(i, dest_path);
                    //cambio la radice anche a tutti i nodi figli del nodo incompleto
                    System.out.println("cambio la radice anche a tutti i nodi figli del nodo incompleto");
                    //String old_radice = Get_Radice(src_path); //es /a/b diventa /a/d, radice = /a/b
                    //String new_radice = Get_Radice(dest_path);
                    String old_radice = src_path;
                    String new_radice = dest_path;
                    //per ogni nodo figlio nella lista degli incompleti:
                    //devo scorrere tutta la lista un' altra volta, e vedere se ogni path contiene la radice
                    //e sostituirla con la nuova
                    for(int k = 0; k < incomplete_nodes.size(); k++)
                    {
                        String nodo = incomplete_nodes.get(k);
                        if(Ha_Radice(nodo, old_radice))
                        {
                            String nodo_nuovo = Cambia_Radice(nodo, old_radice, new_radice);
                            //aggiorno nodo figlio con la nuova radice
                            incomplete_nodes.set(k, nodo_nuovo);
                            System.out.println("--cambio nodo figlio " + nodo + " in " + nodo_nuovo);                            
                        }    
                    }    
            }
        }    
}


static void Add_Property(Event e, Session s_old, Session s_new) throws RepositoryException
{
       //controllo se la proprietà viene aggiunta in seguito ad un evento Add_Node o no
    //nel primo caso ho già fatto l'import dal cluster vecchio, quindi ho importato anche la proprietà
    
//nel secondo caso è stata aggiunta una proprietà ad un nodo già esistente
//quindi aggiungo solo la proprietà senza fare l'import completo del nodo
    
    String adding_property_path = e.getPath();
    
    //se la proprietà non esiste nel repo vecchio,
    //significa che dopo un altro evento o l'ha spostata o l'ha cancellata,
    //quindi si aggiunge alla lista dei nodi incompleti il nodo
    //alla fine di Redo_Events quindi faccio export_import per avere il nodo sincronizzato
    
    try
    {
            //la proprietà per adesso esiste solo nel repo vecchio
        //potrebbe generare PathNotFoundException
            Property old_p = s_old.getProperty(adding_property_path);   

            Node old_container = old_p.getParent();
            Node container = s_new.getNode(old_container.getPath()); //sessione nuova, nodo nuovo repo

            //in ogni caso, se la proprietà è di tipo Binary, assegno il valore con il metodo set_property
            //perchè, anche se nel primo caso (il nodo è stato aggiunto nello stesso set di eventi)
            //è stato fatto l'export e l'import senza binary data
            if(old_p.getType() == PropertyType.BINARY)
            {
                //controllo se è multipla
                if(old_p.isMultiple())
                {
                    container.setProperty(old_p.getName(), old_p.getValues(), old_p.getType());
                }
                else
                {
                    container.setProperty(old_p.getName(), old_p.getValue(), old_p.getType());
                }
                return;
            }    

            //controllo se sono nel primo caso
            String path_to_control = old_container.getPath();
            System.out.println("added nodes length = " + added_nodes.size());
            for(String path:added_nodes)
            {
                //String n_path = n.getPath();
                System.out.println("CHECK 1st caso: " + path + " ==? " + path_to_control);        
                if(path.equals(path_to_control))
                {            
                    //sono nel primo caso, non aggiungo la proprietà
                    System.out.println("Salto Add_Property() - added_nodes");
                    return;
                }    
            }
            //stessa cosa per gli incomplete_nodes
            for(String path:incomplete_nodes)
            {
                //String n_path = n.getPath();
                System.out.println("CHECK 1st caso: " + path + " ==? " + path_to_control);        
                if(path.equals(path_to_control))
                {            
                    //sono nel primo caso, non aggiungo la proprietà
                    System.out.println("Salto Add_Property() - incomplete-nodes");
                    return;
                }    
            }
            
        //se arrivo qui sono nel secondo caso
           //aggiungo la proprietà nel nodo del repo nuovo, 
            //leggendo il nome e il valore dallo stesso nodo del repo vecchio:
System.out.println("Chiamo Add_Property(), il nodo non è ne incompleto, ne aggiunto in questa sessione");
            //aggiungo la proprietà:
            //--controllo se è multipla o no
            if(old_p.isMultiple())
            {
                container.setProperty(old_p.getName(), old_p.getValues(), old_p.getType());
            }
            else
            {
                container.setProperty(old_p.getName(), old_p.getValue(), old_p.getType());
            }    
    
    }
    catch(PathNotFoundException pe) //la proprietà non esiste nel nodo del repo vecchio, sarà cancellata in futuro
    {
        //aggiungo il nodo contenitore della proprietà alla lista dei nodi incompleti

        String parent_node = GetParentNodePath(adding_property_path);
            
        //Node container = s_new.getNode(parent_node);
        //aggiungo il nodo alla lista degli incompleti solo se non è già aggiunto alla lista degli incompleti
        if(!Is_In_Incomplete_Nodes(parent_node))
        {
            incomplete_nodes.add(parent_node);            
            System.out.println("Add_Property Catch PathNotFoundException:"
                + "\r\naggiungo il nodo contenitore della proprietà alla lista dei nodi incompleti"
                    + "\r\n nodo = " + parent_node);            
        }
        else
        {
            System.out.println("Era già in incomplete_Nodes, non lo riaggiungo");
        }    
    }        
}

static void Change_Property(Event e, Session s_old, Session s_new) throws RepositoryException
{
        //la proprietà esiste sicuramente nel repo nuovo perchè rifa tutti gli eventi
    //nel repo vecchio invece, la proprietà potrebbe non esistere più perchè il nodo
    //a cui fa riferimento potrebbe essere stato spostato.
    //NON si può usare l'id del nodo come riferimento perchè dopo un move può cambiare
    //(dipende dal repository, mi attengo alle specifiche jcr)
    
    //TODONT, FARE METODO GET_ITEM_NAME (dopo l'ultima /)
    //RENDERE LA PROPRIETÀ INCOMPLETA
    //SE SI SPOSTA IL NODO, DEVO SAPERE CHE HA UNA PROPRIETÀ INCOMPLETA
    //ALLA FINE DI REDO_EVENTS LEGGERÒ POI IL VALORE DELLA PROPRIETÀ
    String changing_property_path = e.getPath();
    
    try
    {
        Property p = s_new.getProperty(e.getPath());
        Property old_p = s_old.getProperty(e.getPath());
        //controllo se è multipla
        if(old_p.isMultiple())
        {
            p.setValue(old_p.getValues());            
        }
        else
        {
            if(p.getType() == PropertyType.BINARY)
            {
                p.setValue(old_p.getBinary());            
            }
            else
            {
                p.setValue(old_p.getValue());            
            }    
        }        
    }
    catch(PathNotFoundException pe)
    {
        String parent_node = GetParentNodePath(changing_property_path);
        if(!Is_In_Incomplete_Nodes(parent_node))
        {
            incomplete_nodes.add(parent_node);            
            System.out.println("Add_Property Catch PathNotFoundException:"
                + "\r\naggiungo il nodo contenitore della proprietà alla lista dei nodi incompleti"
                    + "\r\n nodo = " + parent_node);            
        }
        else
        {
            System.out.println("Era già in incomplete_Nodes, non lo riaggiungo");
        }
    }                
}

static void Remove_Property(Event e, Session s_old, Session s_new) throws RepositoryException
{
    try
    {
        Property p = s_new.getProperty(e.getPath());
        p.remove();                
    }
    catch(javax.jcr.PathNotFoundException pe)
    {
        //la propretà non esiste, non la cancello
    }    

}

    static ByteArrayOutputStream EsportaSource(Node source, boolean export_recursively, Session s_old, Session s_new) throws RepositoryException, IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        System.out.println("Exporting Node: " + source.getPath()); 
        //true =legge sottonodi ricorsivamente per l'export
        //false = i binary data non vengono salvati in output
        if(export_recursively)
        {           
            System.out.println("------------recursively");
            //true = skip binary
            //false = no recursively, quindi ricorsivo
            s_old.exportSystemView(source.getPath(), baos, true, false);
        }
        else
        {
            System.out.println("------------not recursively");
            //true = skip binary
            //true = no recursively            
            s_old.exportSystemView(source.getPath(), baos, true, true); 
        }
        
        return baos;
        
    }
//DA TESTARE
    static String GetParentNodePath(String path)
    {
        //es da /home/luca ritorna /home
        String parent_node_path = path.substring(0, path.lastIndexOf('/'));
        //se il path è una stringa vuota, significa che il parent node era la root '/'
        if(parent_node_path.equals(""))
        {
            parent_node_path = "/";
        }    
        return parent_node_path;
    }        
    
    static void ImportaDest(Node dest, byte[] buffer, Session s_old, Session s_new) throws IOException, RepositoryException
    {
        System.out.println("Importing node under " + dest.getPath() + " :");
        
        //ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
                    System.out.println("Info array: bais available = " + bais.available());
            System.out.println("Info array: buffer length = " + buffer.length);

            s_new.importXML(dest.getPath(), bais, ImportUUIDBehavior.IMPORT_UUID_COLLISION_REPLACE_EXISTING);                    
            
    }   


static boolean Is_In_Incomplete_Nodes(String path) throws RepositoryException
{
    for(String incomplete:incomplete_nodes)
    {
        if(incomplete.equals(path))
        {
            return true;
        }    
    }
    return false;
}        

//rimuove le quadre in tuti inodi del percorso
static String Rimuovi_Quadre(String path)
{
    String output = "";
    for(int i = 0; i < path.length(); i++)
    {
        char c = path.charAt(i);
        if(c == '[')
        {
            i += 2;            
        }
        else
        {
            output += c;
        }    
    }
    return output;
}             

static String Cambia_Radice(String path, String old_radice, String new_radice)
{
    //do per scontato che la old_radice sia la radice vera del path
    String nome = path.substring(old_radice.length(), path.length());
    String new_path = new_radice + nome;
    return new_path;
}

static boolean Ha_Radice(String path, String radice)
{
    //controllo carattere per carattere, se i primi caratteri di path sono gli stessi di tutti i caratteri di radice
    //path.length > radice.length, altrimenti è false
    int path_length = path.length();
    int radice_length = radice.length();
    if(path_length < radice_length)
    {
        return false;
    }    

    //controllo carattere per carattere
    for(int i = 0; i < radice_length; i++)
    {
        char p = path.charAt(i);
        char r = radice.charAt(i);
        if(p != r)
        {
            return false;
        }    
    }    

    return true;
}
    

}

