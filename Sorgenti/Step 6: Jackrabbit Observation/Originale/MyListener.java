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
 
public class MyListener implements EventListener
{
    
public void onEvent(EventIterator events)
{
    System.out.println("MyListener called with events:");
    try 
    {
        Event e = events.nextEvent();
        try 
        {
            System.out.println(" Event: " + this.getEventTypeName(e.getType()) + " '" + e.getPath() + "'");
            e = events.nextEvent();
        } 
        catch (java.util.NoSuchElementException ex) 
        {
            System.out.println("\n");
        }
    } 
    catch (javax.jcr.RepositoryException e) 
    {
        System.out.println(" An error occured: " + e.getMessage());
    }
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

}
