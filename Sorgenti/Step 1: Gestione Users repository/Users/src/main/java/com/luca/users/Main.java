/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.users;

import java.io.*;
import java.util.*;

//Serve per Repository e Session
import javax.jcr.*;

//serve per JcrUtilities
import org.apache.jackrabbit.commons.*;
import org.apache.jackrabbit.api.security.user.*;
import org.apache.jackrabbit.api.*;
import org.apache.jackrabbit.core.*;
import org.apache.jackrabbit.core.config.*;

/**
 *
 * @author luca
 */
public class Main {
    
    //static String url = "http://localhost:8080/jackrabbit-webapp-2.8.0/server";
    
//percorso di dove si trova il repository.xml
    static String path = "/srv/jackrabbit";
    static String admin_password = "";
    static boolean change_user = false;
    static String user_to_change = "";
    static String new_password = "";
    static boolean create_user = false;
    static String username = "";
    static String password = "";
    static boolean delete_user = false;
    static String user_to_delete = "";
    
    public static void main(String[] args) throws Exception 
    {
        //Repository repository = JcrUtils.getRepository(url); 
        CheckArguments(args);
        
        //serve per loggarsi / creare ad un repository jackrabbit
         RepositoryConfig config = RepositoryConfig.install(new File(path));
         //NB nella cartella ci devono essere i permessi di scrittura per creare il file .lock
         Repository repository = RepositoryImpl.create(config);         

        Session session; 
        
        //controllo se l'utente esiste        
        try
        {
            session = repository.login(new
            SimpleCredentials("admin",admin_password.toCharArray()));              
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

         //cambia password all'utente
         if(change_user)
         {
              Authorizable authorizable = userManager.getAuthorizable(user_to_change);
              ((User) authorizable).changePassword(new_password);
         }
         //crea utente - password
         if(create_user)
         {
             final User user = userManager.createUser(username, password);
         }
         //cancella utente
         if(delete_user)
         {
            Authorizable user = userManager.getAuthorizable(user_to_delete);
            user.remove();                 
         }    
        
        session.save();
        session.logout();     
        
        //NB cancella manualmente il file .lock nella cartella dov'è il repository.xml
    }
    
   static void CheckArguments(String[] args) throws RepositoryException
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //prende il percorso della cartella dov è localizzato il repository.xml file (es /srv/jackrabbit)
            if(args[i].equals("-path"))
            {
                path = args[++i];
            }
            //prende la password dell admin, necessario per fare login nel repository
            if(args[i].equals("-p"))
            {
                admin_password = args[++i];
            }
            //setta password nuova all'utente admin
            if(args[i].equals("-cp"))
            {
                change_user = true;
                user_to_change = args[++i];
                new_password = args[++i];
            }
            //crea nuovo utente, seguono 2 parametri: username e password
            if(args[i].equals("-cu"))
            {
                create_user = true;
                username = args[++i];
                password = args[++i];
            }
            //cancella utente, parametro è la username
            if(args[i].equals("-canc"))
            {
                delete_user = true;
                user_to_delete = args[++i];
            }            
            if(args[i].equals("--help"))
            {
                //il prossimo parametro è l'url nel quale si leggono i nodi
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-path <path where repository.xml file is located>, necessario per fare login nel repository\r\n"
                        + "-p <password>, password dell admin, necessario per fare login al repository\r\n"
                        + "-cp <username> <new password>, assegna nuova password allo user selezionato\r\n"
                        + "-cu <username> <password>, crea nuovo utente per il repository\r\n"
                        + "-canc <username>, cancella utente dal repository\r\n");                
                System.exit(0);
            }              
        }
    }
}
