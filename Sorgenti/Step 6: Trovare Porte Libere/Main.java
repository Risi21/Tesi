/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.findport;

import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 *
 * @author luca
 */
public class Main {
    
    public static void main(String[] args) throws Exception 
    {
        System.out.println("Prova a creare 100 istanze di tomcat, 3 porte ogni istanza\r\n\r\n");
        //11000 per connector server web
        //12000 per shutdown
        //13000 per ajp
        ArrayList c = new ArrayList();
        ArrayList s = new ArrayList();
        ArrayList a = new ArrayList();       
        
        for(int i = 0; i < 100; i++)
        {
            
            ServerSocket connector = create(11000,11999);
            ServerSocket shutdown = create(12000,12999);
            ServerSocket ajp = create(13000,13999);
            
            //DEBUG: evita che il garbage collector rimuova le ServerSocket appena create
            c.add(connector);
            s.add(shutdown);
            a.add(ajp);
            
            System.out.println("Connector port: " + connector.getLocalPort());
            System.out.println("Shutdown port: " + shutdown.getLocalPort());
            System.out.println("Ajp port: " + ajp.getLocalPort() + "\r\n");
        }    
        
    }
    
    public static ServerSocket create(int min_port, int max_port) throws IOException 
    {
        for (int port = min_port; port <= max_port; port++) 
        {
            try 
            {
                return new ServerSocket(port);
            } 
            catch (IOException ex) 
            {            
                continue; // try next port
            }
    }

    // if the program gets here, no port in the range was found
    throw new IOException("no free port found");
}    
    
}

