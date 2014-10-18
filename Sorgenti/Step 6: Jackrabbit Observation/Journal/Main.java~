/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.journal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import javax.jcr.Credentials;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Workspace;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventJournal;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.EventListenerIterator;

import javax.jcr.observation.ObservationManager;
import javax.jcr.version.*;
import org.apache.jackrabbit.commons.JcrUtils;
import sun.net.www.MimeTable;


/**
 *
 * @author luca
 */
public class Main {
    
    static Session s_old = null;
    //per ogni evento Add_Node(), aggiunge nodo della sessione del repo vecchio
    //serve per aggiungere / modificare in seguito eventuali proprietà o binary data
    static List<Node> added_nodes = new ArrayList<Node>();
    
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
            s_old = repo.login(sc,workspace);

            ObservationManager omgr = s_old.getWorkspace().getObservationManager();           
            
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

static void Redo_Events(Stack<Event> events, String repo_url, String workspace, String username, String password) throws RepositoryException, IOException
{
    //DEBUG
    //Print_Events(events);
    
    //faccio il login nel repo che deve rifare gli eventi per essere sincronizzato
    Repository repo = JcrUtils.getRepository(repo_url);
    Credentials sc = new SimpleCredentials(username,password.toCharArray());
    Session s = repo.login(sc,workspace);
    
    try
    {
        Event e = events.pop();
        while(true)
        {
        //DEBUG
        System.out.println("Print Event Info");
        Print_Event_Info(e);

            switch(e.getType())
            {
                case Event.NODE_ADDED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Add_Node()");
                        //aggiungo nodo
                        Add_Node(s,e);
                        break;
                    }
                case Event.NODE_REMOVED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Remove_Node()");                        
                        Remove_Node(s, e);
                        break;
                    }
                case Event.NODE_MOVED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Move_Node()");
                        Move_Node(s, e);                                                
                        break;
                    }          
                
                case Event.PROPERTY_ADDED:
                    {
                                                //DEBUG
                        System.out.println("Chiamo Evento Add_Prop()");
                        Add_Property(s, e);
                        break;
                    }
                case Event.PROPERTY_CHANGED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Change_Prop()");                        
                        Change_Property(s, e);
                        break;
                    }
                case Event.PROPERTY_REMOVED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento REmove_Prop()");                        
                        Remove_Property(s, e);
                        break;
                    }
                
                case Event.PERSIST:
                    {
                                                //DEBUG
                        System.out.println("Chiamo Evento Persist() session.save()");
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

static void Add_Node(Session s, Event e) throws RepositoryException, IOException
{
    //parametri: absolute_path, node_type, session

    //controllo prima che il nodo da importare non sia già presente nel repo nuovo
    /*
    potrebbe capitare che nello stesso set di eventi, il vecchio repo abbia aggiunto sia il nodo padre 
    che il nodo figlio, quindi quando faccio l'import ricorsivo del padre ho anche il figlio
    */
    if(s.nodeExists(e.getPath()))
    {
        //se il nodo esiste già non faccio niente
        System.out.println("Salto Add_Node()");
        //lo aggiungo negli added_nodes:
        System.out.println("Lo aggiungo negli added nodes:");
        added_nodes.add(s_old.getNode(e.getPath()));
        return;
    }
    
    //prendo nodo padre, al quale farò poi l'import:
    String adding_node_path = e.getPath();
    String parent_node_path = GetParentNodePath(adding_node_path); //prende il path fino all'ultima sbarra / (senza la sbarra, es /home/luca ritorna /home)
    Node parent_node = s.getNode(parent_node_path);    
    //faccio l'export sempre ricorsivo
    boolean export_recursively = true;
    //prendo nodo da esposrtare nella sessione del cluster vecchio:
    Node to_export = s_old.getNode(e.getPath());
    ByteArrayOutputStream baos = EsportaSource(to_export, export_recursively);
    ImportaDest(parent_node, baos.toByteArray(), s);    
    
    //aggiunge il nodo esportato dalla sessione del repo vecchio alla lista dei nodi aggiunti
    added_nodes.add(to_export);
}

static void Remove_Node(Session s, Event e) throws RepositoryException
{
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
            //richiamo il metodo orderBefore con i 2 parametri letti:
            Node to_order = s.getNode(e.getPath());
            to_order.orderBefore(srcChildRelPath, destChildRelPath);
        }
        catch(Exception ex) //session or workspace move
        {
            String srcAbsPath = info.get("srcAbsPath").toString();
            String destAbsPath = info.get("destAbsPath").toString();                    
            //leggi JCR observation move and order
            s.move(srcAbsPath, destAbsPath);
            //chiami session move con questi 2 parametri nel repo copia
        }        
}


static void Add_Property(Session s, Event e) throws RepositoryException
{
       //controllo se la proprietà viene aggiunta in seguito ad un evento Add_Node o no
    //nel primo caso ho già fatto l'import dal cluster vecchio, quindi ho importato anche la proprietà
    
//nel secondo caso è stata aggiunta una proprietà ad un nodo già esistente
//quindi aggiungo solo la proprietà senza fare l'import completo del nodo
    
    //la proprietà per adesso esiste solo nel repo vecchio
    Property old_p = s_old.getProperty(e.getPath());   
    
    Node old_container = old_p.getParent();
    Node container = s.getNode(old_container.getPath()); //sessione nuova, nodo nuovo repo
    
    //in ogni caso, se la proprietà è di tipo Binary, assegno il valore con il metodo set_property
    //perchè, anche se nel primo caso (il nodo è stato aggiunto nello stesso set di eventi)
    //è stato fatto l'export e l'import senza binary data
    if(old_p.getType() == PropertyType.BINARY)
    {
        //controllo se è multipla
        if(old_p.isMultiple())
        {
            container.setProperty(old_p.getName(), old_p.getValues(), old_p.getType());
        }
        else
        {
            container.setProperty(old_p.getName(), old_p.getValue(), old_p.getType());
        }
        return;
    }    
    
    //controllo se sono nel primo caso
    String path_to_control = old_container.getPath();
    System.out.println("added nodes length = " + added_nodes.size());
    for(Node n:added_nodes)
    {
        String n_path = n.getPath();
System.out.println("CHECK 1st caso: " + n_path + " ==? " + path_to_control);        
        if(n_path.equals(path_to_control))
        {            
            //sono nel primo caso, non aggiungo la proprietà
            System.out.println("Salto Add_Property()");
            return;
        }    
    }
    
//se arrivo qui sono nel secondo caso
   //aggiungo la proprietà nel nodo del repo nuovo, 
    //leggendo il nome e il valore dallo stesso nodo del repo vecchio:

    //aggiungo la proprietà:
    //--controllo se è multipla o no
    if(old_p.isMultiple())
    {
        container.setProperty(old_p.getName(), old_p.getValues(), old_p.getType());
    }
    else
    {
        container.setProperty(old_p.getName(), old_p.getValue(), old_p.getType());
    }    
}

static void Change_Property(Session s, Event e) throws RepositoryException
{
        Property p = s.getProperty(e.getPath());
        Property old_p = s_old.getProperty(e.getPath());
        //controllo se è multipla
        if(old_p.isMultiple())
        {
            p.setValue(old_p.getValues());            
        }
        else
        {
            p.setValue(old_p.getValue());            
        }
        
}

static void Remove_Property(Session s, Event e) throws RepositoryException
{
        Property p = s.getProperty(e.getPath());
        p.remove();        
}

    static ByteArrayOutputStream EsportaSource(Node source, boolean export_recursively) throws RepositoryException, IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        System.out.println("Exporting Node: " + source.getPath()); 
        //true =legge sottonodi ricorsivamente per l'export
        //false = i binary data non vengono salvati in output
        if(export_recursively)
        {           
            System.out.println("------------recursively");
            //true = skip binary
            //false = no recursively, quindi ricorsivo
            s_old.exportSystemView(source.getPath(), baos, true, false);

            //DEBUG FILE XML
            /*
            		FileOutputStream fop = null;
		File file;

		try {
 
			file = new File("/home/luca/Scrivania/out.xml");
			fop = new FileOutputStream(file);
 
			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}
 
                        s_old.exportSystemView(source.getPath(), fop, false, false);

			fop.flush();
			fop.close();
 
			System.out.println("Done");
 
		} catch (IOException e) {
			e.printStackTrace();
		}
            */
        }
        else
        {
            System.out.println("------------not recursively");
            //true = skip binary
            //true = no recursively            
            s_old.exportSystemView(source.getPath(), baos, true, true); 
        }
        
        return baos;
        
    }
//DA TESTARE
    static String GetParentNodePath(String path)
    {
        //es da /home/luca ritorna /home
        String parent_node_path = path.substring(0, path.lastIndexOf('/'));
        //se il path è una stringa vuota, significa che il parent node era la root '/'
        if(parent_node_path.equals(""))
        {
            parent_node_path = "/";
        }    
        return parent_node_path;
    }        
    
    static void ImportaDest(Node dest, byte[] buffer, Session s_new) throws IOException, RepositoryException
    {
        System.out.println("Importing node under " + dest.getPath() + " :");
        
        //ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
                    System.out.println("Info array: bais available = " + bais.available());
            System.out.println("Info array: buffer length = " + buffer.length);
      //  try
       // {
            s_new.importXML(dest.getPath(), bais, ImportUUIDBehavior.IMPORT_UUID_COLLISION_REMOVE_EXISTING);                    
            /*}
        catch(java.lang.ArrayIndexOutOfBoundsException e)
        {
            System.out.println(e.getMessage());
            System.out.println("");
            System.out.println("Info array: bais available = " + bais.available());
            System.out.println("Info array: buffer length = " + buffer.length);
        }
                */
            
    }   

static void Print_Event_Info(Event e) throws RepositoryException
{
    System.out.println(" - Data: " + e.getDate() + "\r\n - Id: " + e.getIdentifier() + "\r\n - Path: " + e.getPath() + "\r\n - Type: " + getEventTypeName(e.getType()) + "\r\n - UserData " + e.getUserData() + "\r\n - UserID: " + e.getUserID() + "\r\n");                
}        
    
}

