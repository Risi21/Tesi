/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.observation;

/**
 *
 * @author luca
 */
import javax.jcr.observation.Event;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.EventIterator;

import java.util.Stack;
import java.util.Map;
 
public class MyListener implements EventListener
{
    
public void onEvent(EventIterator events)
{
    System.out.println("MyListener called with " + events.getSize() + " events:");
    Stack<Event> ordered_events = Reverse_StackEvents(events);
    PrintEvents(ordered_events);
}
 
public String getEventTypeName(int type)
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

//Sembra che gli eventi che genera li mette in uno stack, provo a stamparli in ordine inverso
private Stack<Event> Reverse_StackEvents(EventIterator events)
{
    Stack<Event> ordered_events = new Stack<Event>();
    
    //scorro tutti gli eventi a rovescio:
    try 
    {
        while(true)
        {
            ordered_events.push(events.nextEvent());
        }           
    }
    catch (java.util.NoSuchElementException ex) 
    {
        System.out.println("\nFine Reverse nello stack");

    }        
    
    return ordered_events;
}

private void PrintEvents(Stack<Event> events)
{
    
    try 
    {
        Event e = events.pop();
        while(true)
        {
            System.out.println(" Event: " + this.getEventTypeName(e.getType()) + " '" + e.getPath() + "'");
            
            //controllo se l'evento era NODE MOVED, in questo caso devi distinguere se è stato generato
            //da Session / Workspace move() o Node.orderBefore()
            if(e.getType() == Event.NODE_MOVED)
            {
                //stampo informazioni aggiuntive:                
                Map info = e.getInfo();
                //controllo se è stato generato da Node.orderBefore o no
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
                        
            e = events.pop();
        }           
    }
    catch (java.util.NoSuchElementException ex) 
    {
        System.out.println("\nFine eventi MyListener");
    }
    catch (javax.jcr.RepositoryException e) 
    {
        System.out.println(" An error occured: " + e.getMessage());
    }    
        
}        

}
