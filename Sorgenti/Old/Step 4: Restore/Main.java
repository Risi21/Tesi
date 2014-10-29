/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mycompany.restore;

import static com.mycompany.restore.Constants.*; //importa tutte le costanti

import java.sql.*; //mysql
import java.io.*; //BufferedRreader, output del processo exec

/**
 *
 * @author ideato
 */
public class Main {
    
    static String db_name = ""; //nome del database mysql del repository jackrabbit (col prefisso)
    static String repo_name = ""; //nome del repository (senza prefisso)
    static String datastore_path = ""; // /srv/repo/<repo name>
    static String repo_backup = ""; // /srv/backup/<repo name>
    static String datastore_backup = ""; // /srv/backup/<repo name>/datastore
    static String persistenceManager_backup = ""; // /srv/backup/<repo name>/pm.sql
    static String repo_path = ""; // /srv/repo/<repo name>
    
    public static void main(String[] args) throws Exception 
    {       
        CheckArguments(args);
      
        //Unzip tar.gz
        long inizio = System.currentTimeMillis();
        
        Stop_Instance();
        
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
        
        Start_Instance();

        long fine = System.currentTimeMillis();
        long tempo = (fine - inizio) / 1000;
        System.out.println("Tempo di esecuzione = " + tempo + " secondi");        
        
        System.out.println("RESTORE DONE");
        
    }
    
  static void CheckArguments(String[] args)
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //nome della nuova istanza di tomcat
            if(args[i].equals("-reponame"))
            {
                repo_name = args[++i];
                db_name = MYSQL_REPO_PREFIX + repo_name;
                repo_path = ROOT_REPO_PATH + "/" + repo_name;
                repo_backup = PATH_BACKUP + "/" + repo_name;
                datastore_backup = repo_backup + "/datastore";
                persistenceManager_backup = repo_backup + "/pm.sql";                
            }
            if(args[i].equals("--help"))
            {
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-reponame <mysql db del repository jackrabbit>,\r\n");
                System.exit(0);
            }              
        }
    }    
    
  static void Delete_RepoFolders() throws IOException, InterruptedException
  {
       String cmd = "sudo rm -r " + repo_path + "/repository";
       System.out.println("\r\nCancello cartella repository: " + cmd);
       ForkProcess(cmd);
       
       cmd = "sudo rm -r " + repo_path + "/workspaces";
       System.out.println("\r\nCancello cartella workspaces: " + cmd);
       ForkProcess(cmd);
       
  }
  
  static void Delete_PersistenceManager() throws IOException, InterruptedException, SQLException
  {
        //inizio sessione Mysql
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + db_name + "?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");
      
        System.out.println("SHOW TABLES FROM " + db_name + ";");
        Statement s = cn.createStatement();
        ResultSet rs = s.executeQuery("SHOW TABLES FROM " + db_name + ";");
        
        //per ogni tabella del database scelto fa il lock
        String cmd = "DROP TABLE ";
        while(rs.next())
        {
            String table = rs.getString(1);
            cmd += db_name + "." + table + ", ";
        } 

        //elimino ultimi 2 caratteri, lo spazio finale e la virgola
        cmd = cmd.substring(0, cmd.length()-2);
        //fine query mysql
        cmd += ";";
        
        System.out.println(cmd);
        Statement drop = cn.createStatement();
        drop.executeUpdate(cmd);      
        
        //fine sessione Mysql, di default tutti i lock vengono cancellati
        cn.close();
  }        
  
  static void Restore_Datastore() throws IOException, InterruptedException 
  {

       String cmd = "sudo mkdir " + repo_path + "/repository";
       System.out.println("\r\nCrea cartella repository vuota: " + cmd);
       ForkProcess(cmd);      
      
       cmd = "sudo rsync -au " + datastore_backup + " " + repo_path + "/repository/datastore";
       System.out.println("\r\nRestore Datastore: " + cmd);
       ForkProcess(cmd);      
  }
  
  static void Restore_PersistenceManager() throws IOException, InterruptedException
  {
       String cmd = "sudo mysql -u " + ADMIN_JRSAAS + " -p" + ADMIN_JRSAAS_PWD + " " + db_name + " < " +  persistenceManager_backup;
       System.out.println("\r\nRestore Persistence Manager: " + cmd);
       ForkProcess(cmd);            
  }        
  
   static void Start_Instance() throws IOException, InterruptedException
   {
       String cmd = "sudo sh " + TOMCAT_SCRIPTS + "/start_" + repo_name + ".sh";
       System.out.println("\r\nAvvio istanza di tomcat richiamando lo script di start");
       ForkProcess(cmd);
   }
   
   static void Stop_Instance() throws IOException, InterruptedException
   {
       String cmd = "sudo sh " + TOMCAT_SCRIPTS + "/stop_" + repo_name + ".sh";
       System.out.println("\r\nFermo istanza di tomcat richiamando lo script di stop");
       ForkProcess(cmd);
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

