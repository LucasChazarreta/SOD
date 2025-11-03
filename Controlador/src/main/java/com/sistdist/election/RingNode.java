package com.sistdist.election;

import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.ExportException;

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

    // Hook para iniciar EM local cuando este nodo es líder (inyectado desde fuera)
    private final Runnable onBecomeLeader;
    // Hook para actualizar la dirección/lookup del EM en los clientes locales (Controlador/Fertirrigación)
    private final Consumer<Long> onLeaderAnnounced;

    // ====== Reintentos/básicos ======
    private static final int LOOKUP_RETRIES = 3;
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

        RingNode node = new RingNode(uid, bind, succ, onBecomeLeader, onLeaderAnnounced);
        Naming.rebind(bind, node);
        log("RingNode bound en: " + bind + " | UID=" + uid + " | sucesor=" + succ);
        return node;
    }

    private RingNode(long myUid,
                     String myBindName,
                     String successorBindName,
                     Runnable onBecomeLeader,
                     Consumer<Long> onLeaderAnnounced) throws RemoteException {
        super(0);
        this.myUid = myUid;
        this.myBindName = myBindName;
        this.successorBindName = successorBindName;
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
        String next = successorBindName;
        for (int i = 0; i < LOOKUP_RETRIES; i++) {
            RingElection succ = lookup(next);
            if (succ != null) {
                try {
                    succ.onElection(uid);
                    return;
                } catch (Exception e) {
                    log("Fallo al enviar ELECTION a " + next + " -> " + e.getMessage());
                }
            }
            sleep(RETRY_DELAY_MS);
            // opcional: hook para actualizar dinámicamente el sucesor si cambiara por config o descubrimiento
        }
        log("ERROR: No se pudo enviar ELECTION. Verificá topología del anillo/sucesor.");
    }

    private void sendElected(long leaderUid) {
        String next = successorBindName;
        for (int i = 0; i < LOOKUP_RETRIES; i++) {
            RingElection succ = lookup(next);
            if (succ != null) {
                try {
                    succ.onElected(leaderUid);
                    return;
                } catch (Exception e) {
                    log("Fallo al enviar ELECTED a " + next + " -> " + e.getMessage());
                }
            }
            sleep(RETRY_DELAY_MS);
        }
        log("ADVERTENCIA: No pude propagar ELECTED. Reintentará el siguiente hop cuando el sucesor regrese.");
    }

    private RingElection lookup(String bind) {
        try {
            return (RingElection) Naming.lookup(bind);
        } catch (NotBoundException | MalformedURLException | RemoteException e) {
            log("lookup falló: " + bind + " -> " + e.getMessage());
            return null;
        }
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
}
