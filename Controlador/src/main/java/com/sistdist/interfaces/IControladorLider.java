package com.sistdist.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interfaz remota para que los sensores puedan consultar 
 * cuál es el controlador líder actual del anillo.
 */
public interface IControladorLider extends Remote {

    /**
     * Devuelve el host o dirección RMI del líder actual del anillo.
     * Ejemplo: "rmi://localhost:1103/RingElection-CTRL3"
     */
    String obtenerLiderActual() throws RemoteException;

    /**
     * Devuelve el puerto TCP del líder (para conectar los sensores).
     * Ejemplo: 20002
     */
    int obtenerPuertoLider() throws RemoteException;
}
