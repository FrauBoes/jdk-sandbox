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
 * @summary Tests for SimpleFileServer
 * @library /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.net.URIBuilder
 * @run testng/othervm SimpleFileServerTest
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import jdk.test.lib.Platform;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.testng.Assert.*;

public class SimpleFileServerTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    static final Class<UncheckedIOException> UIOE = UncheckedIOException.class;

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final Path TEST_DIR = CWD.resolve("SimpleFileServerTest");

    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    static final boolean ENABLE_LOGGING = true;
    static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    @BeforeTest
    public void setup() throws IOException {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            LOGGER.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            LOGGER.addHandler(ch);
        }
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
        Files.createDirectories(TEST_DIR);
    }

    @Test
    public void testFileGET() throws Exception {
        var root = Files.createDirectory(TEST_DIR.resolve("testFileGET"));
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(file);
        var expectedLength = Long.toString(Files.size(file));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "aFile.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "some text");
            assertEquals(response.headers().firstValue("content-type").get(), "text/plain");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testDirectoryGET() throws Exception {
        var expectedBody = """
                <!DOCTYPE html>
                <html>
                <body>
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                <li><a href="aFile.txt">aFile.txt</a></li>
                </ul>
                </body>
                </html>
                """;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = Files.createDirectory(TEST_DIR.resolve("testDirectoryGET"));
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(root);

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), expectedBody);
        } finally {
            ss.stop(0);
        }
    }

    @DataProvider
    public Object[][] indexFiles() {
        var fileContent = """
                <!DOCTYPE html>
                <html>
                <body>
                <h1>This is an index file</h1>
                </body>
                </html>
                """;
        var dirListing = """
                <!DOCTYPE html>
                <html>
                <body>
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                </ul>
                </body>
                </html>
                """;
        return new Object[][] {
                {"index.html", "text/html",                "77", fileContent, true},
                {"index.htm",  "text/html",                "77", fileContent, true},
                {"index.txt",  "text/html; charset=UTF-8", "95", dirListing,  false}
        };
    }

    @Test(dataProvider = "indexFiles")
    public void testDirectoryWithIndexGET(String filename,
                                          String contentType,
                                          String contentLength,
                                          String expectedContent,
                                          boolean serveIndexFile) throws Exception {
        var root = Files.createDirectories(TEST_DIR.resolve("testDirectoryWithIndexGET"));
        var lastModified = getLastModified(root);
        if (serveIndexFile) {
            Files.writeString(root.resolve(filename), expectedContent, CREATE);
        }
        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), contentType);
            assertEquals(response.headers().firstValue("content-length").get(), contentLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), expectedContent);
        } finally {
            ss.stop(0);
            if (serveIndexFile) {
                Files.delete(root.resolve(filename));
            }
        }
    }

    @Test
    public void testFileHEAD() throws Exception {
        var root = Files.createDirectory(TEST_DIR.resolve("testFileHEAD"));
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(file);
        var expectedLength = Long.toString(Files.size(file));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "aFile.txt"))
                    .method("HEAD", BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), "text/plain");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), "");
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testDirectoryHEAD() throws Exception {
        var expectedLength = Integer.toString(
                """
                <!DOCTYPE html>
                <html>
                <body>
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                <li><a href="aFile.txt">aFile.txt</a></li>
                </ul>
                </body>
                </html>
                """.getBytes(UTF_8).length);
        var root = Files.createDirectory(TEST_DIR.resolve("testDirectoryHEAD"));
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(root);

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, ""))
                    .method("HEAD", BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), "");
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testMovedPermanently() throws Exception {
        var root = Files.createDirectory(TEST_DIR.resolve("testMovedPermanently"));
        Files.createDirectory(root.resolve("aDirectory"));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var uri = uri(ss, "aDirectory");
            var request = HttpRequest.newBuilder(uri).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 301);
            assertEquals(response.headers().firstValue("content-length").get(), "0");
            assertEquals(response.headers().firstValue("location").get(), "%s/".formatted(uri));
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testForbidden() throws Exception {
        if (!Platform.isWindows()) {  // not applicable on Windows
            var root = Files.createDirectory(TEST_DIR.resolve("testForbidden"));
            var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);

            file.toFile().setReadable(false, false);
            assert !Files.isReadable(file);

            var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
            ss.start();
            try {
                var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
                var request = HttpRequest.newBuilder(uri(ss, "aFile.txt")).build();
                var response = client.send(request, BodyHandlers.ofString());
                assertEquals(response.statusCode(), 403);
                assertEquals(response.headers().firstValue("content-length").get(), "0");
            } finally {
                ss.stop(0);
                file.toFile().setReadable(true, false);
            }
        }
    }

    @Test
    public void testNotFoundGET() throws Exception {
        var expectedBody = """
                <!DOCTYPE html>
                <html>
                <body>
                <h1>File not found</h1>
                <p>&#x2F;doesNotExist.txt</p>
                </body>
                </html>
                """;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = Files.createDirectory(TEST_DIR.resolve("testNotFoundGET"));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "doesNotExist.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), expectedBody);
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testNotFoundHEAD() throws Exception {
        var expectedBody = """
                <!DOCTYPE html>
                <html>
                <body>
                <h1>File not found</h1>
                <p>&#x2F;doesNotExist.txt</p>
                </body>
                </html>
                """;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = Files.createDirectory(TEST_DIR.resolve("testNotFoundHEAD"));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "doesNotExist.txt"))
                    .method("HEAD", BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), "");
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testSymlinkGET() throws Exception {
        var expectedBody = """
                <!DOCTYPE html>
                <html>
                <body>
                <h1>File not found</h1>
                <p>&#x2F;symlink</p>
                </body>
                </html>
                """;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = Files.createDirectory(TEST_DIR.resolve("testSymlinkGET"));
        var symlink = root.resolve("symlink");
        var target = Files.writeString(root.resolve("target.txt"), "some text", CREATE);
        try {
            Files.createSymbolicLink(symlink, target);
        } catch (IOException x) {
            System.err.println(x);
        }

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "symlink")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), expectedBody);
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testHiddenFileGET() throws Exception {
        var root = Files.createDirectory(TEST_DIR.resolve("testHiddenFileGET"));
        var file = createHiddenFile(root);
        var fileName = file.getFileName().toString();
        System.out.println(fileName);
        var expectedBody = """
                <!DOCTYPE html>
                <html>
                <body>
                <h1>File not found</h1>
                <p>&#x2F;""" + fileName +
                """
                </p>
                </body>
                </html>
                """;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, fileName)).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), expectedBody);
        } finally {
            ss.stop(0);
        }
    }

    private Path createHiddenFile(Path root) throws IOException {
        Path file;
        if (Platform.isWindows()) {
            file = Files.createFile(root.resolve("aFile.txt"));
            Files.setAttribute(file, "dos:hidden", true, LinkOption.NOFOLLOW_LINKS);
        } else {
            file = Files.writeString(root.resolve(".aFile.txt"), "some text", CREATE);
        }
        assertTrue(Files.isHidden(file));
        return file;
    }

    @Test
    public void testNull() {
        final var addr = InetSocketAddress.createUnresolved("foo", 8080);
        final var path = Path.of("/tmp");
        final var levl = OutputLevel.INFO;
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
        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, TEST_DIR, OutputLevel.INFO);
        ss.removeContext("/"); // throws if no context.
        ss.stop(0);
    }

    @Test
    public void testBound() {
        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, TEST_DIR, OutputLevel.INFO);
        var boundAddr = ss.getAddress();
        ss.stop(0);
        assertTrue(boundAddr.getAddress() != null);
        assertTrue(boundAddr.getPort() > 0);
    }

    @Test
    public void testIllegalPath() throws Exception {
        var addr = LOOPBACK_ADDR;
        {   // not absolute
            Path p = Path.of(".");
            assert Files.isDirectory(p);
            assert Files.exists(p);
            assert !p.isAbsolute();
            var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.INFO));
            assertTrue(iae.getMessage().contains("is not absolute"));
        }
        {   // not a directory
            Path p = Files.createFile(TEST_DIR.resolve("aFile"));
            assert !Files.isDirectory(p);
            var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.INFO));
            assertTrue(iae.getMessage().contains("not a directory"));
        }
        {   // does not exist
            Path p = TEST_DIR.resolve("doesNotExist");
            assert !Files.exists(p);
            var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.INFO));
            assertTrue(iae.getMessage().contains("does not exist"));
        }
        {   // not readable
            if (!Platform.isWindows()) {  // not applicable on Windows
                Path p = Files.createDirectory(TEST_DIR.resolve("aDir"));
                p.toFile().setReadable(false, false);
                assert !Files.isReadable(p);
                try {
                    var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.INFO));
                    assertTrue(iae.getMessage().contains("not readable"));
                } finally {
                    p.toFile().setReadable(true, false);
                }
            }
        }
        {   // not of the default file system
            Path p = createDirectoryInZipFs(TEST_DIR.resolve("aFile.zip")).toAbsolutePath();
            assert p.getFileSystem() != FileSystems.getDefault();
            try {
                var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.INFO));
                assertTrue(iae.getMessage().contains("not associated with the system-default file system"));
            } finally {
                p.getFileSystem().close();
            }
        }
    }

    static Path createDirectoryInZipFs(Path zipFile) throws Exception {
        var fs = FileSystems.newFileSystem(zipFile, Map.of("create", "true"));
        var file = fs.getPath("fileInZip");
        if (Files.notExists(file)) {
            Files.createDirectory(file);
        }
        return file;
    }

    @Test
    public void testUncheckedIOException() {
        var addr = InetSocketAddress.createUnresolved("foo", 8080);
        assertThrows(UIOE, () -> SimpleFileServer.createFileServer(addr, TEST_DIR, OutputLevel.INFO));
    }

    @Test
    public void testXss() throws Exception {
        var root = Files.createDirectory(TEST_DIR.resolve("testXss"));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "beginDelim%3C%3EEndDelim")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertTrue(response.body().contains("beginDelim%3C%3EEndDelim"));
            assertTrue(response.body().contains("File not found"));
        } finally {
            ss.stop(0);
        }
    }

    @AfterTest
    public void teardown() throws IOException {
        if (Files.exists(TEST_DIR)) {
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        }
    }

    static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/" + path)
                .buildUnchecked();
    }

    static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss v");

    static String getLastModified(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.of("GMT"))
                .format(HTTP_DATE_FORMATTER);
    }
}
