package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

interface Constrains {

    String localhost = "localhost";
    String host = "192.168.1.11";
    String currentHost = localhost;
    int port = 8877;
    String maxLimitResponse = "Maximum number of requests. Please try again";

    TimeUnit timeUnit = TimeUnit.SECONDS;
    int requestLimit = 1;

    String createDocsPath = "/api/create";

    RestController.HttpResponse successResponse = RestController.HttpResponse.builder()
            .data("Success")
            .status(200)
            .build();

    RestController.HttpResponse failResponse = RestController.HttpResponse.builder()
            .data("Fail")
            .status(500)
            .build();

    RestController.HttpResponse badRequestResponse = RestController.HttpResponse.builder()
            .data("Bad request")
            .status(400)
            .build();

    RestController.HttpResponse notFoundResponse = RestController.HttpResponse.builder()
            .data("Not Found")
            .status(404)
            .build();

    /**
     * <dependency>
     *      <groupId>com.fasterxml.jackson.core</groupId>
     *      <artifactId>jackson-databind</artifactId>
     *      <version>2.15.0</version>
     *      <scope>compile</scope>
     * </dependency>
     */

}

public class CrptApi {

    public static void main(String[] args) throws IOException {
        CrptApi crptApi = new CrptApi(Constrains.timeUnit, Constrains.requestLimit);
        RestController restController = new RestController(crptApi);
    }

    private final long timeUnit;
    private final int requestLimit;

    private final HttpServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Deque<Long> requests;

    public CrptApi(TimeUnit timeUnit, int requestLimit) throws IOException {
        this.requests = new ArrayDeque<>();
        this.timeUnit = TimeUnit.MILLISECONDS.convert(1, timeUnit);
        this.requestLimit = requestLimit;
        this.server = HttpServer.create(new InetSocketAddress(Constrains.currentHost, Constrains.port), 0);

        this.serverStart();
    }

    public void create(String path, Consumer<HttpExchange> handler) {
        server.createContext(path, req -> {
            if (isNotLimitMax()) {
                requests.add(System.currentTimeMillis());
                handler.accept(req);
            } else
                maxLimitRequest(req);
        });
    }

    private boolean isNotLimitMax() {
        long current = System.currentTimeMillis();
        while (Objects.nonNull(requests.peek()) && current - requests.peek() > timeUnit)
            requests.pop();
        return requests.size() < requestLimit;
    }

    private void maxLimitRequest(HttpExchange req) {
        OutputStream outputStream = req.getResponseBody();
        try {
            req.sendResponseHeaders(502, Constrains.maxLimitResponse.length());
            outputStream.write(Constrains.maxLimitResponse.getBytes());
            outputStream.flush();
            outputStream.close();
        } catch (IOException ignored) {
        }
    }

    public <T> T createBody(HttpExchange httpExchange, Class<T> clazz) {
        try {
            String body = new String(httpExchange.getRequestBody().readAllBytes());
            return objectMapper.readValue(body, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String getPathVariable(HttpExchange httpExchange, String path) {
        String reqPath = httpExchange.getRequestURI().getPath();
        String pathVariable = reqPath.split(path)[1].substring(1);
        if (pathVariable.contains("/") || pathVariable.contains("?"))
            throw new IllegalArgumentException("Bad request");
        return pathVariable;
    }


    private void serverStart() {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(requestLimit * 2);
        this.server.setExecutor(threadPoolExecutor);
        this.server.start();
    }
}


class RestController {

    private final CrptApi crptApi;


    public RestController(CrptApi crptApi) {
        this.crptApi = crptApi;
        addControllerCreateDocs(Constrains.createDocsPath);
    }

    private HttpResponse createDocs(String signature, Document document) {
        //TODO:
        try {
            Thread.sleep(1);
        } catch (InterruptedException ignored) {}
        return HttpResponse.builder()
                .data("You successfully create document with signature=" + signature + ", id=" + UUID.randomUUID())
                .status(200)
                .build();
    }


    private void addControllerCreateDocs(String path) {
        crptApi.create(path, httpExchange -> {

            HttpResponse res;

            try {
                if ("POST".equals(httpExchange.getRequestMethod())) {

                    Document document = crptApi.createBody(httpExchange, Document.class);
                    String sign = crptApi.getPathVariable(httpExchange, path);

                    res = createDocs(sign, document);
                } else {
                    res = Constrains.notFoundResponse;
                }
            } catch (IllegalArgumentException e) {
                res = Constrains.badRequestResponse;
            } catch (Exception e) {
                res = Constrains.failResponse;
            }
            handleResponse(httpExchange, res);
        });
    }


    private void handleResponse(HttpExchange httpExchange, HttpResponse res) {
        OutputStream outputStream = httpExchange.getResponseBody();
        try {
            httpExchange.sendResponseHeaders(res.getStatus(), res.getData().length());
            outputStream.write(res.getData().getBytes());
            outputStream.flush();
            outputStream.close();
        } catch (IOException ignored) {
        }
    }

    static class Document {

        private String data;

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    static class HttpResponse {

        private String data;
        private int status;

        public String getData() {
            return data;
        }

        public int getStatus() {
            return status;
        }

        static class HttpResponseBuilder {
            private final HttpResponse httpResponse;

            public HttpResponseBuilder() {
                this.httpResponse = new HttpResponse();
            }

            public HttpResponseBuilder data(String data) {
                httpResponse.data = data;
                return this;
            }

            public HttpResponseBuilder status(int status) {
                httpResponse.status = status;
                return this;
            }

            public HttpResponse build() {
                return httpResponse;
            }
        }

        public static HttpResponseBuilder builder() {
            return new HttpResponseBuilder();
        }
    }
}

