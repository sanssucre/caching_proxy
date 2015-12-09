package com.ypg.proxy;

import java.util.HashMap;
import java.util.Map;

/**
 * By: Alain Gaeremynck(alain.gaeremynck@ypg.com) (@sanssucre)
 * On: 12-04-24 / 3:17 PM
 * TODO : add Unit Tests AND javadoc for this class
 */
public class CachedResponse {

    private String body;

    private Map<String, String> headers = new HashMap<String, String>();

    public CachedResponse() {
        headers.put("Content-Type","application/json;charset=UTF-8");
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void addHeader(String name, String value){
        headers.put(name,value);
    }

    public void appendToBody(String s){
        if(body == null)
            body = s;
        else
            body+=s;
    }

    @Override
    public String toString() {
        return "CachedResponse{" +
                "body='" + body + '\'' +
                ", headers=" + headers +
                '}';
    }

}
