/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.observation;

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

import com.luca.observation.MyListener;
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

            String url = "http://localhost:11000/jackrabbit/server/";
            String workspace = "default";

           // DavexClient Client = new DavexClient(url);
            //Repository repo = Client.getRepository();
            Repository repo = JcrUtils.getRepository(url);
            Credentials sc = new SimpleCredentials("usc1","psc1".toCharArray());
            Session s = repo.login(sc,workspace);

            // System.out.println("REPOSITORY CAPACITIES:");
            // System.out.println(" " + Repository.OPTION_OBSERVATION_SUPPORTED + " = " + repo.getDescriptorValue(Repository.OPTION_OBSERVATION_SUPPORTED).getBoolean());
            // System.out.println(" " + Repository.OPTION_JOURNALED_OBSERVATION_SUPPORTED + " = " + repo.getDescriptorValue(Repository.OPTION_JOURNALED_OBSERVATION_SUPPORTED).getBoolean() + "\n");

            ObservationManager omgr = s.getWorkspace().getObservationManager();

            // ----- ATTACH SOME EVENT LISTENER -------------------------------

            omgr.addEventListener(
            new MyListener(),
            Event.NODE_ADDED | Event.PROPERTY_ADDED | Event.NODE_REMOVED | Event.NODE_MOVED | Event.PROPERTY_REMOVED | Event.PROPERTY_CHANGED | Event.PERSIST, // Listen to node additions
            //Event.NODE_ADDED, // Listen to node additions
            "/", // On root node...
            true, // ...and below
            null, // No filter on UUID
            null, // No filter on type name
            //false // Listen to local events as well
            true //solo per un repo esterno? I nodi modificati da qua non si vedono
            );

            // ----- ENUMERATE EVENT LISTENERS --------------------------------

            System.out.println("Registered event listeners:");
            EventListenerIterator it = omgr.getRegisteredEventListeners();

            try 
            {
                EventListener el = it.nextEventListener();
                while (true) 
                {
                    System.out.println(" " + el);
                    el = it.nextEventListener();
                }
            } 
            catch (NoSuchElementException ex) 
            {
                System.out.println(" No more event listeners\n");
            }
            
            System.out.println(" In attesa...\n");
            
            while(true);
        
}
    


    static void InsertImage(Node hello, int i) throws RepositoryException, FileNotFoundException
    {
            //stream all'immagine
            String filePath = "/home/luca/Scrivania/b.jpg";
            InputStream fileStream = new FileInputStream(filePath); 
        
//nodi necessari per l'inserimento dell'immagine
            Node img = hello.addNode("img"+i,"nt:file"); 
            Node bin = img.addNode("jcr:content","nt:resource"); 
    // First check the type of the file to add
                        MimeTable mt = MimeTable.getDefaultTable();
                        String mimeType = mt.getContentTypeFor(filePath);
                        if (mimeType == null)
                        {
                                mimeType = "application/octet-stream";
                        }
                        
               // set the mandatory properties
                        bin.setProperty("jcr:data", fileStream);
                        bin.setProperty("jcr:lastModified", Calendar.getInstance());
                        bin.setProperty("jcr:mimeType", mimeType); 

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

