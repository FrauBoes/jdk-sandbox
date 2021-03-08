/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Basic tests for SimpleFileServerTest
 * @run testng/othervm SimpleFileServerTest
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.testng.Assert.*;

public class SimpleFileServerTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final InetSocketAddress WILDCARD_ADDR = new InetSocketAddress(0);

    static final boolean ENABLE_LOGGING = true;

    @BeforeTest
    public void setup() {
        if (ENABLE_LOGGING) {
            Logger logger = Logger.getLogger("com.sun.net.httpserver");
            ConsoleHandler ch = new ConsoleHandler();
            logger.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            logger.addHandler(ch);
        }
    }

    @Test
    public void testFileGet() throws Exception {
        var root = Files.createDirectory(CWD.resolve("xDir"));
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);

        var ss = SimpleFileServer.createFileServer(WILDCARD_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "aFile.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "some text");
            assertEquals(response.headers().firstValue("content-type").get(), "text/plain");
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testDirectoryGet() throws Exception {
        // TODO: why listing for >>>>&#x2F<<<<;
        var expectedBody = """
                <!DOCTYPE html>
                <html>
                <body><h2>Directory listing for &#x2F;</h2>
                <ul>
                <li><a href="yFile.txt">yFile.txt</a></li>
                </ul><p><hr>
                </body>
                </html>
                """;
        var root = Files.createDirectory(CWD.resolve("yDir"));
        var file = Files.writeString(root.resolve("yFile.txt"), "some text", CREATE);

        var ss = SimpleFileServer.createFileServer(WILDCARD_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
            assertEquals(response.body(), expectedBody);
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testNull() {
        final var addr = InetSocketAddress.createUnresolved("foo", 8080);
        final var path = Path.of("/tmp");
        final var levl = OutputLevel.DEFAULT;
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(null, null, null));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(null, null, levl));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(null, path, null));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(null, path, levl));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(addr, null, null));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(addr, null, levl));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(addr, path, null));

        assertThrows(NPE, () -> SimpleFileServer.createFileHandler(null));

        assertThrows(NPE, () -> SimpleFileServer.createOutputFilter(null, null));
        assertThrows(NPE, () -> SimpleFileServer.createOutputFilter(null, levl));
        assertThrows(NPE, () -> SimpleFileServer.createOutputFilter(System.out, null));
    }

    @Test
    public void testInitialSlashContext() {
        var ss = SimpleFileServer.createFileServer(WILDCARD_ADDR, CWD, OutputLevel.DEFAULT);
        ss.removeContext("/"); // throws if no context.
        ss.stop(0);
    }

    @Test
    public void testBound() {
        var ss = SimpleFileServer.createFileServer(WILDCARD_ADDR, CWD, OutputLevel.DEFAULT);
        var boundAddr = ss.getAddress();
        ss.stop(0);
        assertTrue(boundAddr.getAddress() != null);
        assertTrue(boundAddr.getPort() > 0);
    }

    @Test
    public void testIllegalPath() throws IOException {
        var addr = WILDCARD_ADDR;
        {   // not absolute
            Path p = Path.of(".");
            assert Files.isDirectory(p);
            assert Files.exists(p);
            assert !p.isAbsolute();
            var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.DEFAULT));
            assertTrue(iae.getMessage().contains("is not absolute"));
        }
        {   // not a directory
            Path p = Files.createFile(CWD.resolve("aFile"));
            assert !Files.isDirectory(p);
            var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.DEFAULT));
            assertTrue(iae.getMessage().contains("not a directory"));
        }
        {   // does not exist
            Path p = CWD.resolve("doesNotExist");
            assert !Files.exists(p);
            var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.DEFAULT));
            assertTrue(iae.getMessage().contains("does not exist"));
        }
        {   // is not readable
            Path p = Files.createDirectory(CWD.resolve("aDir"));
            p.toFile().setReadable(false);
            assert !Files.isReadable(p);
            try {
                // TODO: impl does not check if readable!
                //var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.DEFAULT));
                //assertTrue(iae.getMessage().contains("not readable"));
            } finally {
                p.toFile().setReadable(true);
            }
        }
    }

    static URI uri(HttpServer server, String path) {
        return URI.create("http://localhost:%s/%s".formatted(server.getAddress().getPort(), path));
    }
}