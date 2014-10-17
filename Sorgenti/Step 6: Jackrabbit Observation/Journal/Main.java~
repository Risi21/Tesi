/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.journal;

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
            //repo (cluster) che si vuole sincronizzare
            String url = "http://localhost:11003/jackrabbit/server/";
            String workspace = "default";
            String username = "uscc2";
            String password = "pscc2";
            int event_filter = Event.NODE_ADDED | Event.PROPERTY_ADDED | Event.NODE_REMOVED | Event.NODE_MOVED | Event.PROPERTY_REMOVED | Event.PROPERTY_CHANGED | Event.PERSIST;
            
            //legge journal del repo (cluster) identificato dall'url
            EventJournal ej = Read_Journal(url, workspace, username, password, event_filter);            
            
            Stack<Event> events = Reverse_Events(ej);
            
            String url2 = "http://localhost:11004/jackrabbit/server/";
            String workspace2 = "default";
            String username2 = "uscc2c";
            String password2 = "pscc2c";            
            Redo_Events(events,url2, workspace2, username2, password2);
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

static EventJournal Read_Journal(String repo_url, String workspace, String username, String password, int event_filter) throws RepositoryException
{
            Repository repo = JcrUtils.getRepository(repo_url);
            Credentials sc = new SimpleCredentials(username,password.toCharArray());
            Session s = repo.login(sc,workspace);

            ObservationManager omgr = s.getWorkspace().getObservationManager();           
            
            // ----- READ THE EVENT JOURNAL -----------------------------------
            return omgr.getEventJournal(event_filter, "/", true, null, null);

}

static Stack<Event> Reverse_Events(EventJournal ej)
{
    //fare lo skipTo alla data precedente a quella da cui si vuole partire per leggere tutti gli eventi
    long last = 1413472594690L;
    ej.skipTo(last);    
    
    Stack<Event> output = new Stack<Event>();
    
        try 
        {                
            //se non ce ne sono altri, va nel catch
            output.push(ej.nextEvent());                
            while (true) 
            {
                output.push(ej.nextEvent());
            }
        } 
        catch (NoSuchElementException ex) 
        {
            //fine eventi
        }    
    
    return output;
}              

static void Redo_Events(Stack<Event> events, String repo_url, String workspace, String username, String password) throws RepositoryException
{
    //DEBUG
    Print_Events(events);
    
    //faccio il login nel repo che deve rifare gli eventi per essere sincronizzato
    Repository repo = JcrUtils.getRepository(repo_url);
    Credentials sc = new SimpleCredentials(username,password.toCharArray());
    Session s = repo.login(sc,workspace);
    
    try
    {
        Event e = events.pop();
        while(true)
        {
            switch(e.getType())
            {
                case Event.NODE_ADDED:
                    {
                        //aggiungo nodo
                        Add_Node(s,e);
                        break;
                    }
                case Event.NODE_REMOVED:
                    {
                        Remove_Node(s, e);
                        break;
                    }
                case Event.NODE_MOVED:
                    {
                        Move_Node(s, e);                                                
                        break;
                    }          
                
                case Event.PROPERTY_ADDED:
                    {
                        Add_Property(s, e);
                        break;
                    }
                case Event.PROPERTY_CHANGED:
                    {
                        Change_Property(s, e);
                        break;
                    }
                case Event.PROPERTY_REMOVED:
                    {
                        Remove_Property(s, e);
                        break;
                    }
                
                case Event.PERSIST:
                    {
                        //inizia un'altra serie di eventi, quindi faccio il save per salvare gli eventi già sincronizzati:
                        s.save();
                        break;
                    }
                default:
                    {
                        //non dovrebbe mai andare qui
                        System.out.println("ERROR: default generated in switch type_event");
                        break;
                    }
            }    
            if(e.getType() == Event.NODE_MOVED)
            {
                                                
            }              
            System.out.println("\r\n");
            e = events.pop();
        }        
    }    
    catch(java.util.EmptyStackException e)
    {
        System.out.println(" Stack finito: " + e.getMessage());
        System.out.println("Chiamo session.save()");
        s.save();
    }
    
}        

static void Print_Events(Stack<Event> events)
{
    try 
    {
        Event e = events.pop();
        while(true)
        {
            System.out.println(" - Data: " + e.getDate() + "\r\n - Id: " + e.getIdentifier() + "\r\n - Path: " + e.getPath() + "\r\n - Type: " + getEventTypeName(e.getType()) + "\r\n - UserData " + e.getUserData() + "\r\n - UserID: " + e.getUserID() + "\r\n");            
            //controllo se l'evento era NODE MOVED, in questo caso devi distinguere se è stato generato
            //da Session / Workspace move() o Node.orderBefore()
            if(e.getType() == Event.NODE_MOVED)
            {
                //stampo informazioni aggiuntive:                
                Map info = e.getInfo();
                //controllo se è stato generato da Node.orderBefore o no
                //in ogni caso i 2 parametri letti sono gli stessi parametri di input che hanno causato l'evento
                try
                {
                    String srcChildRelPath = info.get("srcChildRelPath").toString();
                    String destChildRelPath = info.get("destChildRelPath").toString();
                    System.out.println("--srcChildRelPath: " + srcChildRelPath);
                    System.out.println("--destChildRelPath: " + destChildRelPath);
                    //chiami nide.orderBefore nella copia con questi 2 parametri
                }
                catch(Exception ex) //session / workspace move
                {
                    String srcAbsPath = info.get("srcAbsPath").toString();
                    String destAbsPath = info.get("destAbsPath").toString();                    
                    System.out.println("--srcAbsPath: " + srcAbsPath);
                    System.out.println("--destAbsPath: " + destAbsPath);         
                    //chiami session move con questi 2 parametri nel repo copia
                }                                                
            }              
            System.out.println("\r\n");
            e = events.pop();
        }           
    }
    catch (java.util.NoSuchElementException ex) 
    {
        System.out.println("\nFine eventi Journal");
    }
    catch (javax.jcr.RepositoryException e) 
    {
        System.out.println(" An error occured: " + e.getMessage());
    }
    catch(java.util.EmptyStackException e)
    {
        System.out.println(" Stack finito: " + e.getMessage());
    }    
    
}

static void Add_Node(Session s, Event e) throws RepositoryException
{
    //parametri: absolute_path, node_type, session
        //Node added = JcrUtils.getOrCreateByPath(e.getPath(), null, s);
    //DA FARE CON EXPORT IMPORT
}

static void Remove_Node(Session s, Event e) throws RepositoryException
{
    //in questo caso fa la get, non la create:
    s.removeItem(e.getPath());
}
//TODONT
static void Move_Node(Session s, Event e) throws RepositoryException
{
        //stampo informazioni aggiuntive:                
        Map info = e.getInfo();
        //controllo se è stato generato da Node.orderBefore o no
        //in ogni caso i 2 parametri letti sono gli stessi parametri di input che hanno causato l'evento
        try
        {
            String srcChildRelPath = info.get("srcChildRelPath").toString();
            String destChildRelPath = info.get("destChildRelPath").toString();
            System.out.println("--srcChildRelPath: " + srcChildRelPath);
            System.out.println("--destChildRelPath: " + destChildRelPath);
            //chiami nide.orderBefore nella copia con questi 2 parametri
        }
        catch(Exception ex) //session / workspace move
        {
            String srcAbsPath = info.get("srcAbsPath").toString();
            String destAbsPath = info.get("destAbsPath").toString();                    
            System.out.println("--srcAbsPath: " + srcAbsPath);
            System.out.println("--destAbsPath: " + destAbsPath);         
            //chiami session move con questi 2 parametri nel repo copia
        }        
}


static void Add_Property(Session s, Event e)
{
       
}

static void Change_Property(Session s, Event e)
{
        
}

static void Remove_Property(Session s, Event e)
{
        
}

}

