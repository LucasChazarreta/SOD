package com.sistdist.servidorexclusionmutua;

import com.sistdist.interfaces.IClienteEM;
import com.sistdist.interfaces.IServicioExclusionMutua;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Servidor EM embebible: se inicia en el puerto que el líder indique.
 */
public class ServerExclusionMutuaRMI extends UnicastRemoteObject implements IServicioExclusionMutua {

    // ---------- Singleton embebido ----------
    private static volatile ServerExclusionMutuaRMI instancia;
    private static volatile int puertoActual = -1;

    // ---------- Estado del recurso/token ----------
    private final Queue<IClienteEM> cola = new ArrayDeque<>();
    private boolean recursoLibre = true;

    // Constructor remoto
    protected ServerExclusionMutuaRMI() throws RemoteException {
        super();
    }

    // ========== Bootstrap embebido ==========
    public static synchronized void iniciar(int puertoRmi) {
        try {
            if (instancia != null && puertoActual == puertoRmi) {
                return;
            }

            try {
                LocateRegistry.createRegistry(puertoRmi);
            } catch (RemoteException ignore) {
            }

            instancia = new ServerExclusionMutuaRMI();
            Naming.rebind("rmi://localhost:" + puertoRmi + "/servidorCentralEM", instancia);
            puertoActual = puertoRmi;

            System.out.println("[Servidor EM] Iniciado en RMI " + puertoRmi + " (bind servidorCentralEM)");
        } catch (Exception e) {
            throw new RuntimeException("No se pudo iniciar EM en puerto " + puertoRmi + ": " + e.getMessage(), e);
        }
    }

    public static synchronized void detener() {
        try {
            if (instancia != null) {
                UnicastRemoteObject.unexportObject(instancia, true);
                instancia = null;
                puertoActual = -1;
                System.out.println("[Servidor EM] Detenido.");
            }
        } catch (Exception e) {
            System.err.println("[Servidor EM] Error al detener: " + e.getMessage());
        }
    }

    // ========== Implementación de la interfaz ==========
    @Override
    public synchronized void ObtenerRecurso(IClienteEM cliente) throws RemoteException {
        if (cliente == null) {
            return;
        }

        if (recursoLibre && cola.isEmpty()) {
            recursoLibre = false;
            try {
                cliente.RecibirToken();
            } catch (RemoteException e) {
                recursoLibre = true;
                throw e;
            }
        } else {
            cola.offer(cliente);
        }
    }

    @Override
    public synchronized void DevolverRecurso() throws RemoteException {
        IClienteEM siguiente = cola.poll();
        if (siguiente != null) {
            try {
                siguiente.RecibirToken();
                recursoLibre = false;
            } catch (RemoteException e) {
                recursoLibre = true;
                while ((siguiente = cola.poll()) != null) {
                    try {
                        siguiente.RecibirToken();
                        recursoLibre = false;
                        break;
                    } catch (RemoteException ignore) {
                        recursoLibre = true;
                    }
                }
            }
        } else {
            recursoLibre = true;
        }
    }

   
}
