/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.asincrono;

import javax.jcr.Credentials;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.SimpleCredentials;
import javax.jcr.version.*;
import java.util.*;
import javax.jcr.observation.ObservationManager;
import javax.jcr.observation.EventJournal;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.EventListenerIterator;

//import ch.liip.jcr.davex.DavexClient;

import com.luca.asincrono.MyListener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import org.apache.jackrabbit.commons.JcrUtils;
import sun.net.www.MimeTable;

/**
 *
 * @author luca
 */
public class Main {
    
    static String src_url = "";
    static String clone_url = "";
    static String db_user = "";
    static String db_pass = "";
    
    public static void main(String[] args) throws Exception 
    {

     Session s_old = Session_Login(src_url, "default", db_user, db_pass);
     Session s_new = Session_Login(clone_url, "default", db_user, db_pass);

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
         if(args[i].equals("-srcUrl"))
         {
             src_url = args[++i];          
         }
         if(args[i].equals("-user"))
         {
             //è uguale, sia per il cluster originale che per il clone
             db_user = args[++i];          
             db_pass = args[++i];          
         }         
         if(args[i].equals("-cloneUrl"))
         {
             clone_url = args[++i];             
         }                        
         if(args[i].equals("--help"))
         {
             System.out.println(""
                     + "\r\nParametri disponibili:\r\n\r\n"
                     + "-srcUrl <url di un nodo attivo del cluster originale>, es: http://localhost:11028/jackrabbit/server/\r\n"
                     + "-cloneUrl <url di un nodo attivo del cluster clone>, es: http://localhost:11028/jackrabbit/server/\r\n"
                     + "-user <username> <password>, credenziali utente per il repository, è uguale sia per l'originale che per il clone\r\n");                
             System.exit(0);
         }              
     }
 }      
    
static Session Session_Login(String url, String workspace, String username, String password) throws RepositoryException
{
        Repository repo = JcrUtils.getRepository(url);
        Credentials sc = new SimpleCredentials(username,password.toCharArray());
        Session s = repo.login(sc,workspace);    
        return s;
}   

static String getEventTypeName(int type)
{ 
    switch (type) 
    {
        case Event.NODE_ADDED: return "NODE ADDED";
        case Event.NODE_MOVED: return "NODE MOVED";
        case Event.NODE_REMOVED: return "NODE REMOVED";
        case Event.PERSIST: return "PERSIST";
        case Event.PROPERTY_ADDED: return "PROP ADDED";
        case Event.PROPERTY_CHANGED: return "PROP CHANGED";
        case Event.PROPERTY_REMOVED: return "PROP REMOVED";
        default: return "UNKNOWN";
    }
}    
    
}

