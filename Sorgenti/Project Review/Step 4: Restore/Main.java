/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.restore;

import static com.luca.restore.Constants.*; //importa tutte le costanti

import java.sql.*; //mysql
import java.io.*; //BufferedRreader, output del processo exec

/**
 *
 * @author luca
 */
public class Main {
    
    static String cluster_pm_name = ""; //nome del database mysql del repository jackrabbit (col prefisso)
    static String cluster_name = ""; //nome del repository (senza prefisso)
    static String cluster_root_path = "";
    static String cluster_datastore_path = ""; // /srv/cluster/<cluster name>/datastore
    static String cluster_backup_path = ""; // /srv/cluster/<cluster name>/backup
    
    static String db_user = "";
    static String db_password = "";
    static String cluster_mysql_url = "";
   
    
    public static void main(String[] args) throws Exception 
    {       
        CheckArguments(args);
      
	Read_Variables();

        //Unzip tar.gz
        
        Stop_All_Nodes();
        
        //cancella cartella repository e workspaces all'interno della cartella del repository jackrabbit, verranno poi ricreate
        // /srv/repo/.../repository e workspaces
        //cancella quindi anche il datastore e tutti i worspaces
        //Delete_RepoFolders();       
        
        //cancella tutte le tabelle MySql del repository, verranno poi ricreate
        //Delete_PersistenceManager();
        
        //copia /srv/backup/.../datastore in /srv/repo/.../repository
        Restore_Datastore();
        
        //importa nel database  mysql del repo le tabelle backuppate
        Restore_PersistenceManager();
        
        Start_All_Nodes();

        System.out.println("RESTORE DONE");
        
    }
    
  static void CheckArguments(String[] args)
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //nome della nuova istanza di tomcat
            if(args[i].equals("-cluster"))
            {
                cluster_name = args[++i];
                cluster_root_path = ROOT_CLUSTER_PATH + "/" + cluster_name;
                cluster_backup_path = cluster_root_path + "/backup";
                cluster_datastore_path = cluster_root_path + "/datastore";  
            }
            if(args[i].equals("--help"))
            {
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-cluster <nome cluster da fare restore>,\r\n");
                System.exit(0);
            }              
        }
    }     

     static void Read_Variables() throws SQLException
   {       
       String query = "SELECT * FROM " + MYSQL_JRSAAS_CONFIG_DB + "." + MYSQL_TABLE_CLUSTER + " WHERE nome = '" + cluster_name + "';";
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_JRSAAS_CONFIG_DB + "?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");
        Statement s = cn.createStatement();
        
         //definisce nuovo Id e Nome per il nuovo nodo jackrabbit
         ResultSet rs = s.executeQuery(query);         
         rs.next();

        db_user = rs.getString("user");
        db_password = rs.getString("userpwd");
        cluster_pm_name = rs.getString("db_name");
        cluster_mysql_url = rs.getString("mysql_url");
        
        cn.close();
        
   }  
  
  static void Restore_Datastore() throws IOException, InterruptedException 
  {      
       String cmd = "sudo rsync -au " + cluster_backup_path + "/datastore/" + " " + cluster_datastore_path;
       System.out.println("\r\nRestore Datastore: " + cmd);
       ForkProcess(cmd);      
  }
  
  static void Restore_PersistenceManager() throws IOException, InterruptedException
  {
       String cmd = "sudo mysql -u " + ADMIN_JRSAAS + " -p" + ADMIN_JRSAAS_PWD + " " + cluster_pm_name + " < " +  cluster_backup_path + "/pm.sql";
       System.out.println("\r\nRestore Persistence Manager: " + cmd);
       ForkProcess(cmd);            
  }        
  
static void Start_All_Nodes() throws SQLException, IOException, InterruptedException
{
    //leggo dalla tabella CLUSTER_NODE del persistence manager tutti i nodi di questo cluster
        //inizio sessione Mysql
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_JRSAAS_CONFIG_DB + "?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");
        
        String cmd = "SELECT * FROM " + MYSQL_JRSAAS_CONFIG_DB + "." + MYSQL_TABLE_CLUSTER_NODE + " WHERE CLUSTER = '" + cluster_name + "';";      
        System.out.println(cmd);
        
        Statement s = cn.createStatement();
        ResultSet rs = s.executeQuery(cmd);
        
        while(rs.next())
        {
            String node_name = rs.getString("nome"); //legge nome del nodo
            String start_script_path = cluster_root_path + "/" + node_name + "/tomcat/scripts/start_" + node_name + ".sh";
            cmd = "sudo sh " + start_script_path;
            System.out.println("Avvio nodo: " + node_name + "\r\n" + cmd);
            ForkProcess(cmd);
        } 
        
        //fine sessione Mysql, di default tutti i lock vengono cancellati
        cn.close();    
}        

static void Stop_All_Nodes() throws SQLException, IOException, InterruptedException
{
    //leggo dalla tabella CLUSTER_NODE del persistence manager tutti i nodi di questo cluster
        //inizio sessione Mysql
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + MYSQL_JRSAAS_CONFIG_DB + "?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");
        
        String cmd = "SELECT * FROM " + MYSQL_JRSAAS_CONFIG_DB + "." + MYSQL_TABLE_CLUSTER_NODE + " WHERE CLUSTER = '" + cluster_name + "';";      
        System.out.println(cmd);
        
        Statement s = cn.createStatement();
        ResultSet rs = s.executeQuery(cmd);
        
        while(rs.next())
        {
            String node_name = rs.getString("nome"); //legge nome del nodo
            String start_script_path = cluster_root_path + "/" + node_name + "/tomcat/scripts/stop_" + node_name + ".sh";
            cmd = "sudo sh " + start_script_path;
            System.out.println("Fermo nodo: " + node_name + "\r\n" + cmd);
            ForkProcess(cmd);
        } 
        
        //fine sessione Mysql, di default tutti i lock vengono cancellati
        cn.close();
}  
    
    static int ForkProcess(String cmd) throws IOException, InterruptedException
    {
        //esporta datastore
        //dentro a /srv/backup/<repo_name>
        
        Process p = Runtime.getRuntime().exec(new String[] { "/bin/bash", "-c", cmd });
        int status = p.waitFor();
        if(status == 0)
        {
            //terminato normalmente            
        }
        else
        {
            //stampo errori
            String line = "";
        
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = in.readLine()) != null) 
            {
                 System.out.println(line);
            }
            in.close();
        }
        
        return status;
    }     
   
   
}

