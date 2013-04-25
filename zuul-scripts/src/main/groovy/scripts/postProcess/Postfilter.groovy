/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package scripts.postProcess


import com.netflix.util.Pair
import com.netflix.zuul.context.NFRequestContext
import com.netflix.zuul.stats.ErrorStatsManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner


import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.netflix.zuul.groovy.ZuulFilter
import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.groovy.ZuulFilter

class Postfilter extends ZuulFilter {

    Postfilter() {

    }

    boolean shouldFilter() {
        if (true.equals(NFRequestContext.getCurrentContext().proxyToProxy)) return false; //request was routed to a zuul server, so don't send response headers
        return true
    }

    Object run() {
        addStandardResponseHeaders(RequestContext.getCurrentContext().getRequest(), RequestContext.getCurrentContext().getResponse())
        return null;
    }


    void addStandardResponseHeaders(HttpServletRequest req, HttpServletResponse res) {
        println(originatingURL)

        String origin = req.getHeader("Origin")
        RequestContext context = RequestContext.getCurrentContext()
        List<Pair<String, String>> headers = context.getProxyResponseHeaders()
        headers.add(new Pair("X-Zuul", "zuul"))
        headers.add(new Pair("X-Zuul-instance", System.getenv("EC2_INSTANCE_ID") ?: "unknown"))
        headers.add(new Pair("Connection", "keep-alive"))
        headers.add(new Pair("X-Originating-URL", originatingURL))

        // trying to force flushes down to clients without Apache mod_proxy buffering
//        headers.add(new Pair("Transfer-Encoding", "chunked"));
//        res.setContentLength(-1);

        if (context.get("ErrorHandled") == null && context.responseStatusCode >= 400) {
            headers.add(new Pair("X-Netflix-Error-Cause", "Error from API Backend"))
            ErrorStatsManager.manager.putStats(RequestContext.getCurrentContext().route, "Error_from_API_Server")

        }
    }

    String getOriginatingURL() {
        HttpServletRequest request = NFRequestContext.getCurrentContext().getRequest();

        String protocol = request.getHeader("X-Forwarded-Proto")
        if (protocol == null) protocol = "http"
        String host = request.getHeader("Host")
        String uri = request.getRequestURI();
        def URL = "${protocol}://${host}${uri}"
        if (request.getQueryString() != null) {
            URL += "?${request.getQueryString()}"
        }
        return URL
    }

    @Override
    String filterType() {
        return 'post'
    }

    @Override
    int filterOrder() {
        return 10
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit {

        @Mock
        HttpServletResponse response
        @Mock
        HttpServletRequest request

        @Before
        public void before() {
            RequestContext.setContextClass(NFRequestContext.class);
        }

        @Test
        public void testHeaderResponse() {

            def f = new Postfilter();
            f = Mockito.spy(f)
            RequestContext.getCurrentContext().setRequest(request)
            RequestContext.getCurrentContext().setResponse(response)
            f.runFilter()
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("X-Zuul", "Zuul"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("X-Zuul-instance", System.getenv("EC2_INSTANCE_ID") ?: "unknown"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("Access-Control-Allow-Origin", "*"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("Access-Control-Allow-Credentials", "true"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("Access-Control-Allow-Headers", "Authorization,Content-Type,Accept,X-Netflix.application.name,X-Netflix.application.version,X-Netflix.esn,X-Netflix.device.type,X-Netflix.certification.version,X-Netflix.request.uuid,X-Netflix.user.id,X-Netflix.oauth.consumer.key,X-Netflix.oauth.token"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("Access-Control-Allow-Methods", "GET, POST"))
            RequestContext.getCurrentContext().proxyResponseHeaders.add(new Pair("Connection", "keep-alive"))


            Assert.assertTrue(RequestContext.getCurrentContext().getProxyResponseHeaders().contains(new Pair("X-Zuul", "Zuul")))
            Assert.assertTrue(RequestContext.getCurrentContext().getProxyResponseHeaders().contains(new Pair("Access-Control-Allow-Origin", "*")))
            Assert.assertTrue(RequestContext.getCurrentContext().getProxyResponseHeaders().contains(new Pair("Access-Control-Allow-Credentials", "true")))
            Assert.assertTrue(RequestContext.getCurrentContext().getProxyResponseHeaders().contains(new Pair("Access-Control-Allow-Headers", "Authorization,Content-Type,Accept,X-Netflix.application.name,X-Netflix.application.version,X-Netflix.esn,X-Netflix.device.type,X-Netflix.certification.version,X-Netflix.request.uuid,X-Netflix.user.id,X-Netflix.oauth.consumer.key,X-Netflix.oauth.token")))
            Assert.assertTrue(RequestContext.getCurrentContext().getProxyResponseHeaders().contains(new Pair("Access-Control-Allow-Methods", "GET, POST")))

            Assert.assertTrue(f.filterType().equals("post"))
            Assert.assertTrue(f.shouldFilter())
        }

    }

}