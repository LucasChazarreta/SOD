/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sistdist.servidorexclusionmutua;

import com.sistdist.interfaces.IDetectorFalla;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 *
 * @author lesca
 */
public class DetectorFallo extends UnicastRemoteObject implements IDetectorFalla{

    boolean llegoMensaje;
    String estado;
    
    String nombre;
    
    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEstado() {
        return estado;
    }
    
    
    
    public DetectorFallo() throws RemoteException{
        super();
        llegoMensaje = false;
        estado = "no sospechoso";
    }
    
    @Override
    public void DameMensaje(IDetectorFalla cliente, String mensaje) throws RemoteException {
        
        if (mensaje.equals("vivo?")){
            cliente.DameMensaje(this, "si");
            System.out.println("Respodiendo por si");
        }
        
        if (mensaje.equals("si")){
            llegoMensaje = true;    
            System.out.println("Llego respuesta del servidor");
        }
        
    }
    
    public void chequearRespuesta(){
        if (llegoMensaje){
            estado = "no sospechoso";
        } else {
            estado = "sospechoso";
        }
        
        System.out.println(estado);
    }
    
}
