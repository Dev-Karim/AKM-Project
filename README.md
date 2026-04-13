# Computer Networks Project — AKM Group

This repository contains the Java web server project for **CMPS 242: Computer Networks** at the **American University of Beirut (AUB)**.

## Group

**AKM Group**

## Project Overview

The goal of this project is to implement a simple **multithreaded HTTP web server in Java**.  
The server is designed to handle multiple client requests in parallel using threads and to support basic web content delivery over non-persistent TCP connections.

The project follows two main development stages:

1. **Step 1:** Build a multithreaded server that accepts browser connections and displays the HTTP request message.
2. **Step 2:** Extend the server so it parses the request, locates the requested file, and sends the correct HTTP response back to the client.

## Main Features

- Java-based web server
- Multithreaded request handling
- HTTP request parsing
- File serving from the local directory
- MIME type detection
- Error handling for missing or invalid requests
- Browser-based testing on a custom port

## Repository Structure

- `WebServer.java` — main Java source file for the server
- `index.html` — sample homepage for testing
- `about.html` — secondary sample page
- `style.css` — stylesheet for the sample website
- other assets such as images, PDF files, icons, or scripts may be added for testing

## How to Run

1. Compile the Java file:
   ```bash
   javac WebServer.java
   ```

2. Run the server:
   ```bash
   java WebServer
   ```

3. Open a browser and visit:
   ```text
   http://localhost:6789/
   ```

You can also request specific files such as:

```text
http://localhost:6789/index.html
http://localhost:6789/about.html
```

## Notes

- The server runs on a non-default port, so the port number must be included in the browser URL.
- All files to be served should be placed in the same working directory as the server, unless the code is later adjusted to use another root folder.
- This repository may be updated as different group members complete their assigned parts.

## Course Information

- **Course:** CMPS 242 — Computer Networks
- **University:** American University of Beirut (AUB)

## Authors

Developed by **AKM Group**.
