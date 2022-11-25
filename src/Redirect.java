import javax.servlet.ServletException;
import javax.servlet.sip.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Redirect extends SipServlet {
    static private Map<String, String> registrarDB;
    static private Map<String, Boolean> stateDB;
    static private ArrayList<String> colabDB;
    static private SipFactory sipFactory;

    /**
     * SipServlet functions
     */
    @Override
    public void init() {
        sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);

        registrarDB = new HashMap<>();
        stateDB = new HashMap<>();
        colabDB = new ArrayList<>();
    }

    @Override
    protected void doPublish(SipServletRequest request) throws ServletException, IOException {
        String aor = getAttr(request.getHeader("From"), "sip:");

        if (!inDomain(request) || !(userType(request, "colaborador") || userType(request, "gestor"))) {
            request.createResponse(403).send();
            return;
        }

        if (!registrarDB.containsKey(aor)) {
            request.createResponse(404).send();
            return;
        }

        stateDB.put(aor, request.getHeader("Content-Length").equals("194"));
        request.createResponse(200).send();
    }

    @Override
    protected void doRegister(SipServletRequest request) throws IOException {
        if (!inDomain(request) || !(userType(request, "colaborador") || userType(request, "gestor"))) {
            request.createResponse(403).send();
            return;
        }

        String contactHeader = request.getHeader("Contact");
        String aor = getAttr(request.getHeader("From"), "sip:");
        String contact = getAttr(contactHeader, "sip:");

        // if not null then linphone
        String expiresHeader = request.getHeader("Expires");
        String expiry = expiresHeader != null ? expiresHeader : contactHeader.split("=")[1];
        System.out.println(expiresHeader != null ? "LINPHONE" : "TWINKLE");
        if (expiry.equals("0")) {
            log("SIP UNREGISTER");

            if (!registrarDB.containsKey(aor)) {
                request.createResponse(404).send();
            } else {
                registrarDB.remove(aor);
                request.createResponse(200).send();
            }

            stateDB.remove(aor);
        } else {
            log("SIP REGISTER");

            registrarDB.put(aor, contact);
            request.createResponse(200).send();
        }
    }

    @Override
    protected void doInvite(SipServletRequest request) throws IOException, TooManyHopsException, ServletParseException {
        if (!inDomain(request)) {
            request.createResponse(403).send();
            return;
        }

        String aorTo = getAttr(request.getHeader("To"), "sip:");

        if (!registrarDB.containsKey(aorTo)) {
            if (userType(request, "gestor") && aorTo.equals("sip:alerta@acme.pt")) {
                SipServletRequest customRequest = sipFactory.createRequest(request.getApplicationSession(), "MESSAGE", "sip:alerta@acme.pt", registrarDB.get("sip:gestor@acme.pt"));
                customRequest.setContent("Consola\nADD address\nREMOVE address".getBytes(), "text/plain");
                customRequest.send();
                request.createResponse(200).send();
            } else {
                request.createResponse(404).send();
            }
        } else {
            request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(aorTo)));
        }
    }

    @Override
    protected void doBye(SipServletRequest request) throws ServletException, IOException {
        request.createResponse(200).send();
    }

    @Override
    protected void doMessage(SipServletRequest request) throws ServletException, IOException {
        String aorFrom = getAttr(request.getHeader("From"), "sip:");
        String aorTo = getAttr(request.getHeader("To"), "sip:");

        if (userType(request, "colaborador") && aorTo.equals("sip:alerta@acme.pt") && registrarDB.containsKey("sip:gestor@acme.pt")) {
            request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get("sip:gestor@acme.pt")));
            request.createResponse(200).send();
            return;
        }

        if (userType(request, "gestor") && aorTo.equals("sip:alerta@acme.pt") ) {
            // 0 - command
            // 1 - argument
            String[] msg = new String(request.getRawContent(), StandardCharsets.UTF_8).split(" ");
            switch (msg[0]) {
                case "ADD": {
                    log("==========================ADD");
                    if (!registrarDB.containsKey(msg[1])) {
                        request.createResponse(404).send();
                        return;
                    }
                    colabDB.add(msg[1]);
                    SipServletRequest res = sipFactory.createRequest(request.getApplicationSession(), "MESSAGE", "sip:alerta@acme.pt", registrarDB.get(aorFrom));
                    res.setContent("OK".getBytes(), "text/plain");
                    res.send();
                    request.createResponse(200).send();
                    break;
                }
                case "REMOVE": {
                    log("==========================REMOVE");
                    if (!registrarDB.containsKey(msg[1])) {
                        request.createResponse(404).send();
                        return;
                    }
                    colabDB.remove(msg[1]);
                    request.createResponse(200).send();
                    break;
                }
                default:
                    request.createResponse(403).send();
            }
            return;
        }

        if (userType(request, "colaborador")) {
            request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(aorTo)));
            request.createResponse(200).send();
            return;
        }
    }

    /**
     * Customs functions
     */
    protected boolean inDomain(SipServletRequest request) {
        return getAttr(request.getHeader("From"), "@").equals("@acme.pt");
    }

    protected boolean userType(SipServletRequest request, String type) {
        return getAttr(request.getHeader("From"), "sip:").contains(type);
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
