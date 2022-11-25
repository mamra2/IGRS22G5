import javax.servlet.ServletException;
import javax.servlet.sip.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Redirect extends SipServlet {
    static private Map<String, String> registrarDB;
    static private Map<String, Boolean> stateDB;
    static private SipFactory sipFactory;

    /**
     * SipServlet functions
     */
    @Override
    public void init() {
        sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);

        registrarDB = new HashMap<>();
        stateDB = new HashMap<>();
    }

    @Override
    protected void doPublish(SipServletRequest request) throws ServletException, IOException {
        String aor = getAttr(request.getHeader("From"), "sip:");

        if (!verifyDomain(request) || !(verifyUserType(request, "colaborador") || verifyUserType(request, "gestor"))) {
            request.createResponse(403).send();
            return;
        }

        if (!registrarDB.containsKey(aor)) {
            request.createResponse(404).send();
            return;
        }

        // this has an Expires header, so handle that maybe?
        stateDB.put(aor, request.getHeader("Content-Length").equals("194"));
        request.createResponse(200).send();
    }

    @Override
    protected void doRegister(SipServletRequest request) throws IOException {
        if (!verifyDomain(request) || !(verifyUserType(request, "colaborador") || verifyUserType(request, "gestor"))) {
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
        if (!verifyDomain(request)) {
            request.createResponse(403).send();
            return;
        }

        String aor = getAttr(request.getHeader("To"), "sip:");

        if (!registrarDB.containsKey(aor)) {
            log("PROXY FORBIDDEN");
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

        if (aor.equals("sip:alerta@acme.pt")) {
            if (registrarDB.containsKey("sip:gestor@acme.pt")) {
                log("PROXY TO MANAGER");
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get("sip:gestor@acme.pt")));
                log("MESSAGE CONTENT LEN: " + request.getContentLength());
                log("MESSAGE CONTENT: " + new String(request.getRawContent(), StandardCharsets.UTF_8));
                request.createResponse(200).send();
            }
        }

        if (aor.equals("sip:gestor@acme.pt")) {
            log("PROXY WORKED");
            request.createResponse(200).send();
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
