package org.mobicents.servlet.sip.example;

import javax.servlet.ServletException;
import javax.servlet.sip.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Redirect extends SipServlet {
    static private Map<String, String> registrarDB;
    static private SipFactory sipFactory;

    /**
     * SipServlet functions
     */
    @Override
    public void init() {
        sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);
        registrarDB = new HashMap<>();
    }

    @Override
    protected void doRegister(SipServletRequest request) throws IOException {
        if (!verifyDomain(request)) {
            request.createResponse(403).send();
            return;
        }

        if (verifyUserType(request, "colaborador")) {
            log("COLABORADOR");
        } else if (verifyUserType(request, "gestor")) {
            log("GESTOR");
        } else {
            log("?????");
        }

        String toHeader = request.getHeader("To");
        String contactHeader = request.getHeader("Contact");
        String aor = getAttr(toHeader, "sip:");
        String contact = getAttr(contactHeader, "sip:");

        // if not null then linphone
        String expires = request.getHeader("Expires");
        if (expires != null) {
            // linphone sends 2 registers for no reason, the one with @127.0.0.1:5666 is to be ignored
            if (getAttr(contactHeader, "@").equals("@127.0.0.1:5666")) {
                request.createResponse(200).send();
                return;
            }

            if (expires.equals("0")) {
                if (!registrarDB.containsKey(aor)) {
                    request.createResponse(404).send();
                } else {
                    registrarDB.remove(aor);
                    request.createResponse(200).send();
                }
            } else {
                log("LINPHONE REGISTER");

                if (registrarDB.containsKey(aor)) {
                    request.createResponse(409).send();
                    return;
                }

                registrarDB.put(aor, contact);
                registrarDB.put("alerta@acme.pt", registrarDB.get(aor));
                request.createResponse(200).send();
            }
        } else {
            if (contactHeader.split("=")[1].equals("0")) {
                log("TWINKLE UNREGISTER");

                if (!registrarDB.containsKey(aor)) {
                    request.createResponse(404).send();
                } else {
                    registrarDB.remove(aor);
                    request.createResponse(200).send();
                }
            } else {
                log("TWINKLE REGISTER");

                if (registrarDB.containsKey(aor)) {
                    request.createResponse(409).send();
                    return;
                }

                registrarDB.put(aor, contact);
                request.createResponse(200).send();
            }
        }
    }

    @Override
    protected void doInvite(SipServletRequest request) throws IOException, TooManyHopsException, ServletParseException {
        if (!verifyDomain(request)) {
            request.createResponse(403).send();
            return;
        }

        String aor = getAttr(request.getHeader("To"), "sip:");

        if (!registrarDB.containsKey(aor)) {
            log("PROXY");
            request.createResponse(404).send();
        } else {
            SipServletResponse response = request.createResponse(300);
            response.setHeader("Contact", registrarDB.get(aor));
            response.send();
        }
    }

    @Override
    protected void doMessage(SipServletRequest request) throws ServletException, IOException {
        String aor = getAttr(request.getHeader("To"), "sip:");

        if (aor.equals("alerta@acme.pt")) {
            request.createResponse(200).send();
            SipServletResponse response = request.createResponse(300);
            response.setHeader("To", "<sip:gestor@acme.pt>");
            response.setHeader("Contact", registrarDB.get("gestor@acme.pt"));
            response.send();
        }
    }

    /**
     * Customs functions
     */
    protected boolean verifyDomain(SipServletRequest request) {
        return getAttr(request.getHeader("From"), "@").equals("@acme.pt");
    }

    protected boolean verifyUserType(SipServletRequest request, String type) {
        return getAttr(request.getHeader("To"), "sip:").contains(type);
    }

    /**
     * Auxiliary function for extracting attribute values
     *
     * @param str  the complete string
     * @param attr the attr name
     * @return attr name and value
     */
    protected String getAttr(String str, String attr) {
        int indexStart = str.indexOf(attr);
        int indexStop = str.indexOf(">", indexStart);
        if (indexStop == -1) {
            indexStop = str.indexOf(";", indexStart);
            if (indexStop == -1) {
                indexStop = str.length();
            }
        }
        return str.substring(indexStart, indexStop);
    }
}
