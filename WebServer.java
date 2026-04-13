import java.io.*;
import java.net.*;
import java.util.*;

public class WebServer
{
    public static void main(String[] args) throws Exception
    {
        // TODO: Create the server socket.
        // TODO: Accept client connections in a loop.
        // TODO: Create an HttpRequest object for each connection.
        // TODO: Start a new thread for each request.
    }
}

class HttpRequest implements Runnable
{
    private Socket socket;

    public HttpRequest(Socket socket)
    {
        this.socket = socket;
    }

    public void run()
    {
        // TODO: Handle one HTTP request here.
    }

    private void processRequest() throws Exception
    {
        // TODO: Read the request line and headers.
        // TODO: Parse the requested file name.
        // TODO: Open the requested file.
        // TODO: Build the HTTP response.
        // TODO: Send the file bytes or a 404 response.
        // TODO: Close streams and socket.
    }

    private static void sendBytes(FileInputStream fis, OutputStream os) throws Exception
    {
        // TODO: Send file contents to the client.
    }

    private static String contentType(String fileName)
    {
        // TODO: Return the correct MIME type for the file.
        return null;
    }
}
