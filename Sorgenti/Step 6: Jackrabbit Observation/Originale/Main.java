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

import com.luca.observation.MyListener;

/**
 *
 * @author luca
 */
public class Main {
    
    public static void main(String[] args) throws Exception 
    {
        try 
        {

            String url = "http://localhost:8080/server/";
            String workspace = "default";

            DavexClient Client = new DavexClient(url);
            Repository repo = Client.getRepository();
            Credentials sc = new SimpleCredentials("admin","admin".toCharArray());
            Session s = repo.login(sc,workspace);

            // System.out.println("REPOSITORY CAPACITIES:");
            // System.out.println(" " + Repository.OPTION_OBSERVATION_SUPPORTED + " = " + repo.getDescriptorValue(Repository.OPTION_OBSERVATION_SUPPORTED).getBoolean());
            // System.out.println(" " + Repository.OPTION_JOURNALED_OBSERVATION_SUPPORTED + " = " + repo.getDescriptorValue(Repository.OPTION_JOURNALED_OBSERVATION_SUPPORTED).getBoolean() + "\n");

            ObservationManager omgr = s.getWorkspace().getObservationManager();

            // ----- ATTACH SOME EVENT LISTENER -------------------------------

            omgr.addEventListener(
            new MyListener(),
            //Event.NODE_ADDED | Event.PROPERTY_ADDED, // Listen to node additions
            Event.NODE_ADDED, // Listen to node additions
            "/", // On root node...
            true, // ...and below
            null, // No filter on UUID
            null, // No filter on type name
            false // Listen to local events as well
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

            // ----- DO SOME NODE MANIPULATIONS -------------------------------
            Node root = s.getRootNode();
            Node node = recreate_node(s, root, "my_node");
            Node bla = root.addNode("bla");
            //node.setProperty("my_prop", "my_prop_value");
            bla.setProperty("my_bla", "my_bla_value");
            s.save();

            // ----- LET SOME TIME TO "PROPAGATE" THE CHANGES ------------------

            Thread.sleep(1000);

            // ----- READ THE EVENT JOURNAL -----------------------------------

            System.out.println("\nJournal content:");

            //EventJournal j = omgr.getEventJournal();
            EventJournal j = omgr.getEventJournal(Event.PROPERTY_ADDED, "/", true, null, null);

            if (j == null) 
            {
                System.out.println("Observation is not supported by your repository");
                return;
            }

            try 
            {
                Event e = j.nextEvent();
                while (true) 
                {
                    System.out.println(" " + e.getDate() + " - " + e.getIdentifier());
                    e = j.nextEvent();
                }
            } 
            catch (NoSuchElementException ex) 
            {
                System.out.println(" No more events\n");
            }

        } 
        catch (RepositoryException e) 
        {
            e.printStackTrace();
        }

        // ----- LET SOME TIME FOR EVENTS BEFORE FINISHING --------------------

        Thread.sleep(3000);
}
    
protected static Node recreate_node(Session s, Node parent, String name) 
{
 
    try 
    {
        Node node;

        if (parent.hasNode(name)) 
        {
            node = parent.getNode(name);
            node.remove();
        }
        s.save();

        node = parent.addNode(name);
        s.save();

        return node;
    } 
    catch (RepositoryException e) 
    {
        e.printStackTrace();
    }
    return null;
}

}

