
package com.sistdist.electrovalvula2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Electrovalvula2 {

    private static final int ID = 2;
    private static final String HOST = "127.0.0.1";
    private static final int PUERTO = 20000;

    public static void main(String[] args) {
        System.out.println("run:");
        try (Socket socket = new Socket(HOST, PUERTO);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("electrovalvula;" + ID);
            out.flush();
            System.out.printf("[EV%d] Handshake enviado: electrovalvula;%d%n", ID, ID);
            System.out.printf("[EV%d] Conectada al controlador.%n", ID);

            String comando;
            while ((comando = in.readLine()) != null) {
                comando = comando.trim();

                if (comando.regionMatches(true, 0, "log|", 0, 4)) {
                    System.out.println(comando.substring(4));
                    continue;
                }

                if ("abrir".equalsIgnoreCase(comando)) {
                    System.out.printf("[EV%d] Válvula abierta, bomba activada.%n", ID);
                    out.println("EV" + ID + ":ok_abierta");
                } else if ("cerrar".equalsIgnoreCase(comando)) {
                    System.out.printf("[EV%d] Válvula cerrada, bomba detenida.%n", ID);
                    out.println("EV" + ID + ":ok_cerrada");
                } else {
                    System.out.printf("[EV%d] Comando desconocido: %s%n", ID, comando);
                    out.println("EV" + ID + ":error");
                }

                out.flush();
            }
        } catch (IOException e) {
            System.err.printf("[EV%d] Error de comunicación: %s%n", ID, e.getMessage());
            e.printStackTrace();
        }
    }
}
