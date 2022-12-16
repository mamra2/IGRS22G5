import javax.servlet.ServletException;
import javax.servlet.sip.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Redirect extends SipServlet {
    static private Map<String, String> registrarDB; // registration db
    static private Map<String, Boolean> stateDB; // online/offline db
    static private ArrayList<String> colabDB; // collaborator db
    static private SipFactory sipFactory;

    static final private String GESTOR = "sip:gestor@acme.pt";
    static final private String ALERTA = "sip:alerta@acme.pt";
    static final private String CONF = "sip:conference@acme.pt";
    static final private String SEMS = "sip:conference@127.0.0.1:5070";
    static private boolean conf = false;

    static private int smsIN = 0;
    static private int confDone = 0;
    static private int callDone = 0;
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
        String expiry = expiresHeader != null ? expiresHeader : contactHeader.split("=")[1];
        log(expiresHeader != null ? "LINPHONE" : "TWINKLE");
        if (expiry.equals("0")) {
            log("SIP UNREGISTER");

            if (!registrarDB.containsKey(aor)) {
                request.createResponse(404).send();
            } else {
                registrarDB.remove(aor);
                stateDB.remove(aor);
                request.createResponse(200).send();
            }
        } else {
            log("SIP REGISTER");

            // this already handles ip/port changes for existing contacts
            // kinda insecure?
            registrarDB.put(aor, contact);
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
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(GESTOR)));
            } else if (aorTo.equals(CONF) && registrarDB.containsKey(aorFrom) && colabDB.contains(aorFrom) && conf) {
                request.getProxy().proxyTo(sipFactory.createURI(SEMS));
            } else {
                request.createResponse(404).send();
            }
        } else {
            if (aorTo.equals(GESTOR)) {
                request.createResponse(403).send();
            } else {
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(aorTo)));
            }
        }
    }

    @Override
    protected void doMessage(SipServletRequest request) throws ServletException, IOException {
        if (!inDomain(request)) {
            request.createResponse(403).send();
            return;
        }

        smsIN++;
        log("======kp1====");
        log( "SMS IN:"+ smsIN);

        String aorFrom = getAttr(request.getHeader("From"), "sip:");
        String aorTo = getAttr(request.getHeader("To"), "sip:");

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
                    request.createResponse(200).send();
                    conf = !conf;

                    if (conf) {
                        confDone++;
                        log("======kpi======");
                        log("CONF DONE: " + confDone);

                        String[] cmd = {"bash", "-c", "./launch.sh"};
                        Runtime.getRuntime().exec(cmd, null, new File("/home/igrs/Desktop/igrs-tools/sems"));

                        for (String c : colabDB) {
                            if (c.equals(GESTOR)) {
                                continue;
                            }

                            if (!stateDB.get(c)) {
                                continue;
                            }

                            SipServletRequest res = sipFactory.createRequest(
                                    request.getApplicationSession(),
                                    "MESSAGE",
                                    ALERTA,
                                    registrarDB.get(c)
                            );
                            res.setContent("Call sip:conference@acme.pt", "text/plain");
                            res.send();
                        }
                    } else {
                        // kill with 2 SIGINT (INT) so everyone gets disconnected
                        String[] cmd = {"bash", "-c", "sudo killall -s 2 sems"};
                        Runtime.getRuntime().exec(cmd);

                        for (String c : colabDB) {
                            if (c.equals(GESTOR)) {
                                continue;
                            }

                            if (!stateDB.get(c)) {
                                continue;
                            }

                            SipServletRequest res = sipFactory.createRequest(
                                    request.getApplicationSession(),
                                    "MESSAGE",
                                    ALERTA,
                                    registrarDB.get(c)
                            );
                            res.setContent("The conference is over.", "text/plain");
                            res.send();
                        }
                    }
                    break;
                }
                case "ALERT":
                    log("==========================ALERT");
                    for (String c : colabDB) {
                        if (c.equals(GESTOR)) {
                            continue;
                        }

                        SipServletRequest res = sipFactory.createRequest(
                                request.getApplicationSession(),
                                "MESSAGE",
                                ALERTA,
                                registrarDB.get(c)
                        );

                        res.setContent(request.getRawContent(), "text/plain");
                        res.send();
                    }
                    request.createResponse(200).send();
                    break;
                default:
                    request.createResponse(403).send();
            }
            return;
        }

        // any directly to manager
        if (aorTo.equals(GESTOR)) {
            request.createResponse(403).send();
            return;
        }

        // user to manager
        if (aorTo.equals(ALERTA)) {
            if (aorFrom.contains("colaborador")) {
                request.createResponse(403).send();
            } else if (registrarDB.containsKey(GESTOR)) {
                request.getProxy().proxyTo(sipFactory.createURI(registrarDB.get(GESTOR)));
                request.createResponse(200).send();
            } else {
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
    protected void doBye(SipServletRequest request) throws ServletException, IOException {
        callDone++;
        log("======kp1====");
        log( "CALL DONE:"+ callDone);

        super.doBye(request);
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