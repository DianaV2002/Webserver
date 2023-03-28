package es.udc.redes.webserver;

import es.udc.redes.webserver.ServerThread;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class WebServer {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Format: <port>");
            System.exit(-1);
        }
        try {
            // Create a server socket
            ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]));
            System.out.println("WEB Server listening on port " + Integer.parseInt(args[0]));
            serverSocket.setSoTimeout(300000);
            // Set a timeout of 300 secs
            while (true) {
                try {
                    // Wait for connections
                    // Create a ServerThread object, with the new connection as parameter
                    // Initiate thread using the start() method
                    Socket clientSocket = serverSocket.accept();
                    Thread serverThread = new ServerThread(clientSocket);
                    serverThread.start();

                } catch (SocketTimeoutException e) {
           System.err.println("Nothing received in 300 secs");
           } catch (Exception e) {
          System.err.println("Error: " + e.getMessage());
          e.printStackTrace();}
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
        }

    }
}
