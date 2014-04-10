package com.fasterxml.jackson.jaxrs.json.dw;

import java.io.*;
import java.net.*;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.jetty.server.Server;
import org.junit.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class SimpleEndpointTestBase extends ResourceTestBase
{
    final static int TEST_PORT = 6011;
    
    static class Point {
        public int x, y;

        protected Point() { }
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    @Path("/point")
    public static class SimpleResource
    {
        @Path("/javascript")
        @GET
        @Produces({ MediaType.APPLICATION_JSON, "application/javascript" })
        public Point getPointJS() {
            return new Point(1, 2);
        }

        @Path("/jsonx")
        @GET
        @Produces({ MediaType.APPLICATION_JSON, "text/x-json" })
        public Point getPointJSONX() {
            return new Point(1, 2);
        }
    }

    public static class SimpleResourceApp extends JsonApplicationWithJackson {
        public SimpleResourceApp() { super(new SimpleResource()); }
    }

    private final static String UNTOUCHABLE_RESPONSE = "[1]";

    @Path("/raw")
    public static class RawResource
    {
        @GET
        @Produces({ MediaType.APPLICATION_JSON, "application/javascript" })
        @Path("string")
        public String getString() {
            return UNTOUCHABLE_RESPONSE;
        }

        @GET
        @Path("bytes")
        @Produces({ MediaType.APPLICATION_JSON, "application/javascript" })
        public byte[] getBytes() throws IOException {
            return UNTOUCHABLE_RESPONSE.getBytes("UTF-8");
        }
    }

    public static class SimpleRawApp extends JsonApplicationWithJackson {
        public SimpleRawApp() { super(new RawResource()); }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testStandardJson() throws Exception
    {
        final ObjectMapper mapper = new ObjectMapper();
        Server server = startServer(TEST_PORT, SimpleResourceApp.class);
        Point p;

        try {
            InputStream in = new URL("http://localhost:"+TEST_PORT+"/point/javascript").openStream();
            p = mapper.readValue(in, Point.class);
            in.close();
        } finally {
            server.stop();
        }
        // ensure we got a valid Point
        assertNotNull(p);
        assertEquals(1, p.x);
        assertEquals(2, p.y);
    }

    public void testAcceptJavascriptType() throws Exception
    {
        final ObjectMapper mapper = new ObjectMapper();
        Server server = startServer(TEST_PORT, SimpleResourceApp.class);
        URL urlJS = new URL("http://localhost:"+TEST_PORT+"/point/javascript");
        URL urlJsonX = new URL("http://localhost:"+TEST_PORT+"/point/jsonx");

        try {
            HttpURLConnection conn = (HttpURLConnection) urlJS.openConnection();

            // First: verify that weird types are not supported...
            conn.setRequestProperty("Accept", "foo/bar");
            conn.connect();
            assertEquals(HttpURLConnection.HTTP_NOT_ACCEPTABLE, conn.getResponseCode());
            conn.disconnect();

            // try again with somewhat non-standard, but supported JSON-like type (application/javascript)
            conn = (HttpURLConnection) urlJS.openConnection();
            conn.setRequestProperty("Accept", "application/javascript");
            assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
            InputStream in = conn.getInputStream();
            Point p;
            try {
                p = mapper.readValue(in, Point.class);
            } finally {
                in.close();
            }
            assertNotNull(p);
            assertEquals(1, p.x);
            assertEquals(2, p.y);
            conn.disconnect();

            // [Issue#40]: another oddball type to consider
            conn = (HttpURLConnection) urlJsonX.openConnection();
            conn.setRequestProperty("Accept", "text/x-json");
            assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
            in = conn.getInputStream();
            p = null;
            try {
                p = mapper.readValue(in, Point.class);
            } finally {
                in.close();
            }
            assertNotNull(p);
            assertEquals(1, p.x);
            assertEquals(2, p.y);
            conn.disconnect();
        } finally {
            server.stop();
        }
    }

    // [Issue#34] Verify that Untouchables act the way as they should
    @SuppressWarnings("resource")
    public void testUntouchables() throws Exception
    {
        Server server = startServer(TEST_PORT, SimpleRawApp.class);
        try {
            InputStream in = new URL("http://localhost:"+TEST_PORT+"/raw/string").openStream();
            assertEquals(UNTOUCHABLE_RESPONSE, readUTF8(in));

            in = new URL("http://localhost:"+TEST_PORT+"/raw/bytes").openStream();
            Assert.assertArrayEquals(UNTOUCHABLE_RESPONSE.getBytes("UTF-8"), readAll(in));
        } finally {
            server.stop();
        }
    }
}