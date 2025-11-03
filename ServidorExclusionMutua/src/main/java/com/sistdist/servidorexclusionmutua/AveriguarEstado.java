/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sistdist.servidorexclusionmutua;

import com.sistdist.interfaces.IDetectorFalla;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author lesca
 */
public class AveriguarEstado extends TimerTask {
    
    DetectorFallo detector;
    
    public AveriguarEstado(DetectorFallo d){
        detector = d;
    }

    @Override
    public void run() {
        if (!detector.getNombre().equals("ServidorMaestro")) {
            try {
                IDetectorFalla servidorMaestro = (IDetectorFalla) Naming.lookup("rmi://localhost:9000/ServidorMaestro");
                detector.llegoMensaje = false;
                servidorMaestro.DameMensaje((IDetectorFalla) detector, "vivo?");
            } catch (NotBoundException ex) {
                Logger.getLogger(AveriguarEstado.class.getName()).log(Level.SEVERE, null, ex);
            } catch (MalformedURLException ex) {
                Logger.getLogger(AveriguarEstado.class.getName()).log(Level.SEVERE, null, ex);
            } catch (RemoteException ex) {
                Logger.getLogger(AveriguarEstado.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        
        
        
    }
    
    
    
}
