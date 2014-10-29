/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.readfulljr;

import java.util.Stack;
import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.*;
import org.apache.jackrabbit.commons.JcrUtils;

/**
 *
 * @author luca
 */
public class Main {
    
    static Session s = null;
    
    static String url = "";
    static String db_user = "";
    static String db_pass = "";
    static String workspace = "default";
    
    public static void main(String[] args) throws Exception 
    {
            //repo (cluster) che si vuole sincronizzare
        /*
            String url = "http://localhost:11028/jackrabbit/server/";
            String workspace = "default";
            String username = "uas4";
            String password = "pas4";
        */
        CheckArguments(args);
        
            s = Session_Login(url, workspace, db_user, db_pass);            
            
            Read_All_Subgraph(s.getRootNode());
            
    }    

   static void CheckArguments(String[] args)
{

    for(int i = 0; i < args.length; i++)
    {
        //nome della nuova istanza di tomcat
        if(args[i].equals("-url"))
        {
          url = args[++i];  
        }
        if(args[i].equals("-workspace"))
        {
            workspace = args[++i];
        }
        if(args[i].equals("-dbuser"))
        {
            db_user = args[++i];
            db_pass = args[++i];
        }
        if(args[i].equals("--help"))
        {
            System.out.println("Opzioni disponibili:\r\n"
                    + "-url <url>, es: http://localhost:11028/jackrabbit/server/\r\n"
                    + "-workspace <name>, di default è default\r\n"
                    + "-dbuser <username> <password>");
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

static void Read_All_Subgraph(Node root) throws RepositoryException
{        
    Stack<Node> stackSource = new Stack<Node>();
    stackSource.push(root);
    boolean first = true;
    while(!stackSource.empty())
    {
        Node currentSource = stackSource.pop();

        //Skip the virtual (and large!) jcr:system subtree 
        if (currentSource.getName().equals("jcr:system") || currentSource.getName().equals("rep:policy")) 
        {
            continue;
        } 
        
        System.out.println("\r\nNode: " + currentSource.getPath());
        Print_Properties(currentSource);        
        
        //controllo se il nodo corrente ha almeno un nodo figlio 
        if(currentSource.hasNodes())
        {
            //System.out.println("----ha nodi");
            //aggiunge allo stack tutti i suoi figli nodi di primo livello
            NodeIterator ni = currentSource.getNodes();
            int cont = 0;
            Node[] childnodes = new Node[(int)ni.getSize()];

            while(ni.hasNext())
            {
                //per riscrivere i nodi nello stesso ordine del server originale,
                //faccio la push() nello stack in ordine inverso, così le future pop()
                //prenderanno per primi i primi nodi
                childnodes[cont++] = ni.nextNode();                                      
            }
            //System.out.println("----sono qui, cont: " + cont);
            //push in ordine inverso dei nodi
            for(int i = cont - 1; i >= 0; i--)
            {                
                stackSource.push(childnodes[i]);
            }    
        }
        /*else
        {
            System.out.println("non ha nodi");
        }*/    
    }
    
}

static void Print_Properties(Node n) throws RepositoryException
{
    PropertyIterator pi = n.getProperties();
    while(pi.hasNext())
    {
        Property p = pi.nextProperty();
        //non stampa proprietà binarie
        if(p.getType() == PropertyType.BINARY)
        {
            System.out.println("--" + p.getName() + " è binaria, non stampo il valore");
        }
        else
        {
            if(p.isMultiple())
            {
                System.out.println("--" + p.getName() + " è multivalore:");
                Value [] v = p.getValues();
                for(int i = 0; i < v.length; i++)
                {
                    System.out.println("----Valore" + i + " = " + v[i]);
                }    
            }            
            else
            {
                System.out.println("--" + p.getName() + " = " + p.getString());
            }                            
        }                    
    }    
}        

}

