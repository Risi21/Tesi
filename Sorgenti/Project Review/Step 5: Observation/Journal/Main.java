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
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.NodeTypeTemplate;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.nodetype.PropertyDefinitionTemplate;
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
    
    //per ogni evento Add_Node(), aggiunge nodo della sessione del repo vecchio
    //serve per aggiungere / modificare in seguito eventuali proprietà o binary data
    static List<String> added_nodes = new ArrayList<String>();
    static List<String> incomplete_nodes = new ArrayList<String>();
    static Session s_old = null;
    static Session s_new = null;
    
    static String src_url = "";
    static String clone_url = "";
    static String db_user = "";
    static String db_pass = "";
    
    public static void main(String[] args) throws Exception 
    {
            CheckArguments(args);
            //repo (cluster) che si vuole sincronizzare
            s_old = Session_Login(src_url, "default", db_user, db_pass);                                    
            s_new = Session_Login(clone_url, "default", db_user, db_pass);
            
            System.out.println("Registro namespaces e nodetypes");
            RegisterNamespaces();        
            RegisterNodeTypes();      
            System.out.println("Done");
            
            Sync_Journal();
}

static void CheckArguments(String[] args)
 {

     for(int i = 0; i < args.length; i++)
     {
         //nome della nuova istanza di tomcat
         if(args[i].equals("-srcUrl"))
         {
             src_url = args[++i];          
         }
         if(args[i].equals("-user"))
         {
             //è uguale, sia per il cluster originale che per il clone
             db_user = args[++i];          
             db_pass = args[++i];          
         }         
         if(args[i].equals("-cloneUrl"))
         {
             clone_url = args[++i];             
         }                        
         if(args[i].equals("--help"))
         {
             System.out.println(""
                     + "\r\nParametri disponibili:\r\n\r\n"
                     + "-srcUrl <url di un nodo attivo del cluster originale>, es: http://localhost:11028/jackrabbit/server/\r\n"
                     + "-cloneUrl <url di un nodo attivo del cluster clone>, es: http://localhost:11028/jackrabbit/server/\r\n"
                     + "-user <username> <password>, credenziali utente per il repository, è uguale sia per l'originale che per il clone\r\n");                
             System.exit(0);
         }              
     }
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

static Session Session_Login(String url, String workspace, String username, String password) throws RepositoryException
{
        Repository repo = JcrUtils.getRepository(url);
        Credentials sc = new SimpleCredentials(username,password.toCharArray());
        Session s = repo.login(sc,workspace);    
        return s;
}        

static EventJournal Read_Journal(Session s_old, int event_filter) throws RepositoryException
{
            ObservationManager omgr = s_old.getWorkspace().getObservationManager();           
            
            // ----- READ THE EVENT JOURNAL -----------------------------------
            return omgr.getEventJournal(event_filter, "/", true, null, null);

}

static Queue< Stack<Event> > Reverse_Events(EventJournal ej, long date)
{
    //fare lo skipTo alla data precedente a quella da cui si vuole partire per leggere tutti gli eventi
    ej.skipTo(date);    
    
    //Stack<Event> output = new Stack<Event>();
    Queue< Stack<Event> > global_set = new LinkedList< Stack<Event> >();
    
        try 
        {                
            //se non ce ne sono altri, va nel catch
            //esempio se lo skipToDate risulta già essere aggiornato all'ultima data va subito nel catch
            //Event e = ej.nextEvent();
            //output.push();                
            Event e;
            while (true) 
            {
                Stack<Event> local_set = new Stack<Event>();
                //ogni gruppo di eventi termina con l'evento PERSIST                
                do
                {
                    //inserisco tutti gli eventi nella coda temporanea
                    e = ej.nextEvent();
                    local_set.push(e);
                }
                while(e.getType() != Event.PERSIST);
                //aggiungo questo gruppo di eventi invertiti nella coda
                global_set.add(local_set);
            }
        } 
        catch (NoSuchElementException ex) 
        {
            //fine eventi
        }    
    
    return global_set;
}              

    
    static void RegisterNamespaces() throws RepositoryException
    {
        NamespaceRegistry nrSource = s_old.getWorkspace().getNamespaceRegistry();
        NamespaceRegistry nrDest = s_new.getWorkspace().getNamespaceRegistry();
        
        //registro dcr:="http://www.day.com/jcr/webdav/1.0" per getInfo()
        //serve per Event.getInfo()...
        nrSource.registerNamespace("dcr", "http://www.day.com/jcr/webdav/1.0");
        nrDest.registerNamespace("dcr", "http://www.day.com/jcr/webdav/1.0");
        
        //prendo tutti i prefissi dei namespace registrati nel database sorgente
        String[] prefixSource = nrSource.getPrefixes();        
        
        //prendo tutti i prefissi dei namespace registrati nel database destinatario (quelli presenti di default)
        String[] vetPrefixDest = nrDest.getPrefixes();
        List prefixDest = Arrays.asList(vetPrefixDest);
                        
        for(int i = 0; i < prefixSource.length; i++)
        {
            System.out.println("prefisso: " + prefixSource[i] + " url: " + nrSource.getURI(prefixSource[i]));
            if(!prefixDest.contains(prefixSource[i]))
            {
                System.out.println("--prefisso: " + prefixSource[i] + " url: " + nrSource.getURI(prefixSource[i]));
                nrDest.registerNamespace(prefixSource[i], nrSource.getURI(prefixSource[i]));
            }                
        }         
    }        
    
    static void RegisterNodeTypes() throws RepositoryException
    {
        NodeTypeManager ntmSource = s_old.getWorkspace().getNodeTypeManager();
        NodeTypeManager ntmDest = s_new.getWorkspace().getNodeTypeManager();
                
        NodeTypeIterator ntiSource = ntmSource.getAllNodeTypes();        
        NodeTypeIterator ntiDest = ntmDest.getAllNodeTypes();        
        
        //riempie la lista con i nodetype già presenti nel database destinazione
        List<String> namesNodeTypeDest = new ArrayList<String>();
        while(ntiDest.hasNext())
        {
            namesNodeTypeDest.add(ntiDest.nextNodeType().getName());
        }                
        
        //fra tutti i nodi del database sorgente, quelli che non sono presenti nel database
        //vecchio vengono aggiunti a questa lista
        List<NodeType> toRegisters = new ArrayList<NodeType>();
        
        while(ntiSource.hasNext())
        {
            NodeType tmp = ntiSource.nextNodeType();        
            if(!namesNodeTypeDest.contains(tmp.getName()))
            {
                toRegisters.add(tmp);
            }            
        }
        
        //numero di nodi da registrare
        int fixedCont = toRegisters.size();
        
        //salvo elementi dell'iteratore in un vettore
        NodeType[] vetToRegisters = new NodeType[fixedCont];  
        
        Iterator<NodeType> vtr = toRegisters.iterator();
        int index = 0;        
        //salva tutti i nodetype dall'iterator al vettore
        while(vtr.hasNext())
        {
            vetToRegisters[index++] = vtr.next();
        }        
        
        
//vettore di flag, se true il rispettivo nodo è già stato registrato, didefault hanno il valore false        
        boolean[] flag = new boolean[fixedCont];
        
//finchè tutti i flag non sono true significa che i nodi non sono stati registrati
//prima registra nodi che non hanno supertypes
//poi registra nodi che hanno tutti i supertypes registrati
//per fare questi controlli, si scorrono i 2 vettori n volte
//quando si registra un nodo, il contatore decrementa e il flag diventa true        
        index = 0;
        int cont = fixedCont;
        while(cont > 0)
        {            
            //se sono arrivato in fondo ai vettori, rincomincio da capo
            if(index == fixedCont)
            {
                index = 0;
            }
            
            //condizione - registro nodo solo se:
            //il flag è false AND
            //non ha supertypes  OR se ha supertypes devono avere flag tutti a true             
            if(flag[index] == false &&
                (vetToRegisters[index].getDeclaredSupertypes().length == 0 
                 || AllSuperTypes(vetToRegisters[index], flag, vetToRegisters) == true))
            {
                RegisterNode(vetToRegisters[index], ntmDest);
                cont--;
                flag[index] = true;
            }
            
            index++;
        }
        
    }             
    
    //N.B. si può migliorare l'efficienza usando un oggetto composto, che contiene
    //(NodeType, boolean) e fare poi il vettore di questo oggetto composto
    static boolean AllSuperTypes(NodeType toCheck, boolean[] flag, NodeType[] vetSource)
    {
        //controllo se tutti i superTypes hanno flag = a true
        NodeType[] st = toCheck.getDeclaredSupertypes();
        //per ogni nodo supertipo devo controllare se il rispettivo flag è true
        //devo prima trovare l'indice del vettore flag, confrontando i nomi del nodetype
        for(int i = 0; i < st.length; i++)
        {
            for(int k = 0; k < vetSource.length; k++)
            {
                if(vetSource[k].getName().equals(st[i].getName()))
                {
                    //controllo flag
                    if(!flag[k])
                    {
                        //devono essere tutti true i flag dei supertypes
                        return false;
                    }    
                }    
            }    
        }    
        //tutti i flag sono true
        return true;
    }       
    
    static void RegisterNode(NodeType toBeRegistered, NodeTypeManager ntmDest) throws RepositoryException
    {
        System.out.println("creazione nodo: " + toBeRegistered.getName());

        //crea il template che sarà poi da registrare nel database nuovo
        NodeTypeTemplate nodeTemplate = ntmDest.createNodeTypeTemplate();                

        nodeTemplate.setName(toBeRegistered.getName());
        nodeTemplate.setAbstract(toBeRegistered.isAbstract());
        nodeTemplate.setMixin(toBeRegistered.isMixin());
        nodeTemplate.setQueryable(toBeRegistered.isQueryable());
        nodeTemplate.setOrderableChildNodes(toBeRegistered.hasOrderableChildNodes());
        nodeTemplate.setPrimaryItemName(toBeRegistered.getPrimaryItemName());

        //setto i supertipi del nodo
        NodeType[] superTypes = toBeRegistered.getDeclaredSupertypes();
        String[] superTypesNames = new String[superTypes.length];
        if(superTypes.length > 0)
        {
            System.out.println("---Supertypes: ");
            for(int i = 0; i < superTypes.length; i++)
            {
                System.out.println("------" + superTypes[i].getName());                        
                superTypesNames[i] = superTypes[i].getName();
            }            
        }
        //setto i supertipi nel template:
        nodeTemplate.setDeclaredSuperTypeNames(superTypesNames);

        //proprietà
        List propertyList = nodeTemplate.getPropertyDefinitionTemplates();  
        PropertyDefinition[] prop = toBeRegistered.getDeclaredPropertyDefinitions();
        if(prop.length > 0)
        {
            System.out.println("---Proprietà: ");
            for(int i = 0; i < prop.length; i++)
            {
                System.out.println("------" + prop[i].getName() + " tipo: " + PropertyType.nameFromValue(prop[i].getRequiredType()) + " mandagtory: " + prop[i].isMandatory() + " multiple: " + prop[i].isMultiple() + " protected: " + prop[i].isProtected() + " autocreated: " + prop[i].isAutoCreated() + " fulltextsearch: " + prop[i].isFullTextSearchable());
                //setto template proprietà
                PropertyDefinitionTemplate propertyTemplate = ntmDest.createPropertyDefinitionTemplate();  

                propertyTemplate.setAutoCreated(prop[i].isAutoCreated());
                propertyTemplate.setFullTextSearchable(prop[i].isFullTextSearchable());
                propertyTemplate.setMandatory(prop[i].isMandatory());
                propertyTemplate.setMultiple(prop[i].isMultiple());
                propertyTemplate.setName(prop[i].getName());
                propertyTemplate.setProtected(prop[i].isProtected());
                propertyTemplate.setQueryOrderable(prop[i].isQueryOrderable());
                propertyTemplate.setOnParentVersion(prop[i].getOnParentVersion());
                propertyTemplate.setRequiredType(prop[i].getRequiredType());
                propertyTemplate.setAvailableQueryOperators(prop[i].getAvailableQueryOperators());
                propertyTemplate.setValueConstraints(prop[i].getValueConstraints());
                propertyTemplate.setDefaultValues(prop[i].getDefaultValues());

                propertyList.add(propertyTemplate);
            }            
        } 
        //registro il nododal template
        ntmDest.registerNodeType(nodeTemplate, true);        
    }    


static void Sync_Journal() throws RepositoryException, IOException
{
   int event_filter = Event.NODE_ADDED | Event.PROPERTY_ADDED | Event.NODE_REMOVED | Event.NODE_MOVED | Event.PROPERTY_REMOVED | Event.PROPERTY_CHANGED | Event.PERSIST;            
    //legge journal del repo (cluster) identificato dall'url
    EventJournal ej = Read_Journal(s_old, event_filter);
    
    //legge la data dell'ultimo evento che si è verificato nel cluster clonato
    //nel tempo intercorso tra il clonaggio e la verifica del journal, potrebbe essere cambiato il journal
    long date = Get_Last_Date(s_new);
    
    //ogni gruppo di eventi è formato dagli eventi base e finisce col PERSIST (session.save() )
    //il gruppo di eventi rimane ordinato ma all'interno gli eventi sono invertiti
    //quindi il primo evento di ogni gruppo sarà il PERSIST
    Queue< Stack<Event> > event_sets = Reverse_Events(ej, date); 
    
    while(!event_sets.isEmpty())
    {
        Redo_Events(event_sets.remove());
    }
    
    //adesso per ogni incomplete_node faccio export_import
    //solo se il nodo esiste nel repo vecchio
    //infatti potrebbero essere rimasti in questa lista dei nodi incompleti figli di altri nodi
    //che sono stati cancellati nel repo, ma non dalla lista
    //System.out.println("numero di nodi incompleti: " + incomplete_nodes.size());
    boolean export_recursively = true;
    Iterator<String> itn = incomplete_nodes.iterator();
    while(itn.hasNext())
    {
        Node incomplete = s_new.getNode(itn.next());

        //se c'è un nodo clone con lo stesso nome e che finisce quindi con le [], è solo per debug
        String tmp = "";
        String incomplete_path = "";
        //DEBUG
        try
        {
            tmp = incomplete.getPath();
            incomplete_path = tmp;                    
        }
        catch(javax.jcr.InvalidItemStateException ie)
        {
            //se sono qui è perchè ho cancellato il nodo incompleto prima di fare l'export import di un altro nodo
            //in questo caso passo al nodo incompleto successivo
            System.out.println("se sono qui è perchè ho cancellato il nodo incompleto prima di fare l'export import di un altro nodo"
                    + "in questo caso passo al nodo incompleto successivo");
            System.out.println("--incomplete: " + incomplete );
            continue;
        }    

        System.out.println("Faccio export import del nodo: " + incomplete_path);

//controllo se il nodo esiste nel repo vecchio:
        if(s_old.nodeExists(incomplete_path))
        {
            System.out.println("--Il nodo esiste nel repo vecchio");
            //potrebbe capitare di aveve un nodo incomplete, che esiste nel repo vecchio,
            //ma che nel repo nuovo non esiste il padre??? DA VERIFICARE
            //---
            //faccio export_import senza binary data
            Node source = s_old.getNode(incomplete_path);
            String parent_node_path = GetParentNodePath(incomplete_path); //prende il path fino all'ultima sbarra / (senza la sbarra, es /home/luca ritorna /home)            
            Node parent_node = s_new.getNode(parent_node_path);                        

            //cancello il nodo che verrà poi esportato dal repo vecchio e aggiunto nel padre del repo nuovo                        
            System.out.println("cancello nodo " + incomplete_path);
            try
            {
                s_new.removeItem(incomplete_path);
                //se il save genera l'eccezione...
                s_new.save();
            }
            catch(javax.jcr.nodetype.ConstraintViolationException cve)
            {
                //se finisco in questo catch è perchè il nodo non si può cancellare, altrimenti
                //violo la definizione del tipo di nodo che dice che deve avere questo figlio obbligatorio
                System.out.println("\r\nNon cancello nodo xkè è stata generata javax.jcr.nodetype.ConstraintViolationException \r\n");
                System.out.println("\r\nRitorna la sessione allo stato consistente con il refresh()");
                s_new.refresh(false);
//cve.printStackTrace();
                System.out.println("\r\n");                
            //    Node dfssf = s_new.getNode(incomplete_path);
             //   System.out.println("figli = " + dfssf.getNodes().getSize());                
                continue;
            }    
                        
            //System.out.println("cancello nodo " + to_delete.getPath());
            
//Node nn = s_new.getNode(incomplete_path);
            ByteArrayOutputStream baos = EsportaSource(source, export_recursively);
            ImportaDest(parent_node, baos.toByteArray()); 
            System.out.println("Salvo sessione dopo cancellazione import export");
            s_new.save();
            //ImportaDest(nn, baos.toByteArray()); 
            //poi controllo, per ogni nodo importato, se aveva una proprietà Binary
            //per ogni proprietà Binary, chiamo il metodo WriteBinaryData()
            //leggo quindi tutto il sottoaolbero del nodo importato
            Sync_BinaryData(parent_node);
            s_new.save();
        }    
    }
    System.out.println("session save ");
    s_new.save();
    
}        

static long Get_Last_Date(Session s) throws RepositoryException
{
   int event_filter = Event.NODE_ADDED | Event.PROPERTY_ADDED | Event.NODE_REMOVED | Event.NODE_MOVED | Event.PROPERTY_REMOVED | Event.PROPERTY_CHANGED | Event.PERSIST;            
    
    ObservationManager omgr = s.getWorkspace().getObservationManager();           

        // ----- READ THE EVENT JOURNAL -----------------------------------
        EventJournal ej = omgr.getEventJournal(event_filter, "/", true, null, null);
                
        Event e = null;
        while(ej.hasNext())
        {
            e = ej.nextEvent();            
        }    
        
        return e.getDate();        
}        

static void Redo_Events(Stack<Event> events) throws RepositoryException, IOException
{
   
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
                        Add_Node(e);
                        break;
                    }
                case Event.NODE_REMOVED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Remove_Node()");                        
                        Remove_Node(e);
                        break;
                    }
                case Event.NODE_MOVED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Move_Node()");
                        Move_Node(e);                                                
                        break;
                    }          
                
                case Event.PROPERTY_ADDED:
                    {
                                                //DEBUG
                        System.out.println("Chiamo Evento Add_Prop()");
                        Add_Property(e);
                        break;
                    }
                case Event.PROPERTY_CHANGED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento Change_Prop()");                        
                        Change_Property(e);
                        break;
                    }
                case Event.PROPERTY_REMOVED:
                    {
                        //DEBUG
                        System.out.println("Chiamo Evento REmove_Prop()");                        
                        Remove_Property(e);
                        break;
                    }
                
                case Event.PERSIST:
                    {
                                                //DEBUG
                        System.out.println("Chiamo Evento Persist() session.save()");
//inizia un'altra serie di eventi, quindi faccio il save per salvare gli eventi già sincronizzati:
                        s_new.save();
                        //cancello la lista degli added_nodes, altrimenti se dopo un session.save c'è
                        //un Add-Property di un nodo aggiunto nella sessione prima, non mi aggiunge
                        //la proprietà nuova
                        added_nodes.clear();
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
        s_new.save();
    }
        
    
}        

static void Sync_BinaryData(Node root) throws RepositoryException
{
    System.out.println("Scrivo tutte le proprietà binarie sotto a: " + root.getPath());
    Stack<Node> stackSource = new Stack<Node>();
    stackSource.push(root);
    boolean first = true;
    while(!stackSource.empty())
    {
        Node currentSource = stackSource.pop();
        //scrive tutte le proprietà Binary dal repo vecchio al nuovo per questo nodo:
        //System.out.println("Chiamo WriteBinaryData per il nodo: " + currentSource.getPath());
        
        //salto tutte le prorprietà del nodo root
        if(!first)
        {
            Write_BinaryData(currentSource);
        }                    
        first = false;
        
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

static void Write_BinaryData(Node parent_node) throws RepositoryException
{
//per ogni proprietà controllo se è di tipo Binary
    PropertyIterator pi = parent_node.getProperties();    
    while(pi.hasNext())
    {
        Property p = pi.nextProperty();
        //System.out.println("--WriteBinaryData prop: " + p.getPath());
        if(p.getType() == PropertyType.BINARY)
        {
            //System.out.println("----è binaria");
            //trovo proprietà del repo vecchio
            
            String property_path = p.getPath();
            //String property_path = Rimuovi_Quadre(tmp);
            //DEBUG
            //se vedo le [] le elimino
            Property p_old = s_old.getProperty(property_path);
            if(p.isMultiple())
            {
                p.setValue(p_old.getValues());
            }
            else
            {
                p.setValue(p_old.getBinary());
            }                
        }    
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

static void Add_Node(Event e) throws RepositoryException, IOException
{
    //parametri: absolute_path, node_type, session
    String adding_node_path = e.getPath();
    
    //controllo prima che il nodo da importare non sia già presente nel repo nuovo
    /*
    potrebbe capitare che nello stesso set di eventi, il vecchio repo abbia aggiunto sia il nodo padre 
    che il nodo figlio, quindi quando faccio l'import ricorsivo del padre ho anche il figlio
    */
    if(s_new.nodeExists(adding_node_path))
    {
    System.out.println("Esiste già nel repo nuovo");    
    //se il nodo esiste già non faccio niente
        System.out.println("Salto Add_Node()");
        //lo aggiungo negli added_nodes:
        System.out.println("Lo aggiungo negli added nodes:");
        //added_nodes.add(s_new.getNode(adding_node_path));
        added_nodes.add(adding_node_path);
        return;
    }
    else
    {
        //se non esiste, può essere per 2 motivi:
        //1) il nodo è stato aggiunto nuovo completamente
        //2) è stato aggiunto il nodo padre, nel futuro questo nodo verrà spostato o eliminato nel repo vecchio
        //quindi quando aggiungi il padre con export import non aggiungi il figlio
      
    }    
    
        
    //se il nodo non esiste più nel repo vecchio, vuol dire che dopo è stato fatto un NODE_REMOVED
    //o un NODE_MOVED, quindi lo aggingo solo con il nome e lo metto nella lista degli incompleti
    //il padre del nodo nel repo nuovo esiste perchè tutti i nodi incompleti sono aggiunti
    //in questo caso si aggiungono anche tutti i figli dei nodi incompleti (etichettando incompleti anche loro)
    //perchè se dopo un PERSIST c'è un MOVE di un nodo figlio incompleto, da PathNotFoundException
    if(!s_old.nodeExists(adding_node_path))
    {
        JcrUtils.getOrCreateByPath(adding_node_path, null, s_new);
        //rileggo nodo aggiuntocon l'istruzione precedente
        //Node incomplete = s_new.getNode(adding_node_path);
        //incomplete_nodes.add(incomplete);
        incomplete_nodes.add(adding_node_path);
        System.out.println("Add_node() sono nel caso in cui nel repo vecchio non esiste più il nodo da aggiungere, perchè"
                + "è stato fatto dopo un Move o un REMOVE");        
        System.out.println("Aggiungo nodo " + adding_node_path + " alla liste degli incompleti");        
        return;
    }        
    
    
    //se sono qui, il nodo esiste ancoa nel repo vecchio e non è il figlio di un altro nodo a cui era stato
    //fatto in precedenza un export_import
    //---
    //prendo nodo padre, al quale farò poi l'import:
    String parent_node_path = GetParentNodePath(adding_node_path); //prende il path fino all'ultima sbarra / (senza la sbarra, es /home/luca ritorna /home)
    Node parent_node = s_new.getNode(parent_node_path);    
    //faccio l'export sempre ricorsivo
    boolean export_recursively = true;
    //prendo nodo da esposrtare nella sessione del cluster vecchio:
    Node to_export = s_old.getNode(adding_node_path);
    ByteArrayOutputStream baos = EsportaSource(to_export, export_recursively);
    ImportaDest(parent_node, baos.toByteArray());    
    
    //aggiunge il nodo importato alla lista dei nodi aggiunti
    //added_nodes.add(s_new.getNode(adding_node_path));
    added_nodes.add(adding_node_path);
}

static void Remove_Node(Event e) throws RepositoryException
{
    String removing_node_path = e.getPath();
    try
    {
        Node to_remove = s_new.getNode(removing_node_path);        
        //cancello il nodo anche dalla lista degli added_node o degli incomplete_nodes
        boolean eliminated = false; 
        eliminated = added_nodes.remove(to_remove);
        if(eliminated)
        {
            System.out.println("Nodo Eliminato Da Added_Nodes");
        }    
        eliminated = incomplete_nodes.remove(to_remove);
        if(eliminated)
        {
            System.out.println("Nodo Eliminato Da Added_Nodes");
        }        
        to_remove.remove();
        System.out.println("Nodo Rimosso dal repo nuovo");
        //N.B
        //non elimino dalle liste tutti i sotto nodi del nodo eliminato, perchè
        //non mi serve, in quanto
        //added_nodes serve solo per saltare l'add_Property
        //incomplete_nodes serve per saltare ADD_Property e per fare alla fine di Redo_Events tutti gli export_import
        //solo se il nodo incompleto esiste nel repo vecchio
    }
    catch(javax.jcr.PathNotFoundException pe)
    {
            //se capita qui è perchè prima ha fatto l'evento NODE_MOVED,
        //il quale dopo genera l'evento NODE_REMOVED
        //non trova il nodo perchè l'ha già spostato
        //quindi non faccio niente
        System.out.println("Remove_Node() sono finito nel catch perchè il nodo da rimuovere non esiste più,"
                + "probabilmente xkè prima è stato fatto un Node_Moved()");
        return;
    }

}

static void Move_Node(Event e) throws RepositoryException
{
        //stampo informazioni aggiuntive:                
        Map info = e.getInfo();

        //DEBUG
        System.out.println(info);
        System.out.println(e);
        
        //controllo se è stato generato da Node.orderBefore o no
        //in ogni caso i 2 parametri letti sono gli stessi parametri di input che hanno causato l'evento
        
        String src_path = "";
        String dest_path = "";
        try
        {
            src_path = info.get("dcr:srcChildRelPath").toString();
            dest_path = info.get("dcr:destChildRelPath").toString();
            //richiamo il metodo orderBefore con i 2 parametri letti:
            Node to_order = s_new.getNode(e.getPath());
            to_order.orderBefore(src_path, dest_path);
        }
        catch(Exception ex) //session or workspace move
        {
            src_path = info.get("dcr:srcAbsPath").toString();
            dest_path = info.get("dcr:destAbsPath").toString();                    
            //leggi JCR observation move and order
            
            //N.B.
            //prima di fare la move() devo controllare se il nodo destinazione esiste già
            //se esiste già e faccio move mi crea un alias con [2] alla fine del nome del nodo
            //es:
            //prima fai NODE_ADDED di /img che ha come figlio /img/b
            //fai export import xkè il nodo /img esiste ancora nel repo vecchio
            //poi dopo fai una move di: /a/b/c/d in /img/b
            //prima di fare la move devo quindi cancellare il nodo destinazione, 
            //in questo caso cancello /img/b
            if(s_new.nodeExists(dest_path))
            {
                //lo cancello
                s_new.removeItem(dest_path);
            }    
            
            s_new.move(src_path, dest_path);
            //chiami session move con questi 2 parametri nel repo copia
        }
        //controllo se il src_path era un nodo incompleto, se lo era lo cambio anche lì
        for(int i = 0; i < incomplete_nodes.size(); i++)
        {
            String s = incomplete_nodes.get(i);
            if(s.equals(src_path))
            {
                //cambio la radice del nodo incompleto
                    System.out.println("Cambio la radice al nodo incompleto " + src_path + " in " + dest_path);
                    incomplete_nodes.set(i, dest_path);
                    //cambio la radice anche a tutti i nodi figli del nodo incompleto
                    System.out.println("cambio la radice anche a tutti i nodi figli del nodo incompleto");
                    //String old_radice = Get_Radice(src_path); //es /a/b diventa /a/d, radice = /a/b
                    //String new_radice = Get_Radice(dest_path);
                    String old_radice = src_path;
                    String new_radice = dest_path;
                    //per ogni nodo figlio nella lista degli incompleti:
                    //devo scorrere tutta la lista un' altra volta, e vedere se ogni path contiene la radice
                    //e sostituirla con la nuova
                    for(int k = 0; k < incomplete_nodes.size(); k++)
                    {
                        String nodo = incomplete_nodes.get(k);
                        if(Ha_Radice(nodo, old_radice))
                        {
                            String nodo_nuovo = Cambia_Radice(nodo, old_radice, new_radice);
                            //aggiorno nodo figlio con la nuova radice
                            incomplete_nodes.set(k, nodo_nuovo);
                            System.out.println("--cambio nodo figlio " + nodo + " in " + nodo_nuovo);                            
                        }    
                    }    
            }
        }    
}


static void Add_Property(Event e) throws RepositoryException
{
       //controllo se la proprietà viene aggiunta in seguito ad un evento Add_Node o no
    //nel primo caso ho già fatto l'import dal cluster vecchio, quindi ho importato anche la proprietà
    
//nel secondo caso è stata aggiunta una proprietà ad un nodo già esistente
//quindi aggiungo solo la proprietà senza fare l'import completo del nodo
    
    String adding_property_path = e.getPath();
    
    //se la proprietà non esiste nel repo vecchio,
    //significa che dopo un altro evento o l'ha spostata o l'ha cancellata,
    //quindi si aggiunge alla lista dei nodi incompleti il nodo
    //alla fine di Redo_Events quindi faccio export_import per avere il nodo sincronizzato
    
    try
    {
            //la proprietà per adesso esiste solo nel repo vecchio
        //potrebbe generare PathNotFoundException
            Property old_p = s_old.getProperty(adding_property_path);   

            Node old_container = old_p.getParent();
            Node container = s_new.getNode(old_container.getPath()); //sessione nuova, nodo nuovo repo

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
            for(String path:added_nodes)
            {
                //String n_path = n.getPath();
                System.out.println("CHECK 1st caso: " + path + " ==? " + path_to_control);        
                if(path.equals(path_to_control))
                {            
                    //sono nel primo caso, non aggiungo la proprietà
                    System.out.println("Salto Add_Property() - added_nodes");
                    return;
                }    
            }
            //stessa cosa per gli incomplete_nodes
            for(String path:incomplete_nodes)
            {
                //String n_path = n.getPath();
                System.out.println("CHECK 1st caso: " + path + " ==? " + path_to_control);        
                if(path.equals(path_to_control))
                {            
                    //sono nel primo caso, non aggiungo la proprietà
                    System.out.println("Salto Add_Property() - incomplete-nodes");
                    return;
                }    
            }
            
        //se arrivo qui sono nel secondo caso
           //aggiungo la proprietà nel nodo del repo nuovo, 
            //leggendo il nome e il valore dallo stesso nodo del repo vecchio:
System.out.println("Chiamo Add_Property(), il nodo non è ne incompleto, ne aggiunto in questa sessione");
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
    catch(PathNotFoundException pe) //la proprietà non esiste nel nodo del repo vecchio, sarà cancellata in futuro
    {
        //aggiungo il nodo contenitore della proprietà alla lista dei nodi incompleti

        String parent_node = GetParentNodePath(adding_property_path);
            
        //Node container = s_new.getNode(parent_node);
        //aggiungo il nodo alla lista degli incompleti solo se non è già aggiunto alla lista degli incompleti
        if(!Is_In_Incomplete_Nodes(parent_node))
        {
            incomplete_nodes.add(parent_node);            
            System.out.println("Add_Property Catch PathNotFoundException:"
                + "\r\naggiungo il nodo contenitore della proprietà alla lista dei nodi incompleti"
                    + "\r\n nodo = " + parent_node);            
        }
        else
        {
            System.out.println("Era già in incomplete_Nodes, non lo riaggiungo");
        }    
    }        
}

static void Change_Property(Event e) throws RepositoryException
{
        //la proprietà esiste sicuramente nel repo nuovo perchè rifa tutti gli eventi
    //nel repo vecchio invece, la proprietà potrebbe non esistere più perchè il nodo
    //a cui fa riferimento potrebbe essere stato spostato.
    //NON si può usare l'id del nodo come riferimento perchè dopo un move può cambiare
    //(dipende dal repository, mi attengo alle specifiche jcr)
    
    //TODONT, FARE METODO GET_ITEM_NAME (dopo l'ultima /)
    //RENDERE LA PROPRIETÀ INCOMPLETA
    //SE SI SPOSTA IL NODO, DEVO SAPERE CHE HA UNA PROPRIETÀ INCOMPLETA
    //ALLA FINE DI REDO_EVENTS LEGGERÒ POI IL VALORE DELLA PROPRIETÀ
    String changing_property_path = e.getPath();
    
    try
    {
        Property p = s_new.getProperty(e.getPath());
        Property old_p = s_old.getProperty(e.getPath());
        //controllo se è multipla
        if(old_p.isMultiple())
        {
            p.setValue(old_p.getValues());            
        }
        else
        {
            if(p.getType() == PropertyType.BINARY)
            {
                p.setValue(old_p.getBinary());            
            }
            else
            {
                p.setValue(old_p.getValue());            
            }    
        }        
    }
    catch(PathNotFoundException pe)
    {
        String parent_node = GetParentNodePath(changing_property_path);
        if(!Is_In_Incomplete_Nodes(parent_node))
        {
            incomplete_nodes.add(parent_node);            
            System.out.println("Add_Property Catch PathNotFoundException:"
                + "\r\naggiungo il nodo contenitore della proprietà alla lista dei nodi incompleti"
                    + "\r\n nodo = " + parent_node);            
        }
        else
        {
            System.out.println("Era già in incomplete_Nodes, non lo riaggiungo");
        }
    }                
}

static void Remove_Property(Event e) throws RepositoryException
{
    try
    {
        Property p = s_new.getProperty(e.getPath());
        p.remove();                
    }
    catch(javax.jcr.PathNotFoundException pe)
    {
        //la propretà non esiste, non la cancello
    }    

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
    
    static void ImportaDest(Node dest, byte[] buffer) throws IOException, RepositoryException
    {
        System.out.println("Importing node under " + dest.getPath() + " :");
        
        //ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
                    System.out.println("Info array: bais available = " + bais.available());
            System.out.println("Info array: buffer length = " + buffer.length);

            s_new.importXML(dest.getPath(), bais, ImportUUIDBehavior.IMPORT_UUID_COLLISION_REPLACE_EXISTING);                    
            
    }   

static void Print_Event_Info(Event e) throws RepositoryException
{
    System.out.println(" - Data: " + e.getDate() + "\r\n - Id: " + e.getIdentifier() + "\r\n - Path: " + e.getPath() + "\r\n - Type: " + getEventTypeName(e.getType()) + "\r\n - UserData " + e.getUserData() + "\r\n - UserID: " + e.getUserID() + "\r\n");                
}        

static boolean Is_In_Incomplete_Nodes(String path) throws RepositoryException
{
    for(String incomplete:incomplete_nodes)
    {
        if(incomplete.equals(path))
        {
            return true;
        }    
    }
    return false;
}        

//rimuove le quadre in tuti inodi del percorso
static String Rimuovi_Quadre(String path)
{
    String output = "";
    for(int i = 0; i < path.length(); i++)
    {
        char c = path.charAt(i);
        if(c == '[')
        {
            i += 2;            
        }
        else
        {
            output += c;
        }    
    }
    return output;
}             

static String Cambia_Radice(String path, String old_radice, String new_radice)
{
    //do per scontato che la old_radice sia la radice vera del path
    String nome = path.substring(old_radice.length(), path.length());
    String new_path = new_radice + nome;
    return new_path;
}

static boolean Ha_Radice(String path, String radice)
{
    //controllo carattere per carattere, se i primi caratteri di path sono gli stessi di tutti i caratteri di radice
    //path.length > radice.length, altrimenti è false
    int path_length = path.length();
    int radice_length = radice.length();
    if(path_length < radice_length)
    {
        return false;
    }    

    //controllo carattere per carattere
    for(int i = 0; i < radice_length; i++)
    {
        char p = path.charAt(i);
        char r = radice.charAt(i);
        if(p != r)
        {
            return false;
        }    
    }    

    return true;
}

}

