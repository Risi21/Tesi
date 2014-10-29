/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.mavenproject1;

import javax.jcr.*; 
import java.io.*;
import sun.net.www.MimeTable;
import java.util.Calendar;

//serve per JcrUtilities
import org.apache.jackrabbit.commons.*;


/**
 *
 * @author luca
 */
public class Main {
    
    private static String urlSource = "http://localhost:11002/jackrabbit/server";

    
    /** 
* The main entry point of the example application. 
* 
* @param args command line arguments (ignored) 
* @throws Exception if an error occurs 
*/ 
    public static void main(String[] args) throws Exception 
    { 
        Repository repositorySource = JcrUtils.getRepository(urlSource);
        Session session = repositorySource.login(new SimpleCredentials("us802","ps802".toCharArray()), "default");
        
        Node root = session.getRootNode(); 

        // Store content 
        for(int i = 8; i < 10; i++)
        {    
            System.out.println(i);
            Node hello = root.addNode("h"+i); 
            InsertImage(hello, i);
            session.save();         
        }
        
    }
    
    static void InsertImage(Node hello, int i) throws RepositoryException, FileNotFoundException
    {
            //stream all'immagine
            String filePath = "/home/luca/Scrivania/a.jpg";
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
    
}

