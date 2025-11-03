/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.sistdist.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author lesca
 */
public interface IServicioExclusionMutua extends Remote {
    public void ObtenerRecurso(IClienteEM cliente) throws RemoteException;
    public void DevolverRecurso() throws RemoteException;
    boolean ping() throws RemoteException;
}
