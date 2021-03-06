package ex.corba.alu;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.Properties;

import nmsSession.NmsSession_I;
import nmsSession.NmsSession_IHelper;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import emsSession.EmsSession_I;
import emsSession.EmsSession_IHolder;
import emsSessionFactory.EmsSessionFactory_I;
import emsSessionFactory.EmsSessionFactory_IHelper;

public class AlcatelConnection {
	public static final Logger LOG = LoggerFactory
			.getLogger(AlcatelConnection.class);

	protected String corbaConnect;
	protected String login;
	protected String pass;
	protected String emsName;
	protected String realEMSName;
	protected ORB orb;
	protected POA rootPOA;
	protected Properties props;

	protected NmsSessionImpl nmsSessionImpl;

	public static void main(String args[]) {
		AlcatelConnection main = new AlcatelConnection();
		EmsSession_I emsSession = null;

		try {
			main.openEmsSession(args);
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			main.closeEmsSession(emsSession);
		}
	}

	public EmsSession_I openEmsSession(String args[]) throws Exception {
		Properties props = getConnectionParams();

		// create and initialize the ORB
		orb = ORB.init(args, props);

		if (LOG.isInfoEnabled()) {
			LOG.info("ORB.init called.");
		}

		// Get the root naming context
		NamingContextExt rootContext = NamingContextExtHelper.narrow(orb
				.resolve_initial_references("NameService"));

		if (LOG.isInfoEnabled()) {
			LOG.info("NameService found.");
		}

		NameComponent[] name = rootContext
				.to_name("alu/nbi/EmsSessionFactory_I");

		org.omg.CORBA.Object ems = rootContext.resolve(name);

		if (LOG.isInfoEnabled()) {
			LOG.info("ems: " + ems);
		}

		EmsSessionFactory_I sessionFactory = EmsSessionFactory_IHelper
				.narrow(ems);

		if (LOG.isInfoEnabled()) {
			LOG.info("EmsSessionFactory: " + sessionFactory);
		}

		// Create NMS Session
		NmsSession_I nmsSession = createNmsSession();

		// Create EMS Session
		EmsSession_IHolder sessionHolder = new EmsSession_IHolder();
		sessionFactory.getEmsSession(login, pass, nmsSession, sessionHolder);
		EmsSession_I emsSession = sessionHolder.value;

		// check the obtained session
		if (emsSession.associatedSession()._non_existent()) {
			if (LOG.isInfoEnabled()) {
				LOG.info("Alcatel 1350 OMS>> Auth Fail or EmsSession not exist");
			}

			closeEmsSession(emsSession);

			return null;
		}

		nmsSessionImpl.setAssociatedSession(emsSession);
		// pingThread = new PingThread(nmsSession, emsSession);

		if (LOG.isInfoEnabled()) {
			LOG.info("Authentication successful!!! emsSession: {}.", emsSession);
		}

		return emsSession;
	}

	public EmsSession_I openEmsSessionUsingIOR(String args[]) throws Exception {
		Properties props = getConnectionParams();

		// create and initialize the ORB
		orb = ORB.init(args, props);

		if (LOG.isInfoEnabled()) {
			LOG.info("ORB.init called.");
		}

		// read stringified object to file (IOR file)
		FileReader fr = new FileReader("alu-lab.ior");
		BufferedReader br = new BufferedReader(fr);
		String ior = br.readLine();
		br.close();

		// Obtaining reference to SessionFactory
		org.omg.CORBA.Object ems = orb.string_to_object(ior);

		if (LOG.isInfoEnabled()) {
			LOG.info("ems: " + ems);
		}

		EmsSessionFactory_I sessionFactory = EmsSessionFactory_IHelper
				.narrow(ems);

		if (LOG.isInfoEnabled()) {
			LOG.info("EmsSessionFactory: " + sessionFactory);
		}

		// Create NMS Session
		NmsSession_I nmsSession = createNmsSession();

		// Create EMS Session
		EmsSession_IHolder sessionHolder = new EmsSession_IHolder();
		sessionFactory.getEmsSession(login, pass, nmsSession, sessionHolder);
		EmsSession_I emsSession = sessionHolder.value;

		// check the obtained session
		if (emsSession.associatedSession()._non_existent()) {
			if (LOG.isInfoEnabled()) {
				LOG.info("Alcatel 1350 OMS>> Auth Fail or EmsSession not exist");
			}

			closeEmsSession(emsSession);

			return null;
		}

		if (LOG.isInfoEnabled()) {
			LOG.info("Authentication successful!!! emsSession: {}.", emsSession);
		}

		return emsSession;
	}

	public NmsSession_I createNmsSession() throws Exception {
		rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
		rootPOA.the_POAManager().activate();

		// Create NMS Session
		nmsSessionImpl = new NmsSessionImpl();

		org.omg.CORBA.Object corbaObj = rootPOA
				.servant_to_reference(nmsSessionImpl);

		if (LOG.isInfoEnabled()) {
			LOG.info("rootPOA corbaObj:" + corbaObj);
		}

		NmsSession_I nmsSession = NmsSession_IHelper.narrow(corbaObj);

		// NmsSession_IPOATie tieobj = new NmsSession_IPOATie(nmsSessionImpl,
		// rootPOA);
		// rootPOA.activate_object(tieobj);
		// NmsSession_I nmsSession = NmsSession_IHelper.narrow(tieobj._this());

		return nmsSession;
	}

	public void closeEmsSession(EmsSession_I emsSession) {
		if (emsSession != null) {
			// pingThread.stopPing();
			emsSession.endSession();
		}

		if (rootPOA != null) {
			rootPOA.destroy(true, true);
		}

		if (orb != null) {
			orb.shutdown(true);
			orb.destroy();
		}
	}

	public Properties getConnectionParams() throws Exception {
		props = new Properties();
		props.load(new FileInputStream(new File("alu.properties")));

		corbaConnect = "corbaloc:iiop:" + props.getProperty("host") + ":"
				+ props.getProperty("port") + "/"
				+ props.getProperty("NameService");

		login = props.getProperty("login");
		pass = props.getProperty("password");
		emsName = props.getProperty("EMSname");

		// prepare ems name for usage
		realEMSName = emsName;
		emsName = emsName.replaceAll("/", "\\\\/");
		emsName = emsName.replaceAll("\\.", "\\\\\\.");

		props.setProperty("jacorb.net.socket_factory",
				"org.jacorb.orb.factory.DefaultSocketFactory");
		props.setProperty("jacorb.net.server_socket_factory",
				"org.jacorb.orb.factory.DefaultServerSocketFactory");

		// props.setProperty("jacorb.log.default.verbosity", "4");
		// props.setProperty("jacorb.logfile", "log/jacorb.log");

		props.setProperty(
				"org.omg.PortableInterceptor.ORBInitializerClass.bidir_init",
				"org.jacorb.orb.giop.BiDirConnectionInitializer");
		props.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
		props.setProperty("org.omg.CORBA.ORBSingletonClass",
				"org.jacorb.orb.ORBSingleton");
		props.put("jacorb.connection.client.keepalive", "on");

		props.setProperty("ORBInitRef.NameService", corbaConnect);

		props.setProperty("jacorb.connection.client.pending_reply_timeout",
				"600000");
		props.setProperty(
				"jacorb.connection.client.timeout_ignores_pending_messages",
				"on");

		return props;
	}
}