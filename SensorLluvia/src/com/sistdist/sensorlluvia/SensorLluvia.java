package com.sistdist.sensorlluvia;

import java.io.PrintWriter;
import java.net.*;
import java.util.Random;

public class SensorLluvia {
    public static void main(String[] args) {
        try {
            InetAddress ipServidor = InetAddress.getByName("127.0.0.1");
            Socket cliente = new Socket(ipServidor, 20000);
            PrintWriter out = new PrintWriter(cliente.getOutputStream(), true);

            out.println("sensorLluvia;1");
            out.flush();
            System.out.println("[SensorLluvia] Conectado al controlador.");

            Random rand = new Random();

            while (true) {
                // Simulación: 20% de probabilidad de lluvia
                int valor = rand.nextDouble() < 0.2 ? 1 : 0;

                // Enviar valor
                out.println(valor);
                out.flush();

                System.out.println("[SensorLluvia] Valor enviado = " + valor);

                // Cada 5 segundos
                Thread.sleep(5000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

