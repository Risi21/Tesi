/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mycompany.hotbackup;

import static com.mycompany.hotbackup.Constants.*;

import java.sql.*; //mysql
import java.io.*; //BufferedRreader, output del processo exec

/**
 *
 * @author ideato
 */
        //NOTA BENE
        //http://stackoverflow.com/questions/20820213/mysqldump-from-java-application
        /*
        The redirection operator doesn't works when using Process exec = Runtime.getRuntime().exec("C:\\Program Files\\MySQL\\MySQL Server 5.6\\bin\\mysqldump "+fisier.getName()+" > C:\\"+fisier.getName()+".sql;");

        It is becasue it does not invokes the command shell, 
        so we cannot get the functionality of the redirection operator, 
        in order to fix this we can execute a command prompt (cmd) 
        followed by the mysqldump command, and it will work.
        */

public class Main {
    
    static String db_name = ""; //nome del database mysql del repository jackrabbit (col prefisso)
    static String repo_name = ""; //nome del repository (senza prefisso)
    static String datastore_path = ""; // /srv/repo/<repo name>
    static String repo_backup = ""; // /srv/backup/<repo name>
    
    public static void main(String[] args) throws Exception 
    {       
        CheckArguments(args);
      
        long inizio = System.currentTimeMillis();
        
        //inizio sessione Mysql
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + MYSQL_URL + "/" + db_name + "?user=" + ADMIN_JRSAAS + "&password=" + ADMIN_JRSAAS_PWD + "");

        LockTables(cn); //i lock rimangono attivi solo all'interno di questa sessione
        
        ExportPersistenceManager();
        
        ExportDatastore();          
        
        UnLockTables(cn);
        
        //fine sessione Mysql, di default tutti i lock vengono cancellati
        cn.close();
        
        //crea un unico file chiamato backup.tar.gz che contiene
        //sia l'esportazione del persistence manager che del datastore
        //MakeTarGz();
        
        long fine = System.currentTimeMillis();
        long tempo = (fine - inizio) / 1000;
        System.out.println("Tempo di esecuzione = " + tempo + " secondi");
        
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
                datastore_path = ROOT_REPO_PATH + "/" + repo_name + "/repository/datastore";
                repo_backup = PATH_BACKUP + "/" + repo_name;
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
    
    static void LockTables(Connection cn) throws SQLException
    {
        
        System.out.println("SHOW TABLES FROM " + db_name + ";");
        Statement s = cn.createStatement();
        ResultSet rs = s.executeQuery("SHOW TABLES FROM " + db_name + ";");
        
        //per ogni tabella del database scelto fa il lock
        String cmd = "LOCK TABLES ";
        while(rs.next())
        {
            String table = rs.getString(1);
            //System.out.println("LOCK TABLES " + db_name + "." + table + " READ;");
            cmd += db_name + "." + table + " READ, ";
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
    
    static void ExportPersistenceManager() throws IOException, InterruptedException
    {
        //esporta persistence manager (database mysql)
        //dentro a /srv/backup/<repo_name>        
        String cmd = "sudo mysqldump -u " + ADMIN_JRSAAS + " -p" + ADMIN_JRSAAS_PWD + " " + db_name + " > " + repo_backup + "/pm.sql";
        System.out.println("\r\nEsporto database mysql:\r\n" + cmd);
        int status = ForkProcess(cmd);
        //se status != 0 ERRORE        
    }        

    static void ExportDatastore() throws IOException, InterruptedException
    {
        //esporta datastore
        //dentro a /srv/backup/<repo_name>        
        String cmd = "sudo rsync -au " + datastore_path + " " + repo_backup;
        System.out.println("\r\nEsporto datastore:\r\n" + cmd);                
        int status = ForkProcess(cmd);
        //se status != 0 ERRORE
    }
    
    static void MakeTarGz() throws IOException, InterruptedException
    {
        String cmd = "sudo tar -zcvf " + repo_backup + "/backup.tar.gz " +  repo_backup + "/";
        System.out.println("\r\nCreo tar.gz del backup:\r\n" + cmd);                
        int status = ForkProcess(cmd);
        //se status != 0 ERRORE
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

