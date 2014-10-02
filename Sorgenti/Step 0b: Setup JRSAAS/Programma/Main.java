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

/**
 *
 * @author luca
 */
public class Main {
    
    public static void main(String[] args) throws Exception 
    {       
        CheckArguments(args);
        
        //installa java 1.8 oracle via repository
        Setup_Java();
        
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
            if(args[i].equals("-reponame"))
            {
           
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
    
    static void Setup_Java() throws IOException, InterruptedException
    {        
        String cmd = "sudo add-apt-repository ppa:webupd8team/java";
        System.out.println("Aggiungo repository" + cmd);
        ForkProcess(cmd);
        
        cmd = "sudo apt-get -y update";
        System.out.println("Update repository" + cmd);
        ForkProcess(cmd);
        
        cmd = "sudo apt-get -y install oracle-java8-installer";
        System.out.println("Installa java 1.8 oracle" + cmd);
        ForkProcess(cmd);                
    }        

    static void Setup_Tomcat() throws IOException, InterruptedException
    {
        //N.B. il file tomcat.tar.gz DEVE essere nella stessa cartella del jar
        
        //estrae cartella tomcat in /usr/share
        String cmd = "sudo tar -zxvf " + current_exe_path + "/tomcat.tar.gz -C /usr/share/";
        System.out.println("Estraggo tomcat in /usr/share/ " + cmd);
        ForkProcess(cmd);
        
        //aggiunge al file .bashrc i 2 export
        Append_Bashrc();
        
        //esegue file bashrc per applicare i 2 export:
        cmd = ". ~/.bashrc";
        System.out.println("Eseguo file bashrc " + cmd);
        ForkProcess(cmd);
        
    }
    
    static void Append_Bashrc()
    {
        File toAppend= new File(bashrc);
        //aggiunge 2 riche di export
        FileWriter f = new FileWriter(toAppend, true);
        f.write("export CATALINA_HOME=/usr/share/tomcat-8.0.12\n" +
"export JAVA_HOME=/usr/lib/jvm/java-8-oracle");
        f.close();            
    }    
    
    static void Setup_JRSAAS()
    {
        
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

