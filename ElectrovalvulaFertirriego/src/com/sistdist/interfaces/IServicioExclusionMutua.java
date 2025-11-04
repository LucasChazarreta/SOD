package com.sistdist.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Servicio remoto que expone operaciones de exclusión mutua.
 */
public interface IServicioExclusionMutua extends Remote {

    void ObtenerRecurso(IClienteEM cliente) throws RemoteException;

    void DevolverRecurso() throws RemoteException;
}
