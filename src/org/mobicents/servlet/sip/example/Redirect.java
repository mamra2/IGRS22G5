/*
 * $Id: EchoServlet.java,v 1.5 2003/06/22 12:32:15 fukuda Exp $
 */
package org.mobicents.servlet.sip.example;

import java.util.*;
import java.io.IOException;

import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.ServletException;
import javax.servlet.sip.URI;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.SipFactory;

/**
 */
public class Redirect extends SipServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	static private Map<String, String> RegistrarDB; // The Location Database 
	static private SipFactory factory;              // Factory for creating Req.s and URIs
	
	public Redirect() {
		super();
	}
	
	public void init() {
		factory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);
		RegistrarDB = new HashMap<String,String>();
	}
	
	protected boolean verifyDomain(SipServletRequest request) throws ServletException,
	IOException {
		String aor = getAttr(request.getHeader("To"), "@");
		log(aor);
		return aor.compareTo("@acme.pt")==0;
	}

	/**
        * Acts as a registrar and location service for REGISTER messages
        * @param  request The SIP message received by the AS 
        */
	protected void doRegister(SipServletRequest request) throws ServletException,
			IOException {
		String toHeader = request.getHeader("To");
		String contactHeader = request.getHeader("Contact");
		String aor = getAttr(toHeader, "sip:");	
		String contact = getAttr(contactHeader, "sip:");
		RegistrarDB.put(aor, contact);
		
		SipServletResponse response; 
		response = request.createResponse(200);
		response.send();
		
	    // Some logs to show the content of the RegistrarDB database.
		log("REGISTER:******");
		Iterator<Map.Entry<String,String>> it = RegistrarDB.entrySet().iterator();
    		while (it.hasNext()) {
        		Map.Entry<String,String> pairs = (Map.Entry<String,String>)it.next();
        		log(pairs.getKey() + " = " + pairs.getValue());
    		}
		log("REGISTER:******");
	}

	/**
        * Sends SIP replies to INVITE messages
        * - 300 if registred
        * - 404 if not registred
        * @param  request The SIP message received by the AS 
        */
	protected void doInvite(SipServletRequest request)
                  throws ServletException, IOException {
		
		if(!verifyDomain(request)){
			SipServletResponse response = request.createResponse(403);
			response.send();
			return;
		
		}
		else{
			SipServletResponse response = request.createResponse(200);
			response.send();
		}
		
		// Some logs to show the content of the Registrar database.
		log("INVITE:***");
		Iterator<Map.Entry<String,String>> it = RegistrarDB.entrySet().iterator();
    		while (it.hasNext()) {
        		Map.Entry<String,String> pairs = (Map.Entry<String,String>)it.next();
        		log(pairs.getKey() + " = " + pairs.getValue());
    		}
		log("INVITE:***");
		
		String aor = getAttr(request.getHeader("To"), "sip:"); // Get the To AoR
		log("INVITE: To: " + aor);
	    if (!RegistrarDB.containsKey(aor)) { // To AoR not in the database, reply 404
			/*
	    	request.createResponse(100).send();
			Proxy proxy = request.getProxy();
			ArrayList<URI> addrList = new ArrayList<URI>();
			addrList.add(factory.createURI("sip:announcement@127.0.0.1:5080"));
			proxy.proxyTo(addrList);
	    	*/
	    	
	    	SipServletResponse response; 
			response = request.createResponse(404);
			response.send();	
			
	    } else {
			// SipServletRequest req = factory.createRequest(request.getApplicationSession(), "INFO", "sip:server@acme.pt", RegistrarDB.get(aor));
	    	
	    	SipServletResponse response = request.createResponse(300);
			// Get the To AoR contact from the database and add it to the response 
			response.setHeader("Contact",RegistrarDB.get(aor));
			response.send();
			
		}
	}
	
	/**
        * Auxiliary function for extracting attribute values
        * @param str the complete string
        * @param attr the attr name 
        * @return attr name and value 
        */
	protected String getAttr(String str, String attr) {
		int indexStart = str.indexOf(attr);
		int indexStop  = str.indexOf(">", indexStart);
		if (indexStop == -1) {
			indexStop  = str.indexOf(";", indexStart);
			if (indexStop == -1) {
				indexStop = str.length();
			}
		}
		return str.substring(indexStart, indexStop);
	}
}
