package de.sofacockpit.app;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class TvIconHttpTest {
    private static final String BASE="http://192.0.2.5:34831/data/user_de/0/com.tcl.ttvs/files/";
    private static final byte[] PNG={(byte)137,80,78,71,13,10,26,10,1,2,3};
    @Test public void acceptsOnlyObservedEndpointOnNumericPairedPeer() throws Exception {
        InetAddress peer=InetAddress.getByAddress(new byte[]{(byte)192,0,2,5});
        assertNotNull(TvIconHttp.requestPath(BASE+"screenShot/_data_app_%7E%7Efoo%3D.png",peer));
        assertNotNull(TvIconHttp.requestPath(BASE+"_data_app_%7E%7Efoo.jpg",peer));
        for(String url:new String[]{BASE.replace("192.0.2.5","192.0.2.6")+"a.png",
                BASE.replace("34831","80")+"a.png",BASE.replace("http:","https:")+"a.png",
                BASE+"../a.png",BASE+"%2e%2e/a.png",BASE+"a%2fb.png",BASE+"a%252fb.png",
                BASE+"a.png?q=x",BASE+"a.png#frag",BASE+"a.html",
                BASE.replace("192.0.2.5","other.invalid")+"a.png",
                BASE.replace("192.0.2.5","user@192.0.2.5")+"a.png"})
            assertNull(url,TvIconHttp.requestPath(url,peer));
    }
    private byte[] response(String head,byte[] body)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(head.getBytes(StandardCharsets.US_ASCII));b.write(body);
        return TvIconHttp.readResponse(new ByteArrayInputStream(b.toByteArray()),System.nanoTime()+2_000_000_000L);
    }
    @Test public void acceptsBoundedImageButNeverRedirectOrNonImage()throws Exception{
        assertArrayEquals(PNG,response("HTTP/1.0 200 OK\r\nContent-Type: image/png\r\nContent-Length: 11\r\n\r\n",PNG));
        assertArrayEquals(PNG,response("HTTP/1.0 200 OK\r\n\r\n",PNG));
        for(String head:new String[]{"HTTP/1.1 302 Found\r\nLocation: http://other.invalid/\r\n\r\n",
            "HTTP/1.0 200 OK\r\nContent-Length: 9999999\r\n\r\n",
            "HTTP/1.0 200 OK\r\nContent-Length: 12\r\n\r\n",
            "HTTP/1.0 200 OK\r\nContent-Type: text/html\r\n\r\n",
            "HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\n\r\n"}){
            try{response(head,PNG);fail(head);}catch(IOException expected){}
        }
    }
    @Test public void acceptsBoundedChunkedResponse()throws Exception{
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        body.write("b\r\n".getBytes(StandardCharsets.US_ASCII));body.write(PNG);
        body.write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(PNG,response("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n",body.toByteArray()));
    }
}
