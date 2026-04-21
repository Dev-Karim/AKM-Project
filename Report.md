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

---

## Phase 2 — Reliable UDP File Transfer (RUDPDestination)

---

## 8. Phase 2 Solution Overview

Phase 2 extends the project to implement a reliable file-transfer protocol layered on top of UDP, mimicking core TCP behaviour (acknowledgements, retransmission on timeout, duplicate suppression) without using TCP itself.

### 8.1 Protocol Design

A custom 9-byte binary header precedes every payload:

```
Byte  0      : packet type  (1 = FILENAME | 2 = DATA | 3 = END)
Bytes 1–4    : offset       (big-endian int — start byte or total size for END)
Bytes 5–8    : length       (big-endian int — payload byte count)
Bytes 9+     : payload      (UTF-8 filename string, or raw file bytes)
```

ACK packets use the same 9-byte format with no payload; the type and offset fields are echoed so the sender knows exactly which packet is being acknowledged.

Three packet types structure the exchange:

1. **FILENAME** — the source sends the filename first; the destination ACKs before any data flows.
2. **DATA** — one chunk per stop-and-wait round trip; the offset field is the byte position within the file.
3. **END** — signals completion; the offset field carries the total byte count so the destination can verify integrity.

### 8.2 RUDPDestination Logic

`RUDPDestination` runs an outer `while(true)` loop so the server stays alive across multiple file transfers. For each transfer it follows three phases:

**Phase 1 — Filename handshake.**  
The server blocks on `socket.receive()` ignoring all non-FILENAME packets until the client identifies itself. Once the filename arrives, an ACK is sent and the output filename is derived by inserting `-copy` before the extension (e.g., `report.pdf` → `report-copy.pdf`).

**Phase 2 — Ordered data reception.**  
A `nextExpectedOffset` counter tracks contiguous bytes received. For every DATA packet:
- `offset == nextExpectedOffset` → chunk is written to `FileOutputStream`, counter advances, `OK` is printed, ACK is sent.
- `offset < nextExpectedOffset` → duplicate (ACK was lost and the source retransmitted); `DISCARDED` is printed and an ACK is still sent so the source can advance.
- `offset > nextExpectedOffset` → gap that cannot occur in stop-and-wait; silently discarded.

**Phase 3 — END handshake.**  
On receiving `TYPE_END`, the server ACKs, closes the file, and prints `[COMPLETE]`.

### 8.3 Duplicate-ACK Strategy

Sending an ACK for a duplicate is intentional and critical. If the destination remained silent on duplicates, the source's retransmission timer would fire repeatedly, never advancing. By re-ACKing duplicates the destination lets the source know it can move forward.

---

## 9. AI Comparison — RUDPDestination

### 9.1 AI-Generated Solution (ChatGPT 4o)

ChatGPT was prompted with the full Phase 2 requirements and asked to produce a `RUDPDestination.java`. The resulting code used the following approach:

- **Packet format:** a text-based header (`"SEQ:<n>|LEN:<n>|TYPE:<str>|"`) prepended to a raw byte array, parsed with `String.split`.
- **File assembly:** a `TreeMap<Integer, byte[]>` keyed on sequence number to buffer all chunks before writing.
- **Duplicate detection:** checking whether the sequence number already existed in the `TreeMap`.
- **Termination:** a special packet whose payload was the ASCII string `"END"`.
- **ACK format:** a short UTF-8 string `"ACK:<seqNum>"` sent back.

The AI solution compiled and ran, but had several issues described below.

### 9.2 Side-by-Side Comparison

| Dimension | Our Implementation | AI-Generated Implementation |
|---|---|---|
| **Header format** | Fixed 9-byte binary (1 type + 4 offset + 4 length) | Variable-length ASCII text header (`"SEQ:…\|LEN:…\|TYPE:…\|"`) |
| **Offset semantics** | Byte offset into the file — naturally handles any chunk size | Packet sequence number — requires client and server to agree on chunk size to reconstruct file correctly |
| **Duplicate handling** | Re-ACKs duplicate; writes only in-order chunks | Inserts duplicate into `TreeMap` (overwrite); no re-ACK sent for duplicates |
| **File assembly** | Streams directly to `FileOutputStream` as chunks arrive in order | Buffers *all* chunks in `TreeMap` in RAM, then writes entire file at end |
| **END signal** | Dedicated `TYPE_END = 3` byte; offset field carries total byte count for verification | ASCII string `"END"` in payload; no integrity check |
| **ACK format** | 9-byte binary mirroring request header | Variable-length ASCII string `"ACK:<seq>"` |
| **Multiple transfers** | Outer `while(true)` loop; server persists across sessions | Server exits after one file |
| **Output filename** | `buildCopyName()` — inserts `-copy` before extension | Simply appends `-copy` after the full filename including extension (`report.pdf-copy`) |
| **Error robustness** | Skips malformed packets (`pktLen < HEADER_SIZE`) | No length guard — `String.split` on a binary payload throws `ArrayIndexOutOfBoundsException` on non-text data |
| **Binary file support** | `FileOutputStream` with raw byte array — safe for any file type | Mixes text parsing (`new String(data)`) with binary write — corrupts non-UTF-8 files (images, PDFs) |

### 9.3 Pros and Cons of the AI Solution

**Pros:**
- Produced a working skeleton very quickly (~10 seconds).
- The `TreeMap` buffering idea is correct for a *sliding-window* protocol, where packets can arrive out of order — a useful concept to adopt if we later extend to Go-Back-N or Selective Repeat.
- The ASCII ACK format (`"ACK:<seq>"`) is human-readable and easy to debug without a packet inspector.

**Cons:**
- The text-based header breaks on binary file payloads because arbitrary bytes are not valid UTF-8. Any image or PDF would be corrupted.
- Using a sequence number instead of a byte offset makes reassembly dependent on the chunk size being fixed and equal on both sides — fragile if either side changes its buffer size.
- Not re-ACKing duplicates stalls the sender: after a lost ACK, the sender retransmits and the destination silently discards, so the sender times out and retransmits indefinitely.
- The `TreeMap` buffers the entire file in memory. For large files this causes an `OutOfMemoryError`.
- The server exits after one transfer, requiring a manual restart between files.
- The filename is saved as `<name>-copy` even when the original has an extension (e.g., `photo.jpg-copy` instead of `photo-copy.jpg`), making the file unrecognisable to the OS.

### 9.4 Improvements Applied from AI Review

Two ideas from the AI solution were evaluated and one was adapted:

**Idea adopted — explicit END integrity check.**  
The AI's solution printed how many total bytes the server expected to receive. We incorporated this by storing the total byte count in the `offset` field of the END packet and comparing it to `nextExpectedOffset` at the destination. This surfaces silent data loss ("received 4096 bytes but expected 4100") that would otherwise go unnoticed.

**Idea considered but rejected — `TreeMap` buffering.**  
Buffering all chunks before writing is correct for sliding-window receivers. For the current stop-and-wait design it is unnecessary overhead. The `TreeMap` approach will be revisited if Phase 2 is extended to Go-Back-N.

The concrete improvement — the byte-count verification in the END packet — is visible in `RUDPDestination.java` lines where `totalBytes` is extracted from `offset` and logged against `nextExpectedOffset`.

---

## 10. Phase 2 References

1. Kurose, J. F., & Ross, K. W. (2021). *Computer Networking: A Top-Down Approach* (8th ed.). Pearson. — Chapter 3, "Building a Reliable Data Transfer Protocol" (rdt 2.x, rdt 3.0, stop-and-wait).

2. Oracle Corporation. (2024). *Java SE 21 API Documentation* — `DatagramSocket`, `DatagramPacket`, `ByteBuffer`, `FileOutputStream`.

3. OpenAI. (2025). *ChatGPT 4o* [Large language model]. Used to generate an alternative `RUDPDestination.java` for comparison purposes as required by the assignment grading rubric. The AI-generated code was reviewed, not copied; see Section 9 for the full analysis.
