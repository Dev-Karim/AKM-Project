import java.io.*;
import java.net.*;
import java.util.*;

// WebServer: listens on a fixed port and spawns a new thread for every
// incoming TCP connection, making the server capable of handling multiple
// clients simultaneously (multithreaded / non-persistent HTTP).
public class WebServer
{
    public static void main(String[] args) throws Exception
    {
        // Set the port number. Ports above 1024 do not require admin rights.
        int port = 6789;

        // Create the server socket and bind it to the chosen port.
        // The server will block on accept() until a client connects.
        ServerSocket serverSocket = new ServerSocket(port);

        System.out.println("Web server running on port " + port);
        System.out.println("Visit: http://localhost:" + port + "/index.html");
        System.out.println("Press Ctrl+C to stop.\n");

        // Process HTTP requests by means of an infinite loop.
        // Each accepted connection is handled in its own thread so the
        // main thread returns immediately to wait for the next client.
        while (true)
        {
            // Wait to be contacted. accept() blocks until a client connects.
            Socket clientSocket = serverSocket.accept();

            // Construct an object to process the HTTP request message.
            HttpRequest request = new HttpRequest(clientSocket);

            // Create a new thread attached to the request.
            Thread thread = new Thread(request);

            // Start the execution of the thread.
            thread.start();
        }
    }
}

// HttpRequest: implements Runnable so each instance can run in its own thread.
// Handles one complete HTTP request-response cycle, then closes the connection.
class HttpRequest implements Runnable
{
    // CRLF is the line terminator required by the HTTP specification.
    final String CRLF = "\r\n";

    // The socket representing the connection to this particular client.
    final Socket socket;

    // Constructor stores the client socket for use in processRequest().
    public HttpRequest(Socket socket) throws Exception
    {
        this.socket = socket;
    }

    // run() is called by Thread.start(). It delegates all work to
    // processRequest() and catches any exception so the thread exits cleanly.
    public void run()
    {
        try { processRequest(); }
        catch (Exception e)
        {
            System.out.println(e);
        }
    }

    // processRequest() reads the HTTP request, locates the requested file,
    // builds the appropriate HTTP response, and sends it back to the client.
    private void processRequest() throws Exception
    {
        // Get a reference to the socket's input and output streams.
        InputStream is = socket.getInputStream();
        DataOutputStream os = new DataOutputStream(socket.getOutputStream());

        // Set up input stream filters so we can read text lines from the client.
        BufferedReader br = new BufferedReader(new InputStreamReader(is));

        // Get the request line of the HTTP request message (e.g. GET /index.html HTTP/1.0).
        String requestLine = br.readLine();

        // Display the request line.
        System.out.println();
        System.out.println(requestLine);

        // Get and display the header lines until the blank line that ends the headers.
        String headerLine = null;
        while ((headerLine = br.readLine()) != null && !headerLine.isEmpty())
        {
            System.out.println(headerLine);
        }

        // Extract the filename from the request line using StringTokenizer.
        StringTokenizer tokens = new StringTokenizer(requestLine);
        tokens.nextToken();                // skip over the method, which should be "GET"
        String fileName = tokens.nextToken(); // extract the requested URI

        // Prepend a "." so that the file request is resolved within the current directory.
        // e.g. "/index.html" becomes "./index.html"
        fileName = "." + fileName;

        // If the client requested the root "/", serve index.html as the default document.
        if (fileName.equals("./"))
        {
            fileName = "./index.html";
        }

        // Open the requested file. If it does not exist, set fileExists to false
        // so we can send a 404 error response instead of crashing the thread.
        FileInputStream fis = null;
        boolean fileExists = true;

        try
        {
            fis = new FileInputStream(fileName);
        }
        catch (FileNotFoundException e)
        {
            fileExists = false;
        }

        // Construct the response message.
        String statusLine      = null;
        String contentTypeLine = null;
        String entityBody      = null;

        if (fileExists)
        {
            // File found: send 200 OK with the correct MIME type.
            statusLine      = "HTTP/1.0 200 OK" + CRLF;
            contentTypeLine = "Content-type: " + contentType(fileName) + CRLF;
        }
        else
        {
            // File not found: send 404 Not Found with a small HTML error page.
            statusLine      = "HTTP/1.0 404 Not Found" + CRLF;
            contentTypeLine = "Content-type: text/html" + CRLF;
            entityBody      = "<HTML>" +
                              "<HEAD><TITLE>Not Found</TITLE></HEAD>" +
                              "<BODY>Not Found</BODY></HTML>";
        }

        // Send the status line.
        os.writeBytes(statusLine);

        // Send the content type line.
        os.writeBytes(contentTypeLine);

        // Send a blank line to indicate the end of the header lines.
        os.writeBytes(CRLF);

        // Send the entity body: the file bytes if found, or the error HTML if not.
        if (fileExists)
        {
            sendBytes(fis, os);
            fis.close();
        }
        else
        {
            os.writeBytes(entityBody);
        }

        // Close streams and socket (non-persistent: one request per connection).
        os.close();
        br.close();
        socket.close();
    }

    // sendBytes copies the file contents into the socket's output stream
    // using a 1 KB buffer, which is correct for both text and binary files.
    private static void sendBytes(FileInputStream fis, OutputStream os) throws Exception
    {
        // Construct a 1K buffer to hold bytes on their way to the socket.
        byte[] buffer = new byte[1024];
        int bytes = 0;

        // Copy requested file into the socket's output stream.
        while ((bytes = fis.read(buffer)) != -1)
        {
            os.write(buffer, 0, bytes);
        }
    }

    // contentType examines the file extension and returns the matching MIME type
    // string so the browser knows how to render or handle the response body.
    private static String contentType(String fileName)
    {
        if (fileName.endsWith(".htm") || fileName.endsWith(".html"))
        {
            return "text/html";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg"))
        {
            return "image/jpeg";
        }
        if (fileName.endsWith(".gif"))
        {
            return "image/gif";
        }
        if (fileName.endsWith(".png"))
        {
            return "image/png";
        }
        if (fileName.endsWith(".css"))
        {
            return "text/css";
        }
        if (fileName.endsWith(".js"))
        {
            return "application/javascript";
        }
        if (fileName.endsWith(".pdf"))
        {
            return "application/pdf";
        }
        if (fileName.endsWith(".ico"))
        {
            return "image/x-icon";
        }
        if (fileName.endsWith(".txt"))
        {
            return "text/plain";
        }

        // Unknown file type: send as raw binary data.
        return "application/octet-stream";
    }
}
