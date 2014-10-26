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
    
    public static void main(String[] args) throws Exception 
    {
        //try 
        //{

     String url_old = "http://localhost:11050/jackrabbit/server/";
     String workspace_old = "default";
     String db_user_old = "usd3";
     String db_pass_old = "psd3";
     
     String url_new = "http://localhost:11049/jackrabbit/server/";
     String workspace_new= "default";
     String db_user_new = "usd2";
     String db_pass_new = "psd2";    
        
     Session s_old = Session_Login(url_old, workspace_old, db_user_old, db_pass_old);
     Session s_new = Session_Login(url_new, workspace_new, db_user_new, db_pass_new);     

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

