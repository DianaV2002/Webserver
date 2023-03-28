package es.udc.redes.webserver;

import java.lang.reflect.Array;
import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class ServerThread extends Thread {

    private Socket socket;

    public ServerThread(Socket s) {
        // Store the socket s
        this.socket = s;
    }

    public void run() {

        try {
            //make the server also respond with an error html page!

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream writer = socket.getOutputStream();
            //set the date
            SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date date = new Date();

            String dateFormat = dateFormatter.format(date);
            String requestLine = reader.readLine();
            String[] parts = requestLine.split(" ");
          ;

            if (parts.length != 3) {
                SendBadRequest(writer, dateFormat);
                return;
            }
            String method = parts[0];
            String resource = parts[1];//file
            String httpVersion = parts[2];

            while (resource.startsWith(File.separator)) {
                resource = resource.substring(1);
            }//we delete the /
            //we set the path of the file from the working directory p1-files
            File file = new File((System.getProperty("user.dir") + File.separator + "p1-files" + File.separator).concat(resource));

            //we read the if modified since header if it exists
            String ifModifiedSinceHeader = null;
            while (!(requestLine = reader.readLine()).isBlank()) {
                if (requestLine.startsWith("If-Modified-Since"))
                    ifModifiedSinceHeader = requestLine;
            }

            if (ifModifiedSinceHeader != null) {
                String ifModifiedSinceDate = ifModifiedSinceHeader.substring(("If-Modified-Since:").length()).trim();
                ;
                Date lastModifiedDate = new Date(file.lastModified());
                String lastDateString = lastModifiedDate.toString();
                if (ifModifiedSinceDate.equals(lastDateString)) {
                    SendNotModified(writer, String.valueOf(lastModifiedDate));
                    //if it is not modified we do not have to call the HandleGetMethod
                    return;
                }
            }

            if (method.equals("GET")) {
                HandleGetRequest(writer, dateFormat, file);
            } else if (method.equals("HEAD")) {
                HandleHeadRequest(writer, dateFormat, file);
            } else {
                SendBadRequest(writer, dateFormat);
                return;
            }

            reader.close();
            writer.flush();
            writer.close();
            if (socket != null)
                socket.close();

            System.out.println("Client disconnected");
        } catch (SocketTimeoutException e) {
            System.err.println("Nothing received in 300 secs");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {

        }
    }

    String getContentType(File file) {
        String path = file.getPath();
        if (path.endsWith(".html")) {
            return "text/html";
        } else if (path.endsWith(".txt")) {
            return "text/plain";
        } else if (path.endsWith(".gif")) {
            return "image/gif";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else {
            return "application/octet-stream";
        }
    }

    private void SendNotModified(OutputStream writer, String date) throws IOException {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
        LocalDateTime dateNow = LocalDateTime.now();
        writer.write(("HTTP/1.0 304 Not Modified\r\n"
                + "Date: " + dtf.format(dateNow) + "\r\n"
                + "Server: MyServer/1.0.0" + "\r\n"
                + "\r\n").getBytes());
        socket.close();
    }

    private void SendBadRequest(OutputStream writer, String date) throws IOException {
        writer.write("HTTP/1.0 400 Bad Request\r\n".getBytes());
        writer.write(("Date: " + date + "\r\n").getBytes());
        writer.write(("Server: MyServer/1.0.0").getBytes());
        writer.write("\n".getBytes());//empty line
        
        String errpath=System.getProperty("user.dir") + File.separator + "p1-files" +File.separator+"error400.html";
        File errfile= new File(errpath);
        byte[] error = readFile(errpath);
        errfile= createErrFile(errpath,400);
        error = readFile(errpath);
        writer.write(error);
        socket.close();
    }

    private void SendNotFound(OutputStream writer, String date, File file) throws IOException {
        writer.write("HTTP/1.0 404 Not Found\r\n".getBytes());
        writer.write(("Date: " + date + "\r\n").getBytes());
        writer.write(("Server: MyServer/1.0.0").getBytes());
        writer.write("\n".getBytes());
        //we generate the error html file
        String errpath=System.getProperty("user.dir") + File.separator + "p1-files" +File.separator+"error400.html";
        File errfile= new File(errpath);
        byte[] error = readFile(errpath);
        errfile= createErrFile(errpath,404);
        error = readFile(errpath);
        writer.write(error);
    }

    private void HandleGetRequest(OutputStream writer, String date, File file) throws IOException {

        String contentType = getContentType(file);
        long contentLength = file.length();
        Date lastModifiedDate = new Date(file.lastModified());
        if (!file.exists() || file.isDirectory()) {
            SendNotFound(writer, date, file);
            return;
        }
        try {
            writer.write(("HTTP/1.0 200 OK\r\n"
                    + "Date: " + date + "\r\n"
                    + "Server: MyServer/1.0.0" + "\r\n"
                    + "Last-Modified: " + lastModifiedDate + "\r\n"
                    + "Content-Length: " + contentLength + "\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "\r\n").getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (InputStream fileInput = new FileInputStream(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            int bytesRead;
            while ((bytesRead = fileInput.read(buffer.array())) != -1) {
                buffer.compact();
                writer.write(buffer.array());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void HandleHeadRequest(OutputStream writer, String date, File file) throws IOException {

        String contentType = getContentType(file);
        long contentLength = file.length();
        Date lastModifiedDate = new Date(file.lastModified());
        File checkFile = file.getCanonicalFile();
        if (!file.exists() || file.isDirectory()) {
            SendNotFound(writer, date, file);
            return;
        }
        writer.write(("HTTP/1.0 200 OK" + "\r\n"
                + "Date: " + date + "\r\n"
                + "Server: MyServer/1.0.0" + "\r\n"
                + "Last-Modified: " + lastModifiedDate + "\r\n"
                + "Content-Length: " + contentLength + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "\r\n").getBytes());
    }
    private File createErrFile(String pathFile, int error)
    {
        String content="<html>\n" +
                "   <head>\n" +
                "      <title>\n" +
                "         ERROR "+error+"\n" +
                "      </title>\n" +
                "   </head>\n" +
                "   <body>\n" +
                "      <p>ERROR "+error+"</p>\n" +
                "   </body>\n" +
                "</html>";

        try {
            File outputFile = new File(pathFile);
            BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
            bw.write(content);
            bw.flush();
            bw.close();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return new File(pathFile);
    }
    public static byte[] readFile(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) return null;
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            return null;
        }
    }
}
