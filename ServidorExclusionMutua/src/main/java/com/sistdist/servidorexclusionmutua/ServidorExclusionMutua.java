/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.sistdist.servidorexclusionmutua;

import com.sistdist.interfaces.IDetectorFalla;
import com.sistdist.interfaces.IServicioExclusionMutua;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ServidorExclusionMutua {

    public static void main(String[] args) {
        
        String nombreServidor = "ServidorMaestro";
        int nroPuerto = 1100;
        Timer planificador = new Timer();
        
        try {            
            LocateRegistry.createRegistry(nroPuerto);
            DetectorFallo detector = new DetectorFallo();
            detector.setNombre(nombreServidor);
            
            Naming.rebind("rmi://localhost:" + nroPuerto  + "/" + nombreServidor, (IDetectorFalla) detector);
            
            
            AveriguarEstado tareaAveriguarEstado = new AveriguarEstado(detector);
            ControlRespuesta tareaControlRespuesta = new ControlRespuesta(detector);
            planificador.schedule(tareaAveriguarEstado, 0, 5000);
            planificador.schedule(tareaControlRespuesta, 0, 8000);
            
            int nroPuertoExcMutua = 10000;
            LocateRegistry.createRegistry(nroPuertoExcMutua);
            ServerExclusionMutuaRMI serverEM = new ServerExclusionMutuaRMI();
            
            Naming.rebind("rmi://localhost:" + nroPuertoExcMutua  + "/servidorCentralEM", (IServicioExclusionMutua) serverEM);
           
            System.out.println("[Servidor EM] Iniciado y escuchando en puertos 1100 y 10000");

        } catch (RemoteException ex) {
            Logger.getLogger(ServidorExclusionMutua.class.getName()).log(Level.SEVERE, null, ex);
        } catch (MalformedURLException ex) {
            Logger.getLogger(ServidorExclusionMutua.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    public boolean ping() throws RemoteException {
        return true;
    }
}






