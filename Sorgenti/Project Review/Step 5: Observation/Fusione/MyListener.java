/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.cloneasync;

/**
 *
 * @author luca
 */
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.EventIterator;

import java.util.Stack;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.NodeTypeTemplate;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.nodetype.PropertyDefinitionTemplate;
import org.apache.jackrabbit.commons.JcrUtils;
 
public class MyListener implements EventListener
{
    
    public Session s_old = null;
    public Session s_new = null;
    
    static List<String> added_nodes = new ArrayList<String>();
    
public void onEvent(EventIterator events)
{
    System.out.println("MyListener called with " + events.getSize() + " events:");

        try 
        {        
            RegisterNamespaces();
            RegisterNodeTypes();    
        } 
        catch (RepositoryException ex) 
        {
            System.out.println("RepositoryException in Register Nodetypes e Namespaces: " + ex.getMessage());
            System.exit(1);
        }
        
    
    Stack<Event> ordered_events = Reverse_StackEvents(events);
    
    try 
    {
        Redo_Events(ordered_events);
    } 
    catch (RepositoryException ex) 
    {
        System.out.println("Repository Exception on Redo_Events: " + ex.getMessage());
        System.exit(1);
    }
    catch (IOException ex) 
    {
        System.out.println("IOException on Redo_Events: " + ex.getMessage());
        System.exit(1);
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

    private void RegisterNamespaces() throws RepositoryException
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
    
    private void RegisterNodeTypes() throws RepositoryException
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
    private boolean AllSuperTypes(NodeType toCheck, boolean[] flag, NodeType[] vetSource)
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
    
    private void RegisterNode(NodeType toBeRegistered, NodeTypeManager ntmDest) throws RepositoryException
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

    
private void Redo_Events(Stack<Event> events) throws RepositoryException, IOException
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

private void Add_Node(Event e) throws RepositoryException, IOException
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
    
    //TODO: aggiungere direttamente qui le proprietà binarie
    Node added = s_new.getNode(adding_node_path);
    Sync_BinaryData(added);
}

private void Remove_Node(Event e) throws RepositoryException
{
    String removing_node_path = e.getPath();
    try
    {
        Node to_remove = s_new.getNode(removing_node_path);        
        //cancello il nodo anche dalla lista degli added_node o degli incomplete_nodes
        to_remove.remove();
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

private void Move_Node(Event e) throws RepositoryException
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
}


private void Add_Property(Event e) throws RepositoryException
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
            
        //se arrivo qui sono nel secondo caso
           //aggiungo la proprietà nel nodo del repo nuovo, 
            //leggendo il nome e il valore dallo stesso nodo del repo vecchio:
System.out.println("Chiamo Add_Property(), il nodo non è aggiunto in questa sessione");
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

private void Change_Property(Event e) throws RepositoryException
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

private void Remove_Property(Event e) throws RepositoryException
{
    try
    {
        Property p = s_new.getProperty(e.getPath());
        p.remove();                
    }
    catch(javax.jcr.PathNotFoundException pe)
    {
        //la propretà non esiste, non la cancello
        System.out.println("Catch Prop Remove, la proprietà non esiste, non la cancello");
    }    

}


    private ByteArrayOutputStream EsportaSource(Node source, boolean export_recursively) throws RepositoryException, IOException
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
    private String GetParentNodePath(String path)
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
    
    private void ImportaDest(Node dest, byte[] buffer) throws IOException, RepositoryException
    {
        System.out.println("Importing node under " + dest.getPath() + " :");
        
        //ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
                    System.out.println("Info array: bais available = " + bais.available());
            System.out.println("Info array: buffer length = " + buffer.length);

            s_new.importXML(dest.getPath(), bais, ImportUUIDBehavior.IMPORT_UUID_COLLISION_REPLACE_EXISTING);                    
            
    }   

    private void Sync_BinaryData(Node root) throws RepositoryException
{
    System.out.println("Scrivo tutte le proprietà binarie sotto a: " + root.getPath());
    Stack<Node> stackSource = new Stack<Node>();
    stackSource.push(root);
    while(!stackSource.empty())
    {
        Node currentSource = stackSource.pop();
        //scrive tutte le proprietà Binary dal repo vecchio al nuovo per questo nodo:
        //System.out.println("Chiamo WriteBinaryData per il nodo: " + currentSource.getPath());
        
        //salto tutte le prorprietà del nodo root

            Write_BinaryData(currentSource);

        
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

private void Write_BinaryData(Node parent_node) throws RepositoryException
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

private void Print_Event_Info(Event e) throws RepositoryException
{
    System.out.println(" - Data: " + e.getDate() + "\r\n - Id: " + e.getIdentifier() + "\r\n - Path: " + e.getPath() + "\r\n - Type: " + getEventTypeName(e.getType()) + "\r\n - UserData " + e.getUserData() + "\r\n - UserID: " + e.getUserID() + "\r\n");                
}

}
