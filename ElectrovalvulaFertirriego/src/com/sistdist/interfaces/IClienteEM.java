package com.sistdist.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Cliente que recibe notificaciones del servidor de exclusión mutua
 * cuando el token queda disponible.
 */
public interface IClienteEM extends Remote {

    void RecibirToken() throws RemoteException;
}
