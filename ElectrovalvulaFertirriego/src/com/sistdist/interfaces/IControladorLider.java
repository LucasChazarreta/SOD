package com.sistdist.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interfaz remota para descubrir el controlador líder actual.
 */
public interface IControladorLider extends Remote {

    String obtenerLiderActual() throws RemoteException;

    int obtenerPuertoLider() throws RemoteException;
}
