package com.sistdist.election;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interfaz RMI para el Algoritmo de Elección en Anillo.
 * Mensajes:
 *  - ELECTION(uid): circula el máximo UID.
 *  - ELECTED(leaderUid): anuncia el coordinador.
 */
public interface RingElection extends Remote {

    /** Mensaje de ELECCIÓN: transporta el UID máximo visto. */
    void onElection(long candidateUid) throws RemoteException;

    /** Mensaje de ANUNCIO: anuncia el líder elegido. */
    void onElected(long leaderUid) throws RemoteException;

    /** Ping simple para health-check entre vecinos. */
    boolean ping() throws RemoteException;
}
