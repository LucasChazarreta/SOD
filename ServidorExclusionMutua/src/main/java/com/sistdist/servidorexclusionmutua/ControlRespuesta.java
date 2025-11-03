/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sistdist.servidorexclusionmutua;

import java.util.TimerTask;

/**
 *
 * @author lesca
 */
public class ControlRespuesta extends TimerTask {
    
    DetectorFallo detector;
    
    public ControlRespuesta(DetectorFallo d){
        detector = d;
    }

    @Override
    public void run() {
        if (!detector.getNombre().equals("ServidorMaestro")) {
            detector.chequearRespuesta();
        }
    }
    
}
