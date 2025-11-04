package com.sistdist.election;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Implementación del anillo (Chang & Roberts) con salto de vecino caído.
 * - Cada nodo conoce a su sucesor (URL RMI) y posee un UID único.
 * - Integra con tu detector de fallos: startElection() ante caída del EM.
 * - Cuando este nodo gana: arranca el Servidor de EM y anuncia líder.
 */
public class RingNode extends UnicastRemoteObject implements RingElection {

    // ====== Config dinámica de este nodo ======
    private final long myUid;                          // UID único de este nodo
    private final String myBindName;                   // p.ej. "rmi://127.0.0.1:1099/RingElection-CTRL1"
    private volatile String successorBindName;         // URL RMI del sucesor
    private volatile long currentLeader = -1;          // UID del líder conocido
    private final AtomicBoolean participant = new AtomicBoolean(false);
    private final RingParticipant[] participants;      // Topología conocida (ordenada)
    private final int myIndex;

    // Hook para iniciar EM local cuando este nodo es líder (inyectado desde fuera)
    private final Runnable onBecomeLeader;
    // Hook para actualizar la dirección/lookup del EM en los clientes locales (Controlador/Fertirrigación)
    private final Consumer<Long> onLeaderAnnounced;

    // ====== Reintentos/básicos ======
    private static final int RETRY_DELAY_MS = 500;

    // ====== Builder/Factory ======
    public static RingNode createAndBind(Properties cfg,
                                         Runnable onBecomeLeader,
                                         Consumer<Long> onLeaderAnnounced) throws Exception {
        long uid = Long.parseLong(Objects.requireNonNull(cfg.getProperty("ring.uid")));
        String bind = Objects.requireNonNull(cfg.getProperty("ring.myBind"));
        String succ = Objects.requireNonNull(cfg.getProperty("ring.successorBind"));
        int rmiPort = Integer.parseInt(cfg.getProperty("ring.registryPort", "1099"));

        // Levanta RMI Registry local si no existe
        try {
            LocateRegistry.createRegistry(rmiPort);
            log("RMI Registry levantado en puerto " + rmiPort);
        } catch (ExportException already) {
            log("RMI Registry ya existente en puerto " + rmiPort);
        }

        RingParticipant[] participants = parseParticipants(cfg, uid, bind, succ);

        RingNode node = new RingNode(uid, bind, succ, participants, onBecomeLeader, onLeaderAnnounced);
        Naming.rebind(bind, node);
        log("RingNode bound en: " + bind + " | UID=" + uid + " | sucesor=" + succ);
        return node;
    }

    private RingNode(long myUid,
                     String myBindName,
                     String successorBindName,
                     RingParticipant[] participants,
                     Runnable onBecomeLeader,
                     Consumer<Long> onLeaderAnnounced) throws RemoteException {
        super(0);
        this.myUid = myUid;
        this.myBindName = myBindName;
        this.successorBindName = successorBindName;
        this.participants = participants;
        this.myIndex = locateIndex(participants, myBindName);
        this.onBecomeLeader = onBecomeLeader;
        this.onLeaderAnnounced = onLeaderAnnounced;
    }

    // ====== API pública para disparar elección ======
    public void startElection() {
        if (participant.compareAndSet(false, true)) {
            log("Iniciando ELECCIÓN. Enviando ELECTION(" + myUid + ") al sucesor.");
            sendElection(myUid);
        } else {
            log("Elección ya en curso (participant = true). No se reinicia.");
        }
    }

    // ====== Implementación RingElection ======
    @Override
    public synchronized void onElection(long candidateUid) throws RemoteException {
        // Regla Chang & Roberts:
        // - Si candidateUid < myUid => reemplazo y reenvío myUid
        // - Si candidateUid > myUid => reenvío candidateUid
        // - Si candidateUid == myUid => completé el anillo -> soy líder
        if (candidateUid < myUid) {
            participant.set(true);
            log("ELECTION(" + candidateUid + ") recibido. Reemplazo por myUid=" + myUid + " y reenvío.");
            sendElection(myUid);
        } else if (candidateUid > myUid) {
            participant.set(true);
            log("ELECTION(" + candidateUid + ") recibido. Reenvío sin cambios.");
            sendElection(candidateUid);
        } else { // candidateUid == myUid
            // ¡Di la vuelta! Soy el líder.
            currentLeader = myUid;
            participant.set(false);
            log("Soy LÍDER. UID=" + myUid + ". Arrancando EM local y anunciando ELECTED...");
            // 1) Arranco EM local
            safeRun(onBecomeLeader);
            // 2) Anuncio
            sendElected(myUid);
        }
    }

    @Override
    public synchronized void onElected(long leaderUid) throws RemoteException {
        currentLeader = leaderUid;
        participant.set(false);
        log("ELECTED(" + leaderUid + ") recibido. Nuevo coordinador: " + leaderUid + ". Propago anuncio...");
        safeAcceptLeader(leaderUid);
        // Propagar hasta volver al líder
        if (leaderUid != myUid) {
            sendElected(leaderUid);
        } else {
            log("Anuncio ELECTED completó la vuelta al líder. Elección finalizada.");
        }
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    // ====== Helpers de envío al sucesor ======
    private void sendElection(long uid) {
        // Intenta sucesor actual; si no responde, intenta "saltar" buscando el siguiente válido.
        if (!sendWithFallback(uid, true)) {
            log("ERROR: No se pudo enviar ELECTION. Verificá topología del anillo/sucesor.");
        }
    }

    private void sendElected(long leaderUid) {
        if (!sendWithFallback(leaderUid, false)) {
            log("ADVERTENCIA: No pude propagar ELECTED. Reintentará el siguiente hop cuando el sucesor regrese.");
        }
    }

    private RingElection lookup(String bind) {
        try {
            return (RingElection) Naming.lookup(bind);
        } catch (NotBoundException | MalformedURLException | RemoteException e) {
            log("lookup falló: " + bind + " -> " + e.getMessage());
            return null;
        }
    }

    private boolean sendWithFallback(long payload, boolean electionMessage) {
        List<String> candidates = buildCandidateList();
        if (candidates.isEmpty()) {
            return false;
        }

        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            if (candidate == null || candidate.equals(myBindName)) {
                continue;
            }

            if (i > 0 && !candidate.equals(successorBindName)) {
                setSuccessorBindName(candidate);
            }

            RingElection succ = lookup(candidate);
            if (succ == null) {
                sleep(RETRY_DELAY_MS);
                continue;
            }

            try {
                if (electionMessage) {
                    succ.onElection(payload);
                } else {
                    succ.onElected(payload);
                }
                return true;
            } catch (Exception e) {
                log((electionMessage ? "Fallo al enviar ELECTION a " : "Fallo al enviar ELECTED a ")
                        + candidate + " -> " + e.getMessage());
                sleep(RETRY_DELAY_MS);
            }
        }

        return false;
    }

    private List<String> buildCandidateList() {
        List<String> list = new ArrayList<>();
        if (successorBindName != null && !successorBindName.equals(myBindName)) {
            list.add(successorBindName);
        }
        if (participants.length <= 1) {
            return list;
        }

        for (int offset = 1; offset < participants.length; offset++) {
            int idx = (myIndex + offset) % participants.length;
            String candidate = participants[idx].bind;
            if (candidate == null || candidate.equals(myBindName)) {
                continue;
            }
            if (!list.contains(candidate)) {
                list.add(candidate);
            }
        }
        return list;
    }

    private void safeAcceptLeader(long leaderUid) {
        try {
            if (onLeaderAnnounced != null) onLeaderAnnounced.accept(leaderUid);
        } catch (Exception ex) {
            log("onLeaderAnnounced lanzó excepción: " + ex.getMessage());
        }
    }

    private void safeRun(Runnable r) {
        try {
            if (r != null) r.run();
        } catch (Exception ex) {
            log("onBecomeLeader lanzó excepción: " + ex.getMessage());
        }
    }

    public long getMyUid() { return myUid; }
    public long getCurrentLeader() { return currentLeader; }
    public String getMyBindName() { return myBindName; }
    public String getSuccessorBindName() { return successorBindName; }

    public void setSuccessorBindName(String successorBindName) {
        this.successorBindName = successorBindName;
        log("Sucesor actualizado dinámicamente a: " + successorBindName);
    }

    // ====== Utils ======
    private static void log(String s) {
        System.out.printf("[%s][RingNode] %s%n", LocalDateTime.now(), s);
    }
    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static RingParticipant[] parseParticipants(Properties cfg, long myUid, String myBind, String successorBind) {
        String raw = cfg.getProperty("ring.participants", "");
        Map<String, RingParticipant> ordered = new LinkedHashMap<>();

        if (raw != null && !raw.isBlank()) {
            String[] tokens = raw.split(",");
            for (String token : tokens) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) continue;

                String bind = trimmed;
                if (trimmed.contains("@")) {
                    String[] parts = trimmed.split("@", 2);
                    bind = parts.length > 1 ? parts[1].trim() : "";
                }

                if (!bind.isEmpty()) {
                    ordered.putIfAbsent(bind, new RingParticipant(bind));
                }
            }
        }

        ordered.putIfAbsent(myBind, new RingParticipant(myBind));
        if (successorBind != null && !successorBind.isBlank()) {
            ordered.putIfAbsent(successorBind, new RingParticipant(successorBind));
        }

        RingParticipant[] array = ordered.values().toArray(new RingParticipant[0]);
        if (array.length == 0) {
            array = new RingParticipant[] { new RingParticipant(myBind) };
        }
        return array;
    }

    private static int locateIndex(RingParticipant[] participants, String bind) {
        for (int i = 0; i < participants.length; i++) {
            if (participants[i].bind.equals(bind)) {
                return i;
            }
        }
        return 0;
    }

    private static final class RingParticipant {
        final String bind;

        RingParticipant(String bind) {
            this.bind = bind;
        }
    }
}
