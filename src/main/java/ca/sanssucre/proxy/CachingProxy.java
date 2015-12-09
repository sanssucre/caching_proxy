package ca.sanssucre.proxy;


import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * By: Alain Gaeremynck(alain.gaeremynck@ypg.com) (@sanssucre)
 * On: 12-04-23 / 3:46 PM
 * TODO : add Unit Tests AND javadoc for this class
 */
public class CachingProxy extends HttpServlet {

    public final Map<String, CachedResponse> cache = new HashMap<String, CachedResponse>();

    public volatile Map<String, Integer> callCounts = new HashMap<String, Integer>();

    private Logger logger = Logger.getLogger(getClass().getName());

    private Executor executor = Executors.newCachedThreadPool();

    private Set<String> domainAllowed = new HashSet<String>();

    private volatile int timeoutExceeded = 0;

    private volatile int apiCalls = 0;

    private volatile int totalRequestReceived = 0;

    private long timeOut = 30;

    private long modifyer = 0;

    private long[] waits = {75, 76, 77, 78, 79, 82, 84, 86, 88, 90, 92, 94, 96, 98,
            100, 105, 110, 115, 120, 125, 135, 145, 150, 175, 200, 225, 250, 300, 500, 600, 750, 1000};

    private volatile boolean doWait = false;

    public CachingProxy() throws Exception {
        domainAllowed.add("api.yellowpages.ca");
        domainAllowed.add("api.pagesjaunes.ca");
        domainAllowed.add("api.test02.yellowpages.ca");
        domainAllowed.add("api.yellowid.ca");
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo().contains("/printstats")) {

            printStats(response, (request.getQueryString() + "").contains("reset"));

        } else if (request.getPathInfo().contains("/settings")) {

            updateSettings(request, response);

        } else if (request.getPathInfo().contains("favicon")) {

            response.sendError(404);

        } else if (domainAllowed.contains(request.getServerName())) {

            reply(request, response);

        } else {

            response.sendRedirect("http://www.yellowpages.ca");

        }
    }

    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    private void reply(HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        totalRequestReceived++;
        setAttributes(request);
        String url = request.getScheme() + "://" + request.getServerName() + request.getPathInfo();
        String key = url + request.getAttribute("key");
        CachedResponse reply = null;
        if (cache.containsKey(key)) {
            reply = cache.get(key);
        } else {
            reply = getReply(key, getMethod(request, url));
            if (url.contains("yellowid"))
                System.out.println(request.getRequestURL() + " / \n" + reply);
        }
        if (reply != null && reply.getBody() != null) {
            Map<String, String> headers = reply.getHeaders();
            for (String headerName : headers.keySet()) {
                if (headerName != null && !headerName.contains("Transfer-Encoding") && !headerName.contains("Content-Encoding")) {
                    response.addHeader(headerName, headers.get(headerName));
                }

            }
            synchronized (response) {
                try {
                    if (doWait) {
                        long delay = totalRequestReceived % 10000 == 0 ? 20000 : waits[(totalRequestReceived + 1) % waits.length];
                        delay += modifyer;
                        response.wait(delay);
                    }
                    PrintWriter responseWriter = response.getWriter();
                    responseWriter.print(reply.getBody());
                    responseWriter.flush();
                    responseWriter.close();
                } catch (InterruptedException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }

            }
        } else {
            System.out.println(url + " reply is null");
        }
        int callCount = (callCounts.containsKey(key) ? callCounts.get(key) : 0);
        callCounts.put(key, ++callCount);
    }

    /**
     * Creates the caching key from parameters
     *
     * @param request being processed
     */
    private void setAttributes(HttpServletRequest request) {

        if (request.getMethod().toLowerCase().contains("post")) {
            String params = "";
            String body = "";
            Enumeration<String> pNames = request.getParameterNames();
            while (pNames.hasMoreElements()) {
                String s = pNames.nextElement();
                params += (s + request.getParameter(s));
            }
            request.setAttribute("params", params);
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    body += line;
                }
                request.setAttribute("body", body);
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage());
            }
            request.setAttribute("key",(params.length() > 0 || body.length() > 0 ? Integer.toString(params.hashCode() + body.hashCode())  : ""));
        }else
            request.setAttribute("key", "?" + request.getQueryString());

    }

    /**
     * @param request
     * @param url
     * @return
     */
    private HttpUriRequest getMethod(HttpServletRequest request, String url) {

        HttpUriRequest method;
        if (request.getMethod().equalsIgnoreCase("post")) {
            HttpPost pMethod = new HttpPost(url);
            if (request.getParameterMap().size() > 0) {
                HttpParams params = new BasicHttpParams();
                Enumeration<String> pNames = request.getParameterNames();
                while (pNames.hasMoreElements()) {
                    String pName = pNames.nextElement();
                    params.setParameter(pName, request.getParameter(pName));
                }
                pMethod.setParams(params);
            }
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
                String body = (String) request.getAttribute("body");
                if (body.length() > 0) {
                    StringEntity se = new StringEntity(body);
                    se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, request.getContentType()));
                    pMethod.setEntity(se);
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage());
            }

            method = pMethod;
        } else {
            if (request.getQueryString() != null)
                url += "?" + request.getQueryString();
            method = new HttpGet(url);
        }

        Enumeration<String> headers = request.getHeaderNames();
        while (headers.hasMoreElements()) {
            String hName = headers.nextElement();
            if(! hName.equalsIgnoreCase("content-length"))
                method.setHeader(new BasicHeader(hName, request.getHeader(hName)));
        }
        return method;
    }

    /**
     * @param resp
     * @param reset
     * @throws IOException
     */
    private void printStats(HttpServletResponse resp, boolean reset) throws IOException {
        resp.setContentType("text/html");
        PrintWriter pw = resp.getWriter();
        pw.append("<HTML>");
        pw.append("<HEAD><TITLE>api usage stats</TITLE></HEAD>");
        pw.append("<BODY>");
        pw.append("<h3>Time out exceeded: ").append(Integer.toString(timeoutExceeded))
                .append("; Total number of request peroxided : ").append(Integer.toString(apiCalls))
                .append("; Total amount of handled requests: ").append(Integer.toString(totalRequestReceived)).append("</h3>");
        pw.append("<UL>").append("cached items: ").append(Integer.toString(cache.size()));
        for (String s : callCounts.keySet()) {
            pw.append("<LI>").append(s).append(" => ").append(callCounts.get(s).toString()).append("</LI>");
        }
        pw.append("</UL>");
        pw.append("</BODY>");
        pw.append("</HTML>");
        pw.flush();
        pw.close();

        if (reset) {
            callCounts = new HashMap<String, Integer>();
            apiCalls = 0;
            totalRequestReceived = 0;
        }
    }

    /**
     * @param req
     * @param resp
     * @throws IOException
     * @throws ServletException
     */
    private void updateSettings(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        if (req.getMethod().toLowerCase().contains("post")) {
            logger.log(Level.INFO, "updating settings");

            String[] domains = (req.getParameter("domains") != null) ? req.getParameter("domains").split(",") : null;
            if (domains != null && domains.length > 0)
                domainAllowed = new HashSet<String>(Arrays.asList(domains));
            try {
                timeOut = Long.parseLong(req.getParameter("timeout"));
            } catch (Exception e) {
                // we don't care
            }
            if (req.getParameter("wait") != null)
                doWait = req.getParameter("wait").contains("true");

            if (req.getParameter("mod") != null) {
                modifyer = Long.parseLong(req.getParameter("mod"));
            }
        }

        PrintWriter pw = resp.getWriter();
        pw.append("<HTML>").
                append("<HEAD><TITLE>api usage stats</TITLE></HEAD>").
                append("<BODY>").
                append("\nTimout: ").append(Long.toString(timeOut)).append("<br/>").
                append("\nDomain Allowed: <ul>");
        for (String domain : domainAllowed) {
            pw.append("\n<li>").append(domain).append("</li>");
        }

        pw.append("</UL>").
                append("\n<p>waiting:").append(Boolean.toString(doWait)).append("</p>").
                append("\n<p>modifyer:").append(Long.toString(modifyer)).append("</p>").
                append("\n</BODY>").
                append("</HTML>").flush();
        pw.close();
    }


    /**
     * Invokes the API with the query string to get the actual data
     *
     * @param key to use
     * @return the data from the API
     */
    private CachedResponse getReply(final String key, final HttpUriRequest method) {
        logger.log(Level.WARNING, "fetching : " + key);
        Callable<CachedResponse> processor = new Callable<CachedResponse>() {
            @Override
            public CachedResponse call() throws Exception {
                CachedResponse apiReply = new CachedResponse();
                HttpClient client = createClient(method);
                HttpResponse response = client.execute(method);

                BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    apiReply.appendToBody(line);
                }
                reader.close();

                for (Header header : response.getAllHeaders()) {
                    apiReply.addHeader(header.getName(), header.getValue());
                }

                cache.put(key, apiReply);
                return apiReply;
            }
        };
        try {
            synchronized (cache) {
                apiCalls++;
                FutureTask<CachedResponse> invoker = new FutureTask<CachedResponse>(processor);
                executor.execute(invoker);
                return invoker.get(timeOut, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, e.getMessage());
        } catch (ExecutionException e) {
            logger.log(Level.WARNING, e.getMessage(),e);
        } catch (TimeoutException e) {
            timeoutExceeded++;
            logger.log(Level.WARNING, "exceeded timeout on " + key);
        }
        return null;
    }

    private HttpClient createClient(HttpUriRequest method) {
        //if(method.getURI().getScheme().equalsIgnoreCase("https"))
          //  return NonSecureHttpClient.getInstance();
        return new DefaultHttpClient();
    }


    public static void main(String[] args) throws Exception {
        CachingProxy proxy = new CachingProxy();
        HttpUriRequest method = new HttpGet("https://api.yellowid.ca/v1/authentication/getsocialproviders");
        CachedResponse resp = proxy.getReply("x",method);
        System.out.println(resp.getBody());

    }

}
