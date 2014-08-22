/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.luca.newrepo;

import java.io.*;
import java.util.*;

import java.sql.*; //mysql
import java.nio.file.*; //copiare .war file in tomcat webapps

//Serve per Repository e Session
import javax.jcr.*;

import org.apache.jackrabbit.api.security.user.*;
import org.apache.jackrabbit.api.*;
import org.apache.jackrabbit.core.*;
import org.apache.jackrabbit.core.config.*;

/**
 *
 * @author luca
 */
public class Main {
    
    //static String url = "http://localhost:8080/jackrabbit-webapp-2.8.0/server";
    
//variabili per creare nuovo MySql DB
    	static String url = "localhost:3306";
    	static String root = "root"; //root per login in mysql per creare database e utenti
    	static String rootpwd = "rootpwd";
	static String db_name = "";
    	static String user = "";  //username dell'admin, sia  mysql che jackrabbit
	static String userpwd = ""; //password dell'admin
    
    //variabili per copiare il .war di jackrabbit in tomcat
    static String pathTo = ""; //percorso relativo alla cartella in cui si copia + il nome del file .war
    static String pathToDir = ""; //percorso alla cartella senza file .war
    static String pathFrom = "";  //nel percorso comprende il file  

    //variabili per creare nuovo repository
    static String pathRepo = ""; //percorso (/cartella/nomeRepo) che contiene i repository (percorso a una directory)
    static String tomcat = ""; //nome della versione di tomcat in uso, es tomcat7   
    
    //variabili per gestione utenti del nuovo repository
    static String newAdminPassword = "";
    
    public static void main(String[] args) throws Exception 
    {
        //Repository repository = JcrUtils.getRepository(url); 
        CheckArguments(args);                
        
        CreateNewRepo();
        
    }
    
   static void CheckArguments(String[] args)
    {
        
        for(int i = 0; i < args.length; i++)
        {
            //url del server mysql
            if(args[i].equals("-url"))
          {
                url = args[++i];
            }
            //utente per loggarsi su mysql (meglio se root)
            if(args[i].equals("-login"))
            {
                root = args[++i];
		rootpwd = args[++i];
            }
            //nome del nuovo database
            if(args[i].equals("-db"))
            {
                db_name = args[++i];
            }
            //nome del nuovo utente che avrà tutti i privilegi sul nuovo database creato
            if(args[i].equals("-user"))
            {
                user = args[++i];
                userpwd = args[++i];
            }
            if(args[i].equals("-pathFrom"))
            {
                pathFrom = args[++i];
            }
            if(args[i].equals("-pathTo"))
            {
                pathToDir = args[++i];
                pathTo = pathToDir + ".war";
            }            
            if(args[i].equals("-pathRepo"))
            {
                pathRepo = args[++i];
            }
            if(args[i].equals("-tomcat"))
            {
                tomcat = args[++i];
            }                    
            if(args[i].equals("--help"))
            {
                System.out.println(""
                        + "\r\nParametri disponibili:\r\n\r\n"
                        + "-url <mysql-url:port-number>, mysql server url\r\n"
                        + "-login <root> <rootpwd>, necessario per fare login in mysql\r\n"
                        + "-db <nome database>, nome del nuovo database che viene creato\r\n"
                        + "-user <username> <password>, crea nuovo utente per il database creato con tutti i privilegi\r\n"
                        + "-pathFrom <path file jackrabbit.war>\r\n"
                        + "-pathTo <path webapps di tomcat>\r\n"
                        + "-pathRepo <cartella/nome_repository>, cartella dove sono tutti i repository di jackrabbit + il nome del nuovo repository \r\n"
                        + "-tomcat <versione di tomcat> (es tomcat7)\r\n");
                System.exit(0);
            }              
        }
    }
   
   static void CreaMySqlDB() throws SQLException
   {
        Connection cn = DriverManager.getConnection("jdbc:mysql://" + url + "/?user=" + root + "&password=" + rootpwd + "");
        Statement s = cn.createStatement();
        int Result = s.executeUpdate("CREATE DATABASE " + db_name + ";");
        Result = s.executeUpdate("grant usage on *.* to " + user + "@localhost identified by '" + userpwd + "';");
	Result = s.executeUpdate("grant all privileges on " + db_name + ".* to " + user + "@localhost;");
        cn.close();
        System.out.println("Creato db mysql " + db_name + " nel mysql server " + url + "\r\n");
   }
      static void CopyWarFile() throws IOException
   {
	File src = new File(pathFrom); 
	File dst = new File(pathTo); 
       Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
       System.out.println("Copiato file jackrabbit-webapp.war da " + pathFrom + " in " + pathTo + "\r\n");
   }     
      
    static void CreateNewRepo() throws IOException, SQLException, RepositoryException, InterruptedException
    {
        CopyWarFile();        
        
        CreaMySqlDB();                
        
        //crea cartella repo dentro alla directory del server:
        boolean repo = (new File(pathRepo)).mkdirs();
        if (!repo) 
        {
            // Directory creation failed
            System.out.println("ERROR:  Directory creation failed");
            System.exit(1);
        }
        
        //cambia il proprietario della cartella creata a tomcat:
        Process p = Runtime.getRuntime().exec("chown -R " + tomcat + ":" + tomcat + " " + pathRepo);
p.waitFor();        
        System.out.println("Cambiato proprietario della cartella del repository " + pathRepo + "in " + tomcat + ":" + tomcat);
        
        //crea file bootstrap.properties dentro alla cartella del repository
        CreateBootstrapProperties();
        
        //crea file repository.xml dentro alla cartella del repository
        CreateRepositoryXml();
        
        //cambia web.xml dentro a WEB-INF della cartella del repository che è in tomcat/webapps
        //setta il percorso giusto al file bootstrap.properties
        EditWebXml();   
        
        //1) cambia password admin
        //2) disabilita anonymous
        
//NB se non lo chiamo e il programma finisce crea .lock,
        //se lo chiamo nn lo crea
        ChangeRepoUsers();
    }
    
    static void CreateBootstrapProperties()
    {
        BufferedWriter writer = null;
        try 
        {            
            
            //create bootstrap.properties file
            File bp = new File(pathRepo + "/bootstrap.properties");

            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("#bootstrap properties for the repository startup servlet.\n" +
"#Fri Aug 01 22:09:45 CEST 2014\n" +
"java.naming.factory.initial=org.apache.jackrabbit.core.jndi.provider.DummyInitialContextFactory\n" +
"repository.home=" + pathRepo + "\n" +
"rmi.enabled=true\n" +
"repository.config=" + pathRepo + "/repository.xml\n" +
"repository.name=jackrabbit.repository\n" +
"rmi.host=localhost\n" +
"java.naming.provider.url=http\\://www.apache.org/jackrabbit\n" +
"jndi.enabled=true\n" +
"rmi.port=0");
            
            // This will output the full path where the file will be written to...
            System.out.println("Creato file: " + bp.getCanonicalPath() + "\r\n");            
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        } 
        finally 
        {
            try 
            {
                // Close the writer regardless of what happens...
                writer.close();
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }
    }
    
    static void CreateRepositoryXml()
    {
        BufferedWriter writer = null;
        try 
        {
            //create bootstrap.properties file
            File bp = new File(pathRepo + "/repository.xml");
            
            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("<?xml version=\"1.0\"?>\n" +
"\n" +
"<!DOCTYPE Repository\n" +
"          PUBLIC \"-//The Apache Software Foundation//DTD Jackrabbit 2.0//EN\"\n" +
"          \"http://jackrabbit.apache.org/dtd/repository-2.0.dtd\">\n" +
"\n" +
"<Repository>\n" +
"    <!--\n" +
"        virtual file system where the repository stores global state\n" +
"        (e.g. registered namespaces, custom node types, etc.)\n" +
"    -->\n" +
"<FileSystem class=\"org.apache.jackrabbit.core.fs.db.DbFileSystem\">\n" +
"  <param name=\"driver\" value=\"com.mysql.jdbc.Driver\"/>\n" +
"  <param name=\"url\" value=\"jdbc:mysql://" + url + "/" + db_name + "\"/>\n" +
"  <param name=\"user\" value=\"" + user + "\"/>\n" +
"  <param name=\"password\" value=\"" + userpwd + "\"/>\n" +
"  <param name=\"schema\" value=\"mysql\"/>\n" +
"  <param name=\"schemaObjectPrefix\" value=\"fs_global_state_\"/>\n" +
"</FileSystem>\n" +
"\n" +
"    <!--\n" +
"        data store configuration\n" +
"    -->\n" +
"    <DataStore class=\"org.apache.jackrabbit.core.data.FileDataStore\"/>\n" +
"\n" +
"    <!--\n" +
"        security configuration\n" +
"    -->\n" +
"    <Security appName=\"Jackrabbit\">\n" +
"        <!--\n" +
"            security manager:\n" +
"            class: FQN of class implementing the JackrabbitSecurityManager interface\n" +
"        -->\n" +
"        <SecurityManager class=\"org.apache.jackrabbit.core.DefaultSecurityManager\" workspaceName=\"security\">\n" +
"            <!--\n" +
"            workspace access:\n" +
"            class: FQN of class implementing the WorkspaceAccessManager interface\n" +
"            -->\n" +
"            <!-- <WorkspaceAccessManager class=\"...\"/> -->\n" +
"            <!-- <param name=\"config\" value=\"${rep.home}/security.xml\"/> -->\n" +
"        </SecurityManager>\n" +
"\n" +
"        <!--\n" +
"            access manager:\n" +
"            class: FQN of class implementing the AccessManager interface\n" +
"        -->\n" +
"        <AccessManager class=\"org.apache.jackrabbit.core.security.DefaultAccessManager\">\n" +
"            <!-- <param name=\"config\" value=\"${rep.home}/access.xml\"/> -->\n" +
"        </AccessManager>\n" +
"\n" +
"        <LoginModule class=\"org.apache.jackrabbit.core.security.authentication.DefaultLoginModule\">\n" +
"           <!-- \n" +
"              anonymous user name ('anonymous' is the default value)\n" +
"            -->\n" +
"           <param name=\"anonymousId\" value=\"anonymous\"/>\n" +
"           <!--\n" +
"              administrator user id (default value if param is missing is 'admin')\n" +
"            -->\n" +
"           <param name=\"adminId\" value=\"" + user + "\"/>\n" +
"        </LoginModule>\n" +
"    </Security>\n" +
"\n" +
"    <!--\n" +
"        location of workspaces root directory and name of default workspace\n" +
"    -->\n" +
"    <Workspaces rootPath=\"${rep.home}/workspaces\" defaultWorkspace=\"default\"/>\n" +
"    <!--\n" +
"        workspace configuration template:\n" +
"        used to create the initial workspace if there's no workspace yet\n" +
"    -->\n" +
"    <Workspace name=\"${wsp.name}\">\n" +
"        <!--\n" +
"            virtual file system of the workspace:\n" +
"            class: FQN of class implementing the FileSystem interface\n" +
"        -->\n" +
"<FileSystem class=\"org.apache.jackrabbit.core.fs.db.DbFileSystem\">\n" +
"  <param name=\"driver\" value=\"com.mysql.jdbc.Driver\"/>\n" +
"  <param name=\"url\" value=\"jdbc:mysql://" + url + "/" + db_name + "\"/>\n" +
"  <param name=\"user\" value=\"" + user + "\"/>\n" +
"  <param name=\"password\" value=\"" + userpwd + "\"/>\n" +
"  <param name=\"schema\" value=\"mysql\"/>\n" +
"  <param name=\"schemaObjectPrefix\" value=\"fs_workspace_\"/>\n" +
"</FileSystem>\n" +
"\n" +
"        <!--\n" +
"            persistence manager of the workspace:\n" +
"            class: FQN of class implementing the PersistenceManager interface\n" +
"        -->\n" +
"<PersistenceManager class=\"org.apache.jackrabbit.core.persistence.pool.MySqlPersistenceManager\">\n" +
"    <param name=\"url\" value=\"jdbc:mysql://" + url + "/" + db_name + "\"/> <!-- use your database setup -->\n" +
"    <param name=\"user\" value=\"" + user + "\" />                           <!-- use your database user -->\n" +
"    <param name=\"password\" value=\"" + userpwd + "\" />                       <!-- use your database user's password -->\n" +
"    <param name=\"schema\" value=\"mysql\"/>\n" +
"    <param name=\"schemaObjectPrefix\" value=\"pm_ws_${wsp.name}_\"/>\n" +
"</PersistenceManager>\n" +
"        <!--\n" +
"            Search index and the file system it uses.\n" +
"            class: FQN of class implementing the QueryHandler interface\n" +
"        -->\n" +
"        <SearchIndex class=\"org.apache.jackrabbit.core.query.lucene.SearchIndex\">\n" +
"            <param name=\"path\" value=\"${wsp.home}/index\"/>\n" +
"            <param name=\"supportHighlighting\" value=\"true\"/>\n" +
"        </SearchIndex>\n" +
"    </Workspace>\n" +
"\n" +
"    <!--\n" +
"        Configures the versioning\n" +
"    -->\n" +
"    <Versioning rootPath=\"${rep.home}/version\">\n" +
"        <!--\n" +
"            Configures the filesystem to use for versioning for the respective\n" +
"            persistence manager\n" +
"        -->\n" +
"<FileSystem class=\"org.apache.jackrabbit.core.fs.db.DbFileSystem\">\n" +
"  <param name=\"driver\" value=\"com.mysql.jdbc.Driver\"/>\n" +
"  <param name=\"url\" value=\"jdbc:mysql://" + url + "/" + db_name + "\"/>\n" +
"  <param name=\"user\" value=\"" + user + "\"/>\n" +
"  <param name=\"password\" value=\"" + userpwd + "\"/>\n" +
"  <param name=\"schema\" value=\"mysql\"/>\n" +
"  <param name=\"schemaObjectPrefix\" value=\"fs_version_\"/>\n" +
"</FileSystem>\n" +
"\n" +
"\n" +
"        <!--\n" +
"            Configures the persistence manager to be used for persisting version state.\n" +
"            Please note that the current versioning implementation is based on\n" +
"            a 'normal' persistence manager, but this could change in future\n" +
"            implementations.\n" +
"        -->\n" +
"<PersistenceManager class=\"org.apache.jackrabbit.core.persistence.pool.MySqlPersistenceManager\">\n" +
"    <param name=\"url\" value=\"jdbc:mysql://" + url + "/" + db_name + "\"/> <!-- use your database setup -->\n" +
"    <param name=\"user\" value=\"" + user + "\" />                           <!-- use your database user -->\n" +
"    <param name=\"password\" value=\"" + userpwd + "\" />                       <!-- use your database user's password -->\n" +
"    <param name=\"schema\" value=\"mysql\"/>\n" +
"    <param name=\"schemaObjectPrefix\" value=\"pm_vs_\"/>\n" +
"</PersistenceManager>\n" +
"    </Versioning>\n" +
"\n" +
"    <!--\n" +
"        Search index for content that is shared repository wide\n" +
"        (/jcr:system tree, contains mainly versions)\n" +
"    -->\n" +
"    <SearchIndex class=\"org.apache.jackrabbit.core.query.lucene.SearchIndex\">\n" +
"        <param name=\"path\" value=\"${rep.home}/repository/index\"/>\n" +
"        <param name=\"supportHighlighting\" value=\"true\"/>\n" +
"    </SearchIndex>\n" +
"\n" +
"    <!--\n" +
"        Run with a cluster journal\n" +
"    -->\n" +
"    <Cluster id=\"node1\">\n" +
"        <Journal class=\"org.apache.jackrabbit.core.journal.MemoryJournal\"/>\n" +
"    </Cluster>\n" +
//"<RepositoryLockMechanism class=\"org.apache.jackrabbit.core.util.CooperativeFileLock\"/>\n" +
"</Repository>");
            
            // This will output the full path where the file will be written to...
            System.out.println("Creato file: " + bp.getCanonicalPath() + "\r\n");            
            
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        } 
        finally 
        {
            try 
            {
                // Close the writer regardless of what happens...
                writer.close();
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }
    }
    
    static void EditWebXml()
    {
        
        //Aspetta che tomcat deploy il .war
        //finchè non crea la cartella WEB-INF rimani fermo nel ciclo
        Path p = Paths.get(pathToDir + "/WEB-INF/web.xml");
        while(Files.notExists(p));
        
        System.err.println("Tomcat ha finito di deployare jackrabbit-webapp.war\r\n");
        
        //cancello file web.xml creato da tomcat
        File toCanc = new File(pathToDir + "/WEB-INF/web.xml");
        toCanc.delete();
        
        BufferedWriter writer = null;
        try 
        {
            //create bootstrap.properties file
            System.out.println("scrivendo file: " + pathToDir + "/WEB-INF/web.xml");
            File bp = new File(pathToDir + "/WEB-INF/web.xml");
            
            writer = new BufferedWriter(new FileWriter(bp));
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
"\n" +
"<!DOCTYPE web-app PUBLIC \"-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN\" \"http://java.sun.com/dtd/web-app_2_3.dtd\">\n" +
"<web-app>\n" +
"    <display-name>Jackrabbit JCR Server</display-name>\n" +
"\n" +
"    <listener>\n" +
"        <!-- Releases all Derby resources when the webapp is undeployed.  -->\n" +
"        <!-- See https://issues.apache.org/jira/browse/JCR-1301           -->\n" +
"        <listener-class>\n" +
"            org.apache.jackrabbit.j2ee.DerbyShutdown\n" +
"        </listener-class>\n" +
"    </listener>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- R E P O S I T O R Y   S T A R T U P  S E R V L E T                     -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet>\n" +
"        <servlet-name>RepositoryStartup</servlet-name>\n" +
"        <description>\n" +
"            Repository servlet that starts the repository and registers it to JNDI ans RMI.\n" +
"            If you already have the repository registered in this appservers JNDI context,\n" +
"            or if its accessible via RMI, you do not need to use this servlet.\n" +
"        </description>\n" +
"        <servlet-class>org.apache.jackrabbit.j2ee.RepositoryStartupServlet</servlet-class>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>bootstrap-config</param-name>\n" +
"            <param-value>" + pathRepo + "/bootstrap.properties</param-value>\n" +
"            <description>\n" +
"                Property file that hold the same initialization properties than\n" +
"                the init-params below. If a parameter is specified in both\n" +
"                places the one in the bootstrap-config wins.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <load-on-startup>2</load-on-startup>\n" +
"    </servlet>\n" +
"\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- R E P O S I T O R Y   S E R V L E T                                    -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet>\n" +
"        <servlet-name>Repository</servlet-name>\n" +
"        <description>\n" +
"            This servlet provides other servlets and jsps a common way to access\n" +
"            the repository. The repository can be accessed via JNDI, RMI or Webdav.\n" +
"        </description>\n" +
"        <servlet-class>org.apache.jackrabbit.j2ee.RepositoryAccessServlet</servlet-class>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>bootstrap-config</param-name>\n" +
"            <param-value>" + pathRepo + "/bootstrap.properties</param-value>\n" +
"            <description>\n" +
"                Property file that hold the same initialization properties than\n" +
"                the init-params below. If a parameter is specified in both\n" +
"                places the one in the bootstrap-config wins.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <load-on-startup>3</load-on-startup>\n" +
"    </servlet>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- W E B D A V  S E R V L E T                                              -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet>\n" +
"        <servlet-name>Webdav</servlet-name>\n" +
"        <description>\n" +
"            The webdav servlet that connects HTTP request to the repository.\n" +
"        </description>\n" +
"        <servlet-class>org.apache.jackrabbit.j2ee.SimpleWebdavServlet</servlet-class>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>resource-path-prefix</param-name>\n" +
"            <param-value>/repository</param-value>\n" +
"            <description>\n" +
"                defines the prefix for spooling resources out of the repository.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>resource-config</param-name>\n" +
"            <param-value>/WEB-INF/config.xml</param-value>\n" +
"            <description>\n" +
"                Defines various dav-resource configuration parameters.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <load-on-startup>4</load-on-startup>\n" +
"    </servlet>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- J C R  R E M O T I N G  S E R V L E T                                  -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet>\n" +
"        <servlet-name>JCRWebdavServer</servlet-name>\n" +
"        <description>\n" +
"            The servlet used to remote JCR calls over HTTP.\n" +
"        </description>\n" +
"        <servlet-class>org.apache.jackrabbit.j2ee.JcrRemotingServlet</servlet-class>\n" +
"        <init-param>\n" +
"            <param-name>missing-auth-mapping</param-name>\n" +
"            <param-value></param-value>\n" +
"            <description>\n" +
"                Defines how a missing authorization header should be handled.\n" +
"                 1) If this init-param is missing, a 401 response is generated.\n" +
"                    This is suitable for clients (eg. webdav clients) for which\n" +
"                    sending a proper authorization header is not possible if the\n" +
"                    server never sent a 401.\n" +
"                 2) If this init-param is present with an empty value,\n" +
"                    null-credentials are returned, thus forcing an null login\n" +
"                    on the repository.\n" +
"                 3) If this init-param is present with the value 'guestcredentials'\n" +
"                    java.jcr.GuestCredentials are used to login to the repository.\n" +
"                 4) If this init-param has a 'user:password' value, the respective\n" +
"                    simple credentials are generated.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>resource-path-prefix</param-name>\n" +
"            <param-value>/server</param-value>\n" +
"            <description>\n" +
"                defines the prefix for spooling resources out of the repository.\n" +
"            </description>\n" +
"        </init-param>\n" +
"\n" +
"        <init-param>\n" +
"            <param-name>batchread-config</param-name>\n" +
"            <param-value>/WEB-INF/batchread.properties</param-value>\n" +
"            <description>JcrRemotingServlet: Optional mapping from node type names to default depth.</description>\n" +
"        </init-param>\n" +
"        \n" +
"      <load-on-startup>5</load-on-startup>\n" +
"    </servlet>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- R M I   B I N D I N G   S E R V L E T                                  -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet>\n" +
"      <servlet-name>RMI</servlet-name>\n" +
"      <servlet-class>org.apache.jackrabbit.servlet.remote.RemoteBindingServlet</servlet-class>\n" +
"    </servlet>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- S E R V L E T   M A P P I N G                                          -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <servlet-mapping>\n" +
"        <servlet-name>RepositoryStartup</servlet-name>\n" +
"        <url-pattern>/admin/*</url-pattern>\n" +
"    </servlet-mapping>\n" +
"    <servlet-mapping>\n" +
"        <servlet-name>Webdav</servlet-name>\n" +
"        <url-pattern>/repository/*</url-pattern>\n" +
"    </servlet-mapping>\n" +
"    <servlet-mapping>\n" +
"        <servlet-name>JCRWebdavServer</servlet-name>\n" +
"        <url-pattern>/server/*</url-pattern>\n" +
"    </servlet-mapping>\n" +
"    <servlet-mapping>\n" +
"        <servlet-name>RMI</servlet-name>\n" +
"        <url-pattern>/rmi</url-pattern>\n" +
"    </servlet-mapping>\n" +
"\n" +
"    <!-- ====================================================================== -->\n" +
"    <!-- W E L C O M E   F I L E S                                              -->\n" +
"    <!-- ====================================================================== -->\n" +
"    <welcome-file-list>\n" +
"      <welcome-file>index.jsp</welcome-file>\n" +
"    </welcome-file-list>\n" +
"\n" +
"    <error-page>\n" +
"        <exception-type>org.apache.jackrabbit.j2ee.JcrApiNotFoundException</exception-type>\n" +
"        <location>/error/classpath.jsp</location>\n" +
"    </error-page>\n" +
"    <error-page>\n" +
"        <exception-type>javax.jcr.RepositoryException</exception-type>\n" +
"        <location>/error/repository.jsp</location>\n" +
"    </error-page>\n" +
"    \n" +
"</web-app>");
            
            // This will output the full path where the file will be written to...
            System.out.println("Creato file: " + bp.getCanonicalPath() + "\r\n");            
            
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        } 
        finally 
        {
            try 
            {
                // Close the writer regardless of what happens...
                writer.close();
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }        
    }
    
    static void ChangeRepoUsers() throws RepositoryException, IOException, InterruptedException
    {
        
        WaitForRepoCreation();
        
        System.out.println("Inizio Gestione Utenti Repo " + pathRepo + "\r\n");

        
        //serve per loggarsi / creare ad un repository jackrabbit
         RepositoryConfig config = RepositoryConfig.install(new File(pathRepo));
         //NB nella cartella ci devono essere i permessi di scrittura per creare il file .lock
         RepositoryImpl repository = RepositoryImpl.create(config);         
         
         
        Session session; 
        
        //controllo se l'utente esiste        
        try
        {
            session = repository.login(new
            //per default, all'inizio jackrabbit crea admin con stessa username e password
            SimpleCredentials(user,user.toCharArray()));              
        }    
        catch (Exception e)
        {
            System.out.println("Errore Login Credenziali");
            e.printStackTrace();
            return;
        }        
                                     
        UserManager userManager = ((JackrabbitSession) session).getUserManager();                

        //NB il server deve essere riavviato per rendere effettiva ogni singola operazione fatta qui dentro 
        
        System.out.println("Disabilito utente anonymous \r\n");
        
        //disabilita utente anonymous
        Authorizable a2 = userManager.getAuthorizable("anonymous");
         ((User) a2).disable("prevent anonymous login"); 

         System.out.println("cambio password all'admin \r\n");
         
         //cambia password all'utente admin
        Authorizable authorizable = userManager.getAuthorizable(user);
        ((User) authorizable).changePassword(userpwd);

        //System.out.println("Creo nuovo utente \r\n");
        
        //crea utente stessa username e password di mysql
        //final User repoUser = userManager.createUser(user, userpwd);
                
        session.save();
        session.logout();                     
        
        //cancella file .lock che jackrabbit crea durante la creazione
        ReleaseLock();        
        
        //riavvia tomcat
        Process p = Runtime.getRuntime().exec("sudo service " + tomcat + " restart");
        p.waitFor();        
        System.out.println("Riavviato server tomcat");       


    }
    
    static void WaitForRepoCreation() throws InterruptedException
    {
        File f = new File(pathRepo + "/repository");
        while(!f.exists())
        {
            //aspetto che venga creata cartella repository
            Thread.sleep(2000);
        }
        System.out.println("creata cartella repository\r\n");
        
        f = new File(pathRepo + "/workspaces");
        while(!f.exists())
        {
            //aspetto che venga creata cartella workspaces
        }
        System.out.println("creata cartella workspaces\r\n");
        
        //finchè nn viene creato .lock si ferma nel ciclo
        boolean s = ReleaseLock();
        System.out.print("cancellato .lock " + s + "\r\n");        
                
        f = new File(pathRepo + "/workspaces/default");
        while(!f.exists())
        {
            //f.delete();
            //System.out.print("c");
        }    
        System.out.println("creato workspaces/default\r\n");        
        
        f = new File(pathRepo + "/workspaces/security");
        while(!f.exists())
        {
            //f.delete();
            //System.out.print("c");
        }    
        System.out.println("creato workspaces/security\r\n");        
        
    }
    
    //cancella il file .lock che jackrabbit crea in automatico
    static boolean ReleaseLock()
    {
        File f = new File(pathRepo + "/.lock");
        while(!f.exists())
        {
            //f.delete();
            //System.out.print("c");
        }            
        return f.delete();
        
    }        
}
