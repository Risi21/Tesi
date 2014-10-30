package com.luca.migration;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author luca
 */

import java.io.*;
import java.util.*;

//Serve per Repository e Session
import javax.jcr.*;

//serve per JcrUtilities
import org.apache.jackrabbit.commons.*;

import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.NodeTypeTemplate;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.nodetype.PropertyDefinitionTemplate;


public class Main
{
    private static Repository repositorySource = null;
    private static Repository repositoryDest = null;
    
    //queste sono le url delle 2 istanze di jackrabbit nel mio pc locale
    //se non passo i parametri -urlDest e -urlSource rimangono questi valori di default
    private static String urlSource = "http://localhost:8082/server";
    //private static String urlDest = "http://localhost:8084/server";   
    private static String urlDest = "http://localhost:8080/jackrabbit-webapp-2.8.0/server";
    
    private static Session sessionSource = null;
    private static Session sessionDest = null;
    
    private static String workspace = "";
    //utente per fare login nel source
    private static String src_user = "";
    private static String src_user_pwd = "";
    //utente per fare login nel dest
    private static String dest_user = "";
    private static String dest_user_pwd = "";    
    
    public static void main(String[] args) throws Exception 
    {    

        /*
        //connessione server jackrabbit con derby
        repositorySource = JcrUtils.getRepository("http://localhost:8082/server"); 
        sessionSource = repositorySource.login(new SimpleCredentials("admin","admin".toCharArray()), "default");

        //repositoryDest = JcrUtils.getRepository("http://localhost:8080/jackrabbit-webapp-2.8.0/server");
        repositoryDest = JcrUtils.getRepository("http://localhost:8084/server");
        sessionDest = repositoryDest.login(new SimpleCredentials("admin","admin".toCharArray()), workspace);       
        */
        
        CheckArguments(args);
        
        RegisterNamespaces();        
        RegisterNodeTypes();      
        
        try 
        { 
            
            Node rootSource = sessionSource.getRootNode();
            Node rootDest = sessionDest.getRootNode(); 
            
            TransferDB(rootSource.getNode("cms"), rootDest);   
            
            System.out.println("Saving the session..."); 
            sessionDest.save(); 
            System.out.println("done."); 

        } 
        finally 
        { 
            sessionSource.logout(); 
            sessionDest.logout(); 
        }
              
    }

   static void CheckArguments(String[] args) throws RepositoryException
    {
        String workspaceName = "";
        
        for(int i = 0; i < args.length; i++)
        {
            if(args[i].equals("-cw"))
            {
                //crea workspace, quindi mi aspetto come parametro successivo il nome del workspace nuovo
                workspaceName = args[++i];
            }
            if(args[i].equals("-urlDest"))
            {
                //il prossimo parametro è l'url nel quale si fa l'import dal database vechio
                urlDest = args[++i];
            }
            if(args[i].equals("-urlSource"))
            {
                //il prossimo parametro è l'url nel quale si leggono i nodi
                urlSource = args[++i];
            }
            if(args[i].equals("-workspace"))
            {
                //il prossimo parametro è l'url nel quale si leggono i nodi
                workspace = args[++i];
            }
            if(args[i].equals("-src_user"))
            {
                //il prossimo parametro è l'url nel quale si leggono i nodi
                src_user = args[++i];
                src_user_pwd = args[++i];
            }
            if(args[i].equals("-dest_user"))
            {
                //il prossimo parametro è l'url nel quale si leggono i nodi
                dest_user = args[++i];
                dest_user_pwd = args[++i];
            }            
            if(args[i].equals("--help"))
            {
                //il prossimo parametro è l'url nel quale si leggono i nodi
                System.out.println("Parametri disponibili:\r\n\r\n"
                        + "-cw <new workspace name dest>, crea workspace nella destinazione e usa quel workspace per importare i dati\r\n"
                        + "-urlDest <repository dest url>, es: http://localhost:8082/server\r\n"
                        + "-urlSource <repository source url>, es: http://localhost:8080/server\r\n"
                        + "-workspace <workspace dest name>, definisce il workspace nel quale bisogna fare login (se non è definito il -cw)\r\n"
                        + "-src_user <src user> <src password>, utente admin del repository source\r\n"
                        + "-dest_user <dest user> <dest password>, utente admin del repository dest\r\n");
                System.exit(0);
            }            
        }
        
        //le urlDest e urlSource possono essere state cambiare o no, in ogni caso creo la sessione
        
        Repository repositorySource = JcrUtils.getRepository(urlSource); 
        sessionSource = repositorySource.login(new SimpleCredentials(src_user,src_user_pwd.toCharArray()), "default");

        Repository repositoryDest = JcrUtils.getRepository(urlDest);        
        sessionDest = repositoryDest.login(new SimpleCredentials(dest_user,dest_user_pwd.toCharArray()), workspace);                   
        
        //se l'utnte ha creato un nuovo workspace viene creata una sessione per quel workspace
        if(!workspaceName.equals(""))
        {
            CreaWorkspace(workspaceName);
            //aggiorno la sessione di destinazione col nuovo workspace
            sessionDest = repositoryDest.login(new SimpleCredentials(dest_user,dest_user_pwd.toCharArray()), workspaceName);             
        }       
    }
    
    static void CreaWorkspace(String name) throws UnsupportedRepositoryOperationException, RepositoryException
    {
        //creo workspace nuovo
            Workspace w = sessionDest.getWorkspace();
            w.createWorkspace(name); 
            sessionDest.save();
    }    
    
    static void RegisterNamespaces() throws RepositoryException
    {
        NamespaceRegistry nrSource = sessionSource.getWorkspace().getNamespaceRegistry();
        NamespaceRegistry nrDest = sessionDest.getWorkspace().getNamespaceRegistry();
        
        //prendo tutti i prefissi dei namespace registrati nel database sorgente
        String[] prefixSource = nrSource.getPrefixes();        
        
        //prendo tutti i prefissi dei namespace registrati nel database destinatario (quelli presenti di default)
        String[] vetPrefixDest = nrDest.getPrefixes();
        List prefixDest = Arrays.asList(vetPrefixDest);
        
        for(int i = 0; i < prefixSource.length; i++)
        {
            if(!prefixDest.contains(prefixSource[i]))
            {
                //System.out.println("prefisso: " + prefixSource[i] + " url: " + nrSource.getURI(prefixSource[i]));
                nrDest.registerNamespace(prefixSource[i], nrSource.getURI(prefixSource[i]));
            }                
        }         
    }        
    
    static void RegisterNodeTypes() throws RepositoryException
    {
        NodeTypeManager ntmSource = sessionSource.getWorkspace().getNodeTypeManager();
        NodeTypeManager ntmDest = sessionDest.getWorkspace().getNodeTypeManager();
                
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
    
    
    static void TransferDB(Node rootSource, Node rootDest) throws RepositoryException, IOException, InterruptedException
    {       
        //int size = 100000000; //100 MB
        //ByteArrayOutputStream baos = new ByteArrayOutputStream();                 
        
        Stack<Node> stackSource = new Stack<>();
        //Stack<Node> stackDest = new Stack<>();
        Stack<String> stackDest = new Stack<>();
        
        stackSource.push(rootSource);
        stackDest.push(rootDest.getPath());
        
        while(!stackSource.empty())
        {

            Node currentSource = stackSource.pop();
            Node currentDest = sessionDest.getNode(stackDest.pop());
            
            boolean export_recursively = false;
            
            //esporto singolo nodo e lo importo (senza ricorsione per i child nodes)            
            if(currentSource.isNodeType("nt:file"))
            {
                export_recursively = true;
            }
                 
            //se è nt:file facio export con tutti i sotonodi ricorsivamente, così salva anche
            //il child node obbligatorio nt:resource
            ByteArrayOutputStream baos = new ByteArrayOutputStream();                 
            EsportaSource(currentSource, baos, export_recursively);
            ImportaDest(currentDest, baos.toByteArray());
            
            System.out.println("Saving Session Dest"); 
            sessionDest.save();

            //se ho fatto l'import ricorsivamente, salto tutti i sotto nodi
            if(export_recursively)
            {                    
                System.out.println("Writing Binary Data for node: " + currentSource.getPath()); 
                WriteBinaryData(currentSource, currentDest);
                
                System.out.println("Saving Session Dest"); 
                sessionDest.save();                  
                
                continue;
            }                        
            
            //controllo se il nodo corrente ha almeno un nodo figlio 
            if(currentSource.hasNodes())
            {
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
                
                //push in ordine inverso dei nodi
                for(int i = cont - 1; i >= 0; i--)
                {
                    stackSource.push(childnodes[i]);
                    Node newDest = currentDest.getNode(currentSource.getName());
                    stackDest.push(newDest.getPath());
                }    
            }    
            
        }
    }         
    
    static void EsportaSource(Node source, ByteArrayOutputStream baos, boolean export_recursively) throws RepositoryException, IOException
    {
        System.out.println("Exporting Node: " + source.getPath()); 
        //true =legge sottonodi ricorsivamente per l'export
        //false = i binary data non vengono salvati in output
        if(export_recursively)
        {           
            System.out.println("------------recursively");
            //true = skip binary
            //false = no recursively, quindi 
            sessionSource.exportSystemView(source.getPath(), baos, true, false);
        }
        else
        {
            System.out.println("------------not recursively");
            //true = skip binary
            //true = no recursively            
            sessionSource.exportSystemView(source.getPath(), baos, true, true); 
        }
        
        
    }

    static void ImportaDest(Node dest, byte[] buffer) throws IOException, RepositoryException
    {
        System.out.println("Importing node: " + dest.getPath());
        
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
        sessionDest.importXML(dest.getPath(), bais, ImportUUIDBehavior.IMPORT_UUID_COLLISION_REMOVE_EXISTING);        
    }   


    static void WriteBinaryData(Node source, Node dest) throws RepositoryException
    {
        Node dataSource = source.getNode("jcr:content");        
        Node destData = sessionDest.getNode(dataSource.getPath());
        destData.setProperty("jcr:data", dataSource.getProperty("jcr:data").getValue(), PropertyType.BINARY);
    }     
    
}


