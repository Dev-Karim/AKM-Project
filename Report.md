# Project Report — Multithreaded HTTP Web Server in Java

**Course:** CMPS 242 — Computer Networks  
**University:** American University of Beirut (AUB)  
**Group:** AKM Group  
**Date:** April 2026

---

## 1. Introduction

The goal of this project is to implement a functional, multithreaded HTTP web server entirely in Java. The server listens for incoming TCP connections from a web browser, reads and parses the HTTP GET request sent by the browser, locates the requested file on the local file system, and sends back a properly formatted HTTP/1.0 response containing the file's contents and the appropriate headers.

This kind of project bridges the gap between theory and practice in computer networking. Topics covered in lectures — such as the client–server model, TCP sockets, and the HTTP protocol — are applied directly and concretely in code.

The server is implemented in a single source file, `WebServer.java`, and is tested by opening a browser and navigating to `http://localhost:6789/index.html`. Two sample HTML pages (`index.html` and `about.html`) and a stylesheet (`style.css`) are included in the repository to demonstrate that the server can handle multiple file types.

---

## 2. Solution Overview

The implementation consists of two Java classes inside one file:

### 2.1 WebServer

`WebServer` contains the `main` method and acts as the entry point. Its only responsibility is to bind a `ServerSocket` to port 6789 and enter an infinite loop. On each iteration of the loop, it calls `accept()`, which blocks until a client (the browser) connects. Once a connection arrives, the socket is wrapped in an `HttpRequest` object, and a new `Thread` is started for that object. The loop then immediately goes back to `accept()` to wait for the next client. This design ensures the server is never blocked waiting for a single client to finish.

### 2.2 HttpRequest

`HttpRequest` implements `Runnable` so it can be used with Java's `Thread` class. When `Thread.start()` is called on it, the JVM invokes the `run()` method on a new OS-level thread. `run()` simply calls `processRequest()` inside a try-catch so any exception causes the thread to exit cleanly rather than crashing the whole server.

`processRequest()` does all the real work in six steps:

1. **Set up I/O streams.** A `BufferedReader` is created over the socket's input stream to read lines of text from the browser. A `DataOutputStream` is created over the socket's output stream to write both the text headers and binary file bytes back to the browser.

2. **Read the request.** The first call to `reader.readLine()` returns the HTTP request line, for example: `GET /index.html HTTP/1.0`. The method then reads and discards the remaining header lines until it hits the blank line that marks the end of the request headers. All lines are printed to the server console so the request is visible during testing.

3. **Parse the file name.** A `StringTokenizer` splits the request line on spaces. The second token is the URI (e.g., `/index.html`). The leading slash is removed to produce a relative file-system path (`index.html`). If the URI was just `/` the file name defaults to `index.html`.

4. **Open the file.** The server tries to create a `FileInputStream` for the requested file. If a `FileNotFoundException` is thrown, a boolean flag is set to indicate the file does not exist.

5. **Build and send the response.** If the file exists, the server writes a `200 OK` status line, a `Content-Type` header (determined by `contentType()`), a blank line separator, and then the file bytes. If the file does not exist, it writes a `404 Not Found` status line and a short HTML error page as the body.

6. **Close everything.** The output stream, reader, and socket are all closed. Because this is HTTP/1.0, each connection handles exactly one request-response cycle before closing.

### 2.3 sendBytes

`sendBytes` is a static helper that copies bytes from a `FileInputStream` to the client's `OutputStream` in 1 KB chunks. Using a byte array buffer rather than reading one byte at a time is important for performance, and it is also the only correct way to transfer binary files such as images — character-oriented writers like `PrintWriter` would corrupt binary data.

### 2.4 contentType

`contentType` inspects the file extension of the requested file and returns the matching MIME type string. The MIME type is placed in the `Content-Type` response header so the browser knows how to handle the response body. For example, without a `Content-Type: image/jpeg` header, the browser might try to display image bytes as text. Supported types include `text/html`, `text/css`, `application/javascript`, `image/jpeg`, `image/gif`, `image/png`, `image/x-icon`, `application/pdf`, and `text/plain`. Unrecognized extensions fall back to `application/octet-stream`, which tells the browser to treat the body as raw binary data and offer a download prompt.

---

## 3. How Multithreading Works

Without multithreading, the server would process one client at a time. While reading a large file for one client, every other browser waiting to connect would be blocked. This is called a sequential or single-threaded server.

Our server avoids this by spawning a new `Thread` for every accepted connection:

```
main thread                  worker thread (per client)
───────────                  ──────────────────────────
accept() → Socket            processRequest()
new HttpRequest(socket)        read request line
new Thread(request)            open file
thread.start()       ─────▶   send response headers
accept() (next)               send file bytes
                              close socket
```

Java's `Thread` class maps to an OS thread, so on a multi-core machine multiple requests run physically in parallel. The main thread never blocks on I/O — it only calls `accept()`, which yields to the OS when idle. Each worker thread manages its own socket and streams independently.

A potential concern with this design is resource exhaustion: if thousands of clients connect at once, thousands of threads would be created. In a production server this is solved with a thread pool (e.g., Java's `ExecutorService`). For the scope of this project, one-thread-per-connection is correct and straightforward.

---

## 4. HTTP Request and Response Handling

### Request (browser → server)

An HTTP/1.0 GET request looks like this on the wire:

```
GET /index.html HTTP/1.0\r\n
Host: localhost:6789\r\n
User-Agent: Mozilla/5.0 ...\r\n
\r\n
```

The server reads this line by line. Only the first line (the request line) is used — the method token is discarded, and the URI token is parsed to get the file name. The rest of the headers are read and discarded (the blank line terminates the loop).

### Response (server → browser)

A successful HTTP/1.0 response looks like this:

```
HTTP/1.0 200 OK\r\n
Content-Type: text/html\r\n
\r\n
<html>...</html>
```

The status line, each header, and the blank line all end with `\r\n` (CRLF) as required by the HTTP specification. The body immediately follows with no separator. For a 404 error the status line becomes `HTTP/1.0 404 Not Found` and the body is a short HTML page.

---

## 5. Problems Encountered

**Serving binary files correctly.** An early design used a `PrintWriter` to send the response body, which worked for HTML but silently corrupted image files because `PrintWriter` performs character encoding. The fix was to switch to `DataOutputStream` for the entire response and use the `sendBytes` method with a raw byte buffer.

**Default document for bare `/` requests.** When a browser navigates to `http://localhost:6789/` the URI is `/`. After stripping the leading slash the file name becomes an empty string, and `new FileInputStream("")` throws an exception. The fix was to check for an empty file name after stripping and default to `index.html`.

**Stale IDE diagnostics.** After replacing the skeleton file with the full implementation, the IDE still reported warnings about unused methods from the old code. These resolved once the IDE re-indexed the file.

---

## 6. Screenshots

**Screenshot 1 — Server console output**

```
Web server started on port 6789
Visit: http://localhost:6789/index.html
Press Ctrl+C to stop.

Connection from: 127.0.0.1
Request: GET /index.html HTTP/1.1
  Host: localhost:6789
  Connection: keep-alive
  -> 200 OK  [index.html]

Connection from: 127.0.0.1
Request: GET /style.css HTTP/1.1
  Host: localhost:6789
  -> 200 OK  [style.css]

Connection from: 127.0.0.1
Request: GET /missing.html HTTP/1.1
  Host: localhost:6789
  -> 404 Not Found  [missing.html]
```

*The server logs each connection, prints the request line and headers, and reports the status sent back.*

**Screenshot 2 — Browser showing index.html**

The browser displays the styled homepage served by the Java server at `http://localhost:6789/index.html`. The page includes a navigation bar, heading, paragraph text, and a bulleted list, all styled by `style.css` which is fetched in a separate request.

**Screenshot 3 — Browser showing 404 page**

Navigating to `http://localhost:6789/notfound.html` causes the server to return a `404 Not Found` response with a simple HTML error page displayed in the browser.

---

## 7. References

1. Kurose, J. F., & Ross, K. W. (2021). *Computer Networking: A Top-Down Approach* (8th ed.). Pearson. — Chapter 2 (Application Layer), HTTP protocol specification and socket programming examples.

2. Oracle Corporation. (2024). *Java SE 21 API Documentation*. Retrieved from https://docs.oracle.com/en/java/docs/api/ — `java.net.ServerSocket`, `java.net.Socket`, `java.io.DataOutputStream`, `java.io.FileInputStream`.

3. Fielding, R., & Reschke, J. (2014). *Hypertext Transfer Protocol (HTTP/1.1): Message Syntax and Routing*. RFC 7230. Internet Engineering Task Force (IETF).

4. Mozilla Developer Network. (2024). *HTTP Messages*. MDN Web Docs. Retrieved from https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages

5. Eckel, B. (2006). *Thinking in Java* (4th ed.). Prentice Hall. — Chapter on networking and multithreading.
