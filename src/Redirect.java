import javax.servlet.ServletException;
import javax.servlet.sip.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Redirect extends SipServlet {
    static final private String GESTOR = "sip:gestor@acme.pt";
    static final private String ALERTA = "sip:alerta@acme.pt";
    static final private String CONF = "sip:conference@acme.pt";
    static final private String SEMS = "sip:conference@127.0.0.1:5070";
    static private Map<String, String> registrarDB; // registration db
    static private Map<String, Boolean> stateDB; // online/offline db
    static private ArrayList<String> colabDB; // collaborator db
    static private SipFactory sipFactory;
    static private boolean conf = false;

    static private int smsKPI = 0;
    static private int conferenceKPI = 0;
    static private int callKPI = 0;

    /**
     * SipServlet functions
     */
    @Override
    public void init() {
        sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);

        registrarDB = new HashMap<>();
        stateDB = new HashMap<>();

        colabDB = new ArrayList<>();
        colabDB.add(GESTOR);
    }

    @Override
    protected void doPublish(SipServletRequest request) throws IOException {
        if (!inDomain(request)) {
            request.createResponse(403).send();
            return;
        }

        String aor = getAttr(request.getHeader("From"), "sip:");
        if (!registrarDB.containsKey(aor)) {
            request.createResponse(404).send();
            return;
        }

        stateDB.put(aor, new String(request.getRawContent(), StandardCharsets.UTF_8).contains("<status><basic>open</basic></status>"));
        request.createResponse(200).send();
    }

    @Override
    protected void doRegister(SipServletRequest request) throws IOException {
        if (!inDomain(request)) {
            request.createResponse(403).send();
            return;
        }

        String contactHeader = request.getHeader("Contact");
        String aor = getAttr(request.getHeader("From"), "sip:");
        String contact = getAttr(contactHeader, "sip:");

        // if not null then linphone
        String expiresHeader = request.getHeader("Expires");
        // choose the right expiry value
        String expiry = expiresHeader != null ? expiresHeader : contactHeader.split("=")[1];
        log(expiresHeader != null ? "LINPHONE" : "TWINKLE");
        if (expiry.equals("0")) {
            log("SIP UNREGISTER");

            // can't unregister if not registered
            if (!registrarDB.containsKey(aor)) {
                request.createResponse(404).send();
            } else {
                registrarDB.remove(aor);
                // also remove state
                stateDB.remove(aor);
                request.createResponse(200).send();
            }
        } else {
            log("SIP REGISTER");

            // this already handles ip/port changes for existing contacts
            // kinda insecure?
            registrarDB.put(aor, contact);
            // precaution
            stateDB.put(aor, true);
            request.createResponse(200).send();
        }
    }

    @Override
    protected void doInvite(SipServletRequest request) throws IOException, TooManyHopsException, ServletParseException {
        if (!inDomain(request)) {
            request.createResponse(403).send();
            return;
        }

        String aorFrom = getAttr(request.getHeader("From"), "sip:");
        String aorTo = getAttr(request.getHeader("To"), "sip:");
        if (!registrarDB.containsKey(aorTo)) {
            if (aorTo.equals(ALERTA) && !aorFrom.contains("colaborador") && registrarDB.containsKey(GESTOR)) {
                // to ALERTA from user
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(GESTOR)));
            } else if (aorTo.equals(CONF) && registrarDB.containsKey(aorFrom) && colabDB.contains(aorFrom) && conf) {
                // to CONF from any collaborator while conference is ongoing
                request.getProxy().proxyTo(sipFactory.createURI(SEMS));
            } else {
                // not registered
                request.createResponse(404).send();
            }
        } else {
            // cant contact GESTOR directly
            if (aorTo.equals(GESTOR)) {
                request.createResponse(403).send();
            } else {
                // both have to be online
                if (stateDB.get(aorTo) && stateDB.get(aorFrom)) {
                    request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(aorTo)));
                } else {
                    // 404 if either offline
                    request.createResponse(404).send();
                }
            }
        }
    }

    @Override
    protected void doMessage(SipServletRequest request) throws ServletException, IOException {
        if (!inDomain(request)) {
            request.createResponse(403).send();
            return;
        }

        String aorFrom = getAttr(request.getHeader("From"), "sip:");
        String aorTo = getAttr(request.getHeader("To"), "sip:");

        smsKPI++;
        log("======KPI======");
        log("SMS: " + smsKPI);

        if (aorFrom.equals(GESTOR) && aorTo.equals(ALERTA) && registrarDB.containsKey(GESTOR)) {
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

                    SipServletRequest res = sipFactory.createRequest(
                            request.getApplicationSession(),
                            "MESSAGE",
                            ALERTA,
                            registrarDB.get(GESTOR)
                    );
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

                    SipServletRequest res = sipFactory.createRequest(
                            request.getApplicationSession(),
                            "MESSAGE",
                            ALERTA,
                            registrarDB.get(GESTOR)
                    );
                    res.setContent("OK".getBytes(), "text/plain");
                    res.send();

                    request.createResponse(200).send();
                    break;
                }
                case "CONF": {
                    log("==========================CONF");
                    request.createResponse(200).send();
                    conf = !conf;

                    if (conf) {
                        conferenceKPI++;
                        log("======KPI======");
                        log("CONF: " + conferenceKPI);

                        // launch sems when a conference starts
                        String[] cmd = {"bash", "-c", "./launch.sh"};
                        Runtime.getRuntime().exec(cmd, null, new File("/home/igrs/Desktop/igrs-tools/sems"));

                        for (String c : colabDB) {
                            // skip GESTOR because he's the one sending the message
                            if (c.equals(GESTOR)) {
                                continue;
                            }

                            // dont message offline collaborators
                            if (!stateDB.get(c)) {
                                continue;
                            }

                            SipServletRequest confMsg = sipFactory.createRequest(
                                    request.getApplicationSession(),
                                    "MESSAGE",
                                    ALERTA,
                                    registrarDB.get(c)
                            );
                            confMsg.setContent("Call sip:conference@acme.pt", "text/plain");
                            confMsg.send();
                        }
                    } else {
                        // kill with 2 SIGINT (INT) so everyone gets disconnected
                        String[] cmd = {"bash", "-c", "sudo killall -s 2 sems"};
                        Runtime.getRuntime().exec(cmd);

                        for (String c : colabDB) {
                            // skip GESTOR because he's the one sending the message
                            if (c.equals(GESTOR)) {
                                continue;
                            }

                            // dont message offline collaborators
                            if (!stateDB.get(c)) {
                                continue;
                            }

                            SipServletRequest confMsg = sipFactory.createRequest(
                                    request.getApplicationSession(),
                                    "MESSAGE",
                                    ALERTA,
                                    registrarDB.get(c)
                            );
                            confMsg.setContent("The conference is over.", "text/plain");
                            confMsg.send();
                        }
                    }
                    break;
                }
                case "GETKPI":
                    log("==========================GETKPI");

                    SipServletRequest kpi = sipFactory.createRequest(
                            request.getApplicationSession(),
                            "MESSAGE",
                            ALERTA,
                            registrarDB.get(GESTOR)
                    );

                    kpi.setContent(String.format("SMS: %d CALLS: %d CONF: %d", smsKPI, callKPI, conferenceKPI), "text/plain");
                    kpi.send();

                    request.createResponse(200).send();
                    break;
                case "ALERT":
                    log("==========================ALERT");
                    for (String c : colabDB) {
                        if (c.equals(GESTOR)) {
                            continue;
                        }

                        SipServletRequest alert = sipFactory.createRequest(
                                request.getApplicationSession(),
                                "MESSAGE",
                                ALERTA,
                                registrarDB.get(c)
                        );

                        alert.setContent(request.getContent(), "text/plain");
                        alert.send();
                    }
                    request.createResponse(200).send();
                    break;
                default:
                    request.createResponse(403).send();
            }
            return;
        }

        // block any direct message to manager
        if (aorTo.equals(GESTOR)) {
            request.createResponse(403).send();
            return;
        }

        // user to manager
        if (aorTo.equals(ALERTA)) {
            // block collaborators
            if (aorFrom.contains("colaborador")) {
                request.createResponse(403).send();
            } else if (registrarDB.containsKey(GESTOR)) {
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(GESTOR)));
                request.createResponse(200).send();
            } else {
                // 404 if GESTOR not registered
                request.createResponse(404).send();
            }
            return;
        }

        // collaborator/user to collaborator/user
        if (registrarDB.containsKey(aorTo)) {
            request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(aorTo)));
            request.createResponse(200).send();
        } else {
            request.createResponse(404).send();
        }
    }

    @Override
    protected void doResponse(SipServletResponse response) throws ServletException, IOException {
        // if getContent is not null then it's an INVITE response
        if (response.getContent() != null) {
            callKPI++;
            log("======KPI======");
            log("CALL:" + callKPI);
        }

        super.doResponse(response);
    }

    /**
     * Customs functions
     */
    protected boolean inDomain(SipServletRequest request) {
        return getAttr(request.getHeader("From"), "@").equals("@acme.pt");
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